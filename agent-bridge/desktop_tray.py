"""
Agent Remote Desktop Tray Controller (v2.0)
Lightweight PySide6 System Tray Popover for Windows 10/11.
Communicates via Local Loopback IPC (http://127.0.0.1:8766/ipc) with the headless daemon (INV-05).
Strictly follows @design-taste-frontend aesthetic: pitch black, high-contrast monospace, zero emojis.
"""

import sys
import os
import json
import subprocess
import urllib.request
import urllib.error
from io import BytesIO

import qrcode
from PIL import Image

from PySide6.QtCore import Qt, QTimer, QSize, QPoint
from PySide6.QtGui import QIcon, QPixmap, QPainter, QColor, QFont, QPen, QBrush, QImage
from PySide6.QtWidgets import (
    QApplication, QMainWindow, QWidget, QVBoxLayout, QHBoxLayout,
    QLabel, QPushButton, QComboBox, QTextEdit, QFrame, QDialog,
    QSystemTrayIcon, QMenu, QMessageBox
)

# Design Taste Tokens
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


def create_tray_pixmap(color_hex: str) -> QPixmap:
    """Generates an anti-aliased 32x32 status circle pixmap for the system tray."""
    pixmap = QPixmap(32, 32)
    pixmap.fill(Qt.transparent)
    painter = QPainter(pixmap)
    painter.setRenderHint(QPainter.Antialiasing)

    # Background ring
    painter.setPen(QPen(QColor(COLOR_BORDER), 2))
    painter.setBrush(QBrush(QColor(COLOR_SURFACE)))
    painter.drawRoundedRect(2, 2, 28, 28, 6, 6)

    # Status LED
    painter.setPen(Qt.NoPen)
    painter.setBrush(QBrush(QColor(color_hex)))
    painter.drawEllipse(10, 10, 12, 12)
    painter.end()
    return pixmap


def http_ipc_request(endpoint: str, method: str = "GET", data: dict = None, timeout: float = 1.0) -> dict:
    """Zero-dependency HTTP IPC request to local bridge daemon."""
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
        self.setFixedSize(340, 480)
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
        layout.setContentsMargins(20, 20, 20, 20)
        layout.setSpacing(14)

        title = QLabel("DEVICE PAIRING CHALLENGE")
        title.setStyleSheet(f"font-size: 12px; font-weight: bold; letter-spacing: 1px; color: {COLOR_ACCENT_CYAN};")
        title.setAlignment(Qt.AlignCenter)
        layout.addWidget(title)

        hint = QLabel("Scan with Agent Remote on your mobile device.\nValid for 5 minutes.")
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
        qr_label.setPixmap(pixmap.scaled(200, 200, Qt.KeepAspectRatio, Qt.SmoothTransformation))
        qr_label.setAlignment(Qt.AlignCenter)
        layout.addWidget(qr_label)

        # Copy Payload Button
        copy_btn = QPushButton("Copy Pairing Payload (Manual Paste)")
        copy_btn.clicked.connect(lambda: QApplication.clipboard().setText(payload_str))
        layout.addWidget(copy_btn)

        close_btn = QPushButton("Done")
        close_btn.setStyleSheet(f"background-color: {COLOR_ACCENT_CYAN}; color: {COLOR_CANVAS}; font-weight: bold;")
        close_btn.clicked.connect(self.accept)
        layout.addWidget(close_btn)


