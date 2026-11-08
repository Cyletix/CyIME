#!/usr/bin/env python3
"""下载并合并魔搭上的手写、滑行练习和词库贡献。

1. 用环境变量 MODELSCOPE_API_TOKEN 下载两个私有数据集（接收端写的，路径带格式版本 v<版本>/）：
   - Cyletix/cyime-dict-contrib        → D:/CyIME-Data/dict_contrib/cloud/
   - Cyletix/cyime-handwriting-contrib → D:/CyIME-Data/handwriting_training/contrib/cloud/
2. 词库：tools/dict_contribute/aggregate.py 合并云端和 <inbox>、下载目录里分享来的文件，
   脱敏（数字片段、像人名的、用的人不到 3 个的都去掉）→ D:/CyIME-Data/dict_contrib/merged.tsv 和 .report.txt。
3. 手写：解压到 contrib/inbox/ 并改成 cyime_handwriting_contribute_*.jsonl，交给
   tools/handwriting_training/contrib_ingest.py 去重、合并、回放出报告（需要 numpy、onnxruntime；--no-replay 跳过回放）。
4. 滑行：合并 glide/v1 内的练习，按贡献者与样本编号去重 → D:/CyIME-Data/glide_contrib/samples.jsonl。
兼容原接收端的无版本手写与词库目录。数据只放 D:/CyIME-Data。

用法：python tools/contrib/sync_contrib.py [--no-replay] [--skip-download]
"""
import argparse
import gzip
import json
import re
import os
import pathlib
import subprocess
import sys
import tempfile

ROOT = pathlib.Path(__file__).resolve().parents[2]
DATA = pathlib.Path("D:/CyIME-Data")
DICT_DIR = DATA / "dict_contrib"
HAND_DIR = DATA / "handwriting_training/contrib"
DATASETS = {"dict": "Cyletix/cyime-dict-contrib", "handwriting": "Cyletix/cyime-handwriting-contrib"}
KNOWN_VERSIONS = {"dict": {"v1"}, "handwriting": {"v1"}}


def download(repo: str, target: pathlib.Path) -> None:
    token = os.environ.get("MODELSCOPE_API_TOKEN")
    if not token:
        raise SystemExit("没有 MODELSCOPE_API_TOKEN")
    target.mkdir(parents=True, exist_ok=True)
    try:
        from modelscope_hub import HubApi
    except ImportError:
        from modelscope.hub.api import HubApi
        from modelscope.hub.snapshot_download import dataset_snapshot_download
        HubApi().login(token)
        dataset_snapshot_download(repo, local_dir=str(target))
    else:
        HubApi(token=token).download_repo(repo, "dataset", local_dir=target, allow_patterns=["*.gz", "**/*.gz"])


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
    for version in [cloud, *versioned(cloud, "handwriting")]:
        for gz in sorted(version.glob("*/*.jsonl.gz")):
            if not re.fullmatch(r"[0-9a-f]{8,32}", gz.parent.name):
                continue
            name = f"cyime_handwriting_contribute_{gz.parent.name}_{gz.name[:-len('.jsonl.gz')]}.jsonl"
            out = inbox / name
            if out.exists():
                continue
            out.write_bytes(gzip.decompress(gz.read_bytes()))
            count += 1
    return count


def merge_glide(cloud: pathlib.Path, output: pathlib.Path) -> int:
    """Merge confirmed practice batches. A repeated batch or sample is counted once per writer."""
    seen = set()
    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(dir=output.parent) as staging:
        temporary = pathlib.Path(staging, output.name)
        with temporary.open("w", encoding="utf-8", newline="\n") as dest:
            for source in sorted((cloud / "glide/v1").glob("*/*.jsonl.gz")):
                writer = source.parent.name
                if not re.fullmatch(r"[0-9a-f]{16}", writer) or writer == "0" * 16:
                    continue
                with gzip.open(source, "rt", encoding="utf-8") as stream:
                    for line in stream:
                        sample = json.loads(line)
                        key = writer, sample["id"]
                        if key not in seen:
                            seen.add(key)
                            dest.write(json.dumps(dict(writer=writer, **sample), ensure_ascii=False) + "\n")
        temporary.replace(output)
    return len(seen)


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--log", type=pathlib.Path, help="定时运行时把同步和回放输出写入日志")
    parser.add_argument("--skip-download", action="store_true", help="不下载，只处理已经拉下来的")
    parser.add_argument("--no-replay", action="store_true", help="手写只收集合并，不回放")
    args = parser.parse_args(argv)

    if args.log:
        flags = (["--skip-download"] if args.skip_download else []) + (["--no-replay"] if args.no_replay else [])
        python = pathlib.Path(sys.executable)
        if python.name.lower() == "pythonw.exe":
            python = python.with_name("python.exe")
        with args.log.open("a", encoding="utf-8") as log:
            return subprocess.run([str(python), "-X", "utf8", __file__, *flags], stdout=log, stderr=log,
                                  creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0)).returncode

    dict_cloud = DICT_DIR / "cloud"
    hand_cloud = HAND_DIR / "cloud"
    if not args.skip_download:
        download(DATASETS["dict"], dict_cloud)
        download(DATASETS["handwriting"], hand_cloud)

    total = merge_glide(hand_cloud, DATA / "glide_contrib" / "samples.jsonl")
    print(f"滑行练习：{total} 条去重轨迹")
    sources = [str(d) for d in versioned(dict_cloud, "dict")]
    sources += [str(f) for f in dict_cloud.glob("*.txt.gz")]
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
