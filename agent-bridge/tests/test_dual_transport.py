import os
import sys
import pytest
import json

sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))

from aiohttp.test_utils import TestClient, TestServer
from ring_buffer import TerminalRingBuffer
from ws_server import WebSocketServer

@pytest.mark.asyncio
async def test_dual_client_transport_coexistence():
    ring_buf = TerminalRingBuffer()
    token = "test_dual_token"

    ws_server = WebSocketServer(
        token=token,
        ring_buffer=ring_buf,
        on_input=lambda d: None,
        on_resize=lambda r, c: None,
        on_dismiss_prompt=lambda: None,
        allow_browser_origin=True,
    )

    client = TestClient(TestServer(ws_server.app))
    await client.start_server()

    # 1. Connect local host workstation
    host_ws = await client.ws_connect("/ws", headers={"X-Host-Client": "true"})
    assert len(ws_server.host_clients) == 1
    assert len(ws_server.remote_clients) == 0
    assert len(ws_server.clients) == 1

    # 2. Connect remote mobile client (with bearer token)
    remote_ws = await client.ws_connect("/ws", headers={"Authorization": f"Bearer {token}"})
    assert len(ws_server.host_clients) == 1
    assert len(ws_server.remote_clients) == 1
    assert len(ws_server.clients) == 2

    # Verify both received initial SNAPSHOT
    host_msg = await host_ws.receive_json()
    assert host_msg["type"] == "SNAPSHOT"

    remote_msg = await remote_ws.receive_json()
    assert remote_msg["type"] == "SNAPSHOT"

    # 3. Broadcast output chunk and verify BOTH receive it simultaneously
    await ws_server.broadcast_chunk("Dual-Screen Live Mirroring Test")

    host_out = await host_ws.receive_json()
    assert host_out["type"] == "OUTPUT"
    assert host_out["data"] == "Dual-Screen Live Mirroring Test"

    remote_out = await remote_ws.receive_json()
    assert remote_out["type"] == "OUTPUT"
    assert remote_out["data"] == "Dual-Screen Live Mirroring Test"

    # 4. Cleanup
    await host_ws.close()
    await remote_ws.close()
    await client.close()
