"""How much CPU, frames, memory (and, unplugged, battery current) CyIME costs in the states users are in.

perf_cost_bench.py measures key-to-frame latency per switch. Latency is not energy: a key that waits 16 ms for
vsync costs nothing, a background animation that never delays a key costs battery all the time, and the glass blur
is drawn by SurfaceFlinger, which battery pages charge to the system, not to CyIME. This script measures what the
battery actually pays for, per scenario:

  type            keyboard up, the same pinyin words as perf_cost_bench at a fixed pace (cleared, nothing committed)
  idle_shown      keyboard up, nobody touches it (should be ~0 CPU and ~0 frames)
  hidden_focused  keyboard hidden with the text field still focused (background wake-ups only)
  screen_off      screen off for --screen-off seconds (needs --screen-off > 0; wakes and unlocks the screen after)

For each scenario:
  - CPU ms of every CyIME process (main, :asr, :inference), from /proc/<pid>/stat, plus the uid total from
    `dumpsys batterystats --checkin` (cumulative, so a process that died mid-run is still counted; no --reset,
    the device's own battery history is left alone)
  - SurfaceFlinger CPU ms (where the window blur and extra composition go)
  - frames CyIME rendered and janky frames (`dumpsys gfxinfo`)
  - PSS, swapped PSS and RSS of every CyIME process at the end (memory pages count the swapped part separately;
    a process "using 150 MB" may have 125 MB of it in zram)
  - with --current and the device NOT charging (adb over Wi-Fi): median battery current in mA, sampled each second.
    That is the only number here that is real energy; the rest are its causes. On USB it is skipped.
Typing is reported per 100 keys, idle states per minute, so runs of different length compare.

--other-ime runs the typing scenario once more with another keyboard (Samsung Keyboard, Gboard...) on the same
field and words, so "CyIME uses X" can be said against a reference. That keyboard needs its own key coordinates
(--other-layouts, demo_typer format). CyIME is selected again at the end.

Run it under the guarded runner with a 装到我的设备 build, after reading <notes>/Project/Agent状态同步.md:
  python -I scripts/guarded-device-run.py --serial TABLET_SERIAL -- \\
    python tools/ime_lab/energy_bench.py --serial TABLET_SERIAL \\
      --layouts tools/demo_typer/layouts/sm-x800.json --layout cn26 \\
      --host-cmd "am start -n md.obsidian/.MainActivity" --host-window md.obsidian \\
      --out D:/CyIME-Data/eval/energy-<date>
Read-only on the app: it does not change any CyIME setting. Run the same command before and after a change.
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
import perf_cost_bench as pcb  # noqa: E402  (key maps, touch scripts, guarded adb helpers)

PACKAGE = pcb.PACKAGE
# Whose processes are counted: CyIME, or the --other-ime keyboard while that one is measured.
TARGET = {"package": PACKAGE}
CLK_TCK = 100  # Android kernels: USER_HZ 100
CURRENT_NODE = "/sys/class/power_supply/battery/current_now"


def adb(serial, *args, check=True):
    return pcb.adb(serial, *args, check=check)


def uid_of(serial):
    out = adb(serial, "shell", "cmd", "package", "list", "packages", "-U", PACKAGE)
    match = re.search(rf"package:{re.escape(PACKAGE)} uid:(\d+)", out)
    if not match:
        raise SystemExit(f"{PACKAGE} not installed")
    return int(match.group(1))


def processes(serial):
    """{pid: name} of every process of the keyboard under test (CyIME: main, :asr, :inference)."""
    out = adb(serial, "shell", "ps", "-A", "-o", "PID,NAME", check=False)
    found = {}
    for line in out.splitlines()[1:]:
        parts = line.split()
        package = TARGET["package"]
        if len(parts) == 2 and (parts[1] == package or parts[1].startswith(package + ":")):
            found[int(parts[0])] = parts[1]
    return found


def proc_cpu_ms(serial, pids):
    """{pid: utime+stime ms}; a pid that is gone is left out."""
    if not pids:
        return {}
    cmd = "; ".join(f"echo {pid} $(cat /proc/{pid}/stat 2>/dev/null)" for pid in pids)
    result = {}
    for line in adb(serial, "shell", cmd, check=False).splitlines():
        pid, _, stat = line.partition(" ")
        # comm may hold spaces: fields after the closing parenthesis, utime/stime are the 12th/13th of those
        tail = stat.rpartition(")")[2].split()
        if len(tail) > 12:
            result[int(pid)] = (int(tail[11]) + int(tail[12])) * 1000 // CLK_TCK
    return result


def surfaceflinger_ms(serial):
    pid = adb(serial, "shell", "pidof", "surfaceflinger", check=False).split()
    return next(iter(proc_cpu_ms(serial, [int(pid[0])]).values()), None) if pid else None


def batterystats_cpu_ms(serial, uid):
    """Cumulative user+system CPU ms of the uid since the last full charge (checkin 'cpu' line)."""
    out = adb(serial, "shell", "dumpsys", "batterystats", "--checkin", check=False)
    match = re.search(rf"^9,{uid},l,cpu,(\d+),(\d+)", out, re.M)
    return int(match.group(1)) + int(match.group(2)) if match else None


def gfx_reset(serial):
    adb(serial, "shell", "dumpsys", "gfxinfo", TARGET["package"], "reset", check=False)


def gfx_read(serial):
    out = adb(serial, "shell", "dumpsys", "gfxinfo", TARGET["package"], check=False)
    def num(pattern):
        match = re.search(pattern, out)
        return int(match.group(1)) if match else None
    return {"frames": num(r"Total frames rendered:\s*(\d+)"), "janky": num(r"Janky frames:\s*(\d+)"),
            "p90Ms": num(r"90th percentile:\s*(\d+)ms")}


def memory(serial):
    """{process: {pssMb, swapPssMb, rssMb}} from dumpsys meminfo per pid."""
    result = {}
    for pid, name in processes(serial).items():
        out = adb(serial, "shell", "dumpsys", "meminfo", str(pid), check=False)
        def mb(pattern):
            match = re.search(pattern, out)
            return round(int(match.group(1)) / 1024, 1) if match else None
        result[name] = {"pssMb": mb(r"TOTAL PSS:\s+(\d+)"), "swapPssMb": mb(r"TOTAL SWAP PSS:\s+(\d+)"),
                        "rssMb": mb(r"TOTAL RSS:\s+(\d+)")}
    return result


def unplugged(serial):
    out = adb(serial, "shell", "dumpsys", "battery", check=False)
    return all(f"{kind} powered: false" in out for kind in ("AC", "USB", "Wireless"))


class CurrentSampler:
    """Samples the battery current node once a second in a background adb shell; median |mA| at stop()."""

    def __init__(self, serial, seconds):
        self.proc = subprocess.Popen(
            ["adb", "-s", serial, "shell", f"i=0; while [ $i -lt {int(seconds) + 5} ]; do cat {CURRENT_NODE}; sleep 1; i=$((i+1)); done"],
            stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, text=True)

    def stop(self):
        self.proc.terminate()
        out, _ = self.proc.communicate()
        values = [abs(int(v)) for v in out.split() if re.fullmatch(r"-?\d+", v)]
        if not values:
            return None
        median = statistics.median(values)
        # Most devices report µA, some mA: anything above 20 A must be µA.
        return round(median / 1000 if median > 20000 else median, 1)


def keyboard_shown(serial):
    return "mInputShown=true" in adb(serial, "shell", "dumpsys", "input_method", check=False)


def bring_up_field(args, ime_package):
    adb(args.serial, "shell", args.host_cmd)
    for _ in range(20):
        time.sleep(2)
        focus = adb(args.serial, "shell", "dumpsys", "window", check=False)
        if args.host_window in focus and keyboard_shown(args.serial) and \
                ime_package in adb(args.serial, "shell", "settings", "get", "secure", "default_input_method"):
            return
    raise SystemExit("keyboard did not come up in the host field (tap the field once and rerun)")


def play(serial, text, host_window, ime_package):
    """Same as perf_cost_bench.play, but the guard accepts the keyboard under test (CyIME or --other-ime)."""
    with tempfile.TemporaryDirectory() as local:
        path = os.path.join(local, "script.txt")
        with open(path, "w", encoding="utf-8", newline="\n") as out:
            out.write(text)
        adb(serial, "push", "-q", path, pcb.REMOTE_SCRIPT)
    guard = (f"dumpsys window | grep mCurrentFocus | grep -q {host_window} && "
             f"settings get secure default_input_method | grep -q {ime_package} && "
             "dumpsys input_method | grep -q mInputShown=true || { echo NOGUARD; exit 0; }; ")
    out = adb(serial, "shell", f"{guard}CLASSPATH={pcb.INJECT_DEX} app_process / Inject {pcb.REMOTE_SCRIPT} >/dev/null; "
                               f"rm -f {pcb.REMOTE_SCRIPT}")
    if "NOGUARD" in out:
        raise SystemExit(f"stopped: {host_window} is not in front with the keyboard under test up")


def run_scenario(args, uid, name, action, seconds_hint, keys_typed=0):
    """Wraps one scenario with before/after counters; action() does the work."""
    pids = processes(args.serial)
    cpu0, sf0, bs0 = proc_cpu_ms(args.serial, pids), surfaceflinger_ms(args.serial), batterystats_cpu_ms(args.serial, uid)
    gfx_reset(args.serial)
    sampler = CurrentSampler(args.serial, seconds_hint) if args.current_ok else None
    started = time.time()
    action()
    elapsed = time.time() - started
    current = sampler.stop() if sampler else None
    gfx = gfx_read(args.serial)
    cpu1, sf1, bs1 = proc_cpu_ms(args.serial, pids), surfaceflinger_ms(args.serial), batterystats_cpu_ms(args.serial, uid)
    per_process = {pids[p]: cpu1[p] - cpu0[p] for p in cpu1 if p in cpu0}
    result = {
        "scenario": name, "seconds": round(elapsed, 1), "keys": keys_typed,
        "cpuMs": per_process, "cpuMsTotal": sum(per_process.values()),
        "uidCpuMs": (bs1 - bs0) if bs0 is not None and bs1 is not None else None,
        "surfaceflingerMs": (sf1 - sf0) if sf0 is not None and sf1 is not None else None,
        **gfx, "currentMa": current, "memory": memory(args.serial),
        "restarted": sorted(set(processes(args.serial).values()) ^ set(pids.values())),
    }
    total = result["uidCpuMs"] if result["uidCpuMs"] is not None else result["cpuMsTotal"]
    if keys_typed:
        result["cpuMsPer100Keys"] = round(total * 100 / keys_typed, 1)
    else:
        result["cpuMsPerMinute"] = round(total * 60 / max(elapsed, 1), 1)
    print(json.dumps(result, ensure_ascii=False), flush=True)
    return result


def typing_action(args, keys, ime_package):
    words = pcb.key_sequences(args.words, args.scheme)
    text = pcb.script(keys, words, args.interval, args.hold, args.rounds)
    count = sum(len(w) for w in words) * args.rounds
    return (lambda: play(args.serial, text, args.host_window, ime_package)), count, \
        count * args.interval / 1000 + len(words) * args.rounds * 0.45


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--serial", required=True)
    parser.add_argument("--layouts", required=True)
    parser.add_argument("--layout", default="cn26")
    parser.add_argument("--host-cmd", required=True)
    parser.add_argument("--host-window", required=True)
    parser.add_argument("--scenarios", default="type,idle_shown,hidden_focused")
    parser.add_argument("--words", default=pcb.WORDS)
    parser.add_argument("--scheme", default="pinyin", choices=["pinyin", "flypy"])
    parser.add_argument("--rounds", type=int, default=6, help="typing rounds (6 ≈ 900 keys)")
    parser.add_argument("--interval", type=int, default=160, help="ms between key downs (about 6 keys/s)")
    parser.add_argument("--hold", type=int, default=40)
    parser.add_argument("--idle", type=int, default=60, help="seconds for idle_shown and hidden_focused")
    parser.add_argument("--screen-off", type=int, default=0, help="seconds for screen_off (0 = skip)")
    parser.add_argument("--current", action="store_true", help="sample battery current (only used when unplugged)")
    parser.add_argument("--other-ime", default="", help="IME id to compare on the typing scenario, e.g. "
                        "com.samsung.android.honeyboard/.service.HoneyBoardService")
    parser.add_argument("--other-layouts", default="")
    parser.add_argument("--other-layout", default="cn26")
    parser.add_argument("--cool", type=int, default=20)
    parser.add_argument("--dex", default="D:/CyIME-Data/demo-video/cyime-inject.dex")
    parser.add_argument("--out", required=True)
    args = parser.parse_args()
    os.makedirs(args.out, exist_ok=True)
    args.current_ok = args.current and unplugged(args.serial)
    if args.current and not args.current_ok:
        print("charging or on USB power: battery current skipped (use adb over Wi-Fi, unplugged)", file=sys.stderr)
    if pcb.guarded.current_ime(args.serial) != pcb.guarded.IME:
        raise SystemExit("CyIME is not the default keyboard")
    adb(args.serial, "push", "-q", args.dex, pcb.INJECT_DEX)
    uid = uid_of(args.serial)
    keys = pcb.keys_for(args.layouts, args.layout)
    results = []
    try:
        for name in filter(None, args.scenarios.split(",")):
            bring_up_field(args, PACKAGE)
            if name == "type":
                action, count, seconds = typing_action(args, keys, PACKAGE)
                results.append(run_scenario(args, uid, name, action, seconds, count))
            elif name == "idle_shown":
                results.append(run_scenario(args, uid, name, lambda: time.sleep(args.idle), args.idle))
            elif name == "hidden_focused":
                adb(args.serial, "shell", "input", "keyevent", "KEYCODE_BACK")
                time.sleep(2)
                if keyboard_shown(args.serial):
                    raise SystemExit("keyboard did not hide on BACK")
                results.append(run_scenario(args, uid, name, lambda: time.sleep(args.idle), args.idle))
            elif name == "screen_off" and args.screen_off > 0:
                def off():
                    adb(args.serial, "shell", "input", "keyevent", "KEYCODE_SLEEP")
                    time.sleep(args.screen_off)
                    adb(args.serial, "shell", "input", "keyevent", "KEYCODE_WAKEUP")
                    adb(args.serial, "shell", "wm", "dismiss-keyguard", check=False)
                results.append(run_scenario(args, uid, name, off, args.screen_off))
            else:
                raise SystemExit(f"unknown scenario {name}")
            time.sleep(args.cool)
        if args.other_ime:
            if not args.other_layouts:
                raise SystemExit("--other-ime needs --other-layouts (that keyboard's key coordinates)")
            other_package = args.other_ime.split("/")[0]
            adb(args.serial, "shell", "ime", "enable", args.other_ime, check=False)
            adb(args.serial, "shell", "ime", "set", args.other_ime)
            bring_up_field(args, other_package)
            other_keys = pcb.keys_for(args.other_layouts, args.other_layout)
            action, count, seconds = typing_action(args, other_keys, other_package)
            # The other keyboard's own CPU: count its processes instead of CyIME's.
            other_uid = re.search(r"uid:(\d+)", adb(args.serial, "shell", "cmd", "package", "list", "packages", "-U",
                                                     other_package))
            if not other_uid:
                raise SystemExit(f"{other_package} not installed")
            TARGET["package"] = other_package
            try:
                results.append(run_scenario(args, int(other_uid.group(1)), "type:" + other_package, action, seconds, count))
            finally:
                TARGET["package"] = PACKAGE
    finally:
        if pcb.guarded.current_ime(args.serial) != pcb.guarded.IME and not pcb.guarded.select_ime(args.serial):
            print("WARNING: CyIME is not the default keyboard again; set it by hand", file=sys.stderr)
        with open(os.path.join(args.out, "energy.json"), "w", encoding="utf-8") as out:
            json.dump({"package": PACKAGE, "args": {k: v for k, v in vars(args).items() if k != "dex"},
                       "results": results}, out, ensure_ascii=False, indent=2)


if __name__ == "__main__":
    main()
