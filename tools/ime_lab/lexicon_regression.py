"""Run fixtures/lexicon_regression.tsv against a Rime engine on this computer.

26-key (rime_ice) and 14-key (pinyin_14jian) rows use the Android data: the generated rime_ice directory of
the last build (app/build/generated/chinese-assets/rime_ice, so ext weights and schema patches are the
built ones) plus the 14-key schemas from assets. win26 rows use CyIME-Windows' data/rime with its pinyin
schema. Touch typing on the phone has neighbor correction, so 26/14-key rows run with a QWERTY neighbor
map. Nine-key rows need the joint decoder and word model, which only the WSL host replay
(tools/ime_lab/t9_replay.py) runs; they are listed as not run here.

The engine is any rime.dll built from this repository's librime (e.g. CyIME-Windows'
scripts/build-engine.ps1 output). The data directory is a fresh copy under --work; no personal user dictionary
is read and nothing is committed, so nothing is learned.

Usage: python lexicon_regression.py --dll DIR --work DIR [--windows-data D:/GitHub/CyIME-Windows/data/rime]
                                    [--output result.json] [--baseline old-result.json] [--public-set DIR]
"""
import argparse
import ctypes as C
import json
import os
import re
import shutil
import subprocess
import sys
from collections import defaultdict
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
CASES = Path(__file__).resolve().parent / "fixtures" / "lexicon_regression.tsv"


def qwerty_neighbors():
    rows, offset = ["qwertyuiop", "asdfghjkl", "zxcvbnm"], [0, 0.5, 1.5]
    pos = {c: (r, i + offset[r]) for r, row in enumerate(rows) for i, c in enumerate(row)}
    return ";".join(c + ":" + "".join(d for d, (r2, x2) in pos.items()
                                      if d != c and abs(r2 - r) <= 1 and abs(x2 - x) <= 1.0)
                    for c, (r, x) in pos.items())


def load_cases():
    cases = []
    # CYIME_EXTRA_CASES: extra fixture files in the same format (e.g. a particle_phrases.py set), ';'-separated.
    extra = [Path(p) for p in os.environ.get("CYIME_EXTRA_CASES", "").split(";") if p]
    lines = [line for path in [CASES, *extra] for line in path.read_text(encoding="utf-8").splitlines()]
    for line in lines:
        if not line or line.startswith("#") or line.startswith("id\t"):
            continue
        f = line.split("\t")
        cases.append({"id": f[0], "keyboard": f[1], "input": f[2], "expect": f[3], "top_k": int(f[4] or 1),
                      "source": f[5], "date": f[6], "note": f[7] if len(f) > 7 else ""})
    # Shared public-corpus set (README in that directory); kept out of the repository. Same-message context
    # is not fed to the engine: Rime here has no left-context input, so these measure context-free typing.
    public = os.environ.get("CYIME_PUBLIC_SET")
    if public:
        for name in ("jianpin", "sentence"):
            for line in (Path(public) / f"{name}.jsonl").read_text(encoding="utf-8").splitlines():
                row = json.loads(line)
                typed = row["keys"] if name == "jianpin" else "".join(row["pinyin"]).replace("ü", "v")
                cases.append({"id": f"pub-{row['id']}", "keyboard": "26", "input": typed, "expect": row["text"],
                              "top_k": 1, "source": f"public {name}/{row['group']}", "date": "2026-10-18",
                              "note": "", "count": row.get("count", 1)})
    return cases


def prepare_android(work):
    data = work / "android"
    if data.exists():
        shutil.rmtree(data)
    shutil.copytree(REPO / "app/build/generated/chinese-assets/rime_ice", data)
    for name in ("pinyin_14jian.schema.yaml",):
        shutil.copy(REPO / "app/src/main/assets/rime_chinese" / name, data / name)
    for name in ("default.yaml", "symbols.yaml"):
        shutil.copy(REPO / "app/src/main/assets/rime" / name, data / name)
    text = (data / "default.yaml").read_text(encoding="utf-8")
    text = re.sub(r"schema_list:\n(  - schema: .*\n)+",
                  "schema_list:\n  - schema: rime_ice\n  - schema: pinyin_14jian\n", text)
    (data / "default.yaml").write_text(re.sub(r"page_size: \d+", "page_size: 10", text), encoding="utf-8")
    # Experiments: extra top-level YAML appended to the 26-key schema, and the word model it may name.
    extra = os.environ.get("CYIME_RIME_ICE_EXTRA")
    if extra:
        with open(data / "rime_ice.schema.yaml", "a", encoding="utf-8") as schema:
            schema.write(chr(10) + Path(extra).read_text(encoding="utf-8"))
    base_weights = os.environ.get("CYIME_BASE_WEIGHTS")
    if base_weights:  # experiments: replace base.dict.yaml weights from a text/code/weight gzip TSV
        import gzip
        table = {}
        with gzip.open(base_weights, "rt", encoding="utf-8") as rows:
            for row in rows:
                text, code, weight = row.rstrip(chr(10)).split(chr(9))
                table[text + chr(9) + code] = weight
        path = data / "cn_dicts" / "base.dict.yaml"
        lines = path.read_text(encoding="utf-8").split(chr(10))
        body = False
        for i, line in enumerate(lines):
            if line.strip() == "...":
                body = True
            fields = line.split(chr(9))
            if body and len(fields) == 3 and fields[0] + chr(9) + fields[1] in table:
                lines[i] = fields[0] + chr(9) + fields[1] + chr(9) + table[fields[0] + chr(9) + fields[1]]
        path.unlink()
        path.write_text(chr(10).join(lines), encoding="utf-8")
    overrides = os.environ.get("CYIME_T9_SET")
    if overrides:  # "joint_decoder=true,pool_budget=24": replace those keys in the schema's t9 block
        schema = data / "rime_ice.schema.yaml"
        text = schema.read_text(encoding="utf-8")
        head, block = text.split(chr(10) + "t9:" + chr(10), 1)
        for item in overrides.split(","):
            key, value = item.split("=", 1)
            block, count = re.subn(r"(?m)^  " + re.escape(key) + r": .*$", f"  {key}: {value}", block)
            if not count:
                block = f"  {key}: {value}" + chr(10) + block
        schema.write_text(head + chr(10) + "t9:" + chr(10) + block, encoding="utf-8")
    model = os.environ.get("CYIME_T9_NGRAM")
    if model:
        os.link(model, data / "t9_ngram.lm")
    return data


