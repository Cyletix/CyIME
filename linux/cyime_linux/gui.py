"""Small desktop manager; closing this window never stops the clipboard backend."""

import argparse
import io
import json
import os
from pathlib import Path
import subprocess
import sys
import time

from PyQt6.QtCore import QLockFile, QObject, QRunnable, QThreadPool, QTimer, Qt, pyqtSignal, QSize
from PyQt6.QtGui import QColor, QIcon, QImage, QIntValidator, QKeySequence, QPalette, QPixmap, QShortcut
from PyQt6.QtNetwork import QLocalServer, QLocalSocket
from PyQt6.QtWidgets import (
    QApplication, QCheckBox, QComboBox, QDialog, QDialogButtonBox,
    QFileDialog, QFormLayout, QHBoxLayout, QLabel, QLineEdit, QListWidget,
    QListWidgetItem, QMainWindow, QMessageBox, QPushButton, QSplitter,
    QStyle, QTabWidget, QTextEdit, QToolButton, QVBoxLayout, QWidget,
)
import qrcode

from .control import request
from .core import default_state_dir, private_dir
from .paste_target import PasteTarget, active_target
from .window_frame import FramelessWindow
from .clipboard_media import CATEGORIES
import base64


class ImagePreview(QLabel):
    def setPixmap(self, pixmap):
        self.original = pixmap
        self.fit()

    def fit(self):
        if hasattr(self, "original"):
            super().setPixmap(self.original.scaled(self.contentsRect().size(), Qt.AspectRatioMode.KeepAspectRatio,
                                                   Qt.TransformationMode.SmoothTransformation))

    def resizeEvent(self, event):
        super().resizeEvent(event)
        self.fit()


class ManagerInstance:
    """One manager per private state directory, shared by launcher and shortcut."""

    def __init__(self, directory, command, target=None):
        private_dir(directory)
        self.path = str(directory.resolve() / "manager.sock")
        self.lock = QLockFile(str(directory.resolve() / "manager.lock"))
        self.primary = self.lock.tryLock(0)
        self.server = None
        if not self.primary:
            connection = QLocalSocket()
            deadline = time.monotonic() + 3
            while True:
                connection.connectToServer(self.path)
                if connection.waitForConnected(100):
                    break
                if time.monotonic() >= deadline:
                    break
                connection.abort()
                time.sleep(0.025)
            if connection.state() != QLocalSocket.LocalSocketState.ConnectedState:
                raise RuntimeError("剪贴簿窗口正在启动，请稍后重试")
            connection.write(json.dumps(dict(command=command, target=target)).encode() + b"\n")
            if not connection.waitForBytesWritten(1000):
                raise RuntimeError("无法唤起剪贴簿窗口")
            connection.disconnectFromServer()
            return
        QLocalServer.removeServer(self.path)
        self.server = QLocalServer()
        self.server.setSocketOptions(QLocalServer.SocketOption.UserAccessOption)
        if not self.server.listen(self.path):
            self.lock.unlock()
            raise RuntimeError("无法创建剪贴簿窗口入口")

    def attach(self, window):
        def accept():
            while self.server.hasPendingConnections():
                connection = self.server.nextPendingConnection()
                connection.setReadBufferSize(1024)

                def read(connection=connection):
                    if not connection.canReadLine():
                        return
                    try:
                        message = json.loads(bytes(connection.readLine(1024)))
                    except (ValueError, UnicodeError):
                        connection.disconnectFromServer()
                        return
                    if not isinstance(message, dict):
                        connection.disconnectFromServer()
                        return
                    command = message.get("command")
                    if command == "clipboard":
                        window.open_clipboard(toggle=True, target=message.get("target"))
                    elif command == "show":
                        window.paste_target.remember(message.get("target"))
                        window.showNormal()
                        window.raise_()
                        window.activateWindow()
                    elif command == "settings":
                        window.tabs.setCurrentIndex(2)
                        window.showNormal()
                        window.raise_()
                        window.activateWindow()
                    connection.disconnectFromServer()

                connection.readyRead.connect(read)
                connection.disconnected.connect(connection.deleteLater)
                QTimer.singleShot(3000, connection.abort)
                read()
        self.server.newConnection.connect(accept)

    def close(self):
        if self.server is not None:
            self.server.close()
            self.lock.unlock()


