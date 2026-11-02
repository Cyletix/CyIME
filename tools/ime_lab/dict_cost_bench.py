"""Measure how much imported (cell) dictionaries slow typing on a real device, by total word count.

Turns on sets of real Sogou dictionaries from the built-in catalog (about 300 words up to about 200 thousand),
applies them as 「应用更改」 does, restarts the IME and types the same words as perf_cost_bench.py through the visible
keyboard, then reads the key-to-frame probe and the IME process PSS. "No dictionary" runs first and last; their gap
is the noise floor. The other performance switches stay as the device has them.

The curve goes into the device's entry of Android/data/<package>/files/perf-costs.json (`dictionaries`,
`dictionaryBaseline`), which the 词库 page reads for 「最多慢 X%」 (slowest 5% of keys) and 性能与省电 for the total,
and to --out (dictionary-entry.json) for the repo table app/src/main/assets/perf_costs.json.

Afterwards the dictionaries that were on are applied again and the ones this run downloaded are deleted; the
guarded runner restores and checks settings and the user dictionary. Needs a 装到我的设备 build with the
cell_state / cell_set / cell_remove dev ops, network for the downloads, and the device registered on the status page.

  python -I scripts/guarded-device-run.py --serial TABLET_SERIAL -- \
    python tools/ime_lab/dict_cost_bench.py --serial TABLET_SERIAL \
      --layouts tools/demo_typer/layouts/sm-x800.json --layout cn26 \
      --host-cmd "am start -n md.obsidian/.MainActivity" --host-window md.obsidian \
      --name "Galaxy Tab S8+" --tier high --out D:/CyIME-Data/diagnostics/dict-cost-<date>

Other keyboards (--layout t9, or --scheme flypy on 小鹤双拼) only compare: add --no-push, and the curve stays in --out.
"""
import argparse
import json
import os
import re
import statistics
import subprocess
import sys
import tempfile
import time

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import perf_cost_bench as bench  # noqa: E402

# Catalog ids (CellDictionaryCatalog.offers) and their word counts from the detail pages.
SETS = [
    ("w300", ["sogou_177368"]),                                   # 三角洲行动 323: one tiny dictionary (fixed cost?)
    ("w2k", ["sogou_177368", "sogou_61481"]),                     # + 我的世界 1576 = 1899, the 11-02 QQ group case
    ("w20k", ["sogou_15203", "sogou_15117"]),                     # 物理 13107 + 计算机 10300
    ("w50k", ["sogou_15097", "sogou_15128"]),                     # 成语俗语 46785 + 法律 4560
    ("w100k", ["sogou_15125", "sogou_15117"]),                    # 医学 90047 + 计算机 10300
    ("w200k", ["sogou_15125", "sogou_15097", "sogou_20664", "sogou_20649", "sogou_2"]),  # + 中医 摄影 古诗
]


def cell_state(serial):
    answer = bench.dev_op(serial, "cell_state")
    fields = dict(part.split("=", 1) for part in answer.split() if "=" in part)
    if "applying" not in fields:
        raise SystemExit(f"cell_state: unexpected answer {answer!r} (build without the cell dev ops?)")
    ids = lambda key: [i for i in fields.get(key, "").split(",") if i]  # noqa: E731
    installed = {}
    for pair in ids("installed"):
        name, _, count = pair.rpartition(":")
        installed[name] = int(count)
    return {"selected": ids("selected"), "applied": ids("applied"), "english": fields.get("english") == "true",
            "installed": installed, "applying": fields["applying"] == "true", "last": fields.get("last", "")}


def cell_set(serial, ids, english, timeout=1800):
    answer = bench.dev_op(serial, "cell_set", "--es", "ids", ",".join(ids), "--ez", "english", "true" if english else "false")
    if answer != "cell_set: started":
        raise SystemExit(f"cell_set {ids}: {answer}")
    deadline = time.time() + timeout
    while time.time() < deadline:
        time.sleep(10)
        state = cell_state(serial)
        if not state["applying"]:
            if not state["last"].startswith("ok"):
                raise SystemExit(f"cell_set {ids}: {state['last']}")
            return state
    raise SystemExit(f"cell_set {ids}: still applying after {timeout} s")


