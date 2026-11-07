import pytest
from aiohttp import web
from aiohttp.test_utils import AioHTTPTestCase
from ipc_server import IpcServer

class TestIpcServer(AioHTTPTestCase):
    async def get_application(self):
        self.server = IpcServer(
            get_status_fn=lambda: {"running": True, "port": 8765, "paired": False},
            start_pairing_fn=lambda: {"nonce": "test_nonce_123", "totp": "JBSWY3DPEHPK3PXP"},
            revoke_device_fn=lambda: True,
            session_control_fn=lambda action, cmd: action in ["SPAWN", "RESTART", "SIGINT"],
            get_logs_fn=lambda: ["[INFO] Test log line"]
        )
        return self.server.app

    async def test_status_endpoint(self):
        resp = await self.client.get("/ipc/status")
        assert resp.status == 200
        data = await resp.json()
        assert data["running"] is True
        assert data["port"] == 8765
        assert data["paired"] is False

    async def test_pairing_start(self):
        resp = await self.client.post("/ipc/pairing/start")
        assert resp.status == 200
        data = await resp.json()
        assert data["nonce"] == "test_nonce_123"
        assert data["totp"] == "JBSWY3DPEHPK3PXP"

    async def test_pairing_revoke(self):
        resp = await self.client.post("/ipc/pairing/revoke")
        assert resp.status == 200
        data = await resp.json()
        assert data["success"] is True

    async def test_session_control(self):
        resp = await self.client.post("/ipc/session", json={"action": "RESTART"})
        assert resp.status == 200
        data = await resp.json()
        assert data["success"] is True

        resp_bad = await self.client.post("/ipc/session", json={"action": "UNKNOWN"})
        assert resp_bad.status == 200
        data_bad = await resp_bad.json()
        assert data_bad["success"] is False

    async def test_logs_endpoint(self):
        resp = await self.client.get("/ipc/logs")
        assert resp.status == 200
        data = await resp.json()
        assert len(data["logs"]) == 1
        assert "Test log line" in data["logs"][0]

