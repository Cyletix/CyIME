"""Measure CyIME's CPU benchmark (app/src/main/jni/cpu_bench.cc) on a real device, per CPU cluster.

The 性能与省电 page's 「自动」 tier now comes from this benchmark instead of RAM/cores/GHz (PerformanceTier.kt).
Its tier lines (PerformanceTiers.cpuThresholds) must come from real devices, not from guesses. One flagship
already holds three kinds of cores, so pinning the same benchmark to each cluster gives three data points per
device: prime/big cores (flagship class), middle cores (upper mid-range phones), little cores (A5x class, what
budget phones run on). This script runs exactly what the app runs: the same libcyime_cpubench.so from the
installed APK, through app_process with the same Kotlin entry point (CpuBenchmark.main), pinned with taskset.

It changes nothing on the device: no setting, no IME, no tap. It copies the .so to /data/local/tmp/cyime-cpubench/
(shell may not map files under /data/app) and deletes that folder at the end. If the screen is off it wakes the
device and puts it back to sleep afterwards (AGENTS.md allows this; it only reads, nothing is unlocked or tapped).

Before any adb use read <notes>/Project/Agent状态同步.md and register the device; don't run while
someone is using it. Let the device cool down first (it refuses while the thermal status is above "none" or power
saving is on, because both lower the clocks).

Usage:
  python -I tools/ime_lab/cpu_bench_calibrate.py --serial TABLET_SERIAL --out D:/CyIME-Data/eval/cpu-bench-20261104
Writes <out>/<serial>.json and prints a table (median of --rounds runs; each run is itself a median of 5 passes).
Also prints what the app has stored (dev op cpu_bench_state) when a 装到我的设备 build has measured already.
"""
import argparse
import json
import os
import re
import statistics
import subprocess
import sys
import time

PACKAGE = "com.cyletix.cyime"
MAIN = "com.kingzcheung.xime.runtime.adaptation.CpuBenchmark"
LIB = "libcyime_cpubench.so"
TMP = "/data/local/tmp/cyime-cpubench"
RECEIVER = PACKAGE + "/com.kingzcheung.xime.service.DevLearningReceiver"
ACTION = PACKAGE + ".DEV_LEARNING"
ABI_DIRS = {"arm64-v8a": "arm64", "armeabi-v7a": "arm", "x86_64": "x86_64", "x86": "x86"}


def adb(serial, *args, check=True):
    env = dict(os.environ, MSYS_NO_PATHCONV="1")
    result = subprocess.run(["adb", "-s", serial, *args], capture_output=True, text=True,
                            encoding="utf-8", errors="replace", env=env)
    if check and result.returncode != 0:
        raise SystemExit(f"adb {' '.join(args)} failed: {result.stderr.strip() or result.stdout.strip()}")
    return result.stdout.strip()


def sh(serial, command, check=True):
    return adb(serial, "shell", command, check=check)


def clusters(serial):
    """[(policy, [cpus], max_khz)] from cpufreq policies, slowest first."""
    found = []
    for policy in sh(serial, "ls /sys/devices/system/cpu/cpufreq").split():
        if not re.fullmatch(r"policy\d+", policy):
            continue
        base = f"/sys/devices/system/cpu/cpufreq/{policy}"
        cpus = parse_cpus(sh(serial, f"cat {base}/related_cpus"))
        khz = int(sh(serial, f"cat {base}/cpuinfo_max_freq") or 0)
        found.append((policy, cpus, khz))
    return sorted(found, key=lambda c: (c[2], c[1]))


def parse_cpus(text):
    cpus = []
    for part in text.replace(",", " ").split():
        if "-" in part:
            a, b = part.split("-")
            cpus.extend(range(int(a), int(b) + 1))
        elif part.isdigit():
            cpus.append(int(part))
    return sorted(set(cpus))


def mask(cpus):
    return format(sum(1 << c for c in cpus), "x")


def conditions(serial):
    thermal = re.search(r"Thermal Status:\s*(\d+)", sh(serial, "dumpsys thermalservice", check=False))
    return {
        "thermal_status": int(thermal.group(1)) if thermal else None,
        "low_power": sh(serial, "settings get global low_power", check=False) == "1",
        "battery": sh(serial, "dumpsys battery | grep -E 'level|status|temperature'", check=False).split("\n"),
        "awake": "mWakefulness=Awake" in sh(serial, "dumpsys power | grep mWakefulness", check=False),
    }


