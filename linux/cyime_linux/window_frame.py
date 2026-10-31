"""Qt title bar with native window movement and resizing."""

from PyQt6.QtCore import QEvent, Qt
from PyQt6.QtWidgets import QHBoxLayout, QLabel, QMainWindow, QStyle, QToolButton, QWidget


class TitleBar(QWidget):
    def __init__(self, window):
        super().__init__(window)
        self.window = window
        self.setObjectName("titleBar")
        self.setFixedHeight(44)
        layout = QHBoxLayout(self)
        layout.setContentsMargins(16, 0, 8, 0)
        title = QLabel("CyIME · 剪贴簿")
        title.setStyleSheet("font-size: 13px; font-weight: 600; color: #52616a;")
        title.setAttribute(Qt.WidgetAttribute.WA_TransparentForMouseEvents)
        layout.addWidget(title)
        layout.addStretch()
        self.buttons = []
        for name, icon, callback in (
                ("最小化", QStyle.StandardPixmap.SP_TitleBarMinButton, window.showMinimized),
                ("最大化", QStyle.StandardPixmap.SP_TitleBarMaxButton, self.toggle_maximized),
                ("关闭窗口", QStyle.StandardPixmap.SP_TitleBarCloseButton, window.close)):
            button = QToolButton()
            button.setFixedSize(32, 30)
            button.setIcon(window.style().standardIcon(icon))
            button.setToolTip(name)
            button.setAccessibleName(name)
            if name == "关闭窗口":
                button.setObjectName("closeButton")
            button.clicked.connect(callback)
            layout.addWidget(button)
            self.buttons.append(button)

    def toggle_maximized(self):
        self.window.showNormal() if self.window.isMaximized() else self.window.showMaximized()

    def mousePressEvent(self, event):
        if event.button() == Qt.MouseButton.LeftButton and self.window.windowHandle():
            self.window.windowHandle().startSystemMove()
            event.accept()

    def mouseDoubleClickEvent(self, event):
        if event.button() == Qt.MouseButton.LeftButton:
            self.toggle_maximized()
            event.accept()


class FramelessWindow(QMainWindow):
    def __init__(self):
        super().__init__()
        self.setWindowFlag(Qt.WindowType.FramelessWindowHint)

    def install_frame(self, body):
        from PyQt6.QtWidgets import QVBoxLayout
        shell = QWidget()
        shell.setObjectName("windowShell")
        shell.setMouseTracking(True)
        shell.installEventFilter(self)
        layout = QVBoxLayout(shell)
        layout.setContentsMargins(5, 5, 5, 5)
        layout.setSpacing(0)
        self.title_bar = TitleBar(self)
        layout.addWidget(self.title_bar)
        layout.addWidget(body, 1)
        self.setCentralWidget(shell)

    def eventFilter(self, watched, event):
        if watched.objectName() == "windowShell" and event.type() in (QEvent.Type.MouseMove, QEvent.Type.MouseButtonPress):
            point = event.position()
            edges = Qt.Edge(0)
            if not self.isMaximized():
                if point.x() < 5: edges |= Qt.Edge.LeftEdge
                if point.x() >= watched.width() - 5: edges |= Qt.Edge.RightEdge
                if point.y() < 5: edges |= Qt.Edge.TopEdge
                if point.y() >= watched.height() - 5: edges |= Qt.Edge.BottomEdge
            if event.type() == QEvent.Type.MouseButtonPress and event.button() == Qt.MouseButton.LeftButton and edges:
                return self.windowHandle().startSystemResize(edges)
            cursor = Qt.CursorShape.ArrowCursor
            if edges in (Qt.Edge.LeftEdge | Qt.Edge.TopEdge, Qt.Edge.RightEdge | Qt.Edge.BottomEdge):
                cursor = Qt.CursorShape.SizeFDiagCursor
            elif edges in (Qt.Edge.LeftEdge | Qt.Edge.BottomEdge, Qt.Edge.RightEdge | Qt.Edge.TopEdge):
                cursor = Qt.CursorShape.SizeBDiagCursor
            elif edges & (Qt.Edge.LeftEdge | Qt.Edge.RightEdge):
                cursor = Qt.CursorShape.SizeHorCursor
            elif edges:
                cursor = Qt.CursorShape.SizeVerCursor
            watched.setCursor(cursor)
        return super().eventFilter(watched, event)

    def changeEvent(self, event):
        super().changeEvent(event)
        if event.type() == QEvent.Type.WindowStateChange and hasattr(self, "title_bar"):
            button = self.title_bar.buttons[1]
            button.setToolTip("还原" if self.isMaximized() else "最大化")
            button.setIcon(self.style().standardIcon(QStyle.StandardPixmap.SP_TitleBarNormalButton
                if self.isMaximized() else QStyle.StandardPixmap.SP_TitleBarMaxButton))
