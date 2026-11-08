"""
End-to-End WebSocket Roundtrip & Security Smoke Test for Agent Remote Bridge.
Runs headless against a running bridge daemon on localhost:8765.
"""

import asyncio
import json
import logging
import sys
import uuid
import aiohttp

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
logger = logging.getLogger("smoke-test")


async def test_health_endpoint(session: aiohttp.ClientSession, base_url: str):
    logger.info("Test 1: Querying /health endpoint...")
    async with session.get(f"{base_url}/health") as resp:
        assert resp.status == 200, f"Expected 200, got {resp.status}"
        data = await resp.json()
        assert data.get("status") == "ok", f"Expected status 'ok', got {data}"
        logger.info(f" -> PASS: /health responded ok. Active clients: {data.get('connected_clients')}")


async def test_cswsh_origin_rejection(session: aiohttp.ClientSession, base_url: str):
    logger.info("Test 2: Testing CSWSH browser origin rejection...")
    headers = {"Origin": "https://malicious-website.com"}
    async with session.get(f"{base_url}/ws", headers=headers) as resp:
        assert resp.status == 403, f"Expected 403 Forbidden for browser Origin, got {resp.status}"
        text = await resp.text()
        assert "Browser origins prohibited" in text
        logger.info(" -> PASS: Malicious browser Origin was successfully rejected with 403.")


async def test_unauthorized_rejection(session: aiohttp.ClientSession, base_url: str):
    logger.info("Test 3: Testing unauthorized connection rejection...")
    headers = {"Authorization": "Bearer completely_invalid_token_999"}
    async with session.get(f"{base_url}/ws", headers=headers) as resp:
        assert resp.status == 401, f"Expected 401 Unauthorized, got {resp.status}"
        logger.info(" -> PASS: Invalid token was successfully rejected with 401.")


async def test_websocket_e2e_roundtrip(session: aiohttp.ClientSession, ws_url: str, token: str):
    logger.info("Test 4: Testing WebSocket authenticated connection & SNAPSHOT hydration...")
    headers = {"Authorization": f"Bearer {token}"}
    async with session.ws_connect(ws_url, headers=headers) as ws:
        # Step 1: Wait for SNAPSHOT
        msg = await asyncio.wait_for(ws.receive(), timeout=5.0)
        assert msg.type == aiohttp.WSMsgType.TEXT, f"Expected text frame, got {msg.type}"
        payload = json.loads(msg.data)
        assert payload.get("type") == "SNAPSHOT", f"Expected SNAPSHOT, got {payload.get('type')}"
        snapshot_data = payload.get("payload", {})
        assert "history" in snapshot_data, "Snapshot missing history"
        logger.info(f" -> PASS: Received SNAPSHOT for session: {snapshot_data.get('session_id')}")

        # Step 2: Test RESIZE frame
        logger.info("Test 5: Testing dynamic viewport RESIZE...")
        await ws.send_json({"type": "RESIZE", "cols": 100, "rows": 30})
        logger.info(" -> PASS: Sent RESIZE frame without error.")

        # Step 3: Test Interactive Echo Roundtrip
        echo_marker = f"SMOKE_{uuid.uuid4().hex[:8]}"
        logger.info(f"Test 6: Sending interactive command to ConPTY (marker: {echo_marker})...")
        await ws.send_json({"type": "TEXT_INPUT", "text": f"echo {echo_marker}"})

        found_echo = False
        start_time = asyncio.get_event_loop().time()
        while asyncio.get_event_loop().time() - start_time < 5.0:
            try:
                frame = await asyncio.wait_for(ws.receive(), timeout=2.0)
                if frame.type == aiohttp.WSMsgType.TEXT:
                    data = json.loads(frame.data)
                    if data.get("type") == "OUTPUT" and echo_marker in data.get("data", ""):
                        found_echo = True
                        break
            except asyncio.TimeoutError:
                break

        assert found_echo, f"Did not observe echo output '{echo_marker}' in terminal stream within timeout!"
        logger.info(" -> PASS: Verified bidirectional roundtrip! ConPTY output received over WebSocket.")

        # Step 4: Test RAW_KEY frame
        logger.info("Test 7: Testing RAW_KEY dispatch...")
        await ws.send_json({"type": "RAW_KEY", "key": "ENTER"})
        logger.info(" -> PASS: RAW_KEY ENTER dispatched.")

        # Step 5: Test DISMISS_PROMPT
        logger.info("Test 8: Testing DISMISS_PROMPT dispatch...")
        await ws.send_json({"type": "DISMISS_PROMPT"})
        logger.info(" -> PASS: DISMISS_PROMPT dispatched.")

        await ws.close()
        logger.info(" -> PASS: Gracefully closed WebSocket connection.")


async def main():
    host = "127.0.0.1"
    port = 8765
    token = "agent123"

    if len(sys.argv) > 1:
        token = sys.argv[1]

    base_url = f"http://{host}:{port}"
    ws_url = f"ws://{host}:{port}/ws"

    logger.info(f"Connecting to Agent Remote Bridge on {base_url} (Token: {token[:4]}****)...")

    async with aiohttp.ClientSession() as session:
        try:
            await test_health_endpoint(session, base_url)
            await test_cswsh_origin_rejection(session, base_url)
            await test_unauthorized_rejection(session, base_url)
            await test_websocket_e2e_roundtrip(session, ws_url, token)
            print("\n=======================================================")
            print(" [SUCCESS] ALL 8 BRIDGE SMOKE TESTS PASSED!")
            print("=======================================================")
        except Exception as e:
            logger.error(f"[FAIL] Smoke test encountered failure: {e}", exc_info=True)
            sys.exit(1)


if __name__ == "__main__":
    asyncio.run(main())