class DesktopTrayWindow(QMainWindow):
    """Compact 320x460 popover window managed by the system tray."""

    def __init__(self):
        super().__init__()
        self.setWindowTitle("Agent Remote Controller")
        self.setFixedSize(340, 520)
        self.setWindowFlags(Qt.Window | Qt.WindowCloseButtonHint | Qt.CustomizeWindowHint)

        self.daemon_status = {}
        self.is_paired = False

        self.init_ui()
        self.init_tray()

        # Polling timer for status and logs
        self.poll_timer = QTimer(self)
        self.poll_timer.timeout.connect(self.poll_daemon)
        self.poll_timer.start(1500)
        self.poll_daemon()

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
                padding: 6px 10px;
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
                padding: 4px 8px;
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
        root_layout.setContentsMargins(14, 14, 14, 14)
        root_layout.setSpacing(10)

        # 1. Header (Status Pill + Title + Minimize)
        header_layout = QHBoxLayout()
        self.status_pill = QLabel("DAEMON: CONNECTING")
        self.status_pill.setStyleSheet(f"font-size: 10px; font-weight: bold; color: {COLOR_ACCENT_AMBER};")
        header_layout.addWidget(self.status_pill)
        header_layout.addStretch()

        host_label = QLabel("v2.0 (Interactive)")
        host_label.setStyleSheet(f"color: {COLOR_TEXT_MUTED}; font-size: 10px;")
        header_layout.addWidget(host_label)
        root_layout.addLayout(header_layout)

        # 2. Host Network Info Card
        net_card = QFrame()
        net_card.setObjectName("card")
        net_layout = QVBoxLayout(net_card)
        net_layout.setContentsMargins(10, 8, 10, 8)
        net_layout.setSpacing(4)

        net_title = QLabel("NETWORK ENDPOINTS")
        net_title.setStyleSheet(f"font-size: 10px; color: {COLOR_TEXT_MUTED}; font-weight: bold;")
        net_layout.addWidget(net_title)

        ip_row = QHBoxLayout()
        self.ip_display = QLabel("Tailscale: Detecting...")
        self.ip_display.setStyleSheet("font-size: 11px;")
        ip_row.addWidget(self.ip_display)

        copy_ip_btn = QPushButton("Copy")
        copy_ip_btn.setFixedWidth(50)
        copy_ip_btn.clicked.connect(self.copy_ip)
        ip_row.addWidget(copy_ip_btn)
        net_layout.addLayout(ip_row)
        root_layout.addWidget(net_card)

        # 3. Paired Device Card
        device_card = QFrame()
        device_card.setObjectName("card")
        dev_layout = QVBoxLayout(device_card)
        dev_layout.setContentsMargins(10, 8, 10, 8)
        dev_layout.setSpacing(8)

        dev_title = QLabel("PAIRED MOBILE DEVICE (STRICT SINGLE USER)")
        dev_title.setStyleSheet(f"font-size: 10px; color: {COLOR_TEXT_MUTED}; font-weight: bold;")
        dev_layout.addWidget(dev_title)

        self.device_info_label = QLabel("No device paired")
        self.device_info_label.setStyleSheet(f"color: {COLOR_ACCENT_AMBER}; font-size: 11px;")
        dev_layout.addWidget(self.device_info_label)

        dev_btn_row = QHBoxLayout()
        self.pair_btn = QPushButton("Pair Device (QR)")
        self.pair_btn.setObjectName("btn_primary")
        self.pair_btn.clicked.connect(self.open_pairing_qr)
        dev_btn_row.addWidget(self.pair_btn)

        self.revoke_btn = QPushButton("Revoke")
        self.revoke_btn.setObjectName("btn_danger")
        self.revoke_btn.clicked.connect(self.revoke_device)
        dev_btn_row.addWidget(self.revoke_btn)
        dev_layout.addLayout(dev_btn_row)
        root_layout.addWidget(device_card)

        # 4. Session Controller Card
        session_card = QFrame()
        session_card.setObjectName("card")
        sess_layout = QVBoxLayout(session_card)
        sess_layout.setContentsMargins(10, 8, 10, 8)
        sess_layout.setSpacing(6)

        sess_title = QLabel("CONPTY SESSION SUPERVISOR")
        sess_title.setStyleSheet(f"font-size: 10px; color: {COLOR_TEXT_MUTED}; font-weight: bold;")
        sess_layout.addWidget(sess_title)

        sel_row = QHBoxLayout()
        self.session_select = QComboBox()
        self.session_select.addItems(["powershell.exe", "agy", "claude", "opencode"])
        sel_row.addWidget(self.session_select)

        start_btn = QPushButton("Spawn")
        start_btn.clicked.connect(self.spawn_session)
        sel_row.addWidget(start_btn)
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
        root_layout.addWidget(session_card)

        # 5. Live Feed (4 lines)
        log_title = QLabel("ACTIVITY LOG")
        log_title.setStyleSheet(f"font-size: 10px; color: {COLOR_TEXT_MUTED}; font-weight: bold;")
        root_layout.addWidget(log_title)

        self.log_feed = QTextEdit()
        self.log_feed.setReadOnly(True)
        self.log_feed.setFixedHeight(75)
        root_layout.addWidget(self.log_feed)

        # 6. Bottom Utilities (Closed-Lid Config)
        util_row = QHBoxLayout()
        lid_btn = QPushButton("Configure Closed-Lid Running")
        lid_btn.clicked.connect(self.configure_lid)
        util_row.addWidget(lid_btn)
        root_layout.addLayout(util_row)

    def init_tray(self):
        self.tray_icon = QSystemTrayIcon(self)
        self.tray_icon.setIcon(QIcon(create_tray_pixmap(COLOR_ACCENT_AMBER)))

        tray_menu = QMenu()
        tray_menu.setStyleSheet(f"""
            QMenu {{
                background-color: {COLOR_SURFACE};
                color: {COLOR_TEXT_PRIMARY};
                border: 1px solid {COLOR_BORDER};
                font-family: 'JetBrains Mono', monospace;
                font-size: 11px;
            }}
            QMenu::item:selected {{
                background-color: {COLOR_SURFACE_ELEVATED};
                color: {COLOR_ACCENT_CYAN};
            }}
        """)

        show_action = tray_menu.addAction("Show Remote Controller")
        show_action.triggered.connect(self.show_popover)

        copy_action = tray_menu.addAction("Copy Tailscale IP")
        copy_action.triggered.connect(self.copy_ip)

        tray_menu.addSeparator()
        sigint_action = tray_menu.addAction("Emergency ^C")
        sigint_action.triggered.connect(lambda: self.control_session("SIGINT"))

        tray_menu.addSeparator()
        quit_action = tray_menu.addAction("Exit Controller")
        quit_action.triggered.connect(self.quit_app)

        self.tray_icon.setContextMenu(tray_menu)
        self.tray_icon.activated.connect(self.on_tray_activated)
        self.tray_icon.show()

    def on_tray_activated(self, reason):
        if reason in (QSystemTrayIcon.Trigger, QSystemTrayIcon.DoubleClick):
            if self.isVisible():
                self.hide()
            else:
                self.show_popover()

    def show_popover(self):
        self.show()
        self.raise_()
        self.activateWindow()

    def closeEvent(self, event):
        """Minimize to tray instead of quitting."""
        event.ignore()
        self.hide()

    def quit_app(self):
        QApplication.quit()

    def poll_daemon(self):
        """Polls status and recent logs from loopback IPC."""
        res = http_ipc_request("/ipc/status")
        if "error" in res:
            self.status_pill.setText("DAEMON: OFFLINE")
            self.status_pill.setStyleSheet(f"font-size: 10px; font-weight: bold; color: {COLOR_ACCENT_CRIMSON};")
            self.tray_icon.setIcon(QIcon(create_tray_pixmap(COLOR_ACCENT_CRIMSON)))
            return

        self.daemon_status = res
        clients = res.get("connected_clients", 0)
        self.is_paired = res.get("device_paired", False)

        if clients > 0:
            self.status_pill.setText(f"ONLINE ({clients} ACTIVE)")
            self.status_pill.setStyleSheet(f"font-size: 10px; font-weight: bold; color: {COLOR_ACCENT_EMERALD};")
            self.tray_icon.setIcon(QIcon(create_tray_pixmap(COLOR_ACCENT_EMERALD)))
        else:
            self.status_pill.setText("ONLINE (STANDBY)")
            self.status_pill.setStyleSheet(f"font-size: 10px; font-weight: bold; color: {COLOR_ACCENT_CYAN};")
            self.tray_icon.setIcon(QIcon(create_tray_pixmap(COLOR_ACCENT_CYAN)))

        # Update IP
        tailscale = res.get("ips", {}).get("tailscale")
        lan = res.get("ips", {}).get("lan")
        port = res.get("port", 8765)
        display_ip = tailscale or lan or "127.0.0.1"
        self.ip_display.setText(f"{display_ip}:{port}")

        # Update Paired Device
        paired_info = res.get("paired_device")
        if self.is_paired and paired_info:
            d_name = paired_info.get("device_name", "Paired Device")
            self.device_info_label.setText(f"{d_name} (Active)")
            self.device_info_label.setStyleSheet(f"color: {COLOR_ACCENT_EMERALD}; font-size: 11px;")
            self.pair_btn.setText("Re-Pair")
            self.revoke_btn.setEnabled(True)
        else:
            self.device_info_label.setText("No device paired (Auth required)")
            self.device_info_label.setStyleSheet(f"color: {COLOR_ACCENT_AMBER}; font-size: 11px;")
            self.pair_btn.setText("Pair Device (QR)")
            self.revoke_btn.setEnabled(False)

        # Poll Logs
        logs_res = http_ipc_request("/ipc/logs")
        if "logs" in logs_res:
            recent = "\n".join(logs_res["logs"][-4:])
            self.log_feed.setPlainText(recent)

    def copy_ip(self):
        text = self.ip_display.text()
        QApplication.clipboard().setText(text)

    def open_pairing_qr(self):
        res = http_ipc_request("/ipc/pairing/start", method="POST")
        if "error" in res:
            QMessageBox.warning(self, "Error", f"Failed to start pairing: {res['error']}")
            return

        dialog = PairingQrDialog(res, self)
        dialog.exec_()
        self.poll_daemon()

    def revoke_device(self):
        confirm = QMessageBox.question(
            self,
            "Confirm Revocation",
            "Are you sure you want to revoke the paired device?\nThe device will immediately lose access.",
            QMessageBox.Yes | QMessageBox.No
        )
        if confirm == QMessageBox.Yes:
            http_ipc_request("/ipc/pairing/revoke", method="POST")
            self.poll_daemon()

    def spawn_session(self):
        cmd = self.session_select.currentText()
        res = http_ipc_request("/ipc/session", method="POST", data={"action": "SPAWN", "command": cmd})
        if "error" in res or not res.get("success"):
            QMessageBox.warning(self, "Error", f"Could not spawn session: {res.get('error', 'Unknown')}")
        else:
            self.poll_daemon()

    def control_session(self, action: str):
        http_ipc_request("/ipc/session", method="POST", data={"action": action})
        self.poll_daemon()

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


def main():
    app = QApplication(sys.argv)
    app.setQuitOnLastWindowClosed(False)

    window = DesktopTrayWindow()
    window.show_popover()

    sys.exit(app.exec())


if __name__ == "__main__":
    main()