def prepare_windows(work, source):
    data = work / "windows"
    if data.exists():
        shutil.rmtree(data)
    shutil.copytree(source, data)
    text = (data / "default.yaml").read_text(encoding="utf-8")
    (data / "default.yaml").write_text(re.sub(r"schema_list:\n(  - schema: .*\n)+",
                                              "schema_list:\n  - schema: cyime_pinyin_5\n", text), encoding="utf-8")
    return data


class Traits(C.Structure):
    _fields_ = [("data_size", C.c_int), ("shared_data_dir", C.c_char_p), ("user_data_dir", C.c_char_p),
                ("distribution_name", C.c_char_p), ("distribution_code_name", C.c_char_p),
                ("distribution_version", C.c_char_p), ("app_name", C.c_char_p), ("modules", C.c_void_p),
                ("min_log_level", C.c_int), ("log_dir", C.c_char_p), ("prebuilt_data_dir", C.c_char_p),
                ("staging_dir", C.c_char_p)]


class Cand(C.Structure):
    _fields_ = [("text", C.c_char_p), ("comment", C.c_char_p), ("reserved", C.c_void_p)]


class Iter(C.Structure):
    _fields_ = [("ptr", C.c_void_p), ("index", C.c_int), ("candidate", Cand)]


def run(dll_dir, data, jobs):
    """jobs: [(schema, neighbors, [cases])]; one engine instance per data directory."""
    os.add_dll_directory(str(dll_dir))
    rime = C.CDLL(str(Path(dll_dir) / "rime.dll"))
    t = Traits()
    t.data_size = C.sizeof(Traits) - C.sizeof(C.c_int)
    t.shared_data_dir = t.user_data_dir = str(data).encode()
    t.distribution_name = t.distribution_code_name = b"lexicon-regression"
    t.distribution_version, t.app_name, t.min_log_level = b"1", b"rime.lexicon_regression", 2
    rime.RimeSetup(C.byref(t))
    rime.RimeInitialize(C.byref(t))
    if rime.RimeStartMaintenance(True):
        rime.RimeJoinMaintenanceThread()
    rime.RimeCreateSession.restype = C.c_size_t
    results = {}
    for schema, neighbors, cases in jobs:
        session = C.c_size_t(rime.RimeCreateSession())
        assert rime.RimeSelectSchema(session, schema.encode()), schema
        rime.RimeSetOption(session, b"ascii_mode", False)
        if neighbors:
            rime.RimeSetProperty(session, b"cyime_neighbors", neighbors.encode())
        for case in cases:
            rime.RimeClearComposition(session)
            for ch in case["input"]:
                rime.RimeProcessKey(session, ord(ch), 0)
            it, top = Iter(), []
            if rime.RimeCandidateListBegin(session, C.byref(it)):
                while len(top) < 10 and rime.RimeCandidateListNext(C.byref(it)):
                    top.append(it.candidate.text.decode())
                rime.RimeCandidateListEnd(C.byref(it))
            results[case["id"]] = top
        rime.RimeDestroySession(session)
    rime.RimeFinalize()
    return results


def passed(case, top):
    if case["expect"].startswith("!"):
        return bool(top) and top[0] != case["expect"][1:]
    return case["expect"] in top[:case["top_k"]]


