"""
CLI Agent Remote Control - Bridge Host Daemon (v2.0)
Autonomous Headless Engine running in Interactive Session 1 (INV-03).
Supervises ConPTY processes, WebSocket streaming, Pairing, and Local IPC (INV-05).
"""

import argparse
import asyncio
import collections
import ctypes
import json
import logging
import os
import secrets
import socket
import subprocess
import sys
import uuid
import yaml
from typing import Optional

from ring_buffer import AgentState, TerminalRingBuffer
from prompt_detector import PromptDetector
from notifier import Notifier
from pty_wrapper import ConPtyBridge
from ws_server import WebSocketServer
from pairing_manager import PairingManager
from ipc_server import IpcServer

# Configure Logging with memory ring buffer for Desktop Tray
LOG_HISTORY = collections.deque(maxlen=100)

class MemoryLogHandler(logging.Handler):
    def emit(self, record):
        try:
            msg = self.format(record)
            LOG_HISTORY.append(msg)
        except Exception:
            pass

mem_handler = MemoryLogHandler()
mem_handler.setFormatter(logging.Formatter("%(asctime)s [%(levelname)s] %(message)s", datefmt="%H:%M:%S"))

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] (%(name)s) %(message)s",
    datefmt="%H:%M:%S",
    handlers=[logging.StreamHandler(sys.stdout), mem_handler]
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
            "ipc_port": 8766,
            "heartbeat_seconds": 30,
            "buffer_capacity": 2000,
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
                for section in default_config:
                    if section in loaded and isinstance(loaded[section], dict):
                        default_config[section].update(loaded[section])
                    elif section in loaded:
                        default_config[section] = loaded[section]
        except Exception as e:
            logger.warning(f"Failed to read {config_path}, using defaults: {e}")

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


class SystemPowerHelper:
    """Monitors Windows AC/Battery status to prevent thermal drain (INV-02)."""
    @staticmethod
    def is_on_battery() -> bool:
        try:
            class SYSTEM_POWER_STATUS(ctypes.Structure):
                _fields_ = [
                    ('ACLineStatus', ctypes.c_byte),
                    ('BatteryFlag', ctypes.c_byte),
                    ('BatteryLifePercent', ctypes.c_byte),
                    ('SystemStatusFlag', ctypes.c_byte),
                    ('BatteryLifeTime', ctypes.c_ulong),
                    ('BatteryFullLifeTime', ctypes.c_ulong),
                ]
            status = SYSTEM_POWER_STATUS()
            if ctypes.windll.kernel32.GetSystemPowerStatus(ctypes.byref(status)):
                return status.ACLineStatus == 0
        except Exception:
            pass
        return False


