import contextlib
import io
from pathlib import Path
import tempfile
import os
import unittest
from unittest.mock import patch
from types import SimpleNamespace
import storage_guard as guard


class StorageGuardTest(unittest.TestCase):
    def test_windows_mount_conversion(self):
        self.assertEqual(guard.windows_path(Path("/mnt/d/GitHub/CyIME")), "D:/GitHub/CyIME")
        with self.assertRaises(ValueError):
            guard.windows_path(Path("/home/user"))

    def test_hardlinks_count_once(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "a").write_bytes(b"x" * 1024)
            os.link(root / "a", root / "b")
            self.assertEqual(guard.allocated_inputs([root]), 1024)

    def test_low_space_stops_before_tree_scan(self):
        with patch.object(guard.shutil, "disk_usage", return_value=SimpleNamespace(free=30*guard.GIB)), patch.object(guard, "allocated_inputs") as scan:
            with self.assertRaisesRegex(ValueError, "must retain"):
                guard.check_storage(Path(__file__).resolve().parents[1])
            scan.assert_not_called()

    def test_budget_reserves_next_task(self):
        with patch.object(guard.shutil, "disk_usage", return_value=SimpleNamespace(free=1000*guard.GIB)), patch.object(guard, "allocated_inputs", return_value=156*guard.GIB):
            with self.assertRaisesRegex(ValueError, "limit is 160"):
                guard.check_storage(Path(__file__).resolve().parents[1])

    def test_within_budget(self):
        with patch.object(guard.shutil, "disk_usage", return_value=SimpleNamespace(free=1000*guard.GIB)), patch.object(guard, "allocated_inputs", return_value=100*guard.GIB), contextlib.redirect_stdout(io.StringIO()):
            self.assertEqual(guard.check_storage(Path(__file__).resolve().parents[1]), 100*guard.GIB)


if __name__ == "__main__":
    unittest.main()
