"""Type words on the visible CyIME keyboard at real typing speed for the key-latency probe.

Usage:
  python tools/ime_lab/key_latency_drive.py --serial SERIAL --keys keys.json \
      --words "nihao,zhongguorenmin" --interval 60 --hold 20 --rounds 5

Replays one monkey script, so all touches come from a single process with fixed spacing
(shell cannot write /dev/input on the Samsung tablet, and `adb shell input` costs ~250 ms
per tap). keys.json maps each key label to its screen centre [x, y]; "delete" must be
present: every word ends with a swipe down on delete (one-key clear). The real per-key
interval is in the probe's `at=` values. Check that CyIME is focused on a test field
before running; this script does not look at the screen.
"""
import argparse
import json
import os
import subprocess
import tempfile

REMOTE = "/data/local/tmp/cyime-latency.txt"


def pointer(down_ms, at_ms, action, x, y):
    # Monkey rebases these recorded times to now (pacing still comes from UserWait); a zero
    # down time would make CyIME discard the touch as stale.
    return f"DispatchPointer({down_ms},{at_ms},{action},{x},{y},1.0,0.05,0,1.0,1.0,0,0)"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--serial", required=True)
    parser.add_argument("--keys", required=True)
    parser.add_argument("--words", required=True, help="comma-separated key sequences")
    parser.add_argument("--interval", type=int, default=60, help="ms from one key down to the next")
    parser.add_argument("--hold", type=int, default=20, help="ms between down and up")
    parser.add_argument("--rounds", type=int, default=1)
    args = parser.parse_args()
    with open(args.keys, encoding="utf-8") as source:
        keys = json.load(source)

    events = []
    taps = 0
    now = 1000
    for _ in range(args.rounds):
        for word in args.words.split(","):
            for ch in word:
                x, y = keys[ch]
                events += [pointer(now, now, 0, x, y), f"UserWait({args.hold})", pointer(now, now + args.hold, 1, x, y),
                           f"UserWait({max(0, args.interval - args.hold)})"]
                now += args.interval
                taps += 1
            x, y = keys["delete"]
            start = now
            events.append(pointer(start, now, 0, x, y))
            for step in range(1, 9):
                now += 20
                events += ["UserWait(20)", pointer(start, now, 2, x, y + step * 22)]
            now += 20
            events += ["UserWait(20)", pointer(start, now, 1, x, y + 176), "UserWait(150)"]
            now += 150
    script = "type= raw events\ncount= {}\nspeed= 1.0\nstart data >>\n{}\n".format(len(events), "\n".join(events))
    with tempfile.TemporaryDirectory() as local:
        path = os.path.join(local, "script.txt")
        with open(path, "w", encoding="utf-8", newline="\n") as out:
            out.write(script)
        subprocess.run(["adb", "-s", args.serial, "push", "-q", path, REMOTE], check=True)
    subprocess.run(["adb", "-s", args.serial, "shell", f"monkey -f {REMOTE} 1 >/dev/null; rm -f {REMOTE}"], check=True)
    print(f"keys={taps}")


if __name__ == "__main__":
    main()
