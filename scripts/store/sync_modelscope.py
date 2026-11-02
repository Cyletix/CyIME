"""把扩展商店和模型文件同步到魔搭仓库 Cyletix/cyime-model，给国内用户一条能下的线路。

在电脑上运行（Python 3.10+）。只需要一次准备：
  py -3 -m pip install -U modelscope
  在 https://modelscope.cn/my/myaccesstoken 新建访问令牌，存成用户环境变量 MODELSCOPE_API_TOKEN

用法（在仓库根目录）：
  py -3 -B scripts\\store\\sync_modelscope.py store            # 商店目录 store/ → 魔搭 store/
  py -3 -B scripts\\store\\sync_modelscope.py mirror           # 商店里引用的 GitHub 文件 → 魔搭 store/mirror/<主机>/<路径>
  py -3 -B scripts\\store\\sync_modelscope.py models <文件>...  # 模型文件（Release models 里的同名文件）→ 魔搭根目录
      上传名和本地文件名不同时写成 本地路径=上传名，例如 ch_PP-OCRv5_det_mobile.onnx=ocr-ppocr-v5-det-mobile.onnx
  py -3 -B scripts\\store\\sync_modelscope.py check            # 逐个下载魔搭上的副本，核对 sha256
  加 --dry-run 只列出要传的文件，不上传。

规则和 App 一样（StoreMirrors.kt）：只有写了 sha256 的文件才转存，App 下载后照样校验。
"""
from __future__ import annotations

import argparse
import hashlib
import os
import re
import shutil
import sys
import tempfile
import urllib.request
from pathlib import Path

REPO_ID = "Cyletix/cyime-model"
RESOLVE = f"https://www.modelscope.cn/models/{REPO_ID}/resolve/master/"
ROOT = Path(__file__).resolve().parents[2]
STORE = ROOT / "store"
CURATED = ROOT / "app/src/main/java/com/kingzcheung/xime/settings/CuratedRimeSchemes.kt"
CATALOG = ROOT / "app/src/main/java/com/kingzcheung/xime/model/BuiltinModelCatalog.kt"
MIRROR_HOSTS = {"raw.githubusercontent.com", "github.com"}
SHA = re.compile(r"[0-9a-fA-F]{64}")


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def mirror_path(url: str) -> str | None:
    """与 StoreMirrors.mirrorPath 相同：主机名 + 原路径。"""
    if not url.startswith("https://"):
        return None
    rest = url[len("https://"):]
    host, _, path = rest.partition("/")
    path = path.split("?")[0].split("#")[0]
    if host not in MIRROR_HOSTS or not path or ".." in path:
        return None
    return f"{host}/{path}"


def external_files() -> list[tuple[str, str]]:
    """商店目录和内置方案目录里写了 sha256 的 GitHub 文件：(地址, sha256)。"""
    found: dict[str, str] = {}
    for index in STORE.glob("*/index.yaml"):
        text = index.read_text(encoding="utf-8")
        for m in re.finditer(r'url:\s*"?([^"\s]+)"?\s*\n\s*sha256:\s*"?([0-9a-fA-F]{64})', text):
            found[m.group(1)] = m.group(2).lower()
    text = CURATED.read_text(encoding="utf-8")
    for m in re.finditer(r'url\s*=\s*"([^"]+)",\s*\n\s*sha256\s*=\s*"([0-9a-fA-F]{64})"', text):
        found[m.group(1)] = m.group(2).lower()
    own = ("https://raw.githubusercontent.com/Cyletix/CyIME/main/store/",)
    return [(u, s) for u, s in sorted(found.items()) if mirror_path(u) and not u.startswith(own)]


def catalog_hash(name: str) -> str | None:
    """模型目录里登记过的 Release 文件名 → sha256（防止传错文件）。
    按每个 ModelFile( 到下一个 ModelFile( 之间找：参数可以分好几行写，Release 上的名字也可以只出现在备用地址里。"""
    text = CATALOG.read_text(encoding="utf-8")
    starts = [m.start() for m in re.finditer(r"ModelFile\(", text)]
    for i, start in enumerate(starts):
        chunk = text[start:starts[i + 1] if i + 1 < len(starts) else len(text)]
        if f'{name}"' in chunk or f"/{name}" in chunk:
            m = SHA.search(chunk)
            if m:
                return m.group(0).lower()
    return None


