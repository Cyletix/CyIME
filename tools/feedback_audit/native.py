"""Run small Linux host-engine probes against current stroke assets and the nine-key syllable map."""
import argparse
import json
import os
import shutil
import subprocess
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
sys.path.insert(0, str(ROOT / "tools/ime_lab"))
from lexicon_regression import run
sys.path.insert(0, str(ROOT / "scripts"))
from storage_guard import check_storage


def check_stroke(out, library, baseline):
    data = out / "stroke"
    data.mkdir()
    assets = ROOT / "app/src/main/assets/rime_chinese"
    for name in ("symbols.yaml", "default.yaml", "default.custom.yaml"):
        shutil.copyfile(baseline / name, data / name)
    shutil.copyfile(ROOT / "app/build/generated/stroke-assets/rime_chinese/stroke.dict.yaml", data / "stroke.dict.yaml")
    shutil.copyfile(assets / "stroke.schema.yaml", data / "stroke.schema.yaml")
    (data / "lua").mkdir()
    for path in (assets / "lua").glob("cyime_stroke_*.lua"):
        shutil.copyfile(path, data / "lua" / path.name)
    a, b = "phnphnphhz", "hszhshzs"
    wild = "ph*phnphhz"
    expected = {a: "笔", b: "画", a+b: "笔画", a+"'"+b: "笔画", wild+b: "笔画", wild+"'"+b: "笔画",
                a+"'hszh*hzs": "笔画", "****": None}
    short = "phn'hsz"
    cases = [dict(id=code, input=code) for code in [*expected, short]]
    os.environ["CYIME_REPORT_TOP"] = "60"
    candidates, _, _, _ = run(library, data, [("stroke", None, cases, None)])
    failures = [code for code, word in expected.items() if next(iter(candidates[code]), None) != word]
    return dict(candidates=candidates, failures=failures, abbreviated_has_target="笔画" in candidates[short])


def check_syllables(out):
    source = ROOT / "app/src/main/jni/librime-t9/src"
    program = """
        #include "t9_pinyin_map.h"
        #include <iostream>
        int main() {
            for (const auto& option : rime::T9PinyinMap::Instance().FirstSyllableOptions("9464")) {
                std::cout << option.pinyin << " " << option.digit_length << std::endl;
            }
        }
    """
    exe = out / "syllables"
    subprocess.run(["c++", "-std=c++17", "-I", str(source), str(source / "t9_pinyin_map.cc"), "-x", "c++",
                    "-", "-o", str(exe)], text=True, input=program, check=True)
    rows = subprocess.check_output([str(exe)], text=True).splitlines()
    return dict(digits="9464", options=rows, failures=[s for s in ("xing 4", "ying 4") if s not in rows])


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--library", required=True, type=Path, help="existing host librime_jni.so")
    parser.add_argument("--baseline", required=True, type=Path, help="reference stroke deployment, read only")
    args = parser.parse_args()
    check_storage(ROOT)
    args.output.mkdir(parents=True, exist_ok=False)
    result = dict(syllables=check_syllables(args.output), stroke=check_stroke(args.output, args.library, args.baseline),
                  library=str(args.library), baseline=str(args.baseline))
    (args.output / "native.json").write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(result, ensure_ascii=False))
    raise SystemExit(bool(result["syllables"]["failures"] or result["stroke"]["failures"]))
