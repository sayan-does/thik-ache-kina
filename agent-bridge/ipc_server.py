"""
Local IPC Server for Agent Remote v2.0.
Provides loopback REST / WebSocket API on http://127.0.0.1:8766
for the PySide6 Desktop Tray Application (INV-05) to monitor and control
the headless bridge daemon with complete process decoupling.
"""

import asyncio
import json
import logging
from typing import Any, Callable, Dict, Optional
from aiohttp import web

logger = logging.getLogger("agent-bridge.ipc")


class IpcServer:
    def __init__(
        self,
        get_status_fn: Callable[[], Dict[str, Any]],
        start_pairing_fn: Callable[[], Dict[str, Any]],
        revoke_device_fn: Callable[[], bool],
        session_control_fn: Callable[[str, Optional[str]], bool],
        get_logs_fn: Callable[[], list],
    ):
        self.get_status_fn = get_status_fn
        self.start_pairing_fn = start_pairing_fn
        self.revoke_device_fn = revoke_device_fn
        self.session_control_fn = session_control_fn
        self.get_logs_fn = get_logs_fn

        self.app = web.Application()
        self.app.router.add_get("/ipc/status", self.handle_status)
        self.app.router.add_post("/ipc/pairing/start", self.handle_start_pairing)
        self.app.router.add_post("/ipc/pairing/revoke", self.handle_revoke_device)
        self.app.router.add_post("/ipc/session", self.handle_session_control)
        self.app.router.add_get("/ipc/logs", self.handle_logs)

        self.runner: Optional[web.AppRunner] = None

    async def handle_status(self, request: web.Request) -> web.Response:
        """Returns comprehensive host daemon status and telemetry."""
        status = self.get_status_fn()
        return web.json_response(status)

    async def handle_start_pairing(self, request: web.Request) -> web.Response:
        """Generates a fresh pairing challenge for QR code generation."""
        challenge = self.start_pairing_fn()
        return web.json_response(challenge)

    async def handle_revoke_device(self, request: web.Request) -> web.Response:
        """Revokes the current paired device."""
        success = self.revoke_device_fn()
        return web.json_response({"success": success})

    async def handle_session_control(self, request: web.Request) -> web.Response:
        """Controls the active ConPTY session (SPAWN, RESTART, SIGINT, TERMINATE)."""
        try:
            data = await request.json()
            action = data.get("action", "")
            command = data.get("command", None)
            success = self.session_control_fn(action, command)
            return web.json_response({"success": success})
        except Exception as e:
            return web.json_response({"error": str(e)}, status=400)

    async def handle_logs(self, request: web.Request) -> web.Response:
        """Returns recent activity event logs."""
        logs = self.get_logs_fn()
        return web.json_response({"logs": logs})

    async def start(self, host: str = "127.0.0.1", port: int = 8766):
        """Starts the local loopback IPC server."""
        self.runner = web.AppRunner(self.app)
        await self.runner.setup()
        site = web.TCPSite(self.runner, host, port)
        await site.start()
        logger.info(f"Local IPC Server listening on http://{host}:{port}/ipc")

    async def stop(self):
        """Gracefully shuts down the local IPC server."""
        if self.runner:
            await self.runner.cleanup()
            logger.info("Local IPC Server stopped")
