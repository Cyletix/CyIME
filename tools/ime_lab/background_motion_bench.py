"""Measure what 动态背景 (keyboard background motion) costs while the keyboard sits open, on a real device.

The motion only costs while the keyboard is shown: the IME asks for at most 20 frames per second and each frame
moves one graphics layer (keys are not redrawn). So instead of per-keystroke latency (perf_cost_bench.py measures
that for the same switch id, background_motion) this script keeps the keyboard open and idle for --seconds with
the switch off, on, off, and reads for the IME process:
  - frames rendered per second and frame time percentiles (dumpsys gfxinfo, includes GPU percentiles on API 31+);
  - CPU time per second and per frame (utime + stime from /proc/<pid>/stat, all threads incl. RenderThread);
  - PSS (dumpsys meminfo).
Off is measured before and after on; their gap is the noise floor.

Prerequisites: the keyboard theme has an image background (我的图片 or a built-in image theme), the host app field
from --host-cmd gets focus and shows the keyboard by itself, a 装到我的设备 build (dev tools on, op=perf_switch).
Nothing is typed. The switch is put back as it was.

Before any adb use read <notes>/Project/Agent状态同步.md and register the device. Run it under the
guarded runner:
  python -I scripts/guarded-device-run.py --serial TABLET_SERIAL -- \
    python tools/ime_lab/background_motion_bench.py --serial TABLET_SERIAL \
      --host-cmd "am start -n md.obsidian/.MainActivity" --host-window md.obsidian \
      --out D:/CyIME-Data/diagnostics/background-motion-<date>
"""
import argparse
import json
import os
import re
import sys
import time

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import perf_cost_bench as bench  # noqa: E402

ITEM = "background_motion"
PACKAGE = bench.PACKAGE


def pid(serial):
    out = bench.adb(serial, "shell", "pidof", PACKAGE, check=False).split()
    if not out:
        raise SystemExit("CyIME process not running")
    return out[0]


def cpu_ticks(serial, process):
    stat = bench.adb(serial, "shell", "cat", f"/proc/{process}/stat")
    # Fields after the ")" that closes the command name: state is field 3, utime 14, stime 15.
    fields = stat.rsplit(")", 1)[1].split()
    return int(fields[11]) + int(fields[12])


def clock_ticks(serial):
    out = bench.adb(serial, "shell", "getconf", "CLK_TCK", check=False).strip()
    return int(out) if out.isdigit() else 100


def gfxinfo(serial):
    out = bench.adb(serial, "shell", "dumpsys", "gfxinfo", PACKAGE)
    result = {}
    total = re.search(r"Total frames rendered:\s*(\d+)", out)
    result["frames"] = int(total.group(1)) if total else None
    for name in ("50th", "90th", "95th", "99th"):
        match = re.search(rf"^\s*{name} percentile:\s*(\d+)ms", out, re.M)
        result[f"p{name[:2]}_ms"] = int(match.group(1)) if match else None
        gpu = re.search(rf"^\s*{name} gpu percentile:\s*(\d+)ms", out, re.M)
        result[f"gpu_p{name[:2]}_ms"] = int(gpu.group(1)) if gpu else None
    janky = re.search(r"Janky frames:\s*(\d+)", out)
    result["janky"] = int(janky.group(1)) if janky else None
    return result


def keyboard_up(serial, host_window):
    focus = bench.adb(serial, "shell", "dumpsys", "window", check=False)
    shown = bench.adb(serial, "shell", "dumpsys", "input_method", check=False)
    return host_window in "\n".join(l for l in focus.splitlines() if "mCurrentFocus" in l) and "mInputShown=true" in shown


def measure(args, on, ticks_per_second):
    bench.switch(args.serial, ITEM, on)
    bench.restart_ime(args)
    time.sleep(args.settle)
    if not keyboard_up(args.serial, args.host_window):
        raise SystemExit(f"stopped: {args.host_window} is not in front with the CyIME keyboard up")
    process = pid(args.serial)
    bench.adb(args.serial, "shell", "dumpsys", "gfxinfo", PACKAGE, "reset")
    start_ticks = cpu_ticks(args.serial, process)
    started = time.monotonic()
    time.sleep(args.seconds)
    elapsed = time.monotonic() - started
    end_ticks = cpu_ticks(args.serial, process)
    if not keyboard_up(args.serial, args.host_window):
        raise SystemExit("keyboard went away during the measurement (someone using the device?)")
    frames = gfxinfo(args.serial)
    cpu_ms = (end_ticks - start_ticks) * 1000 / ticks_per_second
    row = {
        "motion": on,
        "seconds": round(elapsed, 1),
        "fps": round(frames["frames"] / elapsed, 1) if frames["frames"] is not None else None,
        "cpu_ms_per_s": round(cpu_ms / elapsed, 1),
        "cpu_ms_per_frame": round(cpu_ms / frames["frames"], 2) if frames["frames"] else None,
        "pss_mb": bench.pss_mb(args.serial),
        **frames,
    }
    print(json.dumps(row, ensure_ascii=False), flush=True)
    return row


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--host-cmd", required=True, help="adb shell command that opens the host field")
    parser.add_argument("--host-window", required=True, help="text in mCurrentFocus of the host window")
    parser.add_argument("--seconds", type=int, default=30, help="idle seconds per measurement")
    parser.add_argument("--settle", type=int, default=5, help="seconds to wait after the keyboard comes up")
    parser.add_argument("--out", required=True, help="directory for result.json")
    args = parser.parse_args()

    before = bench.state(args.serial)
    if ITEM not in before:
        raise SystemExit("this build has no background_motion switch")
    ticks = clock_ticks(args.serial)
    rows = []
    try:
        for on in (False, True, False):
            rows.append(measure(args, on, ticks))
    finally:
        bench.switch(args.serial, ITEM, before[ITEM])
    off = [r for r in rows if not r["motion"]]
    on = next(r for r in rows if r["motion"])
    noise = abs(off[0]["cpu_ms_per_s"] - off[1]["cpu_ms_per_s"])
    summary = {
        "extra_fps": round(on["fps"] - sum(r["fps"] for r in off) / 2, 1) if on["fps"] is not None else None,
        "extra_cpu_ms_per_s": round(on["cpu_ms_per_s"] - sum(r["cpu_ms_per_s"] for r in off) / 2, 1),
        "noise_cpu_ms_per_s": round(noise, 1),
        "cpu_ms_per_motion_frame": on["cpu_ms_per_frame"],
        "extra_pss_mb": round(on["pss_mb"] - sum(r["pss_mb"] for r in off) / 2, 1)
            if on["pss_mb"] is not None and all(r["pss_mb"] is not None for r in off) else None,
    }
    os.makedirs(args.out, exist_ok=True)
    with open(os.path.join(args.out, "result.json"), "w", encoding="utf-8") as sink:
        json.dump({"rows": rows, "summary": summary}, sink, ensure_ascii=False, indent=2)
    print(json.dumps(summary, ensure_ascii=False))


if __name__ == "__main__":
    main()
