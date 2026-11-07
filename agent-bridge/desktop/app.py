"""
Agent Remote Workstation - Desktop Application Master Entrypoint
Supervises background bridge engine and launches the Qt Workstation window.
"""

import sys
import os
import time
import threading
import urllib.request
import subprocess

# Ensure parent directory (agent-bridge) is on sys.path
BASE_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
if BASE_DIR not in sys.path:
    sys.path.insert(0, BASE_DIR)

from PySide6.QtWidgets import QApplication
from PySide6.QtCore import Qt, QSharedMemory

from main_window import MainWindow
from tray_controller import TrayController


def is_daemon_running() -> bool:
    try:
        with urllib.request.urlopen("http://127.0.0.1:8766/ipc/status", timeout=0.8) as resp:
            return resp.status == 200
    except Exception:
        return False


def start_embedded_daemon():
    """Starts the bridge daemon in a background thread if not already running."""
    if is_daemon_running():
        return None

    from bridge import main as daemon_main

    daemon_thread = threading.Thread(target=daemon_main, daemon=True)
    daemon_thread.start()

    # Wait up to 5 seconds for daemon to initialize
    for _ in range(25):
        if is_daemon_running():
            break
        time.sleep(0.2)

    return daemon_thread


def main():
    # Handle automated headless test flag
    if "--test-headless" in sys.argv:
        print("[AgentRemote Test] Running headless self-check...")
        start_embedded_daemon()
        assert is_daemon_running(), "Bridge daemon failed to respond to IPC health check"
        print("[AgentRemote Test] Verification successful!")
        sys.exit(0)

    # Initialize Qt Application
    app = QApplication(sys.argv)
    app.setApplicationName("Agent Remote")
    app.setOrganizationName("AgentRemote")
    app.setQuitOnLastWindowClosed(False)

    # Single-instance lock via QSharedMemory
    shared_mem = QSharedMemory("AgentRemote_SingleInstance_Lock")
    if not shared_mem.create(1):
        # Instance already running
        print("[AgentRemote] An instance of Agent Remote is already running.")
        sys.exit(0)

    # Start embedded bridge daemon if needed
    start_embedded_daemon()

    # Create UI
    window = MainWindow()
    tray = TrayController(window)
    window.tray_controller = tray

    window.show()

    exit_code = app.exec()
    shared_mem.detach()
    sys.exit(exit_code)


if __name__ == "__main__":
    main()
