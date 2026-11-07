"""
Agent Remote Desktop - Main Workstation Window
Full-sized native developer workstation with dual-screen live terminal mirroring,
session lifecycle supervisor, ACPI lid power configuration, and system tray minimization.
"""

import sys
import os
import json
import subprocess
import urllib.request
from io import BytesIO

import qrcode
from PIL import Image

from PySide6.QtCore import Qt, QTimer, QSize
from PySide6.QtGui import QIcon, QPixmap, QColor, QFont, QPen, QBrush, QImage
from PySide6.QtWidgets import (
    QApplication, QMainWindow, QWidget, QVBoxLayout, QHBoxLayout,
    QLabel, QPushButton, QComboBox, QTextEdit, QFrame, QDialog,
    QMessageBox, QSplitter
)

from terminal_widget import TerminalWidget

COLOR_CANVAS = "#0A0B0E"
COLOR_SURFACE = "#12141A"
COLOR_SURFACE_ELEVATED = "#181B22"
COLOR_BORDER = "#212631"
COLOR_BORDER_MUTED = "#1B1F27"
COLOR_TEXT_PRIMARY = "#F0F3F6"
COLOR_TEXT_MUTED = "#8B949E"
COLOR_ACCENT_CYAN = "#38BDF8"
COLOR_ACCENT_EMERALD = "#00E599"
COLOR_ACCENT_CRIMSON = "#F43F5E"
COLOR_ACCENT_AMBER = "#F59E0B"

IPC_BASE_URL = "http://127.0.0.1:8766"


def http_ipc_request(endpoint: str, method: str = "GET", data: dict = None, timeout: float = 1.0) -> dict:
    url = f"{IPC_BASE_URL}{endpoint}"
    req = urllib.request.Request(url, method=method)
    if data is not None:
        req.add_header("Content-Type", "application/json")
        body = json.dumps(data).encode("utf-8")
    else:
        body = None

    try:
        with urllib.request.urlopen(req, data=body, timeout=timeout) as response:
            return json.loads(response.read().decode("utf-8"))
    except Exception as e:
        return {"error": str(e)}


class PairingQrDialog(QDialog):
    """High-contrast modal displaying pairing QR code and raw payload."""
    def __init__(self, pairing_data: dict, parent=None):
        super().__init__(parent)
        self.setWindowTitle("Pair Mobile Device")
        self.setFixedSize(360, 500)
        self.setStyleSheet(f"""
            QDialog {{
                background-color: {COLOR_CANVAS};
                color: {COLOR_TEXT_PRIMARY};
            }}
            QLabel {{
                color: {COLOR_TEXT_PRIMARY};
                font-family: 'JetBrains Mono', 'Segoe UI', monospace;
            }}
            QPushButton {{
                background-color: {COLOR_SURFACE_ELEVATED};
                color: {COLOR_TEXT_PRIMARY};
                border: 1px solid {COLOR_BORDER};
                border-radius: 4px;
                padding: 6px 12px;
                font-size: 11px;
                font-family: 'JetBrains Mono', monospace;
            }}
            QPushButton:hover {{
                border-color: {COLOR_ACCENT_CYAN};
            }}
        """)

        layout = QVBoxLayout(self)
        layout.setContentsMargins(24, 24, 24, 24)
        layout.setSpacing(14)

        title = QLabel("DEVICE PAIRING CHALLENGE")
        title.setStyleSheet(f"font-size: 13px; font-weight: bold; letter-spacing: 1px; color: {COLOR_ACCENT_CYAN};")
        title.setAlignment(Qt.AlignCenter)
        layout.addWidget(title)

        hint = QLabel("Scan with Agent Remote on your mobile device,\nor 1-tap paste payload into the app.\nValid for 5 minutes.")
        hint.setStyleSheet(f"font-size: 11px; color: {COLOR_TEXT_MUTED};")
        hint.setAlignment(Qt.AlignCenter)
        layout.addWidget(hint)

        # Generate QR
        payload_str = json.dumps(pairing_data)
        qr = qrcode.QRCode(version=1, box_size=5, border=2)
        qr.add_data(payload_str)
        qr.make(fit=True)
        img = qr.make_image(fill_color="#0A0B0E", back_color="#FFFFFF").convert("RGBA")

        data = img.tobytes("raw", "RGBA")
        qimage = QImage(data, img.size[0], img.size[1], QImage.Format_RGBA8888)
        pixmap = QPixmap.fromImage(qimage)

        qr_label = QLabel()
        qr_label.setPixmap(pixmap.scaled(210, 210, Qt.KeepAspectRatio, Qt.SmoothTransformation))
        qr_label.setAlignment(Qt.AlignCenter)
        layout.addWidget(qr_label)

        copy_btn = QPushButton("Copy Pairing Payload (Manual Paste)")
        copy_btn.clicked.connect(lambda: QApplication.clipboard().setText(payload_str))
        layout.addWidget(copy_btn)

        close_btn = QPushButton("Done")
        close_btn.setStyleSheet(f"background-color: {COLOR_ACCENT_CYAN}; color: {COLOR_CANVAS}; font-weight: bold;")
        close_btn.clicked.connect(self.accept)
        layout.addWidget(close_btn)


