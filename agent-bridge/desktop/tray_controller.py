"""
Agent Remote Desktop - System Tray Controller
Manages the Windows taskbar system tray icon, minimize-to-tray lifecycle, and balloon toasts.
"""

from PySide6.QtCore import Qt
from PySide6.QtGui import QIcon, QPixmap, QPainter, QColor, QPen, QBrush
from PySide6.QtWidgets import QSystemTrayIcon, QMenu, QApplication

COLOR_CANVAS = "#0A0B0E"
COLOR_SURFACE = "#12141A"
COLOR_SURFACE_ELEVATED = "#181B22"
COLOR_BORDER = "#212631"
COLOR_TEXT_PRIMARY = "#F0F3F6"
COLOR_ACCENT_CYAN = "#38BDF8"
COLOR_ACCENT_EMERALD = "#00E599"
COLOR_ACCENT_CRIMSON = "#F43F5E"
COLOR_ACCENT_AMBER = "#F59E0B"


def create_tray_pixmap(color_hex: str = COLOR_ACCENT_EMERALD) -> QPixmap:
    """Generates an anti-aliased 32x32 status circle pixmap for the Windows system tray."""
    pixmap = QPixmap(32, 32)
    pixmap.fill(Qt.transparent)
    painter = QPainter(pixmap)
    painter.setRenderHint(QPainter.Antialiasing)

    # Outer border ring
    painter.setPen(QPen(QColor(COLOR_BORDER), 2))
    painter.setBrush(QBrush(QColor(COLOR_SURFACE)))
    painter.drawRoundedRect(2, 2, 28, 28, 6, 6)

    # Status LED
    painter.setPen(Qt.NoPen)
    painter.setBrush(QBrush(QColor(color_hex)))
    painter.drawEllipse(10, 10, 12, 12)
    painter.end()
    return pixmap


class TrayController:
    def __init__(self, main_window, on_quit=None):
        self.main_window = main_window
        self.on_quit = on_quit

        self.tray_icon = QSystemTrayIcon(self.main_window)
        self.tray_icon.setIcon(QIcon(create_tray_pixmap(COLOR_ACCENT_EMERALD)))
        self.tray_icon.setToolTip("Agent Remote Workstation v2.1")

        self.init_menu()
        self.tray_icon.activated.connect(self.on_tray_activated)
        self.tray_icon.show()

    def init_menu(self):
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

        show_action = tray_menu.addAction("Show Workstation")
        show_action.triggered.connect(self.show_main_window)

        copy_action = tray_menu.addAction("Copy Endpoint IP:Port")
        copy_action.triggered.connect(self.main_window.copy_ip)

        tray_menu.addSeparator()

        restart_action = tray_menu.addAction("Restart Shell (F5)")
        restart_action.triggered.connect(lambda: self.main_window.control_session("RESTART"))

        sigint_action = tray_menu.addAction("Emergency ^C")
        sigint_action.triggered.connect(lambda: self.main_window.control_session("SIGINT"))

        tray_menu.addSeparator()

        quit_action = tray_menu.addAction("Exit Agent Remote")
        quit_action.triggered.connect(self.quit_application)

        self.tray_icon.setContextMenu(tray_menu)

    def on_tray_activated(self, reason):
        if reason in (QSystemTrayIcon.Trigger, QSystemTrayIcon.DoubleClick):
            if self.main_window.isVisible():
                self.main_window.hide()
            else:
                self.show_main_window()

    def show_main_window(self):
        self.main_window.show()
        self.main_window.raise_()
        self.main_window.activateWindow()

    def notify_minimized(self):
        self.tray_icon.showMessage(
            "Agent Remote Active",
            "Workstation minimized to tray. Terminal session and mobile connections remain active.",
            QSystemTrayIcon.Information,
            2500
        )

    def quit_application(self):
        if self.on_quit:
            self.on_quit()
        QApplication.quit()
