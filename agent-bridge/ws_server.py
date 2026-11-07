"""
Authenticated WebSocket server with CSWSH defense, Bearer token auth, and replay hydration.
"""

import asyncio
import json
import logging
import secrets
from typing import Any, Callable, Dict, Set
from aiohttp import web, WSMsgType

logger = logging.getLogger("agent-bridge.ws")

# Standard VT Key escape sequences
VT_KEY_MAP = {
    "UP": "\x1b[A",
    "DOWN": "\x1b[B",
    "RIGHT": "\x1b[C",
    "LEFT": "\x1b[D",
    "ENTER": "\r",
    "SPACE": " ",
    "TAB": "\t",
    "ESC": "\x1b",
    "SIGINT": "\x03",  # Ctrl+C
    "EOF": "\x04",     # Ctrl+D
}


class WebSocketServer:
    def __init__(
        self,
        token: str,
        ring_buffer: Any,
        on_input: Callable[[str], None],
        on_resize: Callable[[int, int], None],
        on_dismiss_prompt: Callable[[], None],
        allow_browser_origin: bool = False,
        heartbeat_seconds: int = 30,
    ):
        self.token = token
        self.ring_buffer = ring_buffer
        self.on_input = on_input
        self.on_resize = on_resize
        self.on_dismiss_prompt = on_dismiss_prompt
        self.allow_browser_origin = allow_browser_origin
        self.heartbeat_seconds = heartbeat_seconds
        self.clients: Set[web.WebSocketResponse] = set()
        self.app = web.Application()
        self.app.router.add_get("/ws", self.handle_ws)
        self.app.router.add_get("/health", self.handle_health)
        self.runner: web.AppRunner = None

    async def handle_health(self, request: web.Request) -> web.Response:
        return web.json_response({
            "status": "ok",
            "state": self.ring_buffer.state,
            "connected_clients": len(self.clients),
        })

    async def handle_ws(self, request: web.Request) -> web.WebSocketResponse:
        # 1. Defend against Cross-Site WebSocket Hijacking (CSWSH)
        origin = request.headers.get("Origin")
        if origin is not None and not self.allow_browser_origin:
            logger.warning(f"Rejected connection with browser Origin: {origin}")
            return web.Response(status=403, text="Browser origins prohibited for security")

        # 2. Bearer Token Verification
        auth_header = request.headers.get("Authorization", "")
        token = ""
        if auth_header.startswith("Bearer "):
            token = auth_header[7:].strip()
        else:
            token = request.query.get("token", "").strip()

        if not self.token or not secrets.compare_digest(token, self.token):
            logger.warning("Rejected unauthenticated WebSocket connection attempt")
            return web.Response(status=401, text="Unauthorized: Invalid Bearer Token")

        # 3. Upgrade to WebSocket
        ws = web.WebSocketResponse(heartbeat=float(self.heartbeat_seconds))
        await ws.prepare(request)
        self.clients.add(ws)
        logger.info(f"Mobile client connected (Total active: {len(self.clients)})")

        try:
            # 4. Immediate state hydration from circular ring buffer (REL-3)
            snapshot = self.ring_buffer.get_snapshot()
            await ws.send_json({
                "type": "SNAPSHOT",
                "payload": snapshot,
            })

            # 5. Process client messages
            async for msg in ws:
                if msg.type == WSMsgType.TEXT:
                    try:
                        payload = json.loads(msg.data)
                        msg_type = payload.get("type", "")

                        if msg_type == "RAW_KEY":
                            key = payload.get("key", "")
                            seq = VT_KEY_MAP.get(key, key)
                            logger.info(f"Received RAW_KEY from client: {key} -> VT {repr(seq)}")
                            self.on_input(seq)

                        elif msg_type == "TEXT_INPUT":
                            text = payload.get("text", "")
                            # In ConPTY on Windows (PowerShell/PSReadLine), Enter is strictly '\r'
                            # Appending '\r\n' triggers multiline continuation mode ('>>')
                            clean_text = text.rstrip("\r\n") + "\r"
                            logger.info(f"Received TEXT_INPUT from client: {repr(text)} -> writing {repr(clean_text)}")
                            self.on_input(clean_text)

                        elif msg_type == "RESIZE":
                            rows = int(payload.get("rows", 32))
                            cols = int(payload.get("cols", 90))
                            logger.info(f"Client requested terminal RESIZE: {cols}x{rows}")
                            self.on_resize(rows, cols)

                        elif msg_type == "DISMISS_PROMPT":
                            logger.info("Client requested DISMISS_PROMPT")
                            self.on_dismiss_prompt()
                            await self.broadcast_state()

                    except Exception as e:
                        logger.warning(f"Error parsing client message: {e}")

                elif msg.type == WSMsgType.ERROR:
                    logger.warning(f"WebSocket connection closed with error: {ws.exception()}")

        finally:
            self.clients.discard(ws)
            logger.info(f"Mobile client disconnected (Total active: {len(self.clients)})")

        return ws

    async def broadcast_chunk(self, data: str):
        """Streams a live output chunk to all connected clients."""
        if not self.clients or not data:
            return
        payload = json.dumps({"type": "OUTPUT", "data": data})
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
        for ws in list(self.clients):
            await ws.close()
        if self.runner:
            await self.runner.cleanup()
