"""Compact desktop IME preferences and a local theme preview."""

from pathlib import Path

from PyQt6.QtCore import Qt, QRectF, QSize
from PyQt6.QtGui import QColor, QIcon, QPainter, QPixmap
from PyQt6.QtWidgets import (QCheckBox, QColorDialog, QComboBox, QFileDialog, QFormLayout,
    QHBoxLayout, QLabel, QMessageBox, QPushButton, QScrollArea, QSlider, QSpinBox,
    QStyle, QTabWidget, QToolButton, QVBoxLayout, QWidget)

from .ime_settings import ImeSettings, import_background, load, save


class ThemePreview(QWidget):
    def __init__(self):
        super().__init__()
        self.settings = ImeSettings()
        self.setMinimumHeight(112)
        self.setMaximumHeight(112)
        self.image = QPixmap()

    def set_settings(self, settings):
        self.settings = settings
        self.image = QPixmap(settings.background) if settings.background else QPixmap()
        self.update()

    def paintEvent(self, event):
        p = QPainter(self)
        s = self.settings
        light = s.theme in ("system", "light")
        p.fillRect(self.rect(), QColor("#f0f2f5" if light else "#161a20"))
        if s.theme == "custom" and not self.image.isNull():
            image = self.image.scaled(self.size(), Qt.AspectRatioMode.KeepAspectRatioByExpanding,
                                      Qt.TransformationMode.SmoothTransformation)
            p.drawPixmap((self.width() - image.width()) // 2, (self.height() - image.height()) // 2, image)
            p.fillRect(self.rect(), QColor(0, 0, 0, round(s.overlay * 2.55)))
        p.setPen(QColor("#20252b" if light else s.foreground))
        font = p.font()
        font.setPixelSize(s.font_size)
        p.setFont(font)
        p.drawText(16, 32, "ni hao")
        labels = ["你好", "你号", "拟好", "你", "泥", "妮", "倪", "尼", "昵"][:s.page_size]
        x = 16
        for index, text in enumerate(labels):
            label = f"{index + 1} {text}"
            width = p.fontMetrics().horizontalAdvance(label) + 18
            if x + width > self.width() - 44:
                break
            if index == 0:
                p.fillRect(QRectF(x - 4, 48, width, 36), QColor("#cce9ed" if light else "#314854"))
            p.drawText(x, 73, label)
            x += width + 4
        p.drawText(self.width() - 40, 73, "‹  ›")


class Switch(QCheckBox):
    def sizeHint(self):
        return QSize(42, 26)

    def hitButton(self, position):
        return self.rect().contains(position)

    def paintEvent(self, event):
        painter = QPainter(self)
        painter.setRenderHint(QPainter.RenderHint.Antialiasing)
        painter.setPen(Qt.PenStyle.NoPen)
        painter.setBrush(QColor("#247b8a" if self.isChecked() else "#a5adb5"))
        painter.drawRoundedRect(QRectF(1, 3, 40, 22), 11, 11)
        painter.setBrush(QColor("#ffffff"))
        painter.drawEllipse(QRectF(23 if self.isChecked() else 4, 6, 16, 16))
        if self.hasFocus():
            painter.setBrush(Qt.BrushStyle.NoBrush)
            painter.setPen(QColor("#247b8a"))
            painter.drawRoundedRect(QRectF(0, 1, 41, 25), 6, 6)


class ImeSettingsPage(QScrollArea):
    def __init__(self, directory=None):
        super().__init__()
        self.directory = directory
        self.original = load(directory)
        self.background = self.original.background
        self.colors = {key: getattr(self.original, key) for key in ("foreground", "accent")}
        self.setWidgetResizable(True)
        self.setFrameShape(QScrollArea.Shape.NoFrame)
        self.setObjectName("ime-settings")
        self.setStyleSheet("""
            QScrollArea#ime-settings, QScrollArea#ime-settings QWidget { background: #f6f7f8; color: #242b33; font-size: 14px; }
            QScrollArea#ime-settings QLabel#heading { font-size: 22px; font-weight: 600; }
            QScrollArea#ime-settings QLabel#detail { color: #656e77; font-size: 12px; }
            QScrollArea#ime-settings QTabWidget::pane { border: 0; }
            QScrollArea#ime-settings QTabBar::tab { padding: 10px 18px; border-bottom: 2px solid transparent; }
            QScrollArea#ime-settings QTabBar::tab:selected { color: #176474; border-bottom-color: #247b8a; }
            QScrollArea#ime-settings QSpinBox, QScrollArea#ime-settings QComboBox { background: white; border: 1px solid #d3d9de; border-radius: 5px; padding: 6px 10px; min-height: 22px; }
            QScrollArea#ime-settings QPushButton, QScrollArea#ime-settings QToolButton { background: #ffffff; border: 1px solid #d3d9de; border-radius: 5px; padding: 6px 10px; min-height: 22px; }
            QScrollArea#ime-settings QPushButton#apply { background: #247b8a; color: white; border-color: #247b8a; }
        """)
        content = QWidget()
        layout = QVBoxLayout(content)
        layout.setContentsMargins(22, 20, 22, 18)
        layout.setSpacing(16)
        heading = QLabel("输入法设置")
        heading.setObjectName("heading")
        layout.addWidget(heading)
        self.preview = ThemePreview()
        layout.addWidget(self.preview)
        self.tabs = QTabWidget()
        layout.addWidget(self.tabs)
        input_page, theme_page = QWidget(), QWidget()
        input_layout, theme_layout = QVBoxLayout(input_page), QVBoxLayout(theme_page)
        for section in (input_layout, theme_layout):
            section.setContentsMargins(0, 18, 0, 0)
            section.setSpacing(14)
        self.tabs.addTab(input_page, "输入")
        self.tabs.addTab(theme_page, "外观")
        input_form = QFormLayout()
        input_form.setHorizontalSpacing(24)
        input_form.setFieldGrowthPolicy(QFormLayout.FieldGrowthPolicy.AllNonFixedFieldsGrow)
        input_layout.addLayout(input_form)
        form = QFormLayout()
        form.setHorizontalSpacing(24)
        form.setVerticalSpacing(12)
        form.setFieldGrowthPolicy(QFormLayout.FieldGrowthPolicy.AllNonFixedFieldsGrow)
        self.page_size = QSpinBox()
        self.page_size.setMaximumWidth(130)
        self.page_size.setRange(3, 9)
        self.page_size.setValue(self.original.page_size)
        input_form.addRow("每页候选", self.page_size)
        self.font_size = QSpinBox()
        self.font_size.setMaximumWidth(130)
        self.font_size.setRange(12, 28)
        self.font_size.setSuffix(" px")
        self.font_size.setValue(self.original.font_size)
        form.addRow("候选字号", self.font_size)
        self.theme = QComboBox()
        for label, value in (("跟随系统", "system"), ("浅色", "light"), ("深色", "dark"), ("自定义", "custom")):
            self.theme.addItem(label, value)
        self.theme.setCurrentIndex(self.theme.findData(self.original.theme))
        form.addRow("候选窗主题", self.theme)
        image_row = QHBoxLayout()
        image_button = QPushButton("选择背景")
        image_button.setMaximumWidth(150)
        image_button.setIcon(QIcon.fromTheme("document-open"))
        image_button.clicked.connect(self.choose_background)
        image_row.addWidget(image_button)
        clear = QToolButton()
        clear.setIcon(QIcon.fromTheme("edit-clear", self.style().standardIcon(QStyle.StandardPixmap.SP_DialogResetButton)))
        clear.setFixedSize(32, 32)
        clear.setToolTip("清除背景")
        clear.clicked.connect(self.clear_background)
        image_row.addWidget(clear)
        self.background_label = QLabel(Path(self.background).name if self.background else "无")
        self.background_label.setMaximumWidth(230)
        self.background_label.setWordWrap(True)
        image_row.addWidget(self.background_label, 1)
        form.addRow("背景图片", image_row)
        self.overlay = QSlider(Qt.Orientation.Horizontal)
        self.overlay.setRange(60, 95)
        self.overlay.setValue(self.original.overlay)
        self.overlay.setToolTip("背景遮罩强度")
        form.addRow("背景压暗", self.overlay)
        swatches = QHBoxLayout()
        self.swatches = {}
        for key, label in (("foreground", "文字颜色"), ("accent", "选中颜色")):
            button = QToolButton()
            button.setFixedSize(36, 28)
            button.setToolTip(label)
            button.clicked.connect(lambda _, key=key: self.choose_color(key))
            self.swatches[key] = button
            swatches.addWidget(button)
        swatches.addStretch()
        form.addRow("颜色", swatches)
        theme_layout.addLayout(form)
        theme_layout.addStretch()
        self.checks = {}
        for key, label, description in (
            ("inline_preview", "首选词预览", "输入位置显示未提交的首选中文"),
            ("page_keys", "减号 / 等号翻页", "保留 Page Up / Page Down"),
            ("tab_pages", "Tab 翻页", "Shift + Tab 返回上一页"),
            ("ascii_punct", "英文标点", "使用半角英文标点符号"),
            ("full_shape", "全角字符", "字母与数字使用全角形式")):
            row = QHBoxLayout()
            labels = QVBoxLayout()
            labels.setSpacing(3)
            labels.addWidget(QLabel(label))
            detail = QLabel(description)
            detail.setObjectName("detail")
            detail.setWordWrap(True)
            labels.addWidget(detail)
            row.addLayout(labels, 1)
            check = Switch()
            check.setFixedSize(42, 28)
            check.setAccessibleName(label)
            check.setChecked(getattr(self.original, key))
            self.checks[key] = check
            row.addWidget(check)
            input_layout.addLayout(row)
        input_layout.addStretch()
        footer = QHBoxLayout()
        self.status = QLabel()
        self.status.setWordWrap(True)
        footer.addWidget(self.status, 1)
        apply = QPushButton("应用")
        apply.setObjectName("apply")
        apply.setIcon(QIcon.fromTheme("document-save"))
        apply.clicked.connect(self.apply)
        footer.addWidget(apply)
        layout.addLayout(footer)
        layout.addStretch()
        self.setWidget(content)
        for control in (self.page_size, self.font_size, self.overlay):
            control.valueChanged.connect(self.update_preview)
        self.theme.currentIndexChanged.connect(self.update_preview)
        for check in self.checks.values():
            check.toggled.connect(self.update_preview)
        self.update_preview()

    def values(self):
        return ImeSettings(page_size=self.page_size.value(), font_size=self.font_size.value(),
            theme=self.theme.currentData(), background=self.background, overlay=self.overlay.value(),
            **self.colors, **{key: check.isChecked() for key, check in self.checks.items()})

    def update_preview(self, *_):
        self.preview.set_settings(self.values())
        for key, button in self.swatches.items():
            button.setStyleSheet(f"background: {self.colors[key]}; border: 1px solid #68737c;")

    def choose_background(self):
        path, _ = QFileDialog.getOpenFileName(self, "候选窗背景", "", "Images (*.png *.jpg *.jpeg *.webp)")
        if path:
            try:
                self.background = import_background(path, self.directory)
                self.background_label.setText(Path(path).name)
                self.theme.setCurrentIndex(self.theme.findData("custom"))
                self.update_preview()
            except Exception as error:
                QMessageBox.warning(self, "背景图片", str(error))

    def clear_background(self):
        self.background = ""
        self.background_label.setText("无")
        self.update_preview()

    def choose_color(self, key):
        color = QColorDialog.getColor(QColor(self.colors[key]), self)
        if color.isValid():
            self.colors[key] = color.name()
            self.update_preview()

    def apply(self):
        try:
            save(self.values(), self.directory)
            self.status.setText("已保存，下次输入生效")
        except Exception as error:
            QMessageBox.warning(self, "保存设置", str(error))


def main(directory=None):
    import sys
    from PyQt6.QtWidgets import QApplication
    app = QApplication(["cyime-settings"])
    app.setApplicationName("cyime-settings")
    app.setDesktopFileName("cyime-settings")
    app.setStyle("Fusion")
    window = ImeSettingsPage(directory)
    window.setWindowTitle("CyIME · 输入法设置")
    window.resize(680, 700)
    window.show()
    return app.exec()
