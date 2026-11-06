#!/usr/bin/env python3
"""把魔搭上用户上传的手写样本和词库拉到本机，脱敏合并，一次跑完。可以反复运行，只处理新内容。

1. 用环境变量 MODELSCOPE_API_TOKEN 下载两个私有数据集（接收端写的，路径带格式版本 v<版本>/）：
   - Cyletix/cyime-dict-contrib        → D:/CyIME-Data/dict_contrib/cloud/
   - Cyletix/cyime-handwriting-contrib → D:/CyIME-Data/handwriting_training/contrib/cloud/
2. 词库：tools/dict_contribute/aggregate.py 合并云端和 <inbox>、下载目录里分享来的文件，
   脱敏（数字片段、像人名的、用的人不到 3 个的都去掉）→ D:/CyIME-Data/dict_contrib/merged.tsv 和 .report.txt。
3. 手写：解压到 contrib/inbox/ 并改成 cyime_handwriting_contribute_*.jsonl，交给
   tools/handwriting_training/contrib_ingest.py 去重、合并、回放出报告（需要 numpy、onnxruntime；--no-replay 跳过回放）。
只认识的版本才处理（词库 v1、手写 v1）；以后改格式在 KNOWN_VERSIONS 里加。数据只放 D:/CyIME-Data，不进仓库。

用法：python tools/contrib/sync_contrib.py [--no-replay] [--skip-download]
"""
import argparse
import gzip
import os
import pathlib
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
DATA = pathlib.Path("D:/CyIME-Data")
DICT_DIR = DATA / "dict_contrib"
HAND_DIR = DATA / "handwriting_training/contrib"
DATASETS = {"dict": "Cyletix/cyime-dict-contrib", "handwriting": "Cyletix/cyime-handwriting-contrib"}
KNOWN_VERSIONS = {"dict": {"v1"}, "handwriting": {"v1"}}


def download(repo: str, target: pathlib.Path) -> None:
    from modelscope.hub.api import HubApi
    from modelscope.hub.snapshot_download import dataset_snapshot_download
    token = os.environ.get("MODELSCOPE_API_TOKEN")
    if not token:
        raise SystemExit("没有 MODELSCOPE_API_TOKEN")
    HubApi().login(token)
    target.mkdir(parents=True, exist_ok=True)
    dataset_snapshot_download(repo, local_dir=str(target))


def versioned(root: pathlib.Path, kind: str):
    """只返回认识的版本目录；不认识的报出来，不处理。"""
    dirs = []
    for child in sorted(root.glob("v*")) if root.is_dir() else []:
        if not child.is_dir():
            continue
        if child.name in KNOWN_VERSIONS[kind]:
            dirs.append(child)
        else:
            print(f"跳过不认识的{kind}版本：{child}", file=sys.stderr)
    return dirs


def unpack_handwriting(cloud: pathlib.Path, inbox: pathlib.Path) -> int:
    inbox.mkdir(parents=True, exist_ok=True)
    count = 0
    for version in versioned(cloud, "handwriting"):
        for gz in sorted(version.glob("*/*.jsonl.gz")):
            name = f"cyime_handwriting_contribute_{gz.parent.name}_{gz.name[:-len('.jsonl.gz')]}.jsonl"
            out = inbox / name
            if out.exists():
                continue
            out.write_bytes(gzip.decompress(gz.read_bytes()))
            count += 1
    return count


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--skip-download", action="store_true", help="不下载，只处理已经拉下来的")
    parser.add_argument("--no-replay", action="store_true", help="手写只收集合并，不回放")
    args = parser.parse_args(argv)

    dict_cloud = DICT_DIR / "cloud"
    hand_cloud = HAND_DIR / "cloud"
    if not args.skip_download:
        download(DATASETS["dict"], dict_cloud)
        download(DATASETS["handwriting"], hand_cloud)

    sources = [str(d) for d in versioned(dict_cloud, "dict")]
    sources += [str(d) for d in (*([pathlib.Path(os.environ["CYIME_CONTRIB_INBOX"])] if os.environ.get("CYIME_CONTRIB_INBOX") else []), pathlib.Path.home() / "Downloads", DICT_DIR / "inbox")
                if d.is_dir()]
    if sources:
        subprocess.run([sys.executable, str(ROOT / "tools/dict_contribute/aggregate.py"), *sources,
                        "--out", str(DICT_DIR / "merged.tsv")], check=True)
    else:
        print("没有词库文件")

    print(f"手写：新解压 {unpack_handwriting(hand_cloud, HAND_DIR / 'inbox')} 份")
    ingest = [sys.executable, str(ROOT / "tools/handwriting_training/contrib_ingest.py")]
    if args.no_replay:
        ingest.append("--no-replay")
    subprocess.run(ingest, check=True)
    return 0


if __name__ == "__main__":
    sys.exit(main())
