"""
Agent Remote Desktop - High-Performance Embedded Terminal Widget
Hosts xterm.js inside PySide6 QtWebEngineView with full WebGL & ANSI truecolor rendering.
"""

import os
from PySide6.QtCore import QUrl, Qt, Signal
from PySide6.QtWebEngineWidgets import QWebEngineView
from PySide6.QtWebEngineCore import QWebEngineSettings, QWebEnginePage
from PySide6.QtWidgets import QWidget, QVBoxLayout, QMenu
from PySide6.QtGui import QAction, QKeySequence


class TerminalWebPage(QWebEnginePage):
    """Custom WebEnginePage with enhanced developer shortcut routing."""
    def __init__(self, parent=None):
        super().__init__(parent)

    def javaScriptConsoleMessage(self, level, message, lineNumber, sourceID):
        # Filter noisy console messages, or log if needed
        pass


class TerminalWidget(QWidget):
    """Interactive Terminal Viewport embedding hardware-accelerated xterm.js."""

    def __init__(self, parent=None):
        super().__init__(parent)

        self.layout = QVBoxLayout(self)
        self.layout.setContentsMargins(0, 0, 0, 0)
        self.layout.setSpacing(0)

        self.view = QWebEngineView(self)
        self.page = TerminalWebPage(self.view)
        self.view.setPage(self.page)

        # Configure WebEngine Settings
        settings = self.view.settings()
        settings.setAttribute(QWebEngineSettings.LocalContentCanAccessRemoteUrls, True)
        settings.setAttribute(QWebEngineSettings.LocalContentCanAccessFileUrls, True)
        settings.setAttribute(QWebEngineSettings.JavascriptEnabled, True)
        settings.setAttribute(QWebEngineSettings.ScrollAnimatorEnabled, False)

        self.layout.addWidget(self.view)

        # Context Menu for copy/paste
        self.view.setContextMenuPolicy(Qt.CustomContextMenu)
        self.view.customContextMenuRequested.connect(self.show_context_menu)

        self.load_terminal()

    def load_terminal(self):
        assets_dir = os.path.join(os.path.dirname(os.path.abspath(__file__)), "assets", "xterm")
        html_path = os.path.join(assets_dir, "index.html")
        if os.path.exists(html_path):
            self.view.load(QUrl.fromLocalFile(html_path))
        else:
            self.view.setHtml("<h3 style='color:red;'>Terminal assets not found</h3>")

    def show_context_menu(self, pos):
        menu = QMenu(self)
        menu.setStyleSheet("""
            QMenu {
                background-color: #12141A;
                color: #F0F3F6;
                border: 1px solid #212631;
                font-family: 'JetBrains Mono', monospace;
                font-size: 11px;
            }
            QMenu::item:selected {
                background-color: #181B22;
                color: #38BDF8;
            }
        """)

        copy_action = menu.addAction("Copy")
        copy_action.setShortcut(QKeySequence("Ctrl+Shift+C"))
        copy_action.triggered.connect(lambda: self.page.triggerAction(QWebEnginePage.Copy))

        paste_action = menu.addAction("Paste")
        paste_action.setShortcut(QKeySequence("Ctrl+Shift+V"))
        paste_action.triggered.connect(lambda: self.page.triggerAction(QWebEnginePage.Paste))

        menu.addSeparator()

        reload_action = menu.addAction("Reload Viewport")
        reload_action.triggered.connect(self.load_terminal)

        menu.exec_(self.view.mapToGlobal(pos))

    def refit(self):
        """Triggers xterm fit addon geometry recalculation."""
        self.page.runJavaScript("if (window.fitAddon) { window.fitAddon.fit(); }")
