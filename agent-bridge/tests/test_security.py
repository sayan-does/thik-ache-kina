import os
import sys
import pytest
from aiohttp.test_utils import TestClient, TestServer

sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))

from ws_server import WebSocketServer
from ring_buffer import TerminalRingBuffer


@pytest.mark.asyncio
async def test_cswsh_browser_origin_rejection():
    ring_buf = TerminalRingBuffer()
    token = "secret12345"

    ws_server = WebSocketServer(
        token=token,
        ring_buffer=ring_buf,
        on_input=lambda d: None,
        on_resize=lambda r, c: None,
        on_dismiss_prompt=lambda: None,
        allow_browser_origin=False,
    )

    client = TestClient(TestServer(ws_server.app))
    await client.start_server()
    try:
        # 1. Attacking browser sends Origin header: http://malicious-website.com
        resp = await client.get("/ws", headers={"Origin": "http://evil-site.com"})
        assert resp.status == 403
        text = await resp.text()
        assert "Browser origins prohibited" in text
    finally:
        await client.close()


@pytest.mark.asyncio
async def test_unauthorized_token_rejection():
    ring_buf = TerminalRingBuffer()
    token = "secret12345"

    ws_server = WebSocketServer(
        token=token,
        ring_buffer=ring_buf,
        on_input=lambda d: None,
        on_resize=lambda r, c: None,
        on_dismiss_prompt=lambda: None,
        allow_browser_origin=False,
    )

    client = TestClient(TestServer(ws_server.app))
    await client.start_server()
    try:
        # 2. Connection without Bearer token or invalid token
        resp = await client.get("/ws", headers={"Authorization": "Bearer wrong_token"})
        assert resp.status == 401
        text = await resp.text()
        assert "Unauthorized" in text
    finally:
        await client.close()


@pytest.mark.asyncio
async def test_valid_token_connection():
    ring_buf = TerminalRingBuffer()
    token = "secret12345"

    ws_server = WebSocketServer(
        token=token,
        ring_buffer=ring_buf,
        on_input=lambda d: None,
        on_resize=lambda r, c: None,
        on_dismiss_prompt=lambda: None,
        allow_browser_origin=False,
    )

    client = TestClient(TestServer(ws_server.app))
    await client.start_server()
    try:
        # 3. Connection with valid Bearer token should upgrade to WebSocket
        ws = await client.ws_connect("/ws", headers={"Authorization": f"Bearer {token}"})
        assert not ws.closed

        # Receive initial state snapshot
        msg = await ws.receive_json()
        assert msg["type"] == "SNAPSHOT"
        assert "history" in msg["payload"]

        await ws.close()
    finally:
        await client.close()