class BridgeDaemon:
    def __init__(self, command: str, config: dict):
        self.config = config
        self.command = command
        self.session_id = f"sess-{uuid.uuid4().hex[:8]}"

        self.ring_buffer = TerminalRingBuffer(
            capacity=config["server"].get("buffer_capacity", 2000)
        )
        self.ring_buffer.set_session(self.session_id)

        self.prompt_detector = PromptDetector()
        self.pairing_manager = PairingManager("paired_device.json")

        notif_cfg = config["notifications"]
        self.notifier = Notifier(
            topic=notif_cfg.get("topic", ""),
            server_url=notif_cfg.get("server_url", "https://ntfy.sh"),
            enabled=notif_cfg.get("enabled", True),
            cooldown_seconds=notif_cfg.get("cooldown_seconds", 15),
        )

        self.loop: Optional[asyncio.AbstractEventLoop] = None
        self.ws_server: Optional[WebSocketServer] = None
        self.ipc_server: Optional[IpcServer] = None
        self.pty: Optional[ConPtyBridge] = None
        self._output_queue: Optional[asyncio.Queue] = None

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
                self.ring_buffer.set_state(AgentState.RUNNING, None)
                await self.ws_server.broadcast_state()

    def spawn_pty(self):
        """Spawns ConPTY process under supervisor."""
        agent_cfg = self.config["agent"]
        self.ring_buffer.set_state(AgentState.RUNNING)
        self.pty = ConPtyBridge(
            command=self.command,
            on_data=self.on_pty_data,
            on_exit=self.on_pty_exit,
            rows=agent_cfg.get("conpty_rows", 32),
            cols=agent_cfg.get("conpty_cols", 90),
        )
        self.pty.start()

    def session_control(self, action: str, new_cmd: Optional[str] = None) -> bool:
        """Handles session lifecycle actions requested via IPC or mobile."""
        logger.info(f"Executing session control action: {action} (Command: {new_cmd})")
        if action == "SIGINT":
            if self.pty:
                self.pty.write("\x03")
                return True
        elif action in ("RESTART", "SPAWN"):
            if new_cmd:
                self.command = new_cmd
            if self.pty:
                self.pty.terminate()
            self.spawn_pty()
            return True
        elif action == "TERMINATE":
            if self.pty:
                self.pty.terminate()
                self.ring_buffer.set_state(AgentState.IDLE)
                return True
        return False

    async def run(self):
        self.loop = asyncio.get_running_loop()
        self._output_queue = asyncio.Queue()

        sec_cfg = self.config["security"]
        srv_cfg = self.config["server"]
        ws_port = srv_cfg.get("port", 8765)
        ipc_port = srv_cfg.get("ipc_port", 8766)

        # 1. Start Mobile WebSocket Server (Port 8765)
        self.ws_server = WebSocketServer(
            token=sec_cfg["auth_token"],
            ring_buffer=self.ring_buffer,
            on_input=lambda data: self.pty.write(data) if self.pty else None,
            on_resize=lambda r, c: self.pty.resize(r, c) if self.pty else None,
            on_dismiss_prompt=lambda: self.ring_buffer.dismiss_prompt(),
            pairing_manager=self.pairing_manager,
            allow_browser_origin=sec_cfg.get("allow_browser_origin", False),
            heartbeat_seconds=srv_cfg.get("heartbeat_seconds", 30),
        )
        await self.ws_server.start(srv_cfg.get("host", "0.0.0.0"), ws_port)

        # 2. Start Desktop Tray IPC Server (Port 8766, Loopback Only)
        detected_ips = get_detected_network_ips()
        tailscale_ip = detected_ips.get("tailscale")
        lan_ip = detected_ips.get("lan")
        preferred_ip = tailscale_ip or lan_ip or "127.0.0.1"

        def get_ipc_status():
            paired_info = self.pairing_manager.get_paired_info()
            return {
                "daemon_state": "RUNNING",
                "session_id": self.session_id,
                "command": self.command,
                "agent_state": self.ring_buffer.state.value if hasattr(self.ring_buffer.state, 'value') else str(self.ring_buffer.state),
                "ws_port": ws_port,
                "connected_clients": len(self.ws_server.clients) if self.ws_server else 0,
                "tailscale_ip": tailscale_ip,
                "lan_ip": lan_ip,
                "is_paired": self.pairing_manager.is_paired,
                "paired_device": paired_info,
            }

        def start_ipc_pairing():
            challenge = self.pairing_manager.generate_pairing_challenge(valid_seconds=300)
            challenge["host"] = preferred_ip
            challenge["port"] = ws_port
            return challenge

        self.ipc_server = IpcServer(
            get_status_fn=get_ipc_status,
            start_pairing_fn=start_ipc_pairing,
            revoke_device_fn=self.pairing_manager.revoke_device,
            session_control_fn=self.session_control,
            get_logs_fn=lambda: list(LOG_HISTORY),
        )
        await self.ipc_server.start("127.0.0.1", ipc_port)

        # Print Banner
        auth_token = sec_cfg["auth_token"]
        topic = self.config["notifications"]["topic"]
        print("\n" + "=" * 68)
        print("  🤖 CLI AGENT REMOTE CONTROL - BRIDGE DAEMON v2.0 ACTIVE")
        print("=" * 68)
        print(f"  Session ID   : {self.session_id}")
        print(f"  Command      : {self.command}")
        print(f"  WebSocket    : ws://localhost:{ws_port}/ws")
        print(f"  Local IPC    : http://127.0.0.1:{ipc_port}/ipc")
        print(f"  Single-Device: {'PAIRED' if self.pairing_manager.is_paired else 'READY FOR PAIRING'}")
        if self.pairing_manager.is_paired:
            info = self.pairing_manager.get_paired_info()
            print(f"    • Paired: {info['device_name']} ({info['fingerprint']})")
        print(f"  Legacy Token : {auth_token}")
        print(f"  ntfy Topic   : {topic}")
        print("-" * 68)
        print("  Detected Endpoints:")
        if tailscale_ip:
            print(f"    • Tailscale IP : {tailscale_ip} -> ws://{tailscale_ip}:{ws_port}/ws")
        if lan_ip:
            print(f"    • Local LAN IP : {lan_ip} -> ws://{lan_ip}:{ws_port}/ws")
        print("=" * 68 + "\n")

        # 3. Spawn initial PTY
        self.spawn_pty()

        # 4. Output processing pipeline
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
            if self.ipc_server:
                await self.ipc_server.stop()


def main():
    parser = argparse.ArgumentParser(description="CLI Agent Remote Control Bridge v2.0")
    parser.add_argument(
        "command",
        nargs="*",
        help="Command to run (e.g. powershell.exe, agy, opencode, claude).",
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
