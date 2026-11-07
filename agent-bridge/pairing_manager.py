"""
Device Pairing & Cryptographic Authentication Manager for Agent Remote v2.0.
Enforces strict Single-Device Binding (INV-04), ECDSA P-256 Hardware Key Verification,
and zero-dependency RFC 6238 TOTP validation.
"""

import base64
import hashlib
import hmac
import json
import logging
import os
import secrets
import struct
import time
from typing import Any, Dict, Optional, Tuple
from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.hazmat.primitives import hashes, serialization

logger = logging.getLogger("agent-bridge.pairing")


class TotpHelper:
    """Zero-dependency RFC 6238 Time-based One-Time Password generator & validator."""

    @staticmethod
    def generate_secret(length: int = 20) -> str:
        """Generates a cryptographically random Base32 TOTP secret."""
        raw_bytes = secrets.token_bytes(length)
        return base64.b32encode(raw_bytes).decode("utf-8").replace("=", "")

    @staticmethod
    def generate_code(secret_base32: str, timestamp: Optional[float] = None, step: int = 30, digits: int = 6) -> str:
        """Generates 6-digit TOTP code for a given timestamp."""
        if timestamp is None:
            timestamp = time.time()
        counter = int(timestamp // step)
        key = base64.b32decode(secret_base32.upper(), casefold=True)
        msg = struct.pack(">Q", counter)
        h = hmac.new(key, msg, hashlib.sha1).digest()
        offset = h[-1] & 0x0F
        code = (struct.unpack(">I", h[offset:offset + 4])[0] & 0x7FFFFFFF) % (10 ** digits)
        return f"{code:0{digits}d}"

    @classmethod
    def verify_code(cls, secret_base32: str, code: str, window: int = 1) -> bool:
        """Verifies a 6-digit code with clock drift tolerance (default window +- 30s)."""
        clean_code = str(code).strip().zfill(6)
        now = time.time()
        for drift in range(-window, window + 1):
            if cls.generate_code(secret_base32, now + drift * 30) == clean_code:
                return True
        return False


class PairingManager:
    """Manages single-device pairing state, ECDSA P-256 public key verification, and pairing nonces."""

    def __init__(self, db_path: str = "paired_device.json"):
        self.db_path = db_path
        self._paired_data: Optional[Dict[str, Any]] = None
        self._active_nonce: Optional[str] = None
        self._active_totp_secret: Optional[str] = None
        self._nonce_expires_at: float = 0
        self.load()

    def load(self):
        """Loads paired device configuration from disk if present."""
        if os.path.exists(self.db_path):
            try:
                with open(self.db_path, "r", encoding="utf-8") as f:
                    self._paired_data = json.load(f)
                    fingerprint = self._paired_data.get("fingerprint", "unknown")
                    device_name = self._paired_data.get("device_name", "Unknown Device")
                    logger.info(f"Loaded paired device: {device_name} (Fingerprint: {fingerprint})")
            except Exception as e:
                logger.error(f"Failed to read paired device database from {self.db_path}: {e}")
                self._paired_data = None
        else:
            self._paired_data = None

    def save(self):
        """Persists paired device configuration to disk."""
        if self._paired_data:
            try:
                with open(self.db_path, "w", encoding="utf-8") as f:
                    json.dump(self._paired_data, f, indent=2)
                logger.info(f"Saved paired device configuration to {self.db_path}")
            except Exception as e:
                logger.error(f"Failed to write paired device to {self.db_path}: {e}")

    @property
    def is_paired(self) -> bool:
        """Returns True if a device is currently paired."""
        return self._paired_data is not None and bool(self._paired_data.get("public_key_hex"))

    def get_paired_info(self) -> Optional[Dict[str, Any]]:
        """Returns non-sensitive metadata about the paired device."""
        if not self.is_paired or not self._paired_data:
            return None
        return {
            "device_name": self._paired_data.get("device_name", "Unknown Device"),
            "fingerprint": self._paired_data.get("fingerprint", "Unknown"),
            "paired_at": self._paired_data.get("paired_at", 0),
        }

    def generate_pairing_challenge(self, valid_seconds: int = 300) -> Dict[str, Any]:
        """Generates an ephemeral 32-byte pairing nonce and TOTP secret for QR code display."""
        self._active_nonce = secrets.token_hex(32)
        self._active_totp_secret = TotpHelper.generate_secret(20)
        self._nonce_expires_at = time.time() + valid_seconds

        logger.info(f"Generated new pairing challenge (expires in {valid_seconds}s)")
        return {
            "nonce": self._active_nonce,
            "totp_secret": self._active_totp_secret,
            "expires_at": int(self._nonce_expires_at),
        }

    def complete_pairing(
        self,
        nonce: str,
        pubkey_hex: str,
        signature_hex: str,
        device_name: str = "Android Device"
    ) -> Tuple[bool, str]:
        """
        Validates the pairing challenge signature and binds the device public key.
        Strict single-device policy: Overwrites any existing paired device.
        """
        if not self._active_nonce or time.time() > self._nonce_expires_at:
            return False, "Pairing session expired or not initialized"

        if not secrets.compare_digest(nonce, self._active_nonce):
            return False, "Invalid pairing nonce"

        # Verify ECDSA P-256 Signature
        valid, err = self._verify_signature(pubkey_hex, nonce, signature_hex)
        if not valid:
            return False, f"Signature verification failed: {err}"

        # Compute SHA-256 fingerprint of DER public key
        try:
            pub_bytes = bytes.fromhex(pubkey_hex)
            fingerprint = hashlib.sha256(pub_bytes).hexdigest()[:16]
            formatted_fp = ":".join(fingerprint[i:i + 2] for i in range(0, len(fingerprint), 2))
        except Exception as e:
            return False, f"Failed to compute fingerprint: {e}"

        # Register single active paired device
        self._paired_data = {
            "device_name": device_name,
            "fingerprint": formatted_fp,
            "public_key_hex": pubkey_hex,
            "totp_secret": self._active_totp_secret,
            "paired_at": int(time.time()),
        }
        self.save()

        # Invalidate active pairing nonce
        self._active_nonce = None
        self._nonce_expires_at = 0

        logger.info(f"Device pairing complete: {device_name} (Fingerprint: {formatted_fp})")
        return True, "Pairing successful"

    def verify_auth(
        self,
        pubkey_hex: str,
        nonce: str,
        signature_hex: str,
        totp_code: Optional[str] = None
    ) -> Tuple[bool, str]:
        """
        Authenticates a connection handshake against the single paired device.
        1. Checks public key matches pinned paired public key.
        2. Verifies cryptographic ECDSA signature over the connection nonce.
        3. Optionally validates TOTP code if provided.
        """
        if not self.is_paired or not self._paired_data:
            return False, "No device is paired on this host"

        paired_pubkey = self._paired_data.get("public_key_hex", "")
        if not secrets.compare_digest(pubkey_hex.lower(), paired_pubkey.lower()):
            logger.warning("Rejected connection: Public key does not match paired device")
            return False, "Device not authorized (Single-device policy)"

        # Verify signature
        valid, err = self._verify_signature(pubkey_hex, nonce, signature_hex)
        if not valid:
            logger.warning(f"Rejected connection: Cryptographic signature invalid ({err})")
            return False, f"Authentication signature invalid: {err}"

        # Optional TOTP verification
        if totp_code is not None:
            totp_secret = self._paired_data.get("totp_secret", "")
            if not TotpHelper.verify_code(totp_secret, totp_code):
                logger.warning("Rejected connection: Invalid TOTP code")
                return False, "Invalid TOTP verification code"

        return True, "Authorized"

    def revoke_device(self) -> bool:
        """Revokes the active paired device, returning host to unconfigured state."""
        self._paired_data = None
        if os.path.exists(self.db_path):
            try:
                os.remove(self.db_path)
                logger.info(f"Device pairing revoked and {self.db_path} deleted")
                return True
            except Exception as e:
                logger.error(f"Failed to delete {self.db_path}: {e}")
                return False
        return True

    @staticmethod
    def _verify_signature(pubkey_hex: str, data_str: str, signature_hex: str) -> Tuple[bool, str]:
        """Verifies that signature_hex was created by pubkey_hex over data_str using ECDSA P-256 + SHA256."""
        try:
            pub_bytes = bytes.fromhex(pubkey_hex)
            sig_bytes = bytes.fromhex(signature_hex)
            public_key = serialization.load_der_public_key(pub_bytes)

            if not isinstance(public_key, ec.EllipticCurvePublicKey):
                return False, "Key is not an Elliptic Curve public key"

            public_key.verify(sig_bytes, data_str.encode("utf-8"), ec.ECDSA(hashes.SHA256()))
            return True, ""
        except Exception as e:
            return False, str(e)
