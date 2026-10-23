#!/usr/bin/env python3
"""每版证据记录：从构建回执、安装包、测试结果，以及可选的设备和 GitHub，读出一版的证据，
写进 docs/releases/evidence/<版本>.md 的自动部分。Python 3.10+，只用标准库。

  python scripts/release_evidence.py                      # 当前版本（app/build.gradle.kts），写文件
  python scripts/release_evidence.py --version 1.14.103 --adb --github
  python scripts/release_evidence.py --print              # 只打印，不写文件

只读：不构建、不安装、不改 Release；--adb 只读版本和 base.apk 哈希，--github 只读草稿/资产。
只记公开包：带私人释义、开发工具或老人/朋友包的回执直接拒绝。文件里不写绝对路径和设备序列号。
手写的「核对」部分在自动部分之外，重跑不会覆盖。模板和规则见 docs/releases/evidence/README.md。
"""
from __future__ import annotations

import argparse
from datetime import datetime, timezone, timedelta
import hashlib
import importlib.util
import json
from pathlib import Path
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parent.parent
EVIDENCE_DIR = "docs/releases/evidence"
EDITIONS = (("standard", "轻量版"), ("full", "完整版"))
BEGIN, END = "<!-- 自动生成：开始（scripts/release_evidence.py，重跑会覆盖这一段） -->", "<!-- 自动生成：结束 -->"
# Model catalogs that pin a SHA256 for every downloadable or bundled model file.
CATALOGS = ("app/src/main/java/com/kingzcheung/xime/model/BuiltinModelCatalog.kt",
            "app/src/main/java/com/kingzcheung/xime/model/CandidateModelCatalog.kt",
            "app/src/main/java/com/kingzcheung/xime/speech/SpeechModelCatalog.kt")
DATA_PREFIXES = ("assets/bundled-models/", "assets/cell_dictionaries/", "assets/gloss/", "assets/rime_chinese/t9_ngram.lm")
LARGE = 1024 * 1024
JST = timezone(timedelta(hours=9))


class EvidenceError(Exception):
    pass


def load_packager(root: Path):
    spec = importlib.util.spec_from_file_location("cyime_packager", root / "scripts/packager/cyime_packager.py")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def git(root: Path, *args: str, check: bool = True) -> str:
    result = subprocess.run(["git", *args], cwd=root, capture_output=True, text=True, encoding="utf-8")
    if check and result.returncode:
        raise EvidenceError(f"git {' '.join(args)} 失败：{result.stderr.strip()}")
    return result.stdout.strip() if result.returncode == 0 else ""


def sha256_stream(stream) -> str:
    h = hashlib.sha256()
    for block in iter(lambda: stream.read(1024 * 1024), b""):
        h.update(block)
    return h.hexdigest()


def read_json(path: Path):
    return json.loads(path.read_text(encoding="utf-8-sig"))  # PowerShell 5 writes a BOM


def private_reason(receipt: dict) -> str | None:
    variant = receipt.get("packager") or {}
    reasons = [label for key, label in (("privateGloss", "私人释义包"), ("devTools", "开发工具"), ("audience", "老人/朋友包"))
               if variant.get(key)]
    return "、".join(reasons) or None


def data_entries(apk: Path) -> list[dict]:
    """Model and dictionary files inside the APK, hashed from the archive itself."""
    rows = []
    with zipfile.ZipFile(apk) as archive:
        for info in archive.infolist():
            name = info.filename
            if info.is_dir() or not name.startswith("assets/"):
                continue
            if info.file_size < LARGE and not name.startswith(DATA_PREFIXES):
                continue
            with archive.open(info) as stream:
                rows.append({"name": name, "size": info.file_size, "sha256": sha256_stream(stream)})
    return sorted(rows, key=lambda r: r["name"])


def pinned_hashes(root: Path, commit: str) -> set[str]:
    found = set()
    for path in CATALOGS:
        text = git(root, "show", f"{commit}:{path}", check=False)
        found.update(h.lower() for h in re.findall(r"\b[0-9a-fA-F]{64}\b", text))
    return found


