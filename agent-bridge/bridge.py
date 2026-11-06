"""
CLI Agent Remote Control - Bridge Host Daemon
Main entrypoint for spawning, wrapping, and streaming CLI agents.
"""

import argparse
import asyncio
import logging
import os
import secrets
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
        print("\n" + "=" * 65)
        print("  🤖 CLI AGENT REMOTE CONTROL - BRIDGE DAEMON ACTIVE")
        print("=" * 65)
        print(f"  Session ID : {self.session_id}")
        print(f"  Command    : {self.command}")
        print(f"  WebSocket  : ws://localhost:{port}/ws")
        print(f"  Auth Token : {auth_token}")
        print(f"  ntfy Topic : {topic} (https://ntfy.sh/{topic})")
        print("=" * 65)
        print("  Configure your Android app with:")
        print(f"    • Server IP : Your Tailscale 100.x.y.z (Port {port})")
        print(f"    • Auth Token: {auth_token}")
        print("=" * 65 + "\n")

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
