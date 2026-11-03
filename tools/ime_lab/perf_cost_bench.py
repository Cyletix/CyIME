"""Measure what each performance-heavy CyIME switch costs per keystroke and in memory, on a real device.

Each config turns exactly one registered switch on (all others off), restarts the IME, types the same pinyin
words at a fixed pace through the visible keyboard, and reads the key-to-frame probe (KeyLatencyProbe) and the
IME process PSS. Baseline (everything off) runs first and last; their gap is the noise floor. Then a few
combinations (what was on before the run, and everything on) are measured for real, because costs don't add up.

The result goes to the device as Android/data/<package>/files/perf-costs.json, which the settings pages read
("性能代价：每键 +1.2 ms（慢 4%）" under each switch, and the total on 性能与省电), and to --out for the
repo table app/src/main/assets/perf_costs.json. Words are cleared with the delete swipe before they are
committed, so nothing is typed into the host app.

Run it under the guarded runner (snapshot + 无痕 before, restore + verify after), with a 装到我的设备 build:
  python -I scripts/guarded-device-run.py --serial TABLET_SERIAL -- \
    python tools/ime_lab/perf_cost_bench.py --serial TABLET_SERIAL \
      --layouts tools/demo_typer/layouts/sm-x800.json --layout cn26 \
      --host-cmd "am start -n md.obsidian/.MainActivity" --host-window md.obsidian \
      --name "Galaxy Tab S8+" --tier high --out D:/CyIME-Data/diagnostics/perf-cost-<date>

九键模糊音（t9_ 开头的项）在九键上另测，并入同一台设备的表（--merge）。键盘先切到九键全拼：
  ... perf_cost_bench.py --serial TABLET_SERIAL --layouts tools/demo_typer/layouts/sm-x800.json --layout t9 \
      --host-cmd ... --host-window ... --merge --out D:/CyIME-Data/diagnostics/perf-cost-<date>-t9
九键布局只标了数字键时，拼音字母按键上的字母落到对应数字键。--merge 时每项另记这次的基线（baselineKeyMs），
设置页“慢百分之几”按它算。

Before any adb use read <notes>/Project/Agent状态同步.md and register the device. The script
refuses to type unless the host window has focus with the CyIME keyboard up (same guard as demo_typer).
Touches are played by tools/demo_typer/inject (app_process, no monkey: monkey flips a tablet's rotation).
"""
import argparse
import importlib.util
import json
import os
import re
import statistics
import subprocess
import sys
import tempfile
import time

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(os.path.dirname(HERE))
sys.path.insert(0, HERE)
import key_latency_report  # noqa: E402

_spec = importlib.util.spec_from_file_location("guarded", os.path.join(REPO, "scripts", "guarded-device-run.py"))
guarded = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(guarded)

PACKAGE = guarded.PACKAGE
INJECT_DEX = "/data/local/tmp/cyime-inject.dex"
REMOTE_SCRIPT = "/data/local/tmp/cyime-perf.txt"
REMOTE_TABLE = f"/sdcard/Android/data/{PACKAGE}/files/perf-costs.json"
# 2～30 keys, common syllables, touching every fuzzy pair (z/zh c/ch s/sh n/l f/h r/l an/ang en/eng in/ing ian/iang uan/uang).
# Syllables are space-separated so a double-pinyin scheme can be typed from the same words (--scheme).
WORDS = ("ni hao,zhong guo,wo men,shen me,xian zai,jin tian tian qi zhen hao,wo zai xue xiao,zhe ge wen ti hen zhong yao,"
         "da jia hao,ming tian jian,bang wo na gei kuai di,ta ye hui lai de,fang jian li hen liang,chuan shang xin yi fu,ren men lai le")