def edition_evidence(root: Path, version: str, edition: str, packager) -> dict | None:
    receipt_path = root / f"app/build/outputs/apk/editions/{version}/{edition}/release/latest.json"
    if not receipt_path.is_file():
        return None
    receipt = read_json(receipt_path)
    if receipt.get("version") != version:
        raise EvidenceError(f"{receipt_path.name} 记录的版本是 {receipt.get('version')}，不是 {version}")
    reason = private_reason(receipt)
    if reason:
        raise EvidenceError(f"{version} {edition} 是私人包（{reason}），证据记录只收公开包")
    file = (receipt.get("files") or [{}])[0]
    apk = Path(file.get("path", ""))
    result = {"edition": edition, "versionCode": receipt.get("versionCode"), "commit": receipt.get("sourceCommit", ""),
              "name": apk.name, "bytes": file.get("bytes"), "sha256": (file.get("sha256") or "").lower(),
              "layoutGate": receipt.get("layoutGate", "not-run"), "variant": receipt.get("packager") or {},
              "apkPresent": apk.is_file(), "entries": [], "problems": []}
    if apk.is_file():
        with apk.open("rb") as stream:
            if sha256_stream(stream) != result["sha256"]:
                result["problems"].append("安装包哈希和构建回执不一致")
        result["entries"] = data_entries(apk)
        with zipfile.ZipFile(apk) as archive:
            names = archive.namelist()
            texts = {n: archive.read(n) for n in names
                     if n.startswith("assets/") and n.rsplit(".", 1)[-1] in ("json", "yaml", "yml", "txt", "tsv", "md", "list")
                     and not n.startswith("assets/gloss/")}
        result["problems"] += packager.public_violations(names, texts)
    return result


def source_evidence(root: Path, commit: str, version: str) -> dict:
    if not commit:
        return {}
    subject = git(root, "log", "-1", "--format=%s", commit, check=False)
    date = git(root, "log", "-1", "--format=%cI", commit, check=False)
    on_main = bool(git(root, "branch", "-r", "--contains", commit, "--list", "origin/main", check=False))
    tag = git(root, "rev-parse", "-q", "--verify", f"refs/tags/{version}^{{commit}}", check=False)
    notes = subprocess.run(["git", "cat-file", "-e", f"{commit}:docs/{version}.md"], cwd=root,
                           capture_output=True).returncode == 0
    return {"commit": commit, "subject": subject, "date": date, "onMain": on_main,
            "tag": tag, "tagMatches": tag == commit if tag else None, "notes": notes}


def test_evidence(root: Path) -> dict | None:
    files = list((root / "app/build/test-results").glob("**/TEST-*.xml"))
    if not files:
        return None
    total = {"tests": 0, "failures": 0, "errors": 0, "skipped": 0}
    for f in files:
        suite = ET.parse(f).getroot()
        for key in total:
            total[key] += int(suite.get(key, 0) or 0)
    newest = max(f.stat().st_mtime for f in files)
    return {**total, "files": len(files), "time": datetime.fromtimestamp(newest, JST).strftime("%Y-%m-%d %H:%M JST")}


def mask(serial: str) -> str:
    return serial[:2] + "…" + serial[-2:] if len(serial) > 4 else "…"


def adb_evidence(root: Path, packager, hashes: dict[str, str]) -> list[dict]:
    adb = packager.sdk_adb(root)
    if not adb:
        raise EvidenceError("找不到 adb（--adb 需要 Android SDK）")
    rows = []
    for device in packager.list_devices(root):
        if device["state"] != "device":
            rows.append({"model": device["model"] or mask(device["serial"]), "state": device["state"]})
            continue
        run = lambda *cmd: subprocess.run([adb, "-s", device["serial"], "shell", *cmd],
                                          capture_output=True, text=True, timeout=60).stdout
        dump = run("dumpsys", "package", "com.cyletix.cyime")
        name, code = re.search(r"versionName=(\S+)", dump), re.search(r"versionCode=(\d+)", dump)
        base = next((x.removeprefix("package:").strip() for x in run("pm", "path", "com.cyletix.cyime").splitlines()
                     if x.strip().endswith("/base.apk")), None)
        digest = re.match(r"([0-9a-fA-F]{64})\s", run("sha256sum", base) if base else "")
        digest = digest[1].lower() if digest else None
        rows.append({"model": device["model"] or mask(device["serial"]), "state": "device",
                     "versionName": name[1] if name else None, "versionCode": int(code[1]) if code else None,
                     "edition": hashes.get(digest) if digest else None, "sha256": digest})
    return rows