def measure(args, keys, label):
    """perf_cost_bench.measure plus the slowest 5% of keys, which the 词库 page shows."""
    result = bench.measure(args, keys, label, args.out)
    rows = bench.key_latency_report.parse(os.path.join(args.out, f"{label}.log"))
    result["keyP95Ms"] = round(bench.key_latency_report.percentile([r["total"] for r in rows], 95) / 1000, 2)
    return result


def push_entry(serial, model, name, tier, layout, version, baseline, points):
    """Add the curve to this device's entry in the on-device table; other devices and the switch costs stay."""
    table = {"version": 1, "devices": []}
    with tempfile.TemporaryDirectory() as local:
        path = os.path.join(local, "perf-costs.json")
        if subprocess.run(["adb", "-s", serial, "pull", bench.REMOTE_TABLE, path], capture_output=True).returncode == 0:
            with open(path, encoding="utf-8") as source:
                table = json.load(source)
        entry = next((d for d in table["devices"] if d.get("model", "").lower() == model.lower()), None)
        if entry is None:
            # No switch costs measured on this device yet: the dictionary baseline is its baseline.
            entry = {"model": model, "name": name, "tier": tier, "measuredAt": time.strftime("%Y-%m-%d"),
                     "appVersion": version, "layout": layout, "baseline": baseline, "items": {}, "combos": []}
            table["devices"].append(entry)
        entry["dictionaries"] = points
        entry["dictionaryBaseline"] = baseline
        with open(path, "w", encoding="utf-8") as out:
            json.dump(table, out, ensure_ascii=False, indent=2)
        bench.adb(serial, "push", "-q", path, bench.REMOTE_TABLE)


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--serial", required=True)
    parser.add_argument("--layouts", required=True)
    parser.add_argument("--layout", default="cn26")
    parser.add_argument("--layout-name", default=None)
    parser.add_argument("--host-cmd", required=True)
    parser.add_argument("--host-window", required=True)
    parser.add_argument("--name", default="")
    parser.add_argument("--tier", default="")
    parser.add_argument("--sets", default="", help="comma-separated labels from SETS (default: all)")
    parser.add_argument("--no-push", action="store_true", help="only write --out (other keyboards, for comparison)")
    parser.add_argument("--words", default=bench.WORDS)
    parser.add_argument("--scheme", default="pinyin", choices=["pinyin", "flypy"])
    parser.add_argument("--rounds", type=int, default=3)
    parser.add_argument("--interval", type=int, default=110)
    parser.add_argument("--hold", type=int, default=25)
    parser.add_argument("--cool", type=int, default=20)
    parser.add_argument("--dex", default="D:/CyIME-Data/demo-video/cyime-inject.dex")
    parser.add_argument("--out", required=True)
    args = parser.parse_args()
    os.makedirs(args.out, exist_ok=True)
    if args.layout_name is None:
        args.layout_name = {"t9": "中文九键"}.get(args.layout, "小鹤双拼 26 键" if args.scheme == "flypy" else "中文全拼 26 键")
    keys = bench.keys_for(args.layouts, args.layout)
    missing = sorted({c for w in bench.key_sequences(args.words, args.scheme) for c in w} - set(keys)) + \
        ([] if "delete" in keys else ["delete"])
    if missing:
        raise SystemExit(f"layout {args.layout} has no keys for {missing}")
    wanted = set(filter(None, args.sets.split(",")))
    sets = [s for s in SETS if not wanted or s[0] in wanted]
    bench.adb(args.serial, "push", "-q", args.dex, bench.INJECT_DEX)

    original = cell_state(args.serial)
    if original["applying"]:
        raise SystemExit("dictionaries are being applied on the device; try later")
    if sorted(original["selected"]) != sorted(original["applied"]):
        # The user has an unapplied choice on the 词库 page; this run would apply it in the end.
        raise SystemExit("the device has dictionary changes not applied yet; nothing was run")
    print("before:", json.dumps(original, ensure_ascii=False), flush=True)
    results, words = {}, {}
    bench.adb(args.serial, "shell", "setprop", "log.tag.CyIMELatency", "DEBUG")
    try:
        for label, ids in [("none", [])] + sets + [("none_end", [])]:
            state = cell_set(args.serial, ids, english=False)
            words[label] = sum(state["installed"].get(i, 0) for i in ids)
            results[label] = {**measure(args, keys, label), "words": words[label], "dictionaries": ids}
    finally:
        try:
            cell_set(args.serial, original["applied"], original["english"])
            downloaded = sorted(set(cell_state(args.serial)["installed"]) - set(original["installed"]))
            if downloaded:
                print(bench.dev_op(args.serial, "cell_remove", "--es", "ids", ",".join(downloaded)))
            after = cell_state(args.serial)
            if sorted(after["applied"]) != sorted(original["applied"]) or set(after["installed"]) != set(original["installed"]):
                print("!!! DICTIONARIES NOT RESTORED:", json.dumps(after, ensure_ascii=False), file=sys.stderr)
        finally:
            bench.adb(args.serial, "shell", "setprop", "log.tag.CyIMELatency", "''", check=False)
            with open(os.path.join(args.out, "raw.json"), "w", encoding="utf-8") as out:
                json.dump(results, out, ensure_ascii=False, indent=2)

    first, last = results["none"], results["none_end"]
    base = {"keyMs": round((first["keyMs"] + last["keyMs"]) / 2, 2),
            "keyP95Ms": round((first["keyP95Ms"] + last["keyP95Ms"]) / 2, 2)}
    mems = [m for m in (first["memoryMb"], last["memoryMb"]) if m is not None]
    base["memoryMb"] = round(sum(mems) / len(mems), 1) if mems else None
    noise = max(0.5, abs(first["keyMs"] - last["keyMs"]))
    points = []
    for label, ids in sets:
        r = results[label]
        points.append({
            "words": r["words"], "dictionaries": ids,
            "keyMs": round(max(0.0, r["keyMs"] - base["keyMs"]), 2),
            "keyP95Ms": round(max(0.0, r["keyP95Ms"] - base["keyP95Ms"]), 2),
            "memoryMb": None if base["memoryMb"] is None or r["memoryMb"] is None else round(max(0.0, r["memoryMb"] - base["memoryMb"]), 1),
        })
    model = bench.adb(args.serial, "shell", "getprop", "ro.product.model")
    version = re.search(r"versionName=(\S+)", bench.adb(args.serial, "shell", "dumpsys", "package", bench.PACKAGE, check=False))
    version = version.group(1) if version else ""
    entry = {"model": model, "layout": args.layout_name, "appVersion": version, "measuredAt": time.strftime("%Y-%m-%d"),
             "noiseMs": round(noise, 2), "dictionaryBaseline": base, "dictionaries": points}
    with open(os.path.join(args.out, "dictionary-entry.json"), "w", encoding="utf-8") as out:
        json.dump(entry, out, ensure_ascii=False, indent=2)
    print(f"no dictionary: {base['keyMs']} ms/key median, {base['keyP95Ms']} ms p95, noise {noise:.1f} ms")
    for p in points:
        share = p["keyP95Ms"] / base["keyP95Ms"] * 100 if base["keyP95Ms"] else 0
        print(f"{p['words']:>7} words: +{p['keyMs']} ms median, +{p['keyP95Ms']} ms p95 ({share:.1f}%), +{p['memoryMb']} MB")
    if not args.no_push:
        push_entry(args.serial, model, args.name, args.tier, args.layout_name, version, base, points)
        print(f"pushed {bench.REMOTE_TABLE}; entry in {args.out}/dictionary-entry.json")


if __name__ == "__main__":
    main()
