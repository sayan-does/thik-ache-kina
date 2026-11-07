"""
CLI Agent Remote Control - Bridge Host Daemon
Main entrypoint for spawning, wrapping, and streaming CLI agents.
"""

import argparse
import asyncio
import json
import logging
import os
import secrets
import socket
import subprocess
import sys
import uuid
import yaml

from ring_buffer import AgentState, TerminalRingBuffer
from prompt_detector import PromptDetector
from notifier import Notifier
from pty_wrapper import ConPtyBridge
from ws_server import WebSocketServer

# Configure Logging
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] (%(name)s) %(message)s",
    datefmt="%H:%M:%S",
)
logger = logging.getLogger("agent-bridge")


def get_detected_network_ips() -> dict:
    """Detects active network interfaces: Tailscale (100.x.y.z) and Local LAN IP."""
    ips = {"tailscale": None, "lan": None}

    # 1. Try tailscale CLI
    try:
        res = subprocess.run(["tailscale", "ip", "-4"], capture_output=True, text=True, timeout=1.5)
        if res.returncode == 0 and res.stdout.strip():
            ips["tailscale"] = res.stdout.strip().splitlines()[0].strip()
    except Exception:
        pass

    # 2. Try socket outbound routing test
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.settimeout(0.5)
        s.connect(("8.8.8.8", 80))
        outbound = s.getsockname()[0]
        s.close()
        if outbound.startswith("100."):
            if not ips["tailscale"]:
                ips["tailscale"] = outbound
        elif not outbound.startswith("127."):
            ips["lan"] = outbound
    except Exception:
        pass

    # 3. Fallback: inspect host IP list
    try:
        hostname = socket.gethostname()
        for ip in socket.gethostbyname_ex(hostname)[2]:
            if ip.startswith("100.") and not ips["tailscale"]:
                ips["tailscale"] = ip
            elif not ip.startswith("127.") and not ips["lan"]:
                ips["lan"] = ip
    except Exception:
        pass

    return ips


def load_or_init_config(config_path: str = "config.yaml") -> dict:
    default_config = {
        "server": {
            "host": "0.0.0.0",
            "port": 8765,
            "heartbeat_seconds": 30,
            "buffer_capacity": 1000,
        },
        "security": {
            "auth_token": "",
            "allow_browser_origin": False,
        },
        "notifications": {
            "enabled": True,
            "provider": "ntfy",
            "topic": "",
            "server_url": "https://ntfy.sh",
            "cooldown_seconds": 15,
        },
        "agent": {
            "default_command": "powershell.exe",
            "conpty_rows": 32,
            "conpty_cols": 90,
        },
    }

    if os.path.exists(config_path):
        try:
            with open(config_path, "r", encoding="utf-8") as f:
                loaded = yaml.safe_load(f) or {}
                # Shallow/deep merge
                for section in default_config:
                    if section in loaded and isinstance(loaded[section], dict):
                        default_config[section].update(loaded[section])
                    elif section in loaded:
                        default_config[section] = loaded[section]
        except Exception as e:
            logger.warning(f"Failed to read {config_path}, using defaults: {e}")

    # Generate token and topic if missing
    dirty = False
    if not default_config["security"]["auth_token"]:
        default_config["security"]["auth_token"] = secrets.token_hex(32)
        dirty = True

    if not default_config["notifications"]["topic"]:
        default_config["notifications"]["topic"] = f"agent-remote-{uuid.uuid4().hex[:12]}"
        dirty = True

    if dirty:
        try:
            with open(config_path, "w", encoding="utf-8") as f:
                yaml.dump(default_config, f, default_flow_style=False)
            logger.info(f"Generated new security token and notification topic in {config_path}")
        except Exception as e:
            logger.warning(f"Could not persist generated config: {e}")

    return default_config