def github_evidence(root: Path, version: str) -> dict:
    out = subprocess.run(["gh", "release", "view", version, "--json", "isDraft,isPrerelease,publishedAt,url,assets"],
                         cwd=root, capture_output=True, text=True, encoding="utf-8")
    if out.returncode:
        return {"error": (out.stderr.strip().splitlines() or ["gh 失败"])[0]}
    data = json.loads(out.stdout)
    return {"draft": data.get("isDraft"), "publishedAt": data.get("publishedAt"), "url": data.get("url"),
            "assets": {a["name"]: (a.get("digest") or "").removeprefix("sha256:").lower() for a in data.get("assets", [])}}


def render(version: str, editions: list[dict], source: dict, tests: dict | None, pinned: set[str],
           devices: list[dict] | None, github: dict | None, now: str) -> str:
    label = dict(EDITIONS)
    lines = [BEGIN, "", f"读取时间：{now}", "", "### 源码", ""]
    if source:
        lines += [f"- 构建时 HEAD：`{source['commit'][:10]}`（{source['date']}）{source['subject']}",
                  f"- 在 origin/main 上：{'是' if source['onMain'] else '否'}",
                  f"- 标签 {version}：" + ("没有" if not source["tag"] else ("指向同一提交" if source["tagMatches"]
                                                                   else f"指向 `{source['tag'][:10]}`，和构建提交不同")),
                  f"- 发布说明 docs/{version}.md 在该提交里：{'是' if source['notes'] else '否'}"]
    else:
        lines.append("- 构建回执没有记录提交")
    if len({e["commit"] for e in editions}) > 1:
        lines.append("- 注意：两个包的构建提交不同")
    lines += ["", "### 安装包", "", "| 包 | 文件 | 安装序号 | 字节 | SHA256 | 九键模型 | 离线模型 | 尺寸门禁 | 公开内容检查 |",
              "|---|---|---|---|---|---|---|---|---|"]
    for e in editions:
        t9 = (e["variant"].get("t9Model") or {})
        t9_text = f"{t9.get('name')} `{t9.get('sha256', '')[:12]}…`" if t9 else "不带"
        check = "安装包不在本机，未检查" if not e["apkPresent"] else ("通过" if not e["problems"] else "；".join(e["problems"]))
        lines.append(f"| {label[e['edition']]} | `{e['name']}` | {e['versionCode']} | {e['bytes']:,} | `{e['sha256']}` | "
                     f"{t9_text} | {'带' if e['variant'].get('offlineModels') else '不带'} | {e['layoutGate']} | {check} |")
    for e in editions:
        if not e["entries"]:
            continue
        lines += ["", f"#### {label[e['edition']]}里的模型和词库文件", "",
                  "| 文件 | 字节 | SHA256 | 模型目录登记 |", "|---|---|---|---|"]
        for r in e["entries"]:
            lines.append(f"| `{r['name']}` | {r['size']:,} | `{r['sha256']}` | {'已登记' if r['sha256'] in pinned else '—'} |")
    lines += ["", "### 单元测试结果文件", ""]
    lines.append("- 本机没有 app/build/test-results" if not tests else
                 f"- {tests['tests']} 项，失败 {tests['failures']}，出错 {tests['errors']}，跳过 {tests['skipped']}"
                 f"（{tests['files']} 个结果文件，最新 {tests['time']}；按文件时间，不保证就是构建提交那次）")
    if devices is not None:
        lines += ["", "### 设备上实际装的版本（adb 只读）", "", "| 设备 | 状态 | 版本 | 安装序号 | 和哪个包相同 |", "|---|---|---|---|---|"]
        for d in devices or [{"model": "没有连接设备", "state": "—"}]:
            same = label.get(d.get("edition"), "都不同（私人包或别的版本）" if d.get("sha256") else "—")
            lines.append(f"| {d['model']} | {d['state']} | {d.get('versionName') or '—'} | {d.get('versionCode') or '—'} | {same} |")
    if github is not None:
        lines += ["", "### GitHub Release（只读）", ""]
        if github.get("error"):
            lines.append(f"- 读不到：{github['error']}")
        else:
            lines.append(f"- 状态：{'草稿' if github['draft'] else '已公开 ' + (github.get('publishedAt') or '')}")
            for e in editions:
                remote = github["assets"].get(e["name"])
                lines.append(f"- {label[e['edition']]}：" + ("没上传" if remote is None else
                             ("哈希一致" if remote == e["sha256"] else ("GitHub 没给哈希" if not remote else "哈希不一致"))))
    lines += ["", END]
    return "\n".join(lines)


