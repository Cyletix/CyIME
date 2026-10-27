"""Runs an automated typing/test command on a device so it leaves the device exactly as it found it.

Before: snapshot CyIME's settings (无痕, 全角, scheme, layout and every other kime_settings key), custom layouts,
Rime saved options, CyLM learned layer and the user dictionary; then turn 无痕 on.
After (also when the command fails or is interrupted): put all of it back, restart the IME process so nothing
in memory writes the old state back, reselect the IME, and check everything matches the snapshot.
Any difference fails loudly (exit code 3) and lists what differs.

Needs a "装到我的设备" build (dev tools on). Usage:
  python -I scripts/guarded-device-run.py --serial R52T800SKWZ -- python tools/demo_typer/demo_typer.py ...
"""
import argparse
import os
import re
import subprocess
import sys
import time

PACKAGE = "com.cyletix.cyime"
RECEIVER = PACKAGE + "/com.kingzcheung.xime.service.DevLearningReceiver"
ACTION = PACKAGE + ".DEV_LEARNING"


def adb(serial, *args, check=True):
    env = dict(os.environ, MSYS_NO_PATHCONV="1")
    result = subprocess.run(["adb", "-s", serial, *args], capture_output=True, text=True,
                            encoding="utf-8", errors="replace", env=env)
    if check and result.returncode != 0:
        raise SystemExit(f"adb {' '.join(args)} failed: {result.stderr.strip()}")
    return result.stdout.strip()


def dev_op(serial, op, *extra):
    for _ in range(20):  # "busy" while the IME deploys (after an install that takes minutes)
        out = adb(serial, "shell", "am", "broadcast", "-n", RECEIVER, "-a", ACTION, "--es", "op", op, *extra)
        match = re.search(r'data="(.*)"', out, re.S)
        if not match:
            raise SystemExit(f"{op}: no answer from CyIME (dev tools missing?): {out}")
        if not match.group(1).startswith("busy"):
            return match.group(1)
        time.sleep(10)
    raise SystemExit(f"{op}: CyIME stayed busy")


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--serial", required=True)
    parser.add_argument("command", nargs=argparse.REMAINDER)
    args = parser.parse_args()
    command = args.command[1:] if args.command[:1] == ["--"] else args.command
    if not command:
        parser.error("give the command to run after --")

    ime = adb(args.serial, "shell", "settings", "get", "secure", "default_input_method")
    answer = dev_op(args.serial, "snapshot")
    print(answer)
    if not answer.startswith("snapshot:"):
        raise SystemExit("snapshot failed, nothing was run")
    print(dev_op(args.serial, "incognito", "--ez", "on", "true"))

    code = 1
    try:
        code = subprocess.call(command)
    finally:
        restored = dev_op(args.serial, "restore")
        print(restored)
        # Settings and the CyLM layer live in memory too; a fresh process reads the restored files.
        adb(args.serial, "shell", "am", "force-stop", PACKAGE)
        if ime.startswith(PACKAGE):
            adb(args.serial, "shell", "ime", "set", ime)
        verified = dev_op(args.serial, "verify")
        for _ in range(15):  # the restarted IME needs a moment before its engine can export
            if "export failed" not in verified and "failed:" not in verified:
                break
            time.sleep(2)
            verified = dev_op(args.serial, "verify")
        print("verify after restart:", verified)
        if verified != "OK" or not restored.endswith("OK"):
            print("!!! DEVICE NOT RESTORED: " + verified, file=sys.stderr)
            sys.exit(3)
    sys.exit(code)


if __name__ == "__main__":
    main()