def download(url: str, target: Path) -> None:
    target.parent.mkdir(parents=True, exist_ok=True)
    req = urllib.request.Request(url, headers={"User-Agent": "cyime-store-sync"})
    with urllib.request.urlopen(req, timeout=600) as src, open(target, "wb") as dst:
        shutil.copyfileobj(src, dst)


def upload(staging: Path, message: str, dry_run: bool) -> None:
    files = [p for p in staging.rglob("*") if p.is_file()]
    for p in files:
        print("  ", p.relative_to(staging).as_posix(), f"{p.stat().st_size:,} B")
    if dry_run:
        print(f"--dry-run：{len(files)} 个文件未上传")
        return
    token = os.environ.get("MODELSCOPE_API_TOKEN", "").strip()
    if not token:
        sys.exit("缺少环境变量 MODELSCOPE_API_TOKEN（魔搭 → 账号设置 → 访问令牌）")
    from modelscope.hub.api import HubApi
    api = HubApi()
    api.login(token)
    api.upload_folder(repo_id=REPO_ID, folder_path=str(staging), path_in_repo="",
                      commit_message=message, repo_type="model")
    print(f"已上传 {len(files)} 个文件到 {REPO_ID}")


def cmd_store(args) -> None:
    with tempfile.TemporaryDirectory() as tmp:
        staging = Path(tmp)
        shutil.copytree(STORE, staging / "store")
        upload(staging, "sync store/ from CyIME main", args.dry_run)


def cmd_mirror(args) -> None:
    with tempfile.TemporaryDirectory() as tmp:
        staging = Path(tmp)
        for url, digest in external_files():
            target = staging / "store/mirror" / mirror_path(url)
            download(url, target)
            actual = sha256(target)
            if actual != digest:
                sys.exit(f"sha256 不一致，停止：{url}\n  目录 {digest}\n  实际 {actual}")
            print("ok", url)
        upload(staging, "mirror store files from GitHub", args.dry_run)


def cmd_models(args) -> None:
    with tempfile.TemporaryDirectory() as tmp:
        staging = Path(tmp)
        for arg in args.files:
            local, _, remote = arg.partition("=")
            src = Path(local)
            name = remote or src.name
            expected = catalog_hash(name)
            if expected is None:
                sys.exit(f"模型目录里没有登记 {name}，先改 BuiltinModelCatalog.kt")
            if sha256(src) != expected:
                sys.exit(f"sha256 和模型目录不一致：{src}")
            shutil.copy2(src, staging / name)
            print("ok", name)
        upload(staging, "add model files", args.dry_run)


def cmd_check(args) -> None:
    bad = 0
    pairs = [(RESOLVE + "store/mirror/" + mirror_path(u), s) for u, s in external_files()]
    for index in STORE.glob("*/index.yaml"):
        rel = index.relative_to(ROOT).as_posix()
        pairs.append((RESOLVE + rel, sha256(index)))
    with tempfile.TemporaryDirectory() as tmp:
        for url, digest in pairs:
            target = Path(tmp) / "f"
            try:
                download(url, target)
                ok = sha256(target) == digest
            except Exception as e:  # noqa: BLE001
                print("下载失败", url, e)
                ok = False
            print("ok " if ok else "BAD", url)
            bad += 0 if ok else 1
    sys.exit(1 if bad else 0)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--dry-run", action="store_true")
    sub = parser.add_subparsers(dest="cmd", required=True)
    sub.add_parser("store").set_defaults(fn=cmd_store)
    sub.add_parser("mirror").set_defaults(fn=cmd_mirror)
    m = sub.add_parser("models")
    m.add_argument("files", nargs="+")
    m.set_defaults(fn=cmd_models)
    sub.add_parser("check").set_defaults(fn=cmd_check)
    args = parser.parse_args()
    args.fn(args)


if __name__ == "__main__":
    main()