def group(case):
    s = case["source"]
    return "user/found" if s in ("user", "found") or s.startswith("user ") else s


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--dll", required=True, type=Path)
    parser.add_argument("--work", required=True, type=Path)
    parser.add_argument("--windows-data", type=Path, default=Path("D:/GitHub/CyIME-Windows/data/rime"))
    parser.add_argument("--output", type=Path)
    parser.add_argument("--baseline", type=Path)
    parser.add_argument("--public-set", type=Path, help="Directory with jianpin.jsonl / sentence.jsonl")
    parser.add_argument("--rime-ice-extra", type=Path, help="YAML appended to rime_ice.schema.yaml (experiments)")
    parser.add_argument("--t9-ngram", type=Path, help="Word model linked into the data directory as t9_ngram.lm")
    parser.add_argument("--base-weights", type=Path, help="gzip TSV text/code/weight replacing base.dict weights")
    parser.add_argument("--t9-set", help="Override keys of the 26-key t9 block, e.g. joint_decoder=true,pool_budget=24")
    args = parser.parse_args()
    if args.t9_set:
        os.environ["CYIME_T9_SET"] = args.t9_set
    if args.base_weights:
        os.environ["CYIME_BASE_WEIGHTS"] = str(args.base_weights)
    if args.rime_ice_extra:
        os.environ["CYIME_RIME_ICE_EXTRA"] = str(args.rime_ice_extra)
    if args.t9_ngram:
        os.environ["CYIME_T9_NGRAM"] = str(args.t9_ngram)
    if args.public_set:
        os.environ["CYIME_PUBLIC_SET"] = str(args.public_set)
    cases = load_cases()
    by_keyboard = defaultdict(list)
    for case in cases:
        by_keyboard[case["keyboard"]].append(case)
    args.work.mkdir(parents=True, exist_ok=True)
    # Separate processes: librime allows one setup per process.
    results = {}
    for name, keyboards in (("android", ("26", "14")), ("windows", ("win26",))):
        selected = [c for k in keyboards for c in by_keyboard.get(k, [])]
        if not selected:
            continue
        child = args.work / f"{name}.json"
        subprocess.run([sys.executable, __file__, "--child", name, str(args.dll), str(args.work),
                        str(args.windows_data), str(child)], check=True)
        results.update(json.loads(child.read_text(encoding="utf-8")))
    report = {"cases": [], "summary": {}}
    stats = defaultdict(lambda: [0, 0])
    baseline = json.loads(args.baseline.read_text(encoding="utf-8")) if args.baseline else None
    old = {c["id"]: c for c in baseline["cases"]} if baseline else {}
    changes = []
    for case in cases:
        if case["keyboard"] == "9":
            report["cases"].append({**case, "status": "not-run"})
            continue
        top = results.get(case["id"], [])
        ok = passed(case, top)
        key = (case["keyboard"], group(case))
        stats[key][0] += ok
        stats[key][1] += 1
        report["cases"].append({**case, "status": "pass" if ok else "fail", "top": top})
        if case["id"] in old and old[case["id"]].get("status") in ("pass", "fail") and \
                old[case["id"]]["status"] != ("pass" if ok else "fail"):
            changes.append(f"{'FIXED' if ok else 'BROKE'} {case['id']} {case['input']} -> {case['expect']} | "
                           f"{old[case['id']].get('top', [])[:3]} -> {top[:3]}")
    weighted = defaultdict(lambda: [0, 0])
    for item in report["cases"]:
        if item.get("count", 1) > 1 or item["source"].startswith("public jianpin"):
            weighted[item["source"]][0] += item["count"] * (item["status"] == "pass")
            weighted[item["source"]][1] += item["count"]
    for source, (good, total) in sorted(weighted.items()):
        print(f"       {source + ' (by occurrences)':55} {100 * good / total:5.1f}%")
    nine = sum(1 for c in cases if c["keyboard"] == "9")
    for (keyboard, source), (good, total) in sorted(stats.items()):
        if total:
            report["summary"][f"{keyboard} {source}"] = f"{good}/{total}"
            print(f"{keyboard:6} {source:55} {good:4}/{total:<4} {100 * good / total:5.1f}%")
    print(f"nine-key rows not run here: {nine} (use t9_replay.py)")
    for line in changes:
        print(line)
    if args.output:
        args.output.write_text(json.dumps(report, ensure_ascii=False, indent=1), encoding="utf-8")


def child():
    _, _, name, dll, work, windows_data, out = sys.argv
    work = Path(work)
    by_keyboard = defaultdict(list)
    for case in load_cases():
        by_keyboard[case["keyboard"]].append(case)
    neighbors = qwerty_neighbors()
    if name == "android":
        data = prepare_android(work)
        jobs = [("rime_ice", neighbors, by_keyboard["26"]), ("pinyin_14jian", "", by_keyboard["14"])]
    else:
        data = prepare_windows(work, Path(windows_data))
        jobs = [("cyime_pinyin_5", neighbors, by_keyboard["win26"])]
    Path(out).write_text(json.dumps(run(Path(dll), data, jobs), ensure_ascii=False), encoding="utf-8")


if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "--child":
        child()
    else:
        main()