# 小鹤双拼: initial key + final key per syllable; zero-initial syllables as Flypy types them (a→aa, ang→ah, ai→ai).
FLYPY_INITIALS = {"zh": "v", "ch": "i", "sh": "u"}
FLYPY_FINALS = {"iu": "q", "ei": "w", "e": "e", "uan": "r", "ue": "t", "ve": "t", "un": "y", "u": "u", "i": "i",
                "uo": "o", "o": "o", "ie": "p", "a": "a", "ong": "s", "iong": "s", "ai": "d", "en": "f", "eng": "g",
                "ang": "h", "an": "j", "ing": "k", "uai": "k", "iang": "l", "uang": "l", "ou": "z", "ia": "x",
                "ua": "x", "ao": "c", "ui": "v", "v": "v", "in": "b", "iao": "n", "ian": "m"}


def flypy(syllable):
    for initial in ("zh", "ch", "sh", "b", "p", "m", "f", "d", "t", "n", "l", "g", "k", "h", "j", "q", "x",
                    "r", "z", "c", "s", "y", "w"):
        if syllable.startswith(initial) and len(syllable) > len(initial):
            return FLYPY_INITIALS.get(initial, initial) + FLYPY_FINALS[syllable[len(initial):]]
    # zero initial: one letter doubled, two letters as is, longer = first letter + the final's key
    if len(syllable) == 1:
        return syllable * 2
    return syllable if len(syllable) == 2 else syllable[0] + FLYPY_FINALS[syllable[1:]]


def key_sequences(words, scheme):
    """Comma-separated words of space-separated syllables -> the keys to press for each word."""
    out = []
    for word in words.split(","):
        syllables = word.split()
        out.append("".join(flypy(s) for s in syllables) if scheme == "flypy" else "".join(syllables))
    return out


def adb(serial, *args, check=True):
    return guarded.adb(serial, *args, check=check)


def dev_op(serial, op, *extra):
    return guarded.dev_op(serial, op, *extra)


def state(serial):
    """{id: bool} for every registered switch."""
    answer = dev_op(serial, "perf_state")
    pairs = dict(part.split("=", 1) for part in answer.split() if "=" in part)
    if not pairs:
        raise SystemExit(f"perf_state: unexpected answer {answer!r} (build without perf_state?)")
    return {k: v == "true" for k, v in pairs.items()}


def switch(serial, item, on):
    answer = dev_op(serial, "perf_switch", "--es", "id", item, "--ez", "on", "true" if on else "false")
    if answer != f"perf_switch: {item}={'true' if on else 'false'}":
        raise SystemExit(f"perf_switch {item}: {answer}")


def apply_config(serial, wanted, current):
    for item, on in wanted.items():
        if current.get(item) != on:
            switch(serial, item, on)
            # Some items share a setting (a fuzzy group is in both fuzzy_<id> and t9_fuzzy_<id>): reread them all.
            current.clear()
            current.update(state(serial))


T9_LETTERS = {"2": "abc", "3": "def", "4": "ghi", "5": "jkl", "6": "mno", "7": "pqrs", "8": "tuv", "9": "wxyz"}


def keys_for(layouts_path, layout):
    with open(layouts_path, encoding="utf-8") as source:
        data = json.load(source)
    if "layouts" not in data:
        keys = dict(data)  # plain {key: [x, y]}
    else:
        keys = dict(data.get("common", {}))
        keys.update(data["layouts"][layout])
    # A nine-key layout lists only its digit keys: each pinyin letter is typed on the key that carries it.
    for digit, letters in T9_LETTERS.items():
        if digit in keys:
            for letter in letters:
                keys.setdefault(letter, keys[digit])
    return keys


def pointer(down, at, action, x, y):
    return f"DispatchPointer({down},{at},{action},{x},{y},1.0,0.05,0,1.0,1.0,0,0)"


def script(keys, words, interval, hold, rounds):
    """Same touch pattern as key_latency_drive.py: taps at a fixed pace, each word cleared by a swipe down on delete."""
    events, now = [], 1000
    for _ in range(rounds):
        for word in words:
            for ch in word:
                x, y = keys[ch][:2]
                events += [pointer(now, now, 0, x, y), f"UserWait({hold})", pointer(now, now + hold, 1, x, y),
                           f"UserWait({max(0, interval - hold)})"]
                now += interval
            x, y = keys["delete"][:2]
            start = now
            events.append(pointer(start, now, 0, x, y))
            for step in range(1, 9):
                now += 20
                events += ["UserWait(20)", pointer(start, now, 2, x, y + step * 22)]
            now += 20
            events += ["UserWait(20)", pointer(start, now, 1, x, y + 176), "UserWait(250)"]
            now += 250
    return "type= raw events\ncount= {}\nspeed= 1.0\nstart data >>\n{}\n".format(len(events), "\n".join(events))


