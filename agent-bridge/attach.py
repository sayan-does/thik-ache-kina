"""
CLI Agent Remote Control - Host Terminal Attach Client (v2.0)
Attaches any local Windows Terminal (PowerShell / CMD) directly to the running
Agent Remote Bridge Daemon via loopback WebSocket (http://127.0.0.1:8765/ws?host_client=true).

Provides full bi-directional keystroke mirroring:
- Whatever you type here is executed and mirrored to the connected phone in real time.
- Whatever you type or tap on the phone executes and prints here in real time.
"""

import asyncio
import json
import os
import sys
import threading
import time
import yaml

try:
    import msvcrt
    WINDOWS = True
except ImportError:
    WINDOWS = False

import aiohttp

SPECIAL_KEYS = {
    "H": "\x1b[A",  # Up arrow
    "P": "\x1b[B",  # Down arrow
    "M": "\x1b[C",  # Right arrow
    "K": "\x1b[D",  # Left arrow
    "S": "\x1b[3~", # Delete
    "G": "\x1b[H",  # Home
    "O": "\x1b[F",  # End
    "I": "\x1b[5~", # Page Up
    "Q": "\x1b[6~", # Page Down
}


def load_token(config_path="config.yaml") -> str:
    # Try local dir or script dir
    paths = [
        config_path,
        os.path.join(os.path.dirname(__file__), config_path),
        os.path.join(os.path.dirname(__file__), "..", config_path)
    ]
    for p in paths:
        if os.path.exists(p):
            try:
                with open(p, "r", encoding="utf-8") as f:
                    cfg = yaml.safe_load(f) or {}
                    token = cfg.get("security", {}).get("auth_token", "")
                    if token:
                        return token
            except Exception:
                pass
    return "agent123"


async def run_attach(host="127.0.0.1", port=8765):
    token = load_token()
    url = f"ws://{host}:{port}/ws?host_client=true&token={token}"
    headers = {
        "X-Host-Client": "true",
        "Authorization": f"Bearer {token}"
    }

    print("\n" + "=" * 65)
    print("  🖥️  AGENT REMOTE - ATTACHED HOST CONSOLE")
    print("=" * 65)
    print(f"  Target Bridge  : {url}")
    print("  Synchronized   : Laptop Terminal <===> Mobile App (Full Duplex)")
    print("  To Detach      : Press Ctrl+]")
    print("=" * 65 + "\n")

    loop = asyncio.get_running_loop()
    input_queue = asyncio.Queue()
    stop_event = threading.Event()

    def keyboard_reader():
        while not stop_event.is_set():
            if WINDOWS:
                if msvcrt.kbhit():
                    ch = msvcrt.getwch()
                    # Detach shortcut: Ctrl+] (\x1d)
                    if ch == "\x1d":
                        loop.call_soon_threadsafe(input_queue.put_nowait, None)
                        break
                    if ch in ("\x00", "\xe0"):
                        # Special key prefix
                        code = msvcrt.getwch()
                        seq = SPECIAL_KEYS.get(code, "")
                        if seq:
                            loop.call_soon_threadsafe(input_queue.put_nowait, seq)
                    elif ch == "\r":
                        loop.call_soon_threadsafe(input_queue.put_nowait, "\r")
                    elif ch == "\x08":
                        loop.call_soon_threadsafe(input_queue.put_nowait, "\x08")
                    else:
                        loop.call_soon_threadsafe(input_queue.put_nowait, ch)
                else:
                    time.sleep(0.01)
            else:
                line = sys.stdin.readline()
                if not line:
                    break
                loop.call_soon_threadsafe(input_queue.put_nowait, line)

    k_thread = threading.Thread(target=keyboard_reader, daemon=True)
    k_thread.start()

    async with aiohttp.ClientSession() as session:
        try:
            async with session.ws_connect(url, headers=headers) as ws:
                async def sender():
                    while True:
                        data = await input_queue.get()
                        if data is None:
                            print("\n[Agent Remote] Detached from session.")
                            await ws.close()
                            break
                        msg = json.dumps({"type": "STREAM_INPUT", "data": data})
                        await ws.send_str(msg)

                sender_task = asyncio.create_task(sender())

                async for msg in ws:
                    if msg.type == aiohttp.WSMsgType.TEXT:
                        payload = json.loads(msg.data)
                        msg_type = payload.get("type")
                        if msg_type == "SNAPSHOT":
                            lines = payload.get("payload", {}).get("lines", [])
                            if lines:
                                sys.stdout.write("\n".join(lines) + "\n")
                                sys.stdout.flush()
                        elif msg_type == "OUTPUT":
                            data = payload.get("data", "")
                            if data:
                                sys.stdout.write(data)
                                sys.stdout.flush()
                    elif msg.type in (aiohttp.WSMsgType.CLOSED, aiohttp.WSMsgType.ERROR):
                        break

                sender_task.cancel()
        except aiohttp.ClientConnectorError:
            print(f"\n[Agent Remote] ERROR: Could not connect to bridge at {url}.")
            print("Please ensure the bridge is running (python agent-bridge/bridge.py or start-remote.bat).")
        except Exception as e:
            print(f"\n[Agent Remote] Connection error: {e}")
        finally:
            stop_event.set()


def main():
    import argparse
    parser = argparse.ArgumentParser(description="Agent Remote Host Terminal Client")
    parser.add_argument("--port", type=int, default=8765, help="Bridge WebSocket port")
    args = parser.parse_args()

    try:
        asyncio.run(run_attach(port=args.port))
    except KeyboardInterrupt:
        print("\n[Agent Remote] Detached.")


if __name__ == "__main__":
    main()
