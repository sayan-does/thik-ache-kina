"""
Authenticated WebSocket server with CSWSH defense, Device Identity Preemption (INV-04),
ECDSA Hardware Key Verification, and MTU-Safe Terminal Streaming (D-21).
"""

import asyncio
import json
import logging
import secrets
from typing import Any, Callable, Dict, Optional, Set
from aiohttp import web, WSMsgType

logger = logging.getLogger("agent-bridge.ws")

# Standard VT Key escape sequences with Windows ConPTY mapping (D-24)
VT_KEY_MAP = {
    "UP": "\x1b[A",
    "DOWN": "\x1b[B",
    "RIGHT": "\x1b[C",
    "LEFT": "\x1b[D",
    "ENTER": "\r",
    "SPACE": " ",
    "TAB": "\t",
    "BACKSPACE": "\x08",  # ConPTY requires \x08 (BS) instead of \x7f
    "DEL": "\x7f",
    "ESC": "\x1b",
    "SIGINT": "\x03",     # Ctrl+C
    "EOF": "\x04",        # Ctrl+D
}


class WebSocketServer:
    KEY_MAP = VT_KEY_MAP
    def __init__(
        self,
        token: str,
        ring_buffer: Any,
        on_input: Callable[[str], None],
        on_resize: Callable[[int, int], None],
        on_dismiss_prompt: Callable[[], None],
        pairing_manager: Optional[Any] = None,
        allow_browser_origin: bool = False,
        heartbeat_seconds: int = 30,
    ):
        self.token = token
        self.ring_buffer = ring_buffer
        self.on_input = on_input
        self.on_resize = on_resize
        self.on_dismiss_prompt = on_dismiss_prompt
        self.pairing_manager = pairing_manager
        self.allow_browser_origin = allow_browser_origin
        self.heartbeat_seconds = heartbeat_seconds
        self.remote_clients: Set[web.WebSocketResponse] = set()
        self.host_clients: Set[web.WebSocketResponse] = set()

        self.app = web.Application()
        self.app.router.add_get("/ws", self.handle_ws)
        self.app.router.add_get("/health", self.handle_health)
        self.app.router.add_post("/pair", self.handle_pair)
        self.app.router.add_get("/pairing_challenge", self.handle_pairing_challenge)

        self.runner: Optional[web.AppRunner] = None

    @property
    def clients(self) -> Set[web.WebSocketResponse]:
        """Union of remote mobile clients and local host desktop clients."""
        return self.remote_clients | self.host_clients

    async def handle_health(self, request: web.Request) -> web.Response:
        paired_info = self.pairing_manager.get_paired_info() if self.pairing_manager else None
        return web.json_response({
            "status": "ok",
            "state": self.ring_buffer.state,
            "connected_clients": len(self.clients),
            "paired": self.pairing_manager.is_paired if self.pairing_manager else False,
            "device": paired_info,
        })

    async def handle_pairing_challenge(self, request: web.Request) -> web.Response:
        """Returns active pairing challenge for QR display."""
        if not self.pairing_manager:
            return web.json_response({"error": "Pairing manager not configured"}, status=500)
        challenge = self.pairing_manager.generate_pairing_challenge()
        return web.json_response(challenge)

    async def handle_pair(self, request: web.Request) -> web.Response:
        """Processes 20-second QR onboarding pairing request from Android device."""
        if not self.pairing_manager:
            return web.json_response({"error": "Pairing manager not configured"}, status=500)
        try:
            body = await request.json()
            nonce = body.get("nonce", "")
            pubkey_hex = body.get("pubkey", "")
            signature_hex = body.get("signature", "")
            device_name = body.get("device_name", "Android Device")

            ok, msg = self.pairing_manager.complete_pairing(
                nonce=nonce,
                pubkey_hex=pubkey_hex,
                signature_hex=signature_hex,
                device_name=device_name
            )

            if ok:
                return web.json_response({"status": "ok", "message": msg})
            else:
                return web.json_response({"status": "error", "message": msg}, status=400)
        except Exception as e:
            return web.json_response({"status": "error", "message": str(e)}, status=400)

    async def handle_ws(self, request: web.Request) -> web.WebSocketResponse:
        # 1. Defend against Cross-Site WebSocket Hijacking (CSWSH)
        origin = request.headers.get("Origin")
        if origin is not None and not self.allow_browser_origin:
            logger.warning(f"Rejected connection with browser Origin: {origin}")
            return web.Response(status=403, text="Browser origins prohibited for security")

        # 2. Authentication: Loopback Host Client OR Cryptographic Device Identity OR Bearer Token
        is_authenticated = False
        remote_ip = request.remote or ""
        is_loopback = remote_ip in ("127.0.0.1", "::1", "localhost") or remote_ip.endswith("127.0.0.1")
        is_host_client = is_loopback and (
            request.headers.get("X-Host-Client") == "true" or
            request.query.get("host_client") == "true"
        )

        if is_host_client:
            is_authenticated = True
            logger.info("Authenticated local desktop workstation via loopback")

        device_pubkey = request.headers.get("X-Device-Pubkey", "")
        auth_nonce = request.headers.get("X-Auth-Nonce", "")
        auth_signature = request.headers.get("X-Auth-Signature", "")
        auth_totp = request.headers.get("X-Auth-TOTP", None)

        if not is_authenticated and self.pairing_manager and self.pairing_manager.is_paired and device_pubkey:
            # Factor 1 & 2: Cryptographic ECDSA P-256 + optional TOTP
            ok, reason = self.pairing_manager.verify_auth(
                pubkey_hex=device_pubkey,
                nonce=auth_nonce,
                signature_hex=auth_signature,
                totp_code=auth_totp
            )
            if ok:
                is_authenticated = True
                logger.info(f"Authenticated mobile client via hardware key: {device_pubkey[:16]}...")
            else:
                logger.warning(f"Cryptographic authentication failed: {reason}")
                return web.Response(status=403, text=f"Forbidden: {reason}")

        if not is_authenticated:
            # Fallback to configured Bearer Token
            auth_header = request.headers.get("Authorization", "")
            token = ""
            if auth_header.startswith("Bearer "):
                token = auth_header[7:].strip()
            else:
                token = request.query.get("token", "").strip()

            if self.token and secrets.compare_digest(token, self.token):
                is_authenticated = True
                logger.info("Authenticated client via Bearer token")

        if not is_authenticated:
            logger.warning("Rejected unauthenticated WebSocket connection attempt")
            return web.Response(status=401, text="Unauthorized: Invalid Authentication Credentials")

        # 3. Upgrade to WebSocket
        ws = web.WebSocketResponse(heartbeat=float(self.heartbeat_seconds))
        await ws.prepare(request)

        # 4. Client Registration & Device Identity Preemption (INV-04)
        if is_host_client:
            self.host_clients.add(ws)
            logger.info(f"Host desktop workstation connected (Active host: {len(self.host_clients)}, remote: {len(self.remote_clients)})")
        else:
            # Forcefully terminate stale mobile socket to prevent ghost lockouts on cellular handover
            if self.remote_clients:
                logger.info("New connection from paired device: Preempting stale ghost connection (INV-04)")
                for old_ws in list(self.remote_clients):
                    try:
                        await old_ws.close(code=1000, message=b"Superseded by new connection")
                    except Exception:
                        pass
                self.remote_clients.clear()
            self.remote_clients.add(ws)
            logger.info(f"Mobile client connected (Active remote: {len(self.remote_clients)}, host: {len(self.host_clients)})")

        try:
            # 5. Immediate state hydration from circular ring buffer
            snapshot = self.ring_buffer.get_snapshot()
            await ws.send_json({
                "type": "SNAPSHOT",
                "payload": snapshot,
            })

            # 6. Process client messages
            async for msg in ws:
                if msg.type == WSMsgType.TEXT:
                    try:
                        payload = json.loads(msg.data)
                        msg_type = payload.get("type", "")

                        if msg_type == "RAW_KEY":
                            key = payload.get("key", "")
                            seq = VT_KEY_MAP.get(key, key)
                            logger.info(f"Received RAW_KEY: {key} -> VT {repr(seq)}")
                            self.on_input(seq)

                        elif msg_type == "STREAM_INPUT":
                            data = payload.get("data", "")
                            if data:
                                # Translate Del/Backspace \x7f to ConPTY BS \x08
                                if data == "\x7f":
                                    data = "\x08"
                                self.on_input(data)

                        elif msg_type == "TEXT_INPUT":
                            text = payload.get("text", "")
                            # In ConPTY on Windows, Enter is strictly '\r'
                            clean_text = text.rstrip("\r\n") + "\r"
                            logger.info(f"Received TEXT_INPUT: {repr(text)} -> writing {repr(clean_text)}")
                            self.on_input(clean_text)

                        elif msg_type == "RESIZE":
                            rows = int(payload.get("rows", 32))
                            cols = int(payload.get("cols", 90))
                            logger.info(f"Client requested RESIZE: {cols}x{rows}")
                            self.on_resize(rows, cols)

                        elif msg_type == "REQUEST_REFRESH":
                            logger.info("Client requested clean screen refresh (D-21)")
                            cur_snapshot = self.ring_buffer.get_snapshot()
                            await ws.send_json({
                                "type": "SNAPSHOT",
                                "payload": cur_snapshot,
                            })

                        elif msg_type == "DISMISS_PROMPT":
                            logger.info("Client requested DISMISS_PROMPT")
                            self.on_dismiss_prompt()
                            await self.broadcast_state()

                    except Exception as e:
                        logger.warning(f"Error parsing client message: {e}")

                elif msg.type == WSMsgType.ERROR:
                    logger.warning(f"WebSocket closed with error: {ws.exception()}")

        finally:
            if is_host_client:
                self.host_clients.discard(ws)
                logger.info(f"Host desktop workstation disconnected (Active host: {len(self.host_clients)})")
            else:
                self.remote_clients.discard(ws)
                logger.info(f"Mobile client disconnected (Active remote: {len(self.remote_clients)})")

        return ws

    async def broadcast_chunk(self, data: str, max_chunk_size: int = 1400):
        """Streams live output chunk framed within WireGuard MTU (1420 bytes) to prevent packet fragmentation."""
        if not self.clients or not data:
            return

        # Frame into <= 1400 byte chunks
        chunks = [data[i:i + max_chunk_size] for i in range(0, len(data), max_chunk_size)]
        for chunk in chunks:
            payload = json.dumps({"type": "OUTPUT", "data": chunk})
            coros = [client.send_str(payload) for client in list(self.clients) if not client.closed]
            if coros:
                await asyncio.gather(*coros, return_exceptions=True)

    async def broadcast_state(self):
        """Broadcasts current agent state and active prompt update."""
        if not self.clients:
            return
        snapshot = self.ring_buffer.get_snapshot()
        payload = json.dumps({"type": "STATE_UPDATE", "payload": snapshot})
        coros = [client.send_str(payload) for client in list(self.clients) if not client.closed]
        if coros:
            await asyncio.gather(*coros, return_exceptions=True)

    async def start(self, host: str, port: int):
        self.runner = web.AppRunner(self.app)
        await self.runner.setup()
        site = web.TCPSite(self.runner, host, port)
        await site.start()
        logger.info(f"WebSocket server listening on ws://{host}:{port}/ws")

    async def stop(self):
        if self.runner:
            await self.runner.cleanup()
            logger.info("WebSocket server stopped")
