import os
import sys
import tempfile
import pytest
from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.hazmat.primitives import hashes, serialization

sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))
from pairing_manager import PairingManager, TotpHelper


def generate_test_keypair():
    priv = ec.generate_private_key(ec.SECP256R1())
    pub = priv.public_key()
    der_pub = pub.public_bytes(
        encoding=serialization.Encoding.DER,
        format=serialization.PublicFormat.SubjectPublicKeyInfo
    )
    return priv, der_pub.hex()


def test_totp_helper_generation_and_verification():
    secret = TotpHelper.generate_secret()
    code = TotpHelper.generate_code(secret)
    assert len(code) == 6
    assert code.isdigit()
    assert TotpHelper.verify_code(secret, code) is True
    assert TotpHelper.verify_code(secret, "999999") is False


def test_pairing_and_single_device_auth():
    with tempfile.NamedTemporaryFile(suffix=".json", delete=False) as tf:
        db_path = tf.name

    try:
        pm = PairingManager(db_path=db_path)
        assert pm.is_paired is False

        # 1. Generate challenge
        challenge = pm.generate_pairing_challenge(valid_seconds=60)
        nonce = challenge["nonce"]

        # 2. Simulate Device A pairing
        priv_a, pub_a = generate_test_keypair()
        sig_a = priv_a.sign(nonce.encode("utf-8"), ec.ECDSA(hashes.SHA256())).hex()

        ok, msg = pm.complete_pairing(nonce, pub_a, sig_a, device_name="Pixel 8")
        assert ok is True
        assert pm.is_paired is True
        info = pm.get_paired_info()
        assert info["device_name"] == "Pixel 8"
        assert ":" in info["fingerprint"]

        # 3. Authenticate with Device A (Connection handshake)
        conn_nonce = "auth-nonce-987"
        auth_sig_a = priv_a.sign(conn_nonce.encode("utf-8"), ec.ECDSA(hashes.SHA256())).hex()
        totp_code = TotpHelper.generate_code(pm._paired_data["totp_secret"])

        auth_ok, auth_msg = pm.verify_auth(pub_a, conn_nonce, auth_sig_a, totp_code=totp_code)
        assert auth_ok is True

        # 4. Attempt authentication with Device B (Should be rejected under single-device rule)
        priv_b, pub_b = generate_test_keypair()
        auth_sig_b = priv_b.sign(conn_nonce.encode("utf-8"), ec.ECDSA(hashes.SHA256())).hex()

        auth_ok_b, auth_msg_b = pm.verify_auth(pub_b, conn_nonce, auth_sig_b)
        assert auth_ok_b is False
        assert "Single-device policy" in auth_msg_b

        # 5. Revoke Device A
        assert pm.revoke_device() is True
        assert pm.is_paired is False

    finally:
        if os.path.exists(db_path):
            os.remove(db_path)