def play(serial, text, host_window):
    with tempfile.TemporaryDirectory() as local:
        path = os.path.join(local, "script.txt")
        with open(path, "w", encoding="utf-8", newline="\n") as out:
            out.write(text)
        adb(serial, "push", "-q", path, REMOTE_SCRIPT)
    guard = (f"dumpsys window | grep mCurrentFocus | grep -q {host_window} && "
             f"settings get secure default_input_method | grep -q {PACKAGE} && "
             "dumpsys input_method | grep -q mInputShown=true || { echo NOGUARD; exit 0; }; ")
    out = adb(serial, "shell", f"{guard}CLASSPATH={INJECT_DEX} app_process / Inject {REMOTE_SCRIPT} >/dev/null; rm -f {REMOTE_SCRIPT}")
    if "NOGUARD" in out:
        raise SystemExit(f"stopped: {host_window} is not in front with the CyIME keyboard up")


def restart_ime(args):
    """Fresh process per config: settings, deployed schema patches and memory all start from the same place."""
    adb(args.serial, "shell", "am", "force-stop", PACKAGE)
    if not guarded.select_ime(args.serial):
        raise SystemExit("CyIME did not come back as the default keyboard")
    adb(args.serial, "shell", args.host_cmd)
    for _ in range(40):
        time.sleep(3)
        if "mInputShown=true" in adb(args.serial, "shell", "dumpsys", "input_method", check=False):
            break
    else:
        raise SystemExit("keyboard did not come up in the host field (tap the field once and rerun)")
    # Waits out a deploy (schema patches changed): the dev receiver answers once the engine lock is free.
    dev_op(args.serial, "perf_sync")


def pss_mb(serial):
    out = adb(serial, "shell", "dumpsys", "meminfo", PACKAGE, check=False)
    match = re.search(r"TOTAL PSS:\s+(\d+)", out) or re.search(r"^\s*TOTAL\s+(\d+)", out, re.M)
    return round(int(match.group(1)) / 1024, 1) if match else None


def battery_temp(serial):
    match = re.search(r"temperature:\s*(\d+)", adb(serial, "shell", "dumpsys", "battery", check=False))
    return int(match.group(1)) / 10 if match else None


def measure(args, keys, label, out_dir):
    restart_ime(args)
    words = key_sequences(args.words, args.scheme)
    play(args.serial, script(keys, words, args.interval, args.hold, 1), args.host_window)  # warm-up, not counted
    adb(args.serial, "logcat", "-c")
    play(args.serial, script(keys, words, args.interval, args.hold, args.rounds), args.host_window)
    time.sleep(1)
    log = adb(args.serial, "logcat", "-d", "-s", "CyIMELatency:D")
    path = os.path.join(out_dir, f"{label}.log")
    with open(path, "w", encoding="utf-8") as out:
        out.write(log)
    rows = key_latency_report.parse(path)
    if len(rows) < 20:
        raise SystemExit(f"{label}: only {len(rows)} probe lines (is log.tag.CyIMELatency DEBUG set?)")
    result = {
        "label": label, "keys": len(rows),
        "keyMs": round(statistics.median(r["total"] for r in rows) / 1000, 2),
        "keyP95Ms": round(key_latency_report.percentile([r["total"] for r in rows], 95) / 1000, 2),
        "engineMs": round(statistics.median(r.get("engine", 0) for r in rows) / 1000, 2),
        "memoryMb": pss_mb(args.serial), "batteryC": battery_temp(args.serial),
    }
    print(json.dumps(result, ensure_ascii=False), flush=True)
    time.sleep(args.cool)
    return result


