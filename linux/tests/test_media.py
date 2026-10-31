import base64
import json
import sqlite3
import tempfile
from pathlib import Path
import unittest
from unittest.mock import patch

from PyQt6.QtGui import QImage
from PyQt6.QtWidgets import QApplication

from cyime_linux.clipboard_media import CATEGORIES, encode, image_record, validate_image, matches
from cyime_linux.core import Store
from cyime_linux.daemon import Backend


def fixture():
    image = QImage(640, 400, QImage.Format.Format_RGB32)
    image.fill(0xff239f91)
    return image_record(encode(image))


class MediaTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.app = QApplication.instance() or QApplication([])

    def test_phone_equivalent_categories_and_combined_source_search(self):
        self.assertEqual([label for label, _ in CATEGORIES], ["全部", "文本", "链接", "地址", "电话", "邮箱", "图片"])
        for text, category in (("邮件 user@example.org", "email"), ("www.example.org/x", "link"),
                               ("+86 138 0013 8000", "phone"), ("上海市测试路1号", "address")):
            self.assertTrue(matches(text, "text", category))
            self.assertTrue(matches(text, "text", "text"))
        self.assertFalse(matches("12345678", "text", "phone"))
        self.assertFalse(matches("image@example.org", "image", "email"))
        with tempfile.TemporaryDirectory() as temporary:
            store = Store(Path(temporary))
            try:
                store.record("a@example.org", "phone")
                store.record("b@example.org", "linux")
                for i in range(110): store.record(f"普通 {i}", "phone")
                self.assertEqual(len(store.history("phone", "example", "email")), 1)
                self.assertEqual(store.history("phone", category="email")[0]["text"], "a@example.org")
            finally: store.close()

    def test_legacy_database_migration_preserves_text_identity_and_pins(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            with sqlite3.connect(directory / "history.sqlite3") as db:
                db.execute("""CREATE TABLE history(id INTEGER PRIMARY KEY, hash TEXT NOT NULL,
                    text TEXT NOT NULL, source TEXT NOT NULL, created REAL NOT NULL,
                    pinned INTEGER NOT NULL DEFAULT 0, UNIQUE(hash,source))""")
                db.execute("INSERT INTO history VALUES(42, 'legacy', '原有文字', 'phone', 123, 1)")
            store = Store(directory)
            try:
                row = store.history()[0]
                self.assertEqual((row["id"], row["text"], row["source"], row["created"], row["pinned"], row["kind"]),
                                 (42, "原有文字", "phone", 123, 1, "text"))
            finally: store.close()

    def test_preview_has_bounded_dimensions_and_corruption_is_rejected(self):
        value, data = fixture()
        preview = QImage.fromData(base64.b64decode(value["preview"]))
        self.assertLessEqual(max(preview.width(), preview.height()), 256)
        self.assertEqual((value["width"], value["height"], value["size"]), (640, 400, len(data)))
        for changes in (dict(hash="../bad"), dict(preview="invalid"), dict(size=33 * 1024 * 1024),
                        dict(width=99999999), dict(preview_hash="0" * 64)):
            with self.assertRaises((ValueError, OverflowError)):
                validate_image(dict(value, **changes))

    def test_preview_only_persistence_and_backup_restore_include_cached_original(self):
        value, data = fixture()
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            store = Store(directory / "source")
            restored = Store(directory / "restored")
            try:
                store.record("preserved text", "linux")
                store.record_image(value, "phone", owner="device")
                row = store.history(category="image")[0]
                store.pin(row["id"], True)
                self.assertFalse(row["cached"])
                self.assertIsNone(store.image(value["hash"])["original"])
                with self.assertRaises(ValueError): store.record_image(dict(value, width=641), "phone", owner="device")
                with self.assertRaises(PermissionError): store.accept_original(value["hash"], data, "wrong")
                store.accept_original(value["hash"], data, "device")
                backup = directory / "backup.sqlite3"
                store.backup(backup)
                restored.restore(backup)
                result = restored.history(category="image")[0]
                self.assertTrue(result["cached"])
                self.assertEqual(result["pinned"], 1)
                self.assertEqual(restored.image(value["hash"])["original"], data)
                self.assertEqual(restored.history(category="text")[0]["text"], "preserved text")
            finally:
                store.close(); restored.close()

    def test_system_image_capture_and_copy_keep_image_mime(self):
        with tempfile.TemporaryDirectory() as temporary:
            store = Store(Path(temporary))
            with patch.object(Backend, "refresh_network"):
                backend = Backend(store, self.app.clipboard(), self.app, 0)
            try:
                image = QImage(80, 60, QImage.Format.Format_RGB32)
                image.fill(0xffee7822)
                self.app.clipboard().setImage(image)
                self.app.processEvents()
                row = store.history(category="image")[0]
                self.app.clipboard().setText("unrelated text")
                backend.control(dict(action="copy", id=row["id"]))
                self.assertTrue(self.app.clipboard().mimeData().hasImage())
                self.assertEqual(self.app.clipboard().image().size(), image.size())
                newer = QImage(90, 70, QImage.Format.Format_RGB32)
                newer.fill(0xff123456)
                self.app.clipboard().setImage(newer)
                self.app.processEvents()
                self.assertEqual(store.history(category="image")[0]["image"]["width"], 90)
            finally:
                self.app.clipboard().dataChanged.disconnect(backend.capture)
                backend.timer.stop(); backend.network_timer.stop(); store.close()