TEMPLATE = """# CyIME {version} 证据记录

本页记录 {version} 是从哪份源码、带了哪些模型和词库、做过哪些检查、装到了哪些设备。规则见 [README](README.md)。

{auto}

## 核对（手写）

没做的写「未做」，不写通过。证据位置写提交、文档或测试报告路径（不写 D:/CyIME-Data 里的私人内容）。

| 项目 | 结果 | 证据位置 |
|---|---|---|
| 全部单元测试 | 未填 | |
| 公开包逐文件审计 | 未填 | |
| 真机检查 | 未填 | |
| 九键／选词回放 | 未填 | |
| 从 Release 下载回来核对哈希 | 未填 | |

## 设备

- 公开包不装到我自己的手机和平板；真机检查用的同源开发包写在这里（版本、安装序号、机型）。

## 未验收

- 未填
"""


def merge(existing: str | None, version: str, auto: str) -> str:
    if existing is None:
        return TEMPLATE.format(version=version, auto=auto)
    if BEGIN not in existing or END not in existing:
        raise EvidenceError("记录文件里找不到自动生成段的开始/结束标记，不改动")
    head, rest = existing.split(BEGIN, 1)
    return head + auto + rest.split(END, 1)[1]


def main(argv=None) -> int:
    p = argparse.ArgumentParser(description="生成一版的证据记录（只读）")
    p.add_argument("--version", help="默认读 app/build.gradle.kts")
    p.add_argument("--adb", action="store_true", help="读已连接设备上装的版本和 base.apk 哈希")
    p.add_argument("--github", action="store_true", help="用 gh 读 Release 是否草稿、资产哈希")
    p.add_argument("--print", action="store_true", help="只打印自动部分，不写文件")
    a = p.parse_args(argv)
    try:
        packager = load_packager(ROOT)
        version = a.version or packager.read_version(ROOT)[0]
        editions = [e for e in (edition_evidence(ROOT, version, ed, packager) for ed, _ in EDITIONS) if e]
        if not editions:
            raise EvidenceError(f"本机没有 {version} 的构建回执（app/build/outputs/apk/editions/{version}/*/release/latest.json）")
        commit = editions[0]["commit"]
        hashes = {e["sha256"]: e["edition"] for e in editions}
        auto = render(version, editions, source_evidence(ROOT, commit, version), test_evidence(ROOT),
                      pinned_hashes(ROOT, commit) if commit else set(),
                      adb_evidence(ROOT, packager, hashes) if a.adb else None,
                      github_evidence(ROOT, version) if a.github else None,
                      datetime.now(JST).strftime("%Y-%m-%d %H:%M JST"))
    except (EvidenceError, OSError, ValueError, zipfile.BadZipFile, ET.ParseError) as exc:
        print(f"[失败] {exc}", file=sys.stderr)
        return 2
    if a.print:
        print(auto)
        return 0
    target = ROOT / EVIDENCE_DIR / f"{version}.md"
    try:
        text = merge(target.read_text(encoding="utf-8") if target.is_file() else None, version, auto)
    except EvidenceError as exc:
        print(f"[失败] {exc}", file=sys.stderr)
        return 2
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(text, encoding="utf-8", newline="\n")
    print(f"[完成] {target.relative_to(ROOT).as_posix()}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
