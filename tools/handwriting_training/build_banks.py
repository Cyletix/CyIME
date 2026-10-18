"""一条命令生成手写训练模式的外置题库，并放到平板上。

用法：
    python tools/handwriting_training/build_banks.py                 # 只生成到 D:/CyIME-Data/handwriting_training/banks
    python tools/handwriting_training/build_banks.py --push          # 生成后推到唯一连接的设备
    python tools/handwriting_training/build_banks.py --push --serial R52T800SKWZ --skip-build

题库不进安装包：放在设备的 Android/data/com.cyletix.cyime/files/handwriting_training/，
训练模式切到对应分类时读取；也可以在训练模式里“导入题库文件”手动选这些 .tsv。
"""
from __future__ import annotations

import argparse
import os
import pathlib
import subprocess
import sys

HERE = pathlib.Path(__file__).resolve().parent
OUT = pathlib.Path("D:/CyIME-Data/handwriting_training/banks")
PACKAGE = "com.cyletix.cyime"
DEVICE_DIR = f"/sdcard/Android/data/{PACKAGE}/files/handwriting_training"
SOURCES = {
    "math_formulas.tsv": ("extract_math_formulas.py", "D:/GitHub/Math"),
    "english.tsv": ("extract_english.py", "D:/OneDrive/Project/English"),
    "japanese.tsv": ("extract_japanese.py", "D:/CyIME-Data/handwriting_training/日语语法_修订版.xlsx"),
    "chinese_chars.tsv": ("extract_chinese.py", None),  # 用仓库里的拼音词库和手写模型字表，不需要来源文件
}


def adb() -> str:
    sdk = os.environ.get("ANDROID_HOME") or os.path.expanduser("~/AppData/Local/Android/Sdk")
    exe = pathlib.Path(sdk) / "platform-tools" / ("adb.exe" if os.name == "nt" else "adb")
    return str(exe) if exe.exists() else "adb"


def build(out: pathlib.Path) -> None:
    out.mkdir(parents=True, exist_ok=True)
    for name, (script, source) in SOURCES.items():
        if source is None:
            subprocess.run([sys.executable, "-I", str(HERE / script), str(out / name)], check=True)
            continue
        if not pathlib.Path(source).exists():
            print(f"跳过 {name}：找不到来源 {source}")
            continue
        subprocess.run([sys.executable, "-I", str(HERE / script), source, str(out / name)], check=True)


def push(out: pathlib.Path, serial: str | None) -> None:
    base = [adb()]
    if serial:
        base += ["-s", serial]
    else:
        devices = [l.split()[0] for l in subprocess.run([adb(), "devices"], capture_output=True, text=True).stdout
                   .splitlines()[1:] if l.strip().endswith("device")]
        if len(devices) != 1:
            raise SystemExit(f"连着 {len(devices)} 台设备，请用 --serial 指定：{devices}")
    subprocess.run(base + ["shell", "mkdir", "-p", DEVICE_DIR], check=True)
    for name in SOURCES:
        f = out / name
        if f.exists():
            subprocess.run(base + ["push", str(f), f"{DEVICE_DIR}/{name}"], check=True)
    print(f"已放到设备 {DEVICE_DIR}，重新进入训练模式即可使用")


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default=str(OUT))
    ap.add_argument("--push", action="store_true", help="生成后推到设备")
    ap.add_argument("--serial", help="多台设备时指定序列号")
    ap.add_argument("--skip-build", action="store_true", help="不重新生成，直接推送已有文件")
    args = ap.parse_args()
    out = pathlib.Path(args.out)
    if not args.skip_build:
        build(out)
    if args.push:
        push(out, args.serial)
    return 0


if __name__ == "__main__":
    sys.exit(main())