class Signals(QObject):
    finished = pyqtSignal(object, object)


class Task(QRunnable):
    def __init__(self, operation):
        super().__init__()
        self.operation = operation
        self.signals = Signals()

    def run(self):
        try:
            self.signals.finished.emit(self.operation(), None)
        except Exception as error:
            self.signals.finished.emit(None, str(error))


def launch_backend(directory, port):
    try:
        request("status", directory)
        return
    except (OSError, RuntimeError):
        pass
    directory.mkdir(parents=True, exist_ok=True, mode=0o700)
    # A detached session, not a child whose lifetime is bound to the GUI.
    with open(directory / "backend.log", "ab") as output:
        subprocess.Popen([sys.executable, "-m", "cyime_linux.daemon", "--state-dir", str(directory),
                          "--port", str(port)], stdout=output, stderr=output,
                         start_new_session=True, cwd=Path(__file__).resolve().parents[1])
    deadline = time.monotonic() + 15
    while time.monotonic() < deadline:
        try:
            request("status", directory)
            return
        except (OSError, RuntimeError):
            time.sleep(0.15)
    raise RuntimeError(f"后台启动失败，请检查 {directory / 'backend.log'}")


class Window(FramelessWindow):
    def __init__(self, directory):
        super().__init__()
        self.directory = directory
        self.tasks = set()
        self.busy = False
        self.generation = 0
        self.history_rows = None
        self.paste_target = PasteTarget(self)
        self.paste_busy = False
        self.setWindowTitle("CyIME · Linux")
        self.resize(900, 620)
        self.setMinimumSize(650, 470)
        container = QWidget()
        outer = QVBoxLayout(container)
        outer.setContentsMargins(20, 16, 20, 16)
        header = QHBoxLayout()
        title = QLabel("剪贴簿")
        title.setStyleSheet("font-size: 20px; font-weight: 600; color: #166d68;")
        header.addWidget(title)
        header.addStretch()
        self.status = QLabel("正在连接后台")
        self.status.setObjectName("connectionStatus")
        header.addWidget(self.status)
        outer.addLayout(header)
        self.tabs = QTabWidget()
        outer.addWidget(self.tabs)
        self.make_history()
        self.make_devices()
        from .ime_settings_gui import ImeSettingsPage
        self.tabs.addTab(ImeSettingsPage(directory), "输入法")
        self.make_settings()
        self.install_frame(container)
        self.escape = QShortcut(QKeySequence("Escape"), self)
        self.escape.activated.connect(self.hide)
        self.statusBar().showMessage(" ")
        self.setStyleSheet("""
            QWidget { font-size: 14px; color: #293339; }
            QMainWindow { background: #f7f8fa; }
            QWidget#windowShell { border: 1px solid #dce2e6; background: #f7f8fa; }
            QWidget#titleBar { background: #eef2f4; }
            QToolButton#closeButton:hover { background: #fce5e5; }
            QLabel#connectionStatus { color: #54716b; font-size: 12px; }
            QLabel#detailMeta, QLabel#historyCount { color: #77828b; font-size: 12px; }
            QLabel#sectionTitle { font-weight: 600; }
            QTabWidget::pane { border: 0; }
            QTabBar::tab { padding: 12px 18px; background: transparent; color: #77828b; }
            QTabBar::tab:selected { color: #166d68; border-bottom: 2px solid #26857c; }
            QLineEdit, QTextEdit, QListWidget { background: white; border: 1px solid #e0e5e9; border-radius: 6px; padding: 8px; }
            QLineEdit:focus, QTextEdit:focus { border-color: #26857c; }
            QPushButton { padding: 6px 12px; min-height: 22px; background: white; border: 1px solid #dce2e6; border-radius: 5px; }
            QToolButton { padding: 0; background: transparent; border: 1px solid transparent; border-radius: 5px; }
            QPushButton:hover, QToolButton:hover { background: #eaf3f1; border-color: #c5d9d5; }
            QPushButton:disabled, QToolButton:disabled { color: #a6afb6; background: #f0f2f4; }
            QPushButton#pasteButton { color: white; background: #22796f; border-color: #22796f; }
            QPushButton#pasteButton:hover { background: #17665e; }
            QComboBox { padding: 8px; min-width: 68px; background: white; border: 1px solid #e0e5e9; border-radius: 5px; }
            QListWidget { padding: 3px; }
            QListWidget::item { padding: 12px 9px; border-bottom: 1px solid #f0f2f4; }
            QListWidget::item:selected { background: #e7f2ef; color: #175b53; }
            QSplitter::handle { background: transparent; width: 12px; }
            QStatusBar { color: #77828b; font-size: 12px; }
        """)
        self.timer = QTimer(self)
        self.timer.timeout.connect(self.refresh)
        self.timer.start(2000)
        self.refresh()

    def open_clipboard(self, toggle=False, target=None):
        if toggle and self.isVisible() and self.isActiveWindow() and self.tabs.currentIndex() == 0:
            self.hide()
            return
        self.paste_target.remember(target if target is not None else active_target())
        self.tabs.setCurrentIndex(0)
        self.showNormal()
        self.raise_()
        self.activateWindow()
        self.search.setFocus()
        self.refresh()

    def run(self, operation, callback=None):
        task = Task(operation)
        self.tasks.add(task)

        def finished(value, error):
            self.tasks.discard(task)
            if error:
                self.statusBar().showMessage(error, 10000)
            if callback:
                callback(value, error)

        task.signals.finished.connect(finished)
        QThreadPool.globalInstance().start(task)

    def rpc(self, action, callback=None, **kwargs):
        self.run(lambda: request(action, self.directory, **kwargs), callback)

    def icon_button(self, name, tooltip, action):
        button = QToolButton()
        button.setIcon(QIcon.fromTheme(name))
        button.setToolTip(tooltip)
        button.setAccessibleName(tooltip)
        button.setFixedSize(36, 36)
        if button.icon().isNull():
            fallback = {"edit-copy": QStyle.StandardPixmap.SP_FileIcon,
                        "insert-image": QStyle.StandardPixmap.SP_DirOpenIcon,
                        "document-edit": QStyle.StandardPixmap.SP_FileDialogDetailedView,
                        "emblem-favorite": QStyle.StandardPixmap.SP_DialogYesButton,
                        "edit-delete": QStyle.StandardPixmap.SP_TrashIcon}
            button.setIcon(self.style().standardIcon(fallback.get(name, QStyle.StandardPixmap.SP_DialogCancelButton)))
        button.clicked.connect(action)
        return button

    def make_history(self):
        page = QWidget()
        layout = QVBoxLayout(page)
        toolbar = QHBoxLayout()
        self.source = QComboBox()
        for label, value in (("全部", "all"), ("电脑", "linux"), ("手机", "phone")):
            self.source.addItem(label, value)
        self.source.currentIndexChanged.connect(self.history_changed)
        toolbar.addWidget(self.source)
        self.search = QLineEdit()
        self.search.setPlaceholderText("搜索剪贴簿")
        self.search.textChanged.connect(self.history_changed)
        toolbar.addWidget(self.search, 1)
        toolbar.addWidget(self.icon_button("edit-copy", "复制选中条目", lambda: self.selected_action("copy")))
        toolbar.addWidget(self.icon_button("emblem-favorite", "置顶 / 取消置顶", self.pin_selected))
        toolbar.addWidget(self.icon_button("edit-delete", "删除选中条目", lambda: self.selected_action("delete")))
        layout.addLayout(toolbar)
        category_row = QHBoxLayout()
        category_row.addWidget(QLabel("类型"))
        self.category = QComboBox()
        self.category.setAccessibleName("内容类型")
        for label, value in CATEGORIES:
            self.category.addItem(label, value)
        self.category.currentIndexChanged.connect(self.history_changed)
        category_row.addWidget(self.category)
        category_row.addStretch()
        category_row.addWidget(self.icon_button("insert-image", "导入图片", self.import_image))
        layout.addLayout(category_row)
        splitter = QSplitter()
        splitter.setHandleWidth(12)
        self.items = QListWidget()
        self.items.setIconSize(QSize(64, 48))
        self.items.setHorizontalScrollBarPolicy(Qt.ScrollBarPolicy.ScrollBarAlwaysOff)
        self.items.setTextElideMode(Qt.TextElideMode.ElideRight)
        self.items.currentItemChanged.connect(self.show_item)
        self.items.itemDoubleClicked.connect(lambda *_: self.paste_selected())
        self.preview = QTextEdit()
        self.preview.setReadOnly(True)
        self.preview.setPlaceholderText("选择一条剪贴簿内容")
        self.image_preview = ImagePreview()
        self.image_preview.setAlignment(Qt.AlignmentFlag.AlignCenter)
        self.image_preview.setMinimumSize(120, 100)
        self.image_preview.setStyleSheet("background: white; border: 1px solid #e0e5e9; border-radius: 6px;")
        self.image_preview.hide()
        detail = QWidget()
        detail_layout = QVBoxLayout(detail)
        detail_layout.setContentsMargins(0, 0, 0, 0)
        detail_header = QHBoxLayout()
        self.detail_title = QLabel("内容")
        self.detail_title.setObjectName("sectionTitle")
        detail_header.addWidget(self.detail_title)
        detail_header.addStretch()
        self.edit_button = self.icon_button("document-edit", "编辑内容", self.edit_selected)
        self.edit_button.setEnabled(False)
        detail_header.addWidget(self.edit_button)
        self.paste_button = QPushButton("粘贴")
        self.paste_button.setObjectName("pasteButton")
        self.paste_button.setIcon(QIcon.fromTheme("edit-paste"))
        self.paste_button.setToolTip("粘贴到原输入框")
        self.paste_button.clicked.connect(self.paste_selected)
        self.paste_button.setEnabled(False)
        detail_header.addWidget(self.paste_button)
        detail_layout.addLayout(detail_header)
        detail_layout.addWidget(self.preview, 1)
        detail_layout.addWidget(self.image_preview, 1)
        self.detail_meta = QLabel(" ")
        self.detail_meta.setWordWrap(True)
        self.detail_meta.setObjectName("detailMeta")
        detail_layout.addWidget(self.detail_meta)
        splitter.addWidget(self.items)
        splitter.addWidget(detail)
        splitter.setSizes([400, 400])
        layout.addWidget(splitter, 1)
        self.history_count = QLabel("0 条内容")
        self.history_count.setObjectName("historyCount")
        layout.addWidget(self.history_count)
        self.tabs.addTab(page, "剪贴簿")

    def make_devices(self):
        page = QWidget()
        layout = QVBoxLayout(page)
        row = QHBoxLayout()
        row.addWidget(QLabel("已配对设备"))
        row.addStretch()
        pair = QPushButton("配对手机")
        pair.setIcon(QIcon.fromTheme("list-add"))
        pair.clicked.connect(self.pair)
        row.addWidget(pair)
        layout.addLayout(row)
        self.devices = QListWidget()
        layout.addWidget(self.devices)
        self.pending = QComboBox()
        self.pending.setAccessibleName("待连接手机")
        layout.addWidget(self.pending)
        pairing_row = QHBoxLayout()
        self.pair_code = QLineEdit()
        self.pair_code.setPlaceholderText("手机显示的 8 位连接码")
        self.pair_code.setAccessibleName("手机连接码")
        self.pair_code.setMaxLength(8)
        self.pair_code.setValidator(QIntValidator(0, 99999999, self))
        self.pending.currentIndexChanged.connect(self.pair_code.clear)
        pairing_row.addWidget(self.pair_code)
        self.approve = QPushButton("验证并允许")
        self.approve.clicked.connect(self.approve_pending)
        pairing_row.addWidget(self.approve)
        self.reject = self.icon_button("dialog-cancel", "拒绝连接", self.reject_pending)
        if self.reject.icon().isNull():
            self.reject.setIcon(self.style().standardIcon(QStyle.StandardPixmap.SP_DialogCancelButton))
        pairing_row.addWidget(self.reject)
        self.pair_code.returnPressed.connect(self.approve_pending)
        layout.addLayout(pairing_row)
        self.update_pending([])
        revoke = QPushButton("撤销选中设备")
        revoke.setIcon(QIcon.fromTheme("list-remove"))
        revoke.clicked.connect(self.revoke)
        layout.addWidget(revoke, alignment=Qt.AlignmentFlag.AlignLeft)
        self.endpoint = QLabel()
        self.endpoint.setWordWrap(True)
        self.endpoint.setTextInteractionFlags(Qt.TextInteractionFlag.TextSelectableByMouse)
        layout.addWidget(self.endpoint)
        self.tabs.addTab(page, "设备")

    def make_settings(self):
        page = QWidget()
        layout = QVBoxLayout(page)
        self.send = QCheckBox("Linux → 手机")
        self.receive = QCheckBox("手机 → Linux")
        self.send.clicked.connect(lambda value: self.rpc("settings", send_enabled=value))
        self.receive.clicked.connect(lambda value: self.rpc("settings", receive_enabled=value))
        layout.addWidget(self.send)
        layout.addWidget(self.receive)
        row = QHBoxLayout()
        for title, action, icon in (("导出剪贴簿", self.backup, "document-save"),
                                    ("恢复剪贴簿", self.restore, "document-open")):
            button = QPushButton(title)
            button.setIcon(QIcon.fromTheme(icon))
            button.clicked.connect(action)
            row.addWidget(button)
        row.addStretch()
        layout.addLayout(row)
        layout.addStretch()
        stop = QPushButton("停止后台同步")
        stop.setIcon(QIcon.fromTheme("process-stop"))
        stop.clicked.connect(self.stop)
        layout.addWidget(stop, alignment=Qt.AlignmentFlag.AlignLeft)
        self.tabs.addTab(page, "设置")

    def history_changed(self, *_):
        self.generation += 1
        QTimer.singleShot(150, self.refresh)

    def refresh(self):
        if self.busy:
            return
        self.busy = True
        source, search, generation = self.source.currentData(), self.search.text(), self.generation
        category = self.category.currentData()

        def fetch():
            return (request("status", self.directory),
                    request("history", self.directory, source=source, search=search, category=category))

        def update(value, error):
            self.busy = False
            if error:
                self.status.setText("后台离线")
                return
            state, rows = value
            online = sum(time.time() - device["last_seen"] < 30 for device in state["devices"])
            if not (state["send_enabled"] or state["receive_enabled"]):
                self.status.setText("已暂停")
            elif online:
                self.status.setText(f"已连接 {online} 台设备")
            else:
                self.status.setText("后台运行 · 未配对" if not state["devices"] else "后台运行 · 设备离线")
            self.send.setChecked(state["send_enabled"])
            self.receive.setChecked(state["receive_enabled"])
            self.endpoint.setText("\n".join(state["endpoints"]))
            if state.get("discovery_error") or state.get("clipboard_error"):
                self.statusBar().showMessage(state.get("discovery_error") or state["clipboard_error"])
            self.update_devices(state["devices"])
            self.update_pending(state.get("pending_pairings", []))
            if generation == self.generation and rows != self.history_rows:
                self.history_rows = rows
                selected = self.current()
                self.items.blockSignals(True)
                self.items.clear()
                for row in rows:
                    text = row["text"].replace("\n", " ")
                    item = QListWidgetItem(("[置顶] " if row["pinned"] else "") + text[:100])
                    item.setData(Qt.ItemDataRole.UserRole, row)
                    if row.get("kind") == "image":
                        pixmap = QPixmap()
                        pixmap.loadFromData(base64.b64decode(row["image"]["preview"]))
                        item.setIcon(QIcon(pixmap))
                        item.setText(("[置顶] " if row["pinned"] else "") +
                                     f"图片 · {row['image']['width']} × {row['image']['height']}")
                    item.setToolTip("手机" if row["source"] == "phone" else "Linux")
                    self.items.addItem(item)
                    if selected and row["id"] == selected["id"]:
                        self.items.setCurrentItem(item)
                self.items.blockSignals(False)
                self.show_item(self.items.currentItem(), None)
                self.history_count.setText(f"{len(rows)} 条内容")
            elif generation != self.generation:
                QTimer.singleShot(0, self.refresh)

        self.run(fetch, update)

    def current(self):
        item = self.items.currentItem()
        return item.data(Qt.ItemDataRole.UserRole) if item else None

    def show_item(self, current, previous):
        row = current.data(Qt.ItemDataRole.UserRole) if current else None
        image = row is not None and row.get("kind") == "image"
        self.preview.setVisible(not image)
        self.image_preview.setVisible(image)
        self.preview.setPlainText(row["text"] if row else "")
        self.edit_button.setEnabled(row is not None and not image)
        self.paste_button.setEnabled(row is not None and not self.paste_busy)
        if row:
            source = "手机" if row["source"] == "phone" else "电脑"
            self.detail_meta.setText(f"{source} · {len(row['text'])} 字符")
            if image:
                pixmap = QPixmap()
                pixmap.loadFromData(base64.b64decode(row["image"]["preview"]))
                self.image_preview.setPixmap(pixmap)
                cached = "原图已缓存" if row.get("cached") else "预览已缓存 · 粘贴时下载原图"
                self.detail_meta.setText(f"{source} · {row['image']['size'] / 1024:.0f} KiB · {cached}")
        else:
            self.detail_meta.setText(" ")

    def edit_selected(self):
        row = self.current()
        if not row or row.get("kind") == "image":
            return
        dialog = QDialog(self)
        dialog.setWindowTitle("编辑剪贴簿")
        dialog.setWindowModality(Qt.WindowModality.WindowModal)
        dialog.resize(560, 360)
        layout = QVBoxLayout(dialog)
        editor = QTextEdit()
        editor.setAcceptRichText(False)
        editor.setPlainText(row["text"])
        layout.addWidget(editor)
        error_label = QLabel()
        error_label.setWordWrap(True)
        error_label.setStyleSheet("color: #b33f43;")
        layout.addWidget(error_label)
        buttons = QDialogButtonBox(QDialogButtonBox.StandardButton.Save | QDialogButtonBox.StandardButton.Cancel)
        buttons.button(QDialogButtonBox.StandardButton.Save).setText("保存")
        buttons.button(QDialogButtonBox.StandardButton.Cancel).setText("取消")
        layout.addWidget(buttons)
        buttons.rejected.connect(dialog.reject)

        def save():
            from .core import profile
            text = editor.toPlainText()
            try:
                profile(text)
            except (ValueError, OverflowError) as error:
                error_label.setText(str(error))
                return
            buttons.setEnabled(False)
            def completed(value, error):
                buttons.setEnabled(True)
                if error:
                    error_label.setText(error)
                    return
                dialog.accept()
                self.history_changed()
                self.statusBar().showMessage("已保存；点击粘贴或复制可使用修改后的内容", 5000)
            self.rpc("edit", completed, id=row["id"], text=text, expected_hash=row["hash"])

        buttons.accepted.connect(save)
        self.editor_dialog = dialog
        dialog.open()

    def import_image(self):
        path, _ = QFileDialog.getOpenFileName(self, "导入图片", "", "图片 (*.png *.jpg *.jpeg *.webp *.gif)")
        if path:
            self.rpc("import_image", lambda *_: self.history_changed(), path=path)

    def paste_selected(self):
        row = self.current()
        if not row or self.paste_busy:
            return
        self.paste_busy = True
        if row.get("kind") == "image" and not row.get("cached"):
            self.statusBar().showMessage("正在向手机请求原图…")
        self.paste_button.setEnabled(False)
        def failure(error):
            self.statusBar().showMessage(error, 10000)
            self.showNormal()
            self.raise_()
            self.activateWindow()
        def copied(value, error):
            self.paste_busy = False
            self.paste_button.setEnabled(self.current() is not None)
            if not error:
                self.statusBar().clearMessage()
                self.paste_target.paste(self, failure)
        self.rpc("copy", copied, id=row["id"])

    def selected_action(self, action, **kwargs):
        row = self.current()
        if row:
            self.rpc(action, lambda *_: self.refresh(), id=row["id"], **kwargs)

    def pin_selected(self):
        row = self.current()
        if row:
            self.selected_action("pin", value=not row["pinned"])

    def update_devices(self, devices):
        selected = self.devices.currentItem()
        identifier = selected.data(Qt.ItemDataRole.UserRole) if selected else None
        self.devices.clear()
        for device in devices:
            age = int(time.time() - device["last_seen"])
            status = "在线" if age < 30 else "离线"
            item = QListWidgetItem(f"{device['name']}    {status}")
            item.setData(Qt.ItemDataRole.UserRole, device["id"])
            self.devices.addItem(item)
            if device["id"] == identifier:
                self.devices.setCurrentItem(item)

    def pair(self):
        def display(value, error):
            if error:
                return
            dialog = QDialog(self)
            dialog.setWindowTitle("配对手机")
            layout = QVBoxLayout(dialog)
            code = qrcode.QRCode(box_size=6, border=4)
            code.add_data(value["uri"])
            code.make(fit=True)
            bitmap = code.make_image(fill_color="black", back_color="white")
            output = io.BytesIO()
            bitmap.save(output, format="PNG")
            image = QImage.fromData(output.getvalue())
            label = QLabel()
            label.setPixmap(QPixmap.fromImage(image))
            label.setStyleSheet("background: white; padding: 0;")
            label.setAlignment(Qt.AlignmentFlag.AlignCenter)
            layout.addWidget(label)
            remaining = QLabel()
            remaining.setAlignment(Qt.AlignmentFlag.AlignCenter)
            layout.addWidget(remaining)
            countdown = QTimer(dialog)

            def tick():
                seconds = max(0, value["expires_at"] - int(time.time()))
                remaining.setText(f"有效期 {seconds // 60}:{seconds % 60:02d}" if seconds else "二维码已过期")
                if not seconds:
                    label.clear()

            countdown.timeout.connect(tick)
            countdown.start(1000)
            tick()
            buttons = QDialogButtonBox(QDialogButtonBox.StandardButton.Close)
            buttons.rejected.connect(dialog.reject)
            layout.addWidget(buttons)
            dialog.exec()

        self.rpc("offer", display)

    def update_pending(self, rows):
        current = self.pending.currentData()
        identifiers = [row["id"] for row in rows]
        previous = [self.pending.itemData(i) for i in range(self.pending.count())]
        if identifiers != previous:
            self.pending.clear()
            for row in rows:
                self.pending.addItem(row["name"], row["id"])
            if current in identifiers:
                self.pending.setCurrentIndex(identifiers.index(current))
            else:
                self.pair_code.clear()
        for control in (self.pending, self.pair_code, self.approve, self.reject):
            control.setVisible(bool(rows))
        if rows:
            self.tabs.setTabText(1, f"设备 · {len(rows)} 个连接请求")
        else:
            self.tabs.setTabText(1, "设备")

    def approve_pending(self):
        identifier, code = self.pending.currentData(), self.pair_code.text()
        if identifier is None:
            return
        if len(code) != 8:
            self.statusBar().showMessage("请输入手机显示的完整 8 位连接码", 10000)
            return
        def completed(_, error):
            if not error:
                self.pair_code.clear()
                self.statusBar().showMessage("已允许连接，请在手机确认", 10000)
                self.refresh()
        self.rpc("approve_pairing", completed, id=identifier, code=code)

    def reject_pending(self):
        identifier = self.pending.currentData()
        if identifier:
            self.rpc("reject_pairing", lambda *_: self.refresh(), id=identifier)

    def revoke(self):
        item = self.devices.currentItem()
        if item and QMessageBox.question(self, "撤销配对", "撤销此设备的访问权限？") == QMessageBox.StandardButton.Yes:
            self.rpc("revoke", lambda *_: self.refresh(), id=item.data(Qt.ItemDataRole.UserRole))

    def backup(self):
        path, _ = QFileDialog.getSaveFileName(self, "导出剪贴簿", "cyime-clipboard.sqlite3", "SQLite (*.sqlite3)")
        if path:
            self.rpc("backup", path=path)

    def restore(self):
        path, _ = QFileDialog.getOpenFileName(self, "恢复剪贴簿", "", "SQLite (*.sqlite3)")
        if path:
            self.rpc("restore", lambda *_: self.refresh(), path=path)

    def stop(self):
        if QMessageBox.question(self, "停止后台", "停止同步及剪贴簿记录？") == QMessageBox.StandardButton.Yes:
            self.rpc("stop", lambda value, error: self.close() if not error else None)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--state-dir", type=Path, default=default_state_dir())
    parser.add_argument("--port", type=int, default=8788)
    parser.add_argument("--toggle-clipboard", action="store_true")
    parser.add_argument("--open-settings", action="store_true")
    parser.add_argument("--screenshot", type=Path, help=argparse.SUPPRESS)
    args = parser.parse_args()
    target = active_target()
    os.umask(0o077)
    app = QApplication(sys.argv[:1])
    app.setQuitOnLastWindowClosed(True)
    app.setStyle("Fusion")
    palette = QPalette()
    for role, color in ((QPalette.ColorRole.Window, "#f5f7f8"),
                        (QPalette.ColorRole.WindowText, "#24343c"),
                        (QPalette.ColorRole.Base, "#ffffff"),
                        (QPalette.ColorRole.Text, "#24343c"),
                        (QPalette.ColorRole.Button, "#edf1f3"),
                        (QPalette.ColorRole.ButtonText, "#24343c"),
                        (QPalette.ColorRole.Highlight, "#247b8a"),
                        (QPalette.ColorRole.HighlightedText, "#ffffff")):
        palette.setColor(role, QColor(color))
    app.setPalette(palette)
    instance = None
    try:
        command = "settings" if args.open_settings else "clipboard" if args.toggle_clipboard else "show"
        instance = ManagerInstance(args.state_dir, command, target)
        if not instance.primary:
            return 0
        launch_backend(args.state_dir, args.port)
    except Exception as error:
        if instance:
            instance.close()
        QMessageBox.critical(None, "CyIME", str(error))
        return 1
    window = Window(args.state_dir)
    window.paste_target.remember(target)
    instance.attach(window)
    if args.open_settings:
        window.tabs.setCurrentIndex(2)
    if args.toggle_clipboard:
        window.open_clipboard(target=target)
    else:
        window.show()
    if args.screenshot:
        def capture():
            window.grab().save(str(args.screenshot))
            app.quit()
        QTimer.singleShot(2500, capture)
    try:
        return app.exec()
    finally:
        instance.close()


if __name__ == "__main__":
    sys.exit(main())