def run_once(serial, apk, lib, cpus):
    prefix = f"taskset {mask(cpus)} " if cpus else ""
    # Samsung may withdraw a core between enumeration and the next sample.
    out = sh(serial, f"CLASSPATH={apk} {prefix}app_process /system/bin {MAIN} {lib} 2>&1", check=False)
    if cpus and "taskset: failed to set" in out and "Invalid argument" in out:
        return None
    line = next((l for l in out.splitlines() if l.startswith("{")), None)
    if line is None:
        raise SystemExit(f"benchmark printed no result:\n{out}")
    result = json.loads(line)
    if "error" in result:
        raise SystemExit(f"benchmark failed: {result}")
    if cpus:
        seen = set(parse_cpus(result["single_cpus"])) | set(parse_cpus(result["dual_cpus"]))
        result["pin_ok"] = bool(result["single_cpus"] and result["dual_cpus"]) and seen <= set(cpus)
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--out", required=True, help="folder; writes <serial>.json")
    parser.add_argument("--rounds", type=int, default=3)
    parser.add_argument("--cooldown", type=float, default=8.0, help="seconds between runs")
    parser.add_argument("--allow-warm", action="store_true", help="run even if thermal status > 0")
    args = parser.parse_args()

    before = conditions(args.serial)
    if before["low_power"]:
        raise SystemExit("power saving is on: clocks are capped, the numbers would be too low")
    if (before["thermal_status"] or 0) > 0 and not args.allow_warm:
        raise SystemExit(f"thermal status {before['thermal_status']}: let the device cool down first")
    woke = False
    if not before["awake"]:
        sh(args.serial, "input keyevent KEYCODE_WAKEUP")
        woke = True
        time.sleep(2)

    try:
        apk = sh(args.serial, f"pm path {PACKAGE}").splitlines()[0].removeprefix("package:").strip()
        abi = sh(args.serial, "getprop ro.product.cpu.abi")
        installed_lib = f"{os.path.dirname(apk)}/lib/{ABI_DIRS.get(abi, abi)}/{LIB}"
        if sh(args.serial, f"ls {installed_lib}", check=False) != installed_lib:
            raise SystemExit(f"{installed_lib} missing: install a build that contains the CPU benchmark first")
        sh(args.serial, f"mkdir -p {TMP} && cp {installed_lib} {TMP}/{LIB} && chmod 755 {TMP}/{LIB}")
        lib = f"{TMP}/{LIB}"

        groups = clusters(args.serial)
        targets = [("all", [])]
        for policy, cpus, khz in groups:
            # A device can list online cores that its shell process cannot use.
            status = sh(args.serial, f"taskset {mask(cpus)} cat /proc/self/status", check=False)
            match = re.search(r"^Cpus_allowed_list:\s*([0-9,-]+)", status, re.M)
            if not match:
                print(f"skipping unavailable cluster {policy} (CPUs {cpus})", file=sys.stderr)
                continue
            allowed = parse_cpus(match.group(1))
            targets.append((f"{policy} ({len(allowed)}x {khz / 1e6:.2f} GHz)", allowed))
        runs = {name: [] for name, _ in targets}
        for round_index in range(args.rounds):
            for name, cpus in targets:
                sample = run_once(args.serial, apk, lib, cpus)
                if sample is None:
                    print(f"{name} unavailable in round {round_index + 1}", file=sys.stderr)
                    continue
                runs[name].append(sample)
                time.sleep(args.cooldown)
            print(f"round {round_index + 1}/{args.rounds} done", file=sys.stderr)
        after = conditions(args.serial)
    finally:
        sh(args.serial, f"rm -rf {TMP}", check=False)
        if woke:
            sh(args.serial, "input keyevent KEYCODE_SLEEP", check=False)

    app_state = None
    state = sh(args.serial, f"am broadcast -n {RECEIVER} -a {ACTION} --es op cpu_bench_state", check=False)
    match = re.search(r'data="(.*)"', state, re.S)
    if match:
        app_state = match.group(1)

    summary = {}
    for name, cpus in targets:
        results = runs[name]
        if not results:
            continue
        summary[name] = {
            "cpus": cpus,
            "rounds": len(results),
            "single_ms": round(statistics.median(r["single_ns"] for r in results) / 1e6, 2),
            "dual_ms": round(statistics.median(r["dual_ns"] for r in results) / 1e6, 2),
            "single_spread_ms": [round(min(r["single_ns"] for r in results) / 1e6, 2),
                                 round(max(r["single_ns"] for r in results) / 1e6, 2)],
            "dual_spread_ms": [round(min(r["dual_ns"] for r in results) / 1e6, 2),
                               round(max(r["dual_ns"] for r in results) / 1e6, 2)],
            "pin_ok": all(r.get("pin_ok", True) for r in results),
            "seen_cpus": sorted({r["single_cpus"] for r in results} | {r["dual_cpus"] for r in results}),
        }
    report = {
        "serial": args.serial,
        "model": sh(args.serial, "getprop ro.product.model"),
        "soc": " ".join(filter(None, [sh(args.serial, "getprop ro.soc.manufacturer", check=False),
                                      sh(args.serial, "getprop ro.soc.model", check=False)])),
        "platform": sh(args.serial, "getprop ro.board.platform", check=False),
        "abi": abi,
        "mem_total_kb": int(re.search(r"MemTotal:\s*(\d+)", sh(args.serial, "cat /proc/meminfo")).group(1)),
        "benchmark_version": runs["all"][0]["version"],
        "conditions_before": before,
        "conditions_after": after,
        "summary": summary,
        "runs": runs,
        "app_state": app_state,
        "measured_at": time.strftime("%Y-%m-%dT%H:%M:%S%z"),
    }
    os.makedirs(args.out, exist_ok=True)
    path = os.path.join(args.out, f"{args.serial}.json")
    with open(path, "w", encoding="utf-8") as f:
        json.dump(report, f, ensure_ascii=False, indent=2)

    print(f"{report['model']} {report['soc']} ({report['platform']}), {report['mem_total_kb'] / 1048576:.1f} GiB")
    print(f"{'cores':34} {'single ms':>10} {'dual ms':>10}  pinned")
    for name, row in summary.items():
        print(f"{name:34} {row['single_ms']:>10} {row['dual_ms']:>10}  {'ok' if row['pin_ok'] else 'NO ' + str(row['seen_cpus'])}")
    if app_state:
        print("app:", app_state)
    print("thermal before/after:", before["thermal_status"], after["thermal_status"])
    print("wrote", path)


if __name__ == "__main__":
    main()
