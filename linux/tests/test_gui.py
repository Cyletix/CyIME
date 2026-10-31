import os
os.environ.setdefault("QT_QPA_PLATFORM", "offscreen")

import unittest
from pathlib import Path
import tempfile
import subprocess
import sys
from unittest.mock import patch
from PyQt6.QtCore import Qt
from PyQt6.QtTest import QTest
from PyQt6.QtWidgets import QApplication, QDialogButtonBox, QListWidgetItem, QTextEdit

from cyime_linux.gui import Window, ManagerInstance
from cyime_linux.ime_settings_gui import ImeSettingsPage
from cyime_linux.ime_settings import load
from cyime_linux.tray import BackendTray
from test_media import fixture
from cyime_linux.clipboard_media import CATEGORIES


class GuiTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.app = QApplication.instance() or QApplication([])

    def test_settings_controls_and_saving_at_compact_size(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            page = ImeSettingsPage(directory)
            try:
                page.resize(560, 640)
                page.show()
                self.app.processEvents()
                for index in range(page.tabs.count()):
                    page.tabs.setCurrentIndex(index)
                    self.app.processEvents()
                    self.assertGreater(page.tabs.currentWidget().width(), 450)
                page.page_size.setValue(8)
                page.checks["tab_pages"].setChecked(False)
                page.apply()
                self.assertEqual(load(directory).page_size, 8)
                self.assertFalse(load(directory).tab_pages)
                self.assertEqual(page.checks["tab_pages"].accessibleName(), "Tab 翻页")
            finally:
                page.close()

    def test_background_tray_has_settings_and_manager_entries(self):
        with patch.object(BackendTray, "show"), patch("cyime_linux.tray.QProcess.startDetached", return_value=(True, 1)) as start:
            tray = BackendTray(self.app, Path("/tmp/cyime-test"), 8788)
            try:
                actions = tray.menu.actions()
                self.assertEqual([action.text() for action in actions if not action.isSeparator()], ["剪贴簿", "停止同步后台"])
                actions[0].trigger()
                self.assertIn("gui", start.call_args.args[1])
                self.assertIn("/tmp/cyime-test", start.call_args.args[1])
            finally:
                tray.hide()
                tray.deleteLater()

    def test_nearby_entry_submits_code_and_preserves_selection(self):
        with patch.object(Window, "refresh"):
            window = Window(None)
        window.timer.stop()
        try:
            rows = [dict(id="phone-1", name="ASUS", expires_in=90)]
            window.update_pending(rows)
            window.pair_code.setText("01234567")
            window.update_pending(rows)
            self.assertEqual(window.pair_code.text(), "01234567")
            with patch.object(window, "rpc") as rpc:
                window.approve_pending()
                self.assertEqual(rpc.call_args.args[0], "approve_pairing")
                self.assertEqual(rpc.call_args.kwargs, dict(id="phone-1", code="01234567"))
            window.update_pending([])
            self.assertTrue(window.pair_code.isHidden())
        finally:
            window.close()

    def add_row(self, window):
        row = dict(id=21, text="可修改内容", hash="original-hash", source="phone", pinned=1)
        item = QListWidgetItem(row["text"])
        item.setData(Qt.ItemDataRole.UserRole, row)
        window.items.addItem(item)
        window.items.setCurrentItem(item)
        return row, item

    def test_editor_explicit_save_and_cancel_do_not_change_history_implicitly(self):
        with patch.object(Window, "refresh"):
            window = Window(None)
        window.timer.stop()
        try:
            row, _ = self.add_row(window)
            with patch.object(window, "rpc") as rpc:
                window.edit_selected()
                dialog = window.editor_dialog
                editor = dialog.findChild(QTextEdit)
                editor.setPlainText("修改后\n两行")
                buttons = dialog.findChild(QDialogButtonBox)
                buttons.button(QDialogButtonBox.StandardButton.Save).click()
                self.assertEqual(rpc.call_args.args[0], "edit")
                self.assertEqual(rpc.call_args.kwargs, dict(id=21, text="修改后\n两行", expected_hash="original-hash"))
                rpc.call_args.args[1](dict(row, text=editor.toPlainText()), None)
                self.assertFalse(dialog.isVisible())
                rpc.reset_mock()
                window.edit_selected()
                window.editor_dialog.findChild(QTextEdit).setPlainText("不保存")
                window.editor_dialog.reject()
                rpc.assert_not_called()
        finally:
            window.close()

    def test_double_click_waits_for_copy_success_before_pasting(self):
        with patch.object(Window, "refresh"):
            window = Window(None)
        window.timer.stop()
        try:
            _, item = self.add_row(window)
            with patch.object(window, "rpc") as rpc, patch.object(window.paste_target, "paste") as paste:
                window.items.itemDoubleClicked.emit(item)
                self.assertEqual(rpc.call_args.args[0], "copy")
                paste.assert_not_called()
                self.assertTrue(window.paste_busy)
                window.paste_selected()
                self.assertEqual(rpc.call_count, 1)
                rpc.call_args.args[1](True, None)
                paste.assert_called_once()
                self.assertFalse(window.paste_busy)
                paste.reset_mock()
                window.paste_selected()
                rpc.call_args.args[1](None, "backend offline")
                paste.assert_not_called()
        finally:
            window.close()

    def test_custom_titlebar_has_native_frame_hidden_and_window_controls(self):
        with patch.object(Window, "refresh"):
            window = Window(None)
        window.timer.stop()
        try:
            window.show()
            self.app.processEvents()
            self.assertTrue(window.windowFlags() & Qt.WindowType.FramelessWindowHint)
            QTest.mouseDClick(window.title_bar, Qt.MouseButton.LeftButton)
            self.app.processEvents()
            self.assertTrue(window.isMaximized())
            window.title_bar.buttons[1].click()
            self.assertFalse(window.isMaximized())
            window.title_bar.buttons[0].click()
            self.assertTrue(window.isMinimized())
            window.showNormal()
            window.title_bar.buttons[2].click()
            self.assertFalse(window.isVisible())
        finally:
            window.close()

    def test_clipboard_shortcut_opens_history_and_toggles_without_losing_search(self):
        with patch.object(Window, "refresh"):
            window = Window(None)
        window.timer.stop()
        try:
            self.assertEqual([window.source.itemText(i) for i in range(3)], ["全部", "电脑", "手机"])
            window.tabs.setCurrentIndex(1)
            window.search.setText("保留搜索")
            with patch.object(window, "isActiveWindow", return_value=True):
                window.open_clipboard(toggle=True)
                self.assertTrue(window.isVisible())
                self.assertEqual(window.tabs.currentIndex(), 0)
                window.open_clipboard(toggle=True)
                self.assertFalse(window.isVisible())
                window.open_clipboard(toggle=True)
                self.assertTrue(window.isVisible())
            self.assertEqual(window.search.text(), "保留搜索")
        finally:
            window.close()

    def test_clipboard_single_instance_forwards_over_private_socket(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            instance = ManagerInstance(directory, "show")
            self.assertTrue(instance.primary)
            with patch.object(Window, "refresh"):
                window = Window(None)
            window.timer.stop()
            instance.attach(window)
            try:
                script = ("from pathlib import Path; from PyQt6.QtWidgets import QApplication; "
                          "from cyime_linux.gui import ManagerInstance; "
                          "app=QApplication([]); "
                          f"instance=ManagerInstance(Path({str(directory)!r}), 'clipboard', dict(window=123, pid=456)); "
                          "assert not instance.primary")
                child = subprocess.run([sys.executable, "-c", script], capture_output=True, timeout=10)
                self.assertEqual(child.returncode, 0, child.stderr.decode())
                for _ in range(10):
                    self.app.processEvents()
                self.assertTrue(window.isVisible())
                self.assertEqual(window.tabs.currentIndex(), 0)
                self.assertEqual(window.paste_target.target, dict(window=123, pid=456))
                self.assertEqual(directory.stat().st_mode & 0o777, 0o700)
            finally:
                window.close()
                instance.close()
            reopened = ManagerInstance(directory, "show")
            self.assertTrue(reopened.primary)
            reopened.close()

    def test_tabs_and_compact_window_have_non_overlapping_controls(self):
        # No connection to real clipboard/backend and no worker started.
        class Preview(Window):
            def refresh(self):
                pass

        window = Preview(None)
        window.timer.stop()
        try:
            for width, height in ((900, 620), (650, 470)):
                window.resize(width, height)
                window.show()
                self.app.processEvents()
                for index in range(window.tabs.count()):
                    window.tabs.setCurrentIndex(index)
                    self.app.processEvents()
                    self.assertGreater(window.tabs.currentWidget().width(), 500)
                window.tabs.setCurrentIndex(0)
                self.app.processEvents()
                self.assertGreater(window.search.width(), 150)
                self.assertLessEqual(window.source.geometry().right(), window.search.geometry().left())
                self.assertGreater(window.items.width(), 200)
                self.assertGreater(window.preview.width(), 150)
        finally:
            window.close()

    def test_image_preview_filters_and_paste_waits_for_original(self):
        with patch.object(Window, "refresh"):
            window = Window(None)
        window.timer.stop()
        try:
            value, _ = fixture()
            self.assertEqual([window.category.itemText(i) for i in range(7)], [label for label, _ in CATEGORIES])
            row = dict(id=31, text="图片", hash=value["hash"], source="phone", pinned=0,
                       kind="image", image=value, cached=False)
            item = QListWidgetItem("手机图片 · 640 × 400")
            item.setData(Qt.ItemDataRole.UserRole, row)
            window.items.addItem(item)
            window.items.setCurrentItem(item)
            self.assertFalse(window.image_preview.pixmap().isNull())
            self.assertFalse(window.edit_button.isEnabled())
            self.assertIn("粘贴时下载原图", window.detail_meta.text())
            with patch.object(window, "rpc") as rpc, patch.object(window.paste_target, "paste") as paste:
                window.paste_selected()
                paste.assert_not_called()
                rpc.call_args.args[1](None, "原图未缓存，设备离线")
                paste.assert_not_called()
                self.assertEqual(window.items.count(), 1)
            for width, height in ((900, 620), (650, 470)):
                window.resize(width, height)
                window.show()
                self.app.processEvents()
                self.assertLessEqual(window.image_preview.geometry().right(), window.image_preview.parentWidget().width())
                output = os.environ.get("CYIME_MEDIA_SCREENSHOTS")
                if output:
                    Path(output).mkdir(parents=True, exist_ok=True)
                    window.grab().save(str(Path(output) / f"clipboard-image-{width}.png"))
        finally:
            window.close()


if __name__ == "__main__":
    unittest.main()
