"""
Pytest unit test for concurrent bidirectional sync between host workstation and mobile client.
"""
import asyncio
import pytest
from ws_server import WebSocketServer
from ring_buffer import TerminalRingBuffer, AgentState


@pytest.mark.asyncio
async def test_concurrent_host_and_mobile_clients():
    ring_buffer = TerminalRingBuffer(capacity=50)
    ring_buffer.set_session("test-sync-session")

    received_inputs = []

    server = WebSocketServer(
        token="test_token_123",
        ring_buffer=ring_buffer,
        on_input=lambda data: received_inputs.append(data),
        on_resize=lambda r, c: None,
        on_dismiss_prompt=lambda: None,
        allow_browser_origin=False,
    )

    await server.start("127.0.0.1", 18765)

    try:
        import aiohttp

        async with aiohttp.ClientSession() as session:
            # 1. Connect Host Client (loopback authenticated without token)
            async with session.ws_connect("http://127.0.0.1:18765/ws?host_client=true") as host_ws:
                host_snap = await host_ws.receive_json(timeout=2.0)
                assert host_snap["type"] == "SNAPSHOT"

                # 2. Connect Mobile Client (authenticated via token)
                async with session.ws_connect("http://127.0.0.1:18765/ws?token=test_token_123") as mobile_ws:
                    mobile_snap = await mobile_ws.receive_json(timeout=2.0)
                    assert mobile_snap["type"] == "SNAPSHOT"

                    # 3. Assert both clients are concurrent
                    assert len(server.host_clients) == 1
                    assert len(server.remote_clients) == 1
                    assert len(server.clients) == 2

                    # 4. Host client sends input
                    await host_ws.send_json({"type": "TEXT_INPUT", "text": "echo FROM_HOST"})
                    await asyncio.sleep(0.05)
                    assert any("FROM_HOST" in inp for inp in received_inputs)

                    # 5. Broadcast chunk -> both receive it
                    await server.broadcast_chunk("TEST_BROADCAST_PAYLOAD")

                    host_msg = await host_ws.receive_json(timeout=2.0)
                    assert host_msg["type"] == "OUTPUT"
                    assert host_msg["data"] == "TEST_BROADCAST_PAYLOAD"

                    mobile_msg = await mobile_ws.receive_json(timeout=2.0)
                    assert mobile_msg["type"] == "OUTPUT"
                    assert mobile_msg["data"] == "TEST_BROADCAST_PAYLOAD"

                    # 6. Mobile client sends input
                    await mobile_ws.send_json({"type": "TEXT_INPUT", "text": "echo FROM_MOBILE"})
                    await asyncio.sleep(0.05)
                    assert any("FROM_MOBILE" in inp for inp in received_inputs)

    finally:
        await server.stop()
