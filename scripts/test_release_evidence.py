"""Tests for scripts/release_evidence.py on a throwaway git repo and fake APKs.
Run: python scripts/test_release_evidence.py
No build, device or network access.
"""
import hashlib
import importlib.util
import json
from pathlib import Path
import subprocess
import tempfile
import unittest
import zipfile

spec = importlib.util.spec_from_file_location('release_evidence', Path(__file__).with_name('release_evidence.py'))
m = importlib.util.module_from_spec(spec); spec.loader.exec_module(m)
packager = m.load_packager(m.ROOT)

MODEL = b'model-bytes'
MODEL_SHA = hashlib.sha256(MODEL).hexdigest()


def sh(root, *args):
    subprocess.run(['git', *args], cwd=root, check=True, capture_output=True)


class Fixture:
    def __init__(self, tmp, extra_assets=None, variant=None):
        self.root = Path(tmp)
        sh(self.root, 'init', '-q')
        sh(self.root, 'config', 'user.email', 't@example.com'); sh(self.root, 'config', 'user.name', 't')
        catalog = self.root / m.CATALOGS[0]
        catalog.parent.mkdir(parents=True)
        catalog.write_text(f'ModelFile("model.onnx", "u", "{MODEL_SHA}", 11L)', encoding='utf-8')
        (self.root / 'docs').mkdir()
        (self.root / 'docs/9.9.9.md').write_text('# notes', encoding='utf-8')
        sh(self.root, 'add', '-A'); sh(self.root, 'commit', '-qm', 'release 9.9.9')
        self.commit = subprocess.run(['git', 'rev-parse', 'HEAD'], cwd=self.root, capture_output=True, text=True).stdout.strip()
        out = self.root / 'app/build/outputs/apk/editions/9.9.9/full/release'
        out.mkdir(parents=True)
        apk = out / 'CyIME-9.9.9-full-arm64-v8a.apk'
        with zipfile.ZipFile(apk, 'w') as z:
            z.writestr('assets/bundled-models/m/model.onnx', MODEL)
            z.writestr('assets/small.txt', b'not data')
            z.writestr('classes.dex', b'x' * (2 * 1024 * 1024))
            for name, data in (extra_assets or {}).items():
                z.writestr(name, data)
        self.sha = hashlib.sha256(apk.read_bytes()).hexdigest()
        receipt = {'version': '9.9.9', 'versionCode': 99, 'sourceCommit': self.commit, 'layoutGate': 'not-run',
                   'files': [{'path': str(apk), 'bytes': apk.stat().st_size, 'sha256': self.sha.upper()}],
                   'packager': variant if variant is not None else {'offlineModels': True, 't9Model': None}}
        (out / 'latest.json').write_text('﻿' + json.dumps(receipt), encoding='utf-8')


class EvidenceTest(unittest.TestCase):
    def test_public_full_edition_lists_data_files_and_pins(self):
        with tempfile.TemporaryDirectory() as tmp:
            f = Fixture(tmp)
            e = m.edition_evidence(f.root, '9.9.9', 'full', packager)
            self.assertEqual([], e['problems'])
            self.assertEqual(['assets/bundled-models/m/model.onnx'], [r['name'] for r in e['entries']])
            self.assertIsNone(m.edition_evidence(f.root, '9.9.9', 'standard', packager))
            source = m.source_evidence(f.root, f.commit, '9.9.9')
            self.assertTrue(source['notes'])
            self.assertFalse(source['tag'])
            text = m.render('9.9.9', [e], source, None, m.pinned_hashes(f.root, f.commit), None, None, 'now')
            self.assertIn(f'`{MODEL_SHA}` | 已登记', text)
            self.assertIn('| 完整版 |', text)
            self.assertNotIn(tmp, text)  # no absolute paths in a committed record

    def test_private_receipts_are_refused(self):
        with tempfile.TemporaryDirectory() as tmp:
            f = Fixture(tmp, variant={'privateGloss': 'abc', 'devTools': True})
            with self.assertRaisesRegex(m.EvidenceError, '私人包'):
                m.edition_evidence(f.root, '9.9.9', 'full', packager)

    def test_private_gloss_inside_apk_fails_the_public_check(self):
        with tempfile.TemporaryDirectory() as tmp:
            f = Fixture(tmp, extra_assets={'assets/gloss/cyime_gloss-private.tsv': b'x'})
            self.assertTrue(m.edition_evidence(f.root, '9.9.9', 'full', packager)['problems'])

    def test_merge_keeps_hand_written_sections(self):
        first = m.merge(None, '9.9.9', m.BEGIN + '\nold\n' + m.END)
        edited = first.replace('| 全部单元测试 | 未填 | |', '| 全部单元测试 | 2044 / 0 | 线程记录 |')
        again = m.merge(edited, '9.9.9', m.BEGIN + '\nnew\n' + m.END)
        self.assertIn('2044 / 0', again)
        self.assertIn('new', again)
        self.assertNotIn('old', again)
        with self.assertRaises(m.EvidenceError):
            m.merge('no markers', '9.9.9', 'x')


if __name__ == '__main__':
    unittest.main()
