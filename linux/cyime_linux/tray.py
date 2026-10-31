"""Session tray entry owned by the persistent backend, not by a settings window."""

from pathlib import Path
import sys

from PyQt6.QtCore import QProcess
from PyQt6.QtGui import QIcon
from PyQt6.QtWidgets import QMenu, QMessageBox, QStyle, QSystemTrayIcon


class BackendTray(QSystemTrayIcon):
    def __init__(self, app, directory, port):
        icon = QIcon.fromTheme("input-keyboard", app.style().standardIcon(QStyle.StandardPixmap.SP_ComputerIcon))
        super().__init__(icon, app)
        self.directory, self.port = directory, port
        self.setToolTip("CyIME · 输入法与剪贴簿")
        self.menu = QMenu()
        for label, command, icon_name in (("剪贴簿", "gui", "edit-paste"),):
            action = self.menu.addAction(QIcon.fromTheme(icon_name), label)
            action.triggered.connect(lambda _, command=command: self.open(command))
        self.menu.addSeparator()
        action = self.menu.addAction(QIcon.fromTheme("application-exit"), "停止同步后台")
        action.triggered.connect(self.stop)
        self.setContextMenu(self.menu)
        self.activated.connect(self.activate)
        self.show()

    def open(self, command):
        return QProcess.startDetached(sys.executable, [str(Path(__file__).resolve().parents[1] / "run.py"),
            command, "--state-dir", str(self.directory), "--port", str(self.port)])

    def activate(self, reason):
        if reason in (self.ActivationReason.Trigger, self.ActivationReason.DoubleClick):
            self.open("gui")

    def stop(self):
        if QMessageBox.question(None, "CyIME", "停止同步及剪贴簿记录？输入法仍可使用。") == QMessageBox.StandardButton.Yes:
            self.parent().quit()
