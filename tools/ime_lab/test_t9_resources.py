"""Small synthetic resource trees verify immutable host replay input snapshots."""

import hashlib
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile

import t9_resources as resources


REQUIRED = (
    "app/src/main/assets/default.custom.yaml",
    "app/src/main/assets/rime/default.yaml",
    "app/build/generated/chinese-assets/rime_ice/rime_ice.dict.yaml",
    "app/build/generated/chinese-assets/rime_ice/cn_dicts/base.dict.yaml",
    "app/build/generated/chinese-assets/rime_ice/cyime_t9_english.dict.yaml",
    "app/src/main/assets/rime_chinese/t9_pinyin.schema.yaml",
    "app/src/main/assets/rime_chinese/t9_sentence.onnx",
    "app/src/main/assets/rime_chinese/t9_sentence.vocab",
    "app/build/generated/t9-grammar/rime_chinese/wanxiang-lts-zh-hans.gram",
)


def write(root, relative, value):
    path = root / relative
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(value)
    return path


def small_repo(root):
    for index, relative in enumerate(REQUIRED):
        write(root, relative, f"required resource {index}\n".encode())
    return root


class ResourceSnapshotTest(unittest.TestCase):
    def test_resource_precedence_matches_manifest_destination_not_source_filename(self):
        with tempfile.TemporaryDirectory() as directory:
            root = small_repo(Path(directory) / "repo")
            write(root, "app/src/main/assets/rime/shared.dict.yaml", b"base")
            write(root, "app/build/generated/chinese-assets/rime_ice/shared.dict.yaml", b"ice")
            write(root, "app/src/main/assets/rime_chinese/shared.dict.yaml", b"chinese")
            final = write(root, "app/build/generated/t9-grammar/rime_chinese/shared.dict.yaml", b"grammar")
            files = resources.resource_files(root)
            self.assertNotIn("assets/rime_ice/shared.dict.yaml", files)
            self.assertEqual(files["assets/rime_chinese/shared.dict.yaml"], (final, "shared.dict.yaml"))
            output = root.parent / "resources.zip"
            resources.snapshot_resources(root, output)
            with zipfile.ZipFile(output) as archive:
                self.assertEqual(archive.read("assets/rime/shared.dict.yaml"), b"base")
                self.assertEqual(archive.read("assets/rime_chinese/shared.dict.yaml"), b"grammar")
                rows = [line.split("\t") for line in archive.read("assets/rime-bundled-manifest.tsv").decode().splitlines()]
                selected = [row for row in rows if row[3] == "shared.dict.yaml"]
                self.assertEqual(len(selected), 1)
                self.assertEqual(selected[0][2], "rime_chinese/shared.dict.yaml")

    def test_exclusions_keep_optional_layouts_customizations_and_docs_out(self):
        with tempfile.TemporaryDirectory() as directory:
            root = small_repo(Path(directory) / "repo")
            for relative in ("app/src/main/assets/rime/.hidden", "app/src/main/assets/rime/.private/settings.yaml",
                             "app/src/main/assets/rime_chinese/docs/README.md",
                             "app/src/main/assets/rime_chinese/LICENSE.txt",
                             "app/src/main/assets/rime_chinese/default.custom.yaml",
                             "app/build/generated/chinese-assets/rime_ice/pinyin_qwjrtk.schema.yaml",
                             "app/build/generated/chinese-assets/rime_ice/pinyin_cyletix10.schema.yaml"):
                write(root, relative, b"excluded")
            files = resources.resource_files(root)
            self.assertEqual(len(files), len(REQUIRED))
            self.assertIn("assets/default.custom.yaml", files)

    def test_manifest_and_sidecar_hash_exact_archived_bytes_for_every_resource(self):
        with tempfile.TemporaryDirectory() as directory:
            root = small_repo(Path(directory) / "repo")
            output = root.parent / "resources.zip"
            result = resources.snapshot_resources(root, output)
            self.assertEqual(result["sha256"], hashlib.sha256(output.read_bytes()).hexdigest())
            self.assertEqual(json.loads(output.with_suffix(".json").read_text(encoding="utf-8")), result)
            with zipfile.ZipFile(output) as archive:
                for item in result["files"]:
                    content = archive.read(item["entry"])
                    self.assertEqual(item["bytes"], len(content))
                    self.assertEqual(item["sha256"], hashlib.sha256(content).hexdigest())
                    self.assertEqual((root / item["source"]).read_bytes(), content)
                lines = archive.read("assets/rime-bundled-manifest.tsv").decode().splitlines()
                destinations = []
                for line in lines:
                    digest, length, source, destination = line.split("\t")
                    content = archive.read("assets/" + source)
                    self.assertEqual(digest, hashlib.sha256(content).hexdigest())
                    self.assertEqual(int(length), len(content))
                    destinations.append(destination)
                self.assertEqual(destinations, sorted(destinations))
                self.assertEqual(len(destinations), len(set(destinations)))
                self.assertEqual(len(destinations), len(REQUIRED) - 2)

    def test_every_required_file_is_checked_before_writing_output(self):
        with tempfile.TemporaryDirectory() as directory:
            root = small_repo(Path(directory) / "repo")
            output = root.parent / "resources.zip"
            for relative in REQUIRED:
                path = root / relative
                content = path.read_bytes()
                path.unlink()
                with self.subTest(missing=relative), self.assertRaisesRegex(ValueError, "resources missing"):
                    resources.snapshot_resources(root, output)
                self.assertFalse(output.exists())
                self.assertFalse(output.with_suffix(".json").exists())
                path.write_bytes(content)

    def test_existing_zip_or_sidecar_is_never_overwritten(self):
        with tempfile.TemporaryDirectory() as directory:
            root = small_repo(Path(directory) / "repo")
            for filename in ("zip-present.zip", "sidecar-present.zip"):
                output = root.parent / filename
                sentinel = output if filename.startswith("zip") else output.with_suffix(".json")
                sentinel.write_bytes(b"previous evidence")
                with self.subTest(filename=filename), self.assertRaisesRegex(ValueError, "already exists"):
                    resources.snapshot_resources(root, output)
                self.assertEqual(sentinel.read_bytes(), b"previous evidence")
                if sentinel != output:
                    self.assertFalse(output.exists())
            collision = root.parent / "resources.json"
            with self.assertRaisesRegex(ValueError, "different paths"):
                resources.snapshot_resources(root, collision)
            self.assertFalse(collision.exists())

    def test_zip_bytes_are_deterministic_despite_source_timestamps(self):
        with tempfile.TemporaryDirectory() as directory:
            root = small_repo(Path(directory) / "repo")
            first, second = root.parent / "first.zip", root.parent / "second.zip"
            before = resources.snapshot_resources(root, first)
            for path, _ in resources.resource_files(root).values():
                os.utime(path, (1_000_000_000, 1_000_000_000))
            after = resources.snapshot_resources(root, second)
            self.assertEqual(first.read_bytes(), second.read_bytes())
            self.assertEqual(before["sha256"], after["sha256"])
            self.assertEqual(before["files"], after["files"])

    def test_source_changed_during_copy_or_after_its_copy_prevents_success_sidecar(self):
        original_open = zipfile.ZipFile.open
        with tempfile.TemporaryDirectory() as directory:
            root = small_repo(Path(directory) / "repo")
            for later_change in (False, True):
                output = root.parent / ("changed-later.zip" if later_change else "changed-now.zip")
                changed = root / REQUIRED[0]
                first_entry = "assets/default.custom.yaml"
                trigger = "assets/rime_chinese/t9_sentence.vocab" if later_change else first_entry
                original_bytes = changed.read_bytes()

                class TamperedStream:
                    def __init__(self, stream):
                        self.stream = stream

                    def __enter__(self):
                        self.stream.__enter__()
                        return self

                    def __exit__(self, *args):
                        return self.stream.__exit__(*args)

                    def write(self, data):
                        count = self.stream.write(data)
                        changed.write_bytes(original_bytes + b"concurrent edit")
                        return count

                def tampering_open(archive, name, mode="r", *args, **kwargs):
                    stream = original_open(archive, name, mode, *args, **kwargs)
                    entry = name.filename if isinstance(name, zipfile.ZipInfo) else name
                    return TamperedStream(stream) if mode == "w" and entry == trigger else stream

                with self.subTest(later_change=later_change), patch.object(zipfile.ZipFile, "open", tampering_open), \
                        self.assertRaisesRegex(ValueError, "changed during snapshot"):
                    resources.snapshot_resources(root, output)
                self.assertFalse(output.with_suffix(".json").exists())
                changed.write_bytes(original_bytes)


if __name__ == "__main__":
    unittest.main()