class MainWindow(QMainWindow):
    """Main Workstation Window for Agent Remote."""

    def __init__(self, tray_controller=None):
        super().__init__()
        self.tray_controller = tray_controller
        self.setWindowTitle("Agent Remote Workstation")
        self.resize(1060, 680)
        self.setMinimumSize(840, 520)

        self.daemon_status = {}
        self.is_paired = False

        self.init_ui()

        # Telemetry Polling Timer
        self.poll_timer = QTimer(self)
        self.poll_timer.timeout.connect(self.poll_telemetry)
        self.poll_timer.start(1500)
        self.poll_telemetry()

    def init_ui(self):
        self.setStyleSheet(f"""
            QMainWindow {{
                background-color: {COLOR_CANVAS};
            }}
            QWidget {{
                background-color: {COLOR_CANVAS};
                color: {COLOR_TEXT_PRIMARY};
                font-family: 'JetBrains Mono', 'Segoe UI', monospace;
            }}
            QFrame#card {{
                background-color: {COLOR_SURFACE};
                border: 1px solid {COLOR_BORDER};
                border-radius: 6px;
                padding: 10px;
            }}
            QLabel {{
                font-size: 11px;
            }}
            QPushButton {{
                background-color: {COLOR_SURFACE_ELEVATED};
                color: {COLOR_TEXT_PRIMARY};
                border: 1px solid {COLOR_BORDER};
                border-radius: 4px;
                padding: 6px 12px;
                font-size: 11px;
                font-weight: 600;
            }}
            QPushButton:hover {{
                border-color: {COLOR_ACCENT_CYAN};
                color: {COLOR_TEXT_PRIMARY};
            }}
            QPushButton#btn_danger {{
                border-color: {COLOR_ACCENT_CRIMSON};
                color: {COLOR_ACCENT_CRIMSON};
            }}
            QPushButton#btn_danger:hover {{
                background-color: {COLOR_ACCENT_CRIMSON};
                color: {COLOR_CANVAS};
            }}
            QPushButton#btn_primary {{
                background-color: {COLOR_ACCENT_CYAN};
                color: {COLOR_CANVAS};
                border: none;
            }}
            QPushButton#btn_primary:hover {{
                background-color: #60A5FA;
            }}
            QComboBox {{
                background-color: {COLOR_SURFACE_ELEVATED};
                color: {COLOR_TEXT_PRIMARY};
                border: 1px solid {COLOR_BORDER};
                border-radius: 4px;
                padding: 5px 8px;
                font-size: 11px;
            }}
            QComboBox::drop-down {{
                border: none;
            }}
            QComboBox QAbstractItemView {{
                background-color: {COLOR_SURFACE_ELEVATED};
                color: {COLOR_TEXT_PRIMARY};
                selection-background-color: {COLOR_BORDER};
            }}
            QTextEdit {{
                background-color: {COLOR_CANVAS};
                border: 1px solid {COLOR_BORDER_MUTED};
                border-radius: 4px;
                color: {COLOR_TEXT_MUTED};
                font-size: 10px;
                font-family: 'JetBrains Mono', monospace;
            }}
        """)

        central = QWidget()
        self.setCentralWidget(central)
        root_layout = QVBoxLayout(central)
        root_layout.setContentsMargins(12, 12, 12, 12)
        root_layout.setSpacing(10)

        # 1. Top Header Bar
        header = QHBoxLayout()
        header.setSpacing(12)

        logo_col = QVBoxLayout()
        logo_title = QLabel("AGENT REMOTE")
        logo_title.setStyleSheet(f"font-size: 13px; font-weight: bold; color: {COLOR_TEXT_PRIMARY}; letter-spacing: 1.5px;")
        logo_col.addWidget(logo_title)
        logo_sub = QLabel("Workstation v2.1 • Dual-Mirroring ConPTY Terminal")
        logo_sub.setStyleSheet(f"font-size: 10px; color: {COLOR_TEXT_MUTED};")
        logo_col.addWidget(logo_sub)
        header.addLayout(logo_col)

        header.addStretch()

        # Status LED Pill
        self.status_pill = QLabel("ONLINE")
        self.status_pill.setStyleSheet(f"background-color: {COLOR_SURFACE_ELEVATED}; border: 1px solid {COLOR_BORDER}; border-radius: 4px; padding: 4px 10px; font-size: 10px; font-weight: bold; color: {COLOR_ACCENT_EMERALD};")
        header.addWidget(self.status_pill)

        # IP Pill with Copy
        self.ip_btn = QPushButton("Tailscale: Detecting...")
        self.ip_btn.setToolTip("Click to copy IP:Port")
        self.ip_btn.clicked.connect(self.copy_ip)
        header.addWidget(self.ip_btn)

        # Pair QR Button
        pair_btn = QPushButton("Pair Mobile (QR)")
        pair_btn.setObjectName("btn_primary")
        pair_btn.clicked.connect(self.open_pairing_qr)
        header.addWidget(pair_btn)

        # Minimize to Tray Button
        min_tray_btn = QPushButton("Minimize to Tray")
        min_tray_btn.clicked.connect(self.hide)
        header.addWidget(min_tray_btn)

        root_layout.addLayout(header)

        # 2. Main Workstation Area (Splitter: Left Sidebar + Right Terminal Viewport)
        splitter = QSplitter(Qt.Horizontal)
        splitter.setStyleSheet("QSplitter::handle { background-color: #212631; width: 1px; }")

        # Sidebar Widget (280px)
        self.sidebar = QWidget()
        self.sidebar.setMaximumWidth(320)
        self.sidebar.setMinimumWidth(240)
        sidebar_layout = QVBoxLayout(self.sidebar)
        sidebar_layout.setContentsMargins(0, 0, 8, 0)
        sidebar_layout.setSpacing(10)

        # Card A: Session Supervisor
        sess_card = QFrame()
        sess_card.setObjectName("card")
        sess_layout = QVBoxLayout(sess_card)
        sess_layout.setContentsMargins(10, 10, 10, 10)
        sess_layout.setSpacing(8)

        sess_title = QLabel("CONPTY SESSION SUPERVISOR")
        sess_title.setStyleSheet(f"font-size: 10px; color: {COLOR_TEXT_MUTED}; font-weight: bold;")
        sess_layout.addWidget(sess_title)

        self.session_info = QLabel("Active: powershell.exe")
        self.session_info.setStyleSheet(f"font-size: 11px; color: {COLOR_ACCENT_CYAN};")
        sess_layout.addWidget(self.session_info)

        sel_row = QHBoxLayout()
        self.shell_select = QComboBox()
        self.shell_select.addItems(["powershell.exe", "agy", "claude", "opencode", "cmd.exe"])
        sel_row.addWidget(self.shell_select)

        spawn_btn = QPushButton("Spawn")
        spawn_btn.clicked.connect(self.spawn_session)
        sel_row.addWidget(spawn_btn)
        sess_layout.addLayout(sel_row)

        action_row = QHBoxLayout()
        restart_btn = QPushButton("Restart Shell")
        restart_btn.clicked.connect(lambda: self.control_session("RESTART"))
        action_row.addWidget(restart_btn)

        sigint_btn = QPushButton("Emergency ^C")
        sigint_btn.setObjectName("btn_danger")
        sigint_btn.clicked.connect(lambda: self.control_session("SIGINT"))
        action_row.addWidget(sigint_btn)
        sess_layout.addLayout(action_row)
        sidebar_layout.addWidget(sess_card)

        # Card B: Paired Mobile Device
        dev_card = QFrame()
        dev_card.setObjectName("card")
        dev_layout = QVBoxLayout(dev_card)
        dev_layout.setContentsMargins(10, 10, 10, 10)
        dev_layout.setSpacing(6)

        dev_title = QLabel("PAIRED MOBILE DEVICE")
        dev_title.setStyleSheet(f"font-size: 10px; color: {COLOR_TEXT_MUTED}; font-weight: bold;")
        dev_layout.addWidget(dev_title)

        self.device_label = QLabel("No device paired")
        self.device_label.setStyleSheet(f"font-size: 11px; color: {COLOR_ACCENT_AMBER};")
        dev_layout.addWidget(self.device_label)

        self.revoke_btn = QPushButton("Revoke Device")
        self.revoke_btn.setObjectName("btn_danger")
        self.revoke_btn.clicked.connect(self.revoke_device)
        self.revoke_btn.setEnabled(False)
        dev_layout.addWidget(self.revoke_btn)
        sidebar_layout.addWidget(dev_card)

        # Card C: ACPI Lid & Power Lock
        lid_card = QFrame()
        lid_card.setObjectName("card")
        lid_layout = QVBoxLayout(lid_card)
        lid_layout.setContentsMargins(10, 10, 10, 10)
        lid_layout.setSpacing(6)

        lid_title = QLabel("POWER & LID STATE")
        lid_title.setStyleSheet(f"font-size: 10px; color: {COLOR_TEXT_MUTED}; font-weight: bold;")
        lid_layout.addWidget(lid_title)

        lid_info = QLabel("Keep-Awake Lock: Active\nACPI Lid Action: Screen Off (Agents Run)")
        lid_info.setStyleSheet(f"font-size: 10px; color: {COLOR_TEXT_MUTED};")
        lid_layout.addWidget(lid_info)

        lid_btn = QPushButton("Configure Closed-Lid Running")
        lid_btn.clicked.connect(self.configure_lid)
        lid_layout.addWidget(lid_btn)
        sidebar_layout.addWidget(lid_card)

        # Card D: Telemetry Log Feed
        log_title = QLabel("ACTIVITY TELEMETRY")
        log_title.setStyleSheet(f"font-size: 10px; color: {COLOR_TEXT_MUTED}; font-weight: bold;")
        sidebar_layout.addWidget(log_title)

        self.log_feed = QTextEdit()
        self.log_feed.setReadOnly(True)
        sidebar_layout.addWidget(self.log_feed)

        splitter.addWidget(self.sidebar)

        # Right Viewport: Terminal Area
        term_container = QWidget()
        term_layout = QVBoxLayout(term_container)
        term_layout.setContentsMargins(8, 0, 0, 0)
        term_layout.setSpacing(6)

        # Terminal Quick Control Bar
        term_topbar = QHBoxLayout()
        term_label = QLabel("POWERSHELL CONPTY VIEWPORT")
        term_label.setStyleSheet(f"font-size: 10px; font-weight: bold; color: {COLOR_TEXT_MUTED};")
        term_topbar.addWidget(term_label)
        term_topbar.addStretch()

        toggle_sb_btn = QPushButton("Toggle Sidebar")
        toggle_sb_btn.clicked.connect(self.toggle_sidebar)
        term_topbar.addWidget(toggle_sb_btn)

        refit_btn = QPushButton("Refit Viewport")
        refit_btn.clicked.connect(lambda: self.terminal_widget.refit())
        term_topbar.addWidget(refit_btn)

        term_layout.addLayout(term_topbar)

        # Terminal Viewport
        self.terminal_widget = TerminalWidget(self)
        term_layout.addWidget(self.terminal_widget)

        splitter.addWidget(term_container)
        splitter.setStretchFactor(0, 0)
        splitter.setStretchFactor(1, 1)

        root_layout.addWidget(splitter)

    def toggle_sidebar(self):
        self.sidebar.setVisible(not self.sidebar.isVisible())

    def closeEvent(self, event):
        """Minimize to tray on close to maintain background continuity."""
        event.ignore()
        self.hide()
        if self.tray_controller:
            self.tray_controller.notify_minimized()

    def poll_telemetry(self):
        res = http_ipc_request("/ipc/status")
        if "error" in res:
            self.status_pill.setText("DAEMON: OFFLINE")
            self.status_pill.setStyleSheet(f"background-color: {COLOR_SURFACE_ELEVATED}; border: 1px solid {COLOR_BORDER}; border-radius: 4px; padding: 4px 10px; font-size: 10px; font-weight: bold; color: {COLOR_ACCENT_CRIMSON};")
            return

        self.daemon_status = res
        clients = res.get("connected_clients", 0)
        self.is_paired = res.get("device_paired", False)

        if clients > 0:
            self.status_pill.setText(f"ONLINE ({clients} ACTIVE)")
            self.status_pill.setStyleSheet(f"background-color: {COLOR_SURFACE_ELEVATED}; border: 1px solid {COLOR_BORDER}; border-radius: 4px; padding: 4px 10px; font-size: 10px; font-weight: bold; color: {COLOR_ACCENT_EMERALD};")
        else:
            self.status_pill.setText("ONLINE (STANDBY)")
            self.status_pill.setStyleSheet(f"background-color: {COLOR_SURFACE_ELEVATED}; border: 1px solid {COLOR_BORDER}; border-radius: 4px; padding: 4px 10px; font-size: 10px; font-weight: bold; color: {COLOR_ACCENT_CYAN};")

        # Update IP
        tailscale = res.get("ips", {}).get("tailscale")
        lan = res.get("ips", {}).get("lan")
        port = res.get("port", 8765)
        display_ip = tailscale or lan or "127.0.0.1"
        self.ip_btn.setText(f"Endpoint: {display_ip}:{port}")

        # Update Session
        cmd = res.get("command", "powershell.exe")
        self.session_info.setText(f"Active: {cmd}")

        # Update Paired Device
        paired_info = res.get("paired_device")
        if self.is_paired and paired_info:
            d_name = paired_info.get("device_name", "Paired Device")
            self.device_label.setText(f"{d_name}\nStatus: Active (Hardware Key Locked)")
            self.device_label.setStyleSheet(f"color: {COLOR_ACCENT_EMERALD}; font-size: 11px;")
            self.revoke_btn.setEnabled(True)
        else:
            self.device_label.setText("No mobile device paired\nAuth Required")
            self.device_label.setStyleSheet(f"color: {COLOR_ACCENT_AMBER}; font-size: 11px;")
            self.revoke_btn.setEnabled(False)

        # Logs
        logs_res = http_ipc_request("/ipc/logs")
        if "logs" in logs_res:
            recent = "\n".join(logs_res["logs"][-30:])
            self.log_feed.setPlainText(recent)
            # Scroll to bottom
            sb = self.log_feed.verticalScrollBar()
            sb.setValue(sb.maximum())

    def copy_ip(self):
        text = self.ip_btn.text().replace("Endpoint: ", "")
        QApplication.clipboard().setText(text)
        QMessageBox.information(self, "Copied", f"Copied endpoint '{text}' to clipboard.")

    def open_pairing_qr(self):
        res = http_ipc_request("/ipc/pairing/start", method="POST")
        if "error" in res:
            QMessageBox.warning(self, "Error", f"Failed to start pairing: {res['error']}")
            return

        dialog = PairingQrDialog(res, self)
        dialog.exec_()
        self.poll_telemetry()

    def revoke_device(self):
        confirm = QMessageBox.question(
            self,
            "Confirm Revocation",
            "Revoke paired mobile device?\nThe device will immediately lose access.",
            QMessageBox.Yes | QMessageBox.No
        )
        if confirm == QMessageBox.Yes:
            http_ipc_request("/ipc/pairing/revoke", method="POST")
            self.poll_telemetry()

    def spawn_session(self):
        cmd = self.shell_select.currentText()
        res = http_ipc_request("/ipc/session", method="POST", data={"action": "SPAWN", "command": cmd})
        if "error" in res or not res.get("success"):
            QMessageBox.warning(self, "Error", f"Could not spawn session: {res.get('error', 'Unknown')}")
        else:
            self.poll_telemetry()

    def control_session(self, action: str):
        http_ipc_request("/ipc/session", method="POST", data={"action": action})
        self.poll_telemetry()

    def configure_lid(self):
        try:
            subprocess.run(["powercfg", "/setacvalueindex", "SCHEME_CURRENT", "SUB_BUTTONS", "LIDACTION", "0"], check=True)
            subprocess.run(["powercfg", "/setactive", "SCHEME_CURRENT"], check=True)
            QMessageBox.information(
                self,
                "Power Configured",
                "Success! Laptop will now keep running coding agents when the lid is closed on AC power."
            )
        except Exception as e:
            QMessageBox.warning(self, "Error", f"Failed to set powercfg: {e}")