def merge_table(serial, entry, merge_items):
    """Keep other devices' entries (and with --merge, this device's other items) in the on-device table."""
    table = {"version": 1, "devices": []}
    with tempfile.TemporaryDirectory() as local:
        path = os.path.join(local, "perf-costs.json")
        if subprocess.run(["adb", "-s", serial, "pull", REMOTE_TABLE, path], capture_output=True).returncode == 0:
            with open(path, encoding="utf-8") as source:
                table = json.load(source)
        old = next((d for d in table["devices"] if d.get("model", "").lower() == entry["model"].lower()), None)
        if merge_items and old:
            old["items"].update(entry["items"])
            names = {combo["name"] for combo in entry["combos"]}
            old["combos"] = [c for c in old.get("combos", []) if c.get("name") not in names] + entry["combos"]
            entry = old
        table["devices"] = [d for d in table["devices"] if d.get("model", "").lower() != entry["model"].lower()] + [entry]
        with open(path, "w", encoding="utf-8") as out:
            json.dump(table, out, ensure_ascii=False, indent=2)
        adb(serial, "push", "-q", path, REMOTE_TABLE)
    return table


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--serial", required=True)
    parser.add_argument("--layouts", required=True, help="demo_typer layouts JSON (or a plain {key: [x, y]} file)")
    parser.add_argument("--layout", default="cn26", help="layout inside --layouts; the keyboard must already show it")
    parser.add_argument("--layout-name", default=None, help="shown in settings next to the numbers (default from --scheme)")
    parser.add_argument("--host-cmd", required=True, help="adb shell command that brings up a focused text field")
    parser.add_argument("--host-window", required=True, help="text in mCurrentFocus while that field is in front")
    parser.add_argument("--name", default="", help="device name shown in settings, e.g. Galaxy Tab S8+")
    parser.add_argument("--tier", default="", help="high / medium / low: other devices of this tier use these numbers as reference")
    parser.add_argument("--items", default="",
                        help="comma-separated switch ids (default: all except --skip; on --layout t9 the t9_ items, "
                             "elsewhere everything but them)")
    parser.add_argument("--skip", default=None,
                        help="ids that do nothing on this scheme; default: double_pinyin_correction and "
                             "double_pinyin_abbreviation on 全拼, "
                             "mixed_input and the three typo switches (全拼 only) on 小鹤 (neighbor_correction also gates 双拼 since 11-03). Measure them on their own scheme with --merge")
    parser.add_argument("--merge", action="store_true", help="add these items to this device's existing entry")
    parser.add_argument("--no-combos", action="store_true")
    parser.add_argument("--words", default=WORDS, help="comma-separated words of space-separated pinyin syllables")
    parser.add_argument("--scheme", default="pinyin", choices=["pinyin", "flypy"],
                        help="what the keyboard is set to: 全拼, or 小鹤双拼 (syllables converted to Flypy keys)")
    parser.add_argument("--rounds", type=int, default=3)
    parser.add_argument("--interval", type=int, default=110, help="ms from one key down to the next (normal typing)")
    parser.add_argument("--hold", type=int, default=25)
    parser.add_argument("--cool", type=int, default=20, help="seconds between configs so the device does not heat up")
    parser.add_argument("--dex", default="D:/CyIME-Data/demo-video/cyime-inject.dex")
    parser.add_argument("--out", required=True)
    args = parser.parse_args()
    os.makedirs(args.out, exist_ok=True)
    keys = keys_for(args.layouts, args.layout)
    missing = sorted({c for w in key_sequences(args.words, args.scheme) for c in w} - set(keys)) + ([] if "delete" in keys else ["delete"])
    if missing:
        raise SystemExit(f"layout {args.layout} has no keys for {missing}")
    adb(args.serial, "push", "-q", args.dex, INJECT_DEX)

    original = state(args.serial)
    if args.layout_name is None:
        args.layout_name = "小鹤双拼 26 键" if args.scheme == "flypy" else "中文全拼 26 键"
    if args.skip is None:
        args.skip = ("mixed_input,transposition_correction,extra_letter_correction,omission_correction"
                     if args.scheme == "flypy"
                     else "double_pinyin_correction,double_pinyin_abbreviation")
    skip = set(filter(None, args.skip.split(",")))
    nine_key = args.layout == "t9"
    items = [i for i in (args.items.split(",") if args.items else
                         [i for i in original if i.startswith("t9_") == nine_key]) if i and i not in skip]
    unknown = [i for i in items if i not in original]
    if unknown:
        raise SystemExit(f"unknown switch ids {unknown}; registered: {sorted(original)}")
    current = dict(original)
    off = {item: False for item in original}
    results = {}
    adb(args.serial, "shell", "setprop", "log.tag.CyIMELatency", "DEBUG")
    try:
        apply_config(args.serial, off, current)
        results["baseline"] = measure(args, keys, "baseline", args.out)
        for item in items:
            apply_config(args.serial, {**off, item: True}, current)
            results[item] = measure(args, keys, item, args.out)
        apply_config(args.serial, off, current)
        results["baseline_end"] = measure(args, keys, "baseline_end", args.out)
        combos = []
        if not args.no_combos:
            before = sorted(i for i, on in original.items() if on and i in items)
            if len(before) > 1:
                combos.append(("current", before))
            if len(items) > 1 and sorted(items) != before:
                combos.append(("all", sorted(items)))
        for name, on in combos:
            apply_config(args.serial, {**off, **{i: True for i in on}}, current)
            results["combo_" + name] = measure(args, keys, "combo_" + name, args.out)
    finally:
        # Put every switch back as it was (also rewrites the schema patches), then the guarded runner restores
        # the rest of the settings and checks them.
        try:
            apply_config(args.serial, original, current)
            dev_op(args.serial, "perf_sync")
        finally:
            adb(args.serial, "shell", "setprop", "log.tag.CyIMELatency", "''", check=False)
            with open(os.path.join(args.out, "raw.json"), "w", encoding="utf-8") as out:
                json.dump(results, out, ensure_ascii=False, indent=2)

    base = (results["baseline"]["keyMs"] + results["baseline_end"]["keyMs"]) / 2
    base_mem = [m for m in (results["baseline"]["memoryMb"], results["baseline_end"]["memoryMb"]) if m is not None]
    base_mb = sum(base_mem) / len(base_mem) if base_mem else None
    noise = max(0.5, abs(results["baseline"]["keyMs"] - results["baseline_end"]["keyMs"]))

    def delta(r):
        result = {"keyMs": round(max(0.0, r["keyMs"] - base), 2),
                  "memoryMb": None if base_mb is None or r["memoryMb"] is None else round(max(0.0, r["memoryMb"] - base_mb), 1)}
        if args.merge:
            # Measured in its own session (e.g. on the nine-key keyboard): percentages use this baseline.
            result["baselineKeyMs"] = round(base, 2)
        return result

    model = adb(args.serial, "shell", "getprop", "ro.product.model")
    version = re.search(r"versionName=(\S+)", adb(args.serial, "shell", "dumpsys", "package", PACKAGE, check=False))
    entry = {
        "model": model, "name": args.name, "tier": args.tier, "measuredAt": time.strftime("%Y-%m-%d"),
        "appVersion": version.group(1) if version else "", "layout": args.layout_name,
        "baseline": {"keyMs": round(base, 2), "memoryMb": None if base_mb is None else round(base_mb, 1)},
        "noiseMs": round(noise, 2),
        "items": {item: delta(results[item]) for item in items},
        # Merged combos keep their own name (t9_all next to the 26-key all).
        "combos": [{"name": (args.layout + "_" + name) if args.merge else name, "on": on, **delta(results["combo_" + name])}
                   for name, on in combos],
    }
    with open(os.path.join(args.out, "device-entry.json"), "w", encoding="utf-8") as out:
        json.dump(entry, out, ensure_ascii=False, indent=2)
    merge_table(args.serial, entry, args.merge)
    print(f"baseline {base:.1f} ms/key, noise {noise:.1f} ms; pushed {REMOTE_TABLE}; entry in {args.out}/device-entry.json")


if __name__ == "__main__":
    main()