class BridgeDaemon:
    def __init__(self, command: str, config: dict):
        self.config = config
        self.command = command
        self.session_id = f"sess-{uuid.uuid4().hex[:8]}"

        self.ring_buffer = TerminalRingBuffer(
            capacity=config["server"].get("buffer_capacity", 1000)
        )
        self.ring_buffer.set_session(self.session_id)

        self.prompt_detector = PromptDetector()

        notif_cfg = config["notifications"]
        self.notifier = Notifier(
            topic=notif_cfg.get("topic", ""),
            server_url=notif_cfg.get("server_url", "https://ntfy.sh"),
            enabled=notif_cfg.get("enabled", True),
            cooldown_seconds=notif_cfg.get("cooldown_seconds", 15),
        )

        self.loop: asyncio.AbstractEventLoop = None
        self.ws_server: WebSocketServer = None
        self.pty: ConPtyBridge = None
        self._output_queue: asyncio.Queue = None
        self._recent_output_chunk = ""

    def on_pty_data(self, data: str):
        """Threadsafe callback from PTY reader worker thread."""
        self.ring_buffer.append(data)
        if self.loop and self._output_queue:
            self.loop.call_soon_threadsafe(self._output_queue.put_nowait, data)

    def on_pty_exit(self, exit_code: int):
        """Callback when PTY child process exits."""
        logger.info(f"CLI process finished with exit code {exit_code}")
        state = AgentState.COMPLETED if exit_code == 0 else AgentState.ERROR
        self.ring_buffer.set_state(state)
        if self.loop and self.ws_server:
            asyncio.run_coroutine_threadsafe(self.ws_server.broadcast_state(), self.loop)

    async def _process_output_loop(self):
        """Dispatches terminal output chunks and detects interactive prompts."""
        accumulated_window = ""
        while True:
            chunk = await self._output_queue.get()
            accumulated_window = (accumulated_window + chunk)[-4000:]

            # Broadcast chunk to mobile clients
            await self.ws_server.broadcast_chunk(chunk)

            # Live mirror chunk to host stdout so user on laptop sees live terminal output
            try:
                sys.stdout.write(chunk)
                sys.stdout.flush()
            except Exception:
                pass

            # Analyze for prompts
            prompt = self.prompt_detector.analyze(accumulated_window)
            if prompt and self.ring_buffer.state != AgentState.WAITING_FOR_INPUT:
                logger.info(f"Interactive prompt detected: {prompt.get('headline')}")
                self.ring_buffer.set_state(AgentState.WAITING_FOR_INPUT, prompt)
                await self.ws_server.broadcast_state()
                self.notifier.send_prompt_alert(self.session_id, prompt)

            elif not prompt and self.ring_buffer.state == AgentState.WAITING_FOR_INPUT:
                # Prompt was resolved or cleared by subsequent output
                self.ring_buffer.set_state(AgentState.RUNNING, None)
                await self.ws_server.broadcast_state()

    async def run(self):
        self.loop = asyncio.get_running_loop()
        self._output_queue = asyncio.Queue()

        # Start WebSocket Server
        sec_cfg = self.config["security"]
        srv_cfg = self.config["server"]
        self.ws_server = WebSocketServer(
            token=sec_cfg["auth_token"],
            ring_buffer=self.ring_buffer,
            on_input=lambda data: self.pty.write(data) if self.pty else None,
            on_resize=lambda r, c: self.pty.resize(r, c) if self.pty else None,
            on_dismiss_prompt=lambda: self.ring_buffer.dismiss_prompt(),
            allow_browser_origin=sec_cfg.get("allow_browser_origin", False),
            heartbeat_seconds=srv_cfg.get("heartbeat_seconds", 30),
        )

        host = srv_cfg.get("host", "0.0.0.0")
        port = srv_cfg.get("port", 8765)
        await self.ws_server.start(host, port)

        # Print banner
        auth_token = sec_cfg["auth_token"]
        topic = self.config["notifications"]["topic"]
        detected_ips = get_detected_network_ips()
        tailscale_ip = detected_ips.get("tailscale")
        lan_ip = detected_ips.get("lan")
        preferred_ip = tailscale_ip or lan_ip or "127.0.0.1"

        print("\n" + "=" * 68)
        print("  🤖 CLI AGENT REMOTE CONTROL - BRIDGE DAEMON ACTIVE")
        print("=" * 68)
        print(f"  Session ID : {self.session_id}")
        print(f"  Command    : {self.command}")
        print(f"  WebSocket  : ws://localhost:{port}/ws")
        print(f"  Auth Token : {auth_token}")
        print(f"  ntfy Topic : {topic} (https://ntfy.sh/{topic})")
        print("-" * 68)
        print("  Detected Network Endpoints:")
        if tailscale_ip:
            print(f"    • Tailscale IP : {tailscale_ip} -> ws://{tailscale_ip}:{port}/ws")
        else:
            print(f"    • Tailscale IP : (Not detected / not connected)")
        if lan_ip:
            print(f"    • Local LAN IP : {lan_ip} -> ws://{lan_ip}:{port}/ws")
        print("-" * 68)
        print("  Android 1-Tap Quick Connect Payload (Copy & tap 'Paste' in app):")
        payload_json = json.dumps({
            "host": preferred_ip,
            "port": port,
            "token": auth_token,
            "ntfy": topic
        })
        print(f"    {payload_json}")
        print("=" * 68 + "\n")

        # Spawn PTY Process
        agent_cfg = self.config["agent"]
        self.pty = ConPtyBridge(
            command=self.command,
            on_data=self.on_pty_data,
            on_exit=self.on_pty_exit,
            rows=agent_cfg.get("conpty_rows", 32),
            cols=agent_cfg.get("conpty_cols", 90),
        )
        self.pty.start()

        # Run output processing pipeline
        output_task = asyncio.create_task(self._process_output_loop())
        try:
            await output_task
        except asyncio.CancelledError:
            pass
        finally:
            if self.pty:
                self.pty.terminate()
            if self.ws_server:
                await self.ws_server.stop()


def main():
    parser = argparse.ArgumentParser(description="CLI Agent Remote Control Bridge")
    parser.add_argument(
        "command",
        nargs="*",
        help="Command to run (e.g. agy, opencode, claude). Defaults to config.default_command",
    )
    parser.add_argument("--config", default="config.yaml", help="Path to config file")
    args = parser.parse_args()

    config = load_or_init_config(args.config)
    cmd = " ".join(args.command) if args.command else config["agent"].get("default_command", "powershell.exe")

    daemon = BridgeDaemon(cmd, config)
    try:
        asyncio.run(daemon.run())
    except KeyboardInterrupt:
        print("\n[Bridge] Stopping daemon gracefully...")


if __name__ == "__main__":
    main()
