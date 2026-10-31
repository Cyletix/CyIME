"""Isolated Xvfb edit/double-click/real-input integration; never use the real session."""

import argparse
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import time
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from PyQt6.QtCore import Qt, QTimer
from PyQt6.QtTest import QTest
from PyQt6.QtGui import QImage
from PyQt6.QtWidgets import QApplication, QDialogButtonBox, QListWidgetItem, QTextEdit


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--editor-output", type=Path)
    args = parser.parse_args()
    if os.environ.get("CYIME_ISOLATED_CLIPBOARD") != "1" or os.environ.get("DISPLAY") == ":1":
        raise SystemExit("Refusing non-isolated clipboard test")
    app = QApplication([])
    app.setQuitOnLastWindowClosed(False)
    if args.editor_output:
        class Destination(QTextEdit):
            def insertFromMimeData(self, mime):
                if mime.hasImage():
                    image = QImage(mime.imageData())
                    args.editor_output.with_suffix(".png").write_bytes(b"")
                    image.save(str(args.editor_output.with_suffix(".png")))
                else:
                    super().insertFromMimeData(mime)
        editor = Destination()
        editor.setWindowTitle("CyIME isolated paste destination")
        editor.setPlainText("prefix:")
        cursor = editor.textCursor()
        cursor.movePosition(cursor.MoveOperation.End)
        editor.setTextCursor(cursor)
        editor.textChanged.connect(lambda: args.editor_output.write_text(editor.toPlainText()))
        editor.show()
        print(int(editor.winId()), flush=True)
        QTimer.singleShot(20000, app.quit)
        return app.exec()

    from Xlib import X, Xatom, display
    from cyime_linux.core import Store
    from cyime_linux.daemon import Backend
    from cyime_linux.gui import Window
    from cyime_linux.paste_target import active_target
    from cyime_linux.clipboard_media import image_record, encode

    with tempfile.TemporaryDirectory() as temporary:
        directory = Path(temporary)
        output = directory / "editor.txt"
        child = subprocess.Popen([sys.executable, __file__, "--editor-output", str(output)],
                                 stdout=subprocess.PIPE, text=True)
        connection = display.Display()
        root = connection.screen().root
        active_atom = connection.intern_atom("_NET_ACTIVE_WINDOW")
        root.change_attributes(event_mask=X.SubstructureNotifyMask)
        def activate(identifier):
            target = connection.create_resource_object("window", identifier)
            root.change_property(active_atom, Xatom.WINDOW, 32, [identifier])
            target.set_input_focus(X.RevertToParent, X.CurrentTime)
            connection.sync()

        def wm_events():
            while connection.pending_events():
                event = connection.next_event()
                if event.type == X.ClientMessage and event.client_type == active_atom:
                    activate(event.window.id)

        wm = QTimer()
        wm.timeout.connect(wm_events)
        wm.start(5)
        store = Store(directory / "state")
        with patch.object(Backend, "refresh_network"):
            backend = Backend(store, app.clipboard(), app, 0)
        with patch.object(Window, "refresh"):
            window = Window(directory / "state")
        window.timer.stop()
        window.refresh = lambda: None
        def rpc(action, callback=None, **body):
            try:
                value, error = backend.control(dict(action=action, **body)), None
            except Exception as problem:
                value, error = None, str(problem)
            if callback:
                callback(value, error)
        window.rpc = rpc
        try:
            destination = int(child.stdout.readline())
            for _ in range(5):
                app.processEvents()
            activate(destination)
            target = active_target()
            assert target["window"] == destination
            store.record("原始内容", "phone")
            row = store.history()[0]
            item = QListWidgetItem(row["text"])
            item.setData(Qt.ItemDataRole.UserRole, row)
            window.items.addItem(item)
            window.items.setCurrentItem(item)
            window.open_clipboard(target=target)
            app.processEvents()
            activate(int(window.winId()))
            window.edit_selected()
            dialog = window.editor_dialog
            dialog.findChild(QTextEdit).setPlainText("编辑后\n可粘贴")
            dialog.findChild(QDialogButtonBox).button(QDialogButtonBox.StandardButton.Save).click()
            row = store.item(row["id"])
            assert row["text"] == "编辑后\n可粘贴"
            item.setData(Qt.ItemDataRole.UserRole, row)
            item.setText(row["text"])
            window.show_item(item, None)
            app.processEvents()
            activate(int(window.winId()))
            position = window.items.visualItemRect(item).center()
            QTest.mouseClick(window.items.viewport(), Qt.MouseButton.LeftButton, pos=position)
            QTest.mouseDClick(window.items.viewport(), Qt.MouseButton.LeftButton, pos=position)
            deadline = time.monotonic() + 5
            while time.monotonic() < deadline:
                app.processEvents()
                if output.exists() and output.read_text() == "prefix:编辑后\n可粘贴":
                    break
                time.sleep(.01)
            else:
                raise AssertionError("Paste did not reach the real editor: " +
                                     (output.read_text() if output.exists() else window.statusBar().currentMessage()))
            assert not window.isVisible()
            print("PASS: edit/save -> double-click -> restored X11 focus -> real Ctrl+V input")
            # Invalid targets never type into the currently focused editor.
            errors = []
            window.paste_target.target = dict(window=0x7fffffff, pid=999999)
            window.paste_target.paste(window, errors.append)
            assert errors and output.read_text() == "prefix:编辑后\n可粘贴"
            print("PASS: invalid destination refused without additional input")
            image = QImage(640, 400, QImage.Format.Format_RGB32)
            image.fill(0xff239f91)
            descriptor, original = image_record(encode(image))
            store.record_image(descriptor, "phone", original=original)
            row = next(row for row in store.history() if row.get("kind") == "image")
            window.items.clear()
            item = QListWidgetItem("手机图片 · 640 × 400")
            item.setData(Qt.ItemDataRole.UserRole, row)
            window.items.addItem(item)
            window.items.setCurrentItem(item)
            window.open_clipboard(target=target)
            app.processEvents()
            activate(int(window.winId()))
            position = window.items.visualItemRect(item).center()
            QTest.mouseClick(window.items.viewport(), Qt.MouseButton.LeftButton, pos=position)
            QTest.mouseDClick(window.items.viewport(), Qt.MouseButton.LeftButton, pos=position)
            deadline = time.monotonic() + 5
            while time.monotonic() < deadline:
                app.processEvents()
                pasted = QImage(str(output.with_suffix(".png")))
                if not pasted.isNull():
                    assert pasted.size() == image.size(), "Pasted thumbnail instead of original"
                    break
                time.sleep(.01)
            else:
                raise AssertionError("Image paste did not reach the external editor")
            print("PASS: image double-click -> real Ctrl+V -> full-size image, not preview")
            render_out = os.environ.get("CYIME_CLIPBOARD_RENDER_OUT")
            if render_out:
                target_dir = Path(render_out)
                target_dir.mkdir(parents=True, exist_ok=True)
                window.status.setText("后台运行 · 设备离线")
                window.history_count.setText("1 条内容")
                window.statusBar().clearMessage()
                for width, height in ((900, 620), (650, 470)):
                    window.resize(width, height)
                    window.show()
                    app.processEvents()
                    window.grab().save(str(target_dir / f"clipboard-{width}.png"))
        finally:
            child.terminate()
            child.wait(timeout=5)
            window.close()
            backend.timer.stop()
            backend.network_timer.stop()
            store.close()
            connection.close()
    return 0


if __name__ == "__main__":
    sys.exit(main())
