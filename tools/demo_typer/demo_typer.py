"""Type the CyIME demo note on a real device keyboard, for screen-recorded demo videos.

Every key goes through the visible CyIME keyboard: one monkey script per run of touch steps,
like tools/ime_lab/key_latency_drive.py (`adb shell input tap` costs ~250 ms a tap). Voice clips
play on the PC speaker between monkey runs; handwriting is the user's own ink, recorded once
with getevent and replayed point by point.

  python tools/demo_typer/demo_typer.py devices                       # touch/pen devices, rotation
  python tools/demo_typer/demo_typer.py screenshot cn26              # calibration picture
  python tools/demo_typer/demo_typer.py taps                         # print where you tap
  python tools/demo_typer/demo_typer.py record-ink formula1          # write once, saved on 3 s idle
  python tools/demo_typer/demo_typer.py tts                          # voice clips from the script
  python tools/demo_typer/demo_typer.py run [--segment NAME] [--dry]
  python tools/demo_typer/demo_typer.py check [--segment NAME]       # note file vs expected text
  python tools/demo_typer/demo_typer.py clear                        # empty the demo note file

Check that CyIME is focused in the demo note before `run`; this script does not look at the screen.
Recorded ink, clips and pictures live in --data (outside the repo).
"""
import argparse
import difflib
import json
import math
import os
import re
import subprocess
import sys
import tempfile
import threading
import time

import yaml

HERE = os.path.dirname(os.path.abspath(__file__))
REMOTE = "/data/local/tmp/cyime-demo.txt"
T9 = {c: d for d, letters in {"2": "abc", "3": "def", "4": "ghi", "5": "jkl", "6": "mno",
                              "7": "pqrs", "8": "tuv", "9": "wxyz"}.items() for c in letters}
# Kana nine-key: tap, left, up, right, down (app/.../JapaneseKanaInput.kt); the modifier key
# cycles voiced and small forms (kanaModifierCycles), so ご is こ + one modifier tap.
KANA_ROWS = ["あいうえお", "かきくけこ", "さしすせそ", "たちつてと", "なにぬねの", "はひふへほ",
             "まみむめも", "や（ゆ）よ", "らりるれろ", "わをんー　", "、。？！・"]
KANA_DIRS = ["tap", "left", "up", "right", "down"]
# Characters inside word tokens that name a key differently in the layout file.
ALIASES = {"'": "apostrophe", "-": "minus", "/": "slash", ".": "period", ",": "comma", ":": "colon", " ": "space"}
KANA_MODIFIED = {"ぁ": ("あ", 1), "ぃ": ("い", 1), "ぅ": ("う", 2), "ゔ": ("う", 1), "ぇ": ("え", 1), "ぉ": ("お", 1),
                 "っ": ("つ", 1), "ゃ": ("や", 1), "ゅ": ("ゆ", 1), "ょ": ("よ", 1), "ゎ": ("わ", 1), "ゕ": ("か", 2), "ゖ": ("け", 2)}
for plain, voiced in zip("かきくけこさしすせそたちつてとはひふへほ", "がぎぐげござじずぜぞだぢづでどばびぶべぼ"):
    KANA_MODIFIED[voiced] = (plain, 2 if plain == "つ" else 1)
for plain, half in zip("はひふへほ", "ぱぴぷぺぽ"):
    KANA_MODIFIED[half] = (plain, 2)


def adb(serial, *args, text=True, check=True):
    return subprocess.run(["adb", "-s", serial, *args], capture_output=True, text=text, check=check,
                          encoding="utf-8" if text else None).stdout


def rotation(serial):
    out = adb(serial, "shell", "dumpsys", "input")
    match = re.search(r"Viewport INTERNAL:.*?orientation=(\d)", out) or re.search(r"SurfaceOrientation: (\d)", out)
    return int(match.group(1)) if match else 0


class Pointer:
    """Collects monkey events; times are ms from script start."""

    def __init__(self):
        self.events, self.now, self.down = [], 1000, 0

    def wait(self, ms):
        ms = int(round(ms))
        if ms > 0:
            self.events.append(f"UserWait({ms})")
            self.now += ms

    def _emit(self, action, x, y, pressure=1.0):
        # A zero down time makes CyIME discard the touch as stale (see key_latency_drive.py).
        self.events.append(f"DispatchPointer({self.down},{self.now},{action},{x:.1f},{y:.1f},{pressure:.2f},0.05,0,1.0,1.0,0,0)")

    def press(self, x, y, pressure=1.0):
        self.down = self.now
        self._emit(0, x, y, pressure)

    def move(self, x, y, pressure=1.0):
        self._emit(2, x, y, pressure)

    def release(self, x, y):
        self._emit(1, x, y)

    def tap(self, x, y, hold, after):
        self.press(x, y)
        self.wait(hold)
        self.release(x, y)
        self.wait(after)

    def hold_and_tap(self, held, target, after):
        """Second finger taps `target` while the first holds `held`: monkey's two-pointer PinchZoom
        with both points standing still (down, pointer down, pointer up, up)."""
        (hx, hy), (tx, ty) = held, target
        self.events.append(f"PinchZoom({hx},{hy},{hx},{hy},{tx},{ty},{tx},{ty},1)")
        self.wait(after)

    def stroke(self, points, after):
        """points: [(x, y, ms, pressure)], ms from stroke start."""
        x, y, t0, p = points[0]
        self.press(x, y, p)
        last = t0
        for x, y, t, p in points[1:]:
            self.wait(t - last)
            last = t
            self.move(x, y, p)
        self.wait(8)
        self.release(x, y)
        self.wait(after)

    def script(self):
        return "type= raw events\ncount= {}\nspeed= 1.0\nstart data >>\n{}\n".format(len(self.events), "\n".join(self.events))


class Runner:
    def __init__(self, args, plan, layouts):
        self.args, self.plan, self.layouts = args, plan, layouts
        self.pace = {"interval": 40, "hold": 15, "commit": 120, "sentence": 400, "paragraph": 1200,
                     "ui": 500, "flick": 60, "pre_commit": 150, "after_enter": 350, "hold_long": 700, "glide_step": 70, **plan.get("pace", {})}
        if args.interval:
            self.pace["interval"] = args.interval
        self.layout_name = None
        self.played = []  # (seconds since time.monotonic() origin, clip) for the recording's audio track
        self.pointer = Pointer()
        self.taps = 0

    @property
    def keys(self):
        table = dict(self.layouts.get("common", {}))
        table.update(self.layouts["layouts"][self.layout_name])
        return table

    def key(self, name):
        spec = self.keys.get(name)
        if spec is None:
            raise SystemExit(f"layout {self.layout_name}: no key {name!r}")
        return spec

    def press_spec(self, spec, gesture=None, after=None):
        x, y = spec[0], spec[1]
        gesture = gesture or (spec[2] if len(spec) > 2 else "tap")
        after = self.pace["interval"] - self.pace["hold"] if after is None else after
        p = self.pointer
        if gesture == "tap":
            p.tap(x, y, self.pace["hold"], after)
        elif gesture == "hold":
            p.tap(x, y, self.pace["hold_long"], after)
        else:
            dx, dy = {"left": (-1, 0), "right": (1, 0), "up": (0, -1), "down": (0, 1)}[gesture]
            dist = spec[3] if len(spec) > 3 else self.pace["flick"]
            p.press(x, y)
            for step in range(1, 5):
                p.wait(12)
                p.move(x + dx * dist * step / 4, y + dy * dist * step / 4)
            p.wait(10)
            p.release(x + dx * dist, y + dy * dist)
            p.wait(after)
        self.taps += 1

    def letters(self, word):
        layout = self.layouts["layouts"][self.layout_name]
        for ch in word:
            if layout.get("_t9") and ch in T9:
                self.press_spec(self.key(T9[ch]))
            elif layout.get("_kana") and (ch in KANA_MODIFIED or any(ch in row for row in KANA_ROWS)):
                base, modifier_taps = KANA_MODIFIED.get(ch, (ch, 0))
                row = next(r for r in KANA_ROWS if base in r)
                self.press_spec(self.key(row[0]), KANA_DIRS[row.index(base)])
                for _ in range(modifier_taps):
                    self.press_spec(self.key("mod"))
            elif ch.isupper() and ch not in self.keys:
                self.press_spec(self.key("shift"))
                self.press_spec(self.key(ch.lower()))
            else:
                self.press_spec(self.key(ALIASES.get(ch, ch)))

    def slide(self, start, end, hold):
        p = self.pointer
        p.press(start[0], start[1])
        p.wait(hold)
        for step in range(1, 13):
            p.wait(20)
            p.move(start[0] + (end[0] - start[0]) * step / 12, start[1] + (end[1] - start[1]) * step / 12)
        p.wait(60)
        p.release(end[0], end[1])
        p.wait(self.pace["ui"])
        self.taps += 1

    def glide(self, word):
        points, t = [], 0
        centers = [self.key(ch)[:2] for ch in word]
        for (x0, y0), (x1, y1) in zip(centers, centers[1:]):
            steps = max(3, int(math.dist((x0, y0), (x1, y1)) / 40))
            for i in range(steps):
                points.append((x0 + (x1 - x0) * i / steps, y0 + (y1 - y0) * i / steps, t, 1.0))
                t += self.pace["glide_step"] / steps
        points.append((*centers[-1], t, 1.0))
        self.pointer.stroke(points, self.pace["commit"])

    def ink(self, name):
        with open(os.path.join(self.args.data, "ink", name + ".json"), encoding="utf-8") as source:
            ink = json.load(source)
        if ink["rotation"] % 2 != self.layouts["rotation"] % 2:
            raise SystemExit(f"ink {name} was recorded at rotation {ink['rotation']}")
        strokes = ink["strokes"]
        # Monkey rebases event times from the start of its script; deep into a long script the
        # first moves of a stroke land before their own down and get dropped, so ink gets its own run.
        self.flush()
        for index, stroke in enumerate(strokes):
            gap = strokes[index + 1][0][2] - stroke[-1][2] if index + 1 < len(strokes) else 0
            t0, kept = stroke[0][2], [stroke[0]]
            for point in stroke[1:-1]:  # monkey waits are coarse; one point per 8 ms is enough
                if point[2] - kept[-1][2] >= 8:
                    kept.append(point)
            kept += stroke[-1:] if len(stroke) > 1 else []
            self.pointer.stroke([(x, y, t - t0, p) for x, y, t, p in kept], max(30, gap))
        self.flush()

    def flush(self):
        if not self.pointer.events:
            return
        script = self.pointer.script()
        if self.args.dry:
            print(f"  monkey: {len(self.pointer.events)} events, {self.pointer.now - 1000} ms")
        else:
            with tempfile.TemporaryDirectory() as local:
                path = os.path.join(local, "script.txt")
                with open(path, "w", encoding="utf-8", newline="\n") as out:
                    out.write(script)
                adb(self.args.serial, "push", "-q", path, REMOTE)
            adb(self.args.serial, "shell", f"monkey -f {REMOTE} 1 >/dev/null; rm -f {REMOTE}")
        self.pointer = Pointer()

    def voice(self, name):
        mic = self.key("mic")
        clip = os.path.join(self.args.data, "tts", name + ".wav")
        if not os.path.exists(clip):
            raise SystemExit(f"missing {clip}; run `tts` first")
        self.pointer.tap(mic[0], mic[1], self.pace["hold"], 0)
        self.flush()
        voice = self.plan["voices"][name]
        time.sleep(voice.get("lead", 1200) / 1000)
        if self.args.dry:
            print(f"  play {clip}")
        else:
            self.played.append((time.monotonic(), clip))
            subprocess.run(["ffplay", "-nodisp", "-autoexit", "-loglevel", "error", clip], check=True)
        time.sleep(voice.get("tail", 1800) / 1000)
        self.pointer.tap(mic[0], mic[1], self.pace["hold"], self.pace["paragraph"])

    def token(self, token):
        if token.startswith("en:"):  # en:Word_or_two - a burst of English: language key, letters ("_" = space), back
            back = self.layout_name
            self.press_spec(self.key("lang"), after=self.pace["ui"])
            self.layout_name = "en26"
            self.letters(token[3:].replace("_", " "))
            self.press_spec(self.key("lang"), after=self.pace["ui"])
            self.layout_name = back
        elif token.startswith("layout="):
            self.layout_name = token[7:]
        elif token.startswith("wait:"):
            value = token[5:]
            self.pointer.wait(self.pace[value] if value in self.pace else int(value))
        elif token.startswith("@"):  # an interface tap at a human pace
            self.press_spec(self.key(token[1:]), after=self.pace["ui"])
        elif token.startswith("<") and token.endswith(">"):
            name, _, gesture = token[1:-1].partition(":")
            # A new line makes the editor restart input; give it a moment before the next word.
            self.press_spec(self.key(name), gesture or None, after=self.pace["after_enter"] if name == "enter" else None)
        elif token.startswith("drag:"):  # drag:a>b - press a, slide onto b, release there
            start, end = (self.key(name) for name in token[5:].split(">"))
            self.slide(start, end, 150)
        elif token.startswith("swipe:"):  # swipe:key:dx:dy:hold_ms - hold, then slide by (dx, dy)
            name, dx, dy, hold = token[6:].split(":")
            start = self.key(name)
            self.slide(start, (start[0] + int(dx), start[1] + int(dy)), int(hold))
        elif token.startswith("shiftpick:"):  # shiftpick:x:y - hold Shift and tap the candidate at (x, y)
            x, y = token[10:].split(":")
            self.pointer.hold_and_tap(self.key("shift")[:2], (int(x), int(y)), self.pace["commit"])
            self.taps += 2
        elif token.startswith("tap:"):  # tap:x:y - a one-off screen point, such as a later candidate
            x, y = token[4:].split(":")
            self.press_spec([int(x), int(y)], after=self.pace["commit"])
        elif token.startswith("glide:"):
            self.glide(token[6:])
        elif token.startswith("ink:"):
            self.ink(token[4:])
        elif token.startswith("voice:"):
            self.voice(token[6:])
        elif token.endswith("~"):
            self.letters(token[:-1])
        else:  # a word: its keys, then space takes the first candidate
            self.letters(token)
            # A space that lands before the engine has answered the last key is dropped; let it catch up.
            self.pointer.wait(self.pace["pre_commit"] + 8 * len(token))  # long phrases decode longer
            self.press_spec(self.key("space"), after=self.pace["commit"])

    def segment(self, segment):
        print(f"[{segment['name']}]")
        self.layout_name = segment["layout"]
        # The language key toggles, so a segment that starts on the wrong language inverts every
        # English burst; pick Chinese from the language menu first.
        if self.layout_name == "cn26" and "zh" in self.keys:
            self.token("<lang:hold>")
            self.token("@zh")
            self.token("wait:600")  # the first word after the menu otherwise loses its space
        for line in segment["do"]:
            for token in line.split():
                self.token(token)
            # One monkey run per line: stopping the PC side (or `stop`) ends the typing within a line.
            self.flush()
        self.pointer.wait(self.pace["paragraph"])
        self.flush()


def load(args):
    with open(args.script, encoding="utf-8") as source:
        return yaml.safe_load(source)


def pick(plan, names):
    segments = plan["segments"]
    if not names:
        return segments
    chosen = [s for s in segments if s["name"] in names]
    if len(chosen) != len(names):
        raise SystemExit("unknown segment; have: " + ", ".join(s["name"] for s in segments))
    return chosen


def cmd_run(args):
    plan = load(args)
    with open(args.layouts, encoding="utf-8") as source:
        layouts = json.load(source)
    # Display coordinates only depend on portrait vs landscape, not on which way up the tablet is.
    if not args.dry and rotation(args.serial) % 2 != layouts["rotation"] % 2:
        raise SystemExit("screen orientation differs from the calibrated layouts")
    runner = Runner(args, plan, layouts)
    for segment in pick(plan, args.segment):
        if args.record and not args.dry:
            record(args, plan, runner, segment)
        else:
            runner.segment(segment)
    print(f"keys={runner.taps}")


def record(args, plan, runner, segment):
    """One screenrecord file per segment; voice clips played meanwhile become its audio track."""
    os.makedirs(args.record, exist_ok=True)
    index = plan["segments"].index(segment) + 1
    name = f"{index:02d}-{segment['name'].replace(' ', '')}"
    remote = f"/sdcard/cyime-demo-{index:02d}.mp4"
    proc = subprocess.Popen(["adb", "-s", args.serial, "shell", "screenrecord", "--bit-rate", "24000000",
                             "--time-limit", "180", remote])
    time.sleep(1.5)  # screenrecord needs a moment before the first frame
    start = time.monotonic()
    runner.played = []
    runner.segment(segment)
    time.sleep(1.0)
    adb(args.serial, "shell", "pkill", "-INT", "screenrecord", check=False)
    proc.wait()
    time.sleep(1.0)
    video = os.path.join(args.record, name + ".mp4")
    silent = video if not runner.played else os.path.join(args.record, name + "-silent.mp4")
    adb(args.serial, "pull", remote, silent)
    adb(args.serial, "shell", "rm", "-f", remote)
    if runner.played:
        inputs, filters = ["-i", silent], []
        for n, (at, clip) in enumerate(runner.played, 1):
            inputs += ["-i", clip]
            delay = max(0, int((at - start + 1.5) * 1000))  # + the lead-in before `start`
            filters.append(f"[{n}:a]adelay={delay}|{delay}[a{n}]")
        mix = "".join(f"[a{n}]" for n in range(1, len(runner.played) + 1))
        filters.append(f"{mix}amix=inputs={len(runner.played)}:normalize=0[aout]")
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", *inputs, "-filter_complex", ";".join(filters),
                        "-map", "0:v", "-map", "[aout]", "-c:v", "copy", "-c:a", "aac", video], check=True)
    print(f"  {video}")


FRONTMATTER = re.compile(r"\A---\n.*?\n---\n", re.S)


def note_text(args, plan):
    return adb(args.serial, "shell", "cat", f"'{plan['note']}'").replace("\r\n", "\n")


def cmd_check(args):
    plan = load(args)
    expected = "".join(s["text"] for s in pick(plan, args.segment))
    actual = FRONTMATTER.sub("", note_text(args, plan))
    if actual.strip() == expected.strip():
        print("same")
        return
    sys.stdout.writelines(difflib.unified_diff(expected.splitlines(True), actual.splitlines(True), "expected", "note"))
    sys.exit(1)


def cmd_stop(args):
    """Kill the monkey run on the device; it keeps typing its pushed events even without USB."""
    adb(args.serial, "shell", "pid=$(pidof com.android.commands.monkey); [ -n \"$pid\" ] && kill $pid; true",
        check=False)


def cmd_clear(args):
    plan = load(args)
    # Keep the note's properties block (the vault's template); empty only the body.
    head = FRONTMATTER.match(note_text(args, plan))
    body = head.group(0).replace("\n", "\\n") if head else ""
    adb(args.serial, "shell", f"printf '{body}' > '{plan['note']}'")


def cmd_tts(args):
    plan = load(args)
    out_dir = os.path.join(args.data, "tts")
    os.makedirs(out_dir, exist_ok=True)
    for name, voice in plan["voices"].items():
        path = os.path.join(out_dir, name + ".wav")
        text = voice["text"].replace("'", "''")
        ps = ("Add-Type -AssemblyName System.Speech; $s = New-Object System.Speech.Synthesis.SpeechSynthesizer; "
              f"$s.SelectVoice('{voice['voice']}'); $s.Rate = {voice.get('rate', 0)}; "
              f"$s.SetOutputToWaveFile('{path}'); $s.Speak('{text}'); $s.Dispose()")
        subprocess.run(["powershell", "-NoProfile", "-Command", ps], check=True)
        print(path)


def cmd_screenshot(args):
    out_dir = os.path.join(args.data, "calib")
    os.makedirs(out_dir, exist_ok=True)
    path = os.path.join(out_dir, args.name + ".png")
    with open(path, "wb") as out:
        out.write(adb(args.serial, "exec-out", "screencap", "-p", text=False))
    print(path)


class InputDevices:
    """Touchscreen and pen event nodes with their ranges, mapped to display pixels."""

    def __init__(self, serial):
        listing = adb(serial, "shell", "getevent", "-lp")
        self.nodes = {}
        for block in re.split(r"(?=add device \d+: )", listing):
            head = re.match(r"add device \d+: (\S+)\s+name:\s+\"([^\"]+)\"", block, re.S)
            if not head:
                continue
            node, name = head.groups()
            kind = "pen" if re.search(r"pen|stylus", name, re.I) else "touch" if re.search(r"touchscreen", name, re.I) else None
            if not kind:
                continue
            ranges = {axis: int(top) for axis, top in re.findall(r"(ABS_\w+)\s*:.*?max (\d+)", block)}
            self.nodes[node] = (kind, name, ranges)
        physical = re.search(r"Physical size: (\d+)x(\d+)", adb(serial, "shell", "wm", "size"))
        self.width, self.height = int(physical.group(1)), int(physical.group(2))
        self.rotation = rotation(serial)

    def to_display(self, node, raw_x, raw_y):
        kind, _, ranges = self.nodes[node]
        x_axis, y_axis = ("ABS_X", "ABS_Y") if kind == "pen" else ("ABS_MT_POSITION_X", "ABS_MT_POSITION_Y")
        x = raw_x * self.width / (ranges[x_axis] + 1)
        y = raw_y * self.height / (ranges[y_axis] + 1)
        return {0: (x, y), 1: (y, self.width - x), 2: (self.width - x, self.height - y), 3: (self.height - y, x)}[self.rotation]


def read_strokes(serial, devices, idle, on_stroke=None):
    """Read getevent until `idle` seconds pass with no contact after the first stroke."""
    # getevent takes at most one device; watch them all and keep the touch and pen nodes.
    # -tt gives getevent a terminal, so it writes each event at once instead of in 4 KB blocks.
    proc = subprocess.Popen(["adb", "-s", serial, "shell", "-tt", "getevent", "-lt"],
                            stdout=subprocess.PIPE, text=True, encoding="utf-8", errors="replace")
    lines = []
    reader = threading.Thread(target=lambda: [lines.append(line) for line in proc.stdout], daemon=True)
    reader.start()
    while not lines and proc.poll() is None:  # getevent lists the devices first; then it is listening
        time.sleep(0.05)
    print("ready", flush=True)
    strokes, current, used_pen = [], None, False
    state = {node: {"x": 0, "y": 0, "p": 1.0, "down": False} for node in devices.nodes}
    last_up = None
    # getevent omits the node when it watches a single device.
    pattern = re.compile(r"\[\s*([\d.]+)\]\s+(?:(/dev/\S+?):\s+)?(\w+)\s+(\w+)\s+(\w+)")
    only = None
    try:
        while True:
            if not lines:
                if last_up and current is None and time.time() - last_up > idle:
                    break
                time.sleep(0.01)
                continue
            match = pattern.search(lines.pop(0))
            if not match:
                continue
            stamp, node, kind, code, value = match.groups()
            node = node or only
            if node not in state:
                continue
            s = state[node]
            pen = devices.nodes[node][0] == "pen"
            number = int(value, 16) if re.fullmatch(r"[0-9a-f]+", value) else value
            if code in ("ABS_X", "ABS_MT_POSITION_X"):
                s["x"] = number
            elif code in ("ABS_Y", "ABS_MT_POSITION_Y"):
                s["y"] = number
            elif code in ("ABS_PRESSURE", "ABS_MT_PRESSURE"):
                top = devices.nodes[node][2].get(code, 0) or 1
                s["p"] = max(0.05, number / top)
            elif (pen and code == "BTN_TOUCH") or (not pen and code == "ABS_MT_TRACKING_ID"):
                s["down"] = value == "DOWN" if pen else value != "ffffffff"
                if not s["down"] and current is not None and current[0] == node:
                    strokes.append((pen, current[1]))
                    if on_stroke:
                        on_stroke(current[1])
                    current, last_up = None, time.time()
            elif code == "SYN_REPORT" and s["down"]:
                if pen:
                    used_pen = True
                elif used_pen:
                    continue  # palm under the pen
                x, y = devices.to_display(node, s["x"], s["y"])
                if current is None:
                    current = (node, [])
                if current[0] == node:
                    current[1].append([round(x, 1), round(y, 1), int(float(stamp) * 1000), round(s["p"], 3)])
    finally:
        proc.kill()
    # A palm or finger that touched before the pen arrived is not part of the writing.
    if any(is_pen for is_pen, _ in strokes):
        strokes = [(is_pen, stroke) for is_pen, stroke in strokes if is_pen]
    return [stroke for _, stroke in strokes if stroke]


def cmd_devices(args):
    devices = InputDevices(args.serial)
    print(f"display {devices.width}x{devices.height} natural, rotation {devices.rotation}")
    for node, (kind, name, ranges) in devices.nodes.items():
        print(node, kind, name, {k: v for k, v in ranges.items() if "POSITION" in k or k in ("ABS_X", "ABS_Y", "ABS_PRESSURE")})


def cmd_taps(args):
    devices = InputDevices(args.serial)
    print("tap keys; stops after", args.idle, "s without touches")
    read_strokes(args.serial, devices, args.idle, lambda s: print(f"[{s[0][0]:.0f}, {s[0][1]:.0f}]", flush=True))


def cmd_record_ink(args):
    devices = InputDevices(args.serial)
    print(f"write {args.name} now; saved {args.idle} s after the last stroke", flush=True)
    strokes = read_strokes(args.serial, devices, args.idle, lambda s: print(f"  stroke {len(s)} points", flush=True))
    out_dir = os.path.join(args.data, "ink")
    os.makedirs(out_dir, exist_ok=True)
    path = os.path.join(out_dir, args.name + ".json")
    if os.path.exists(path):  # keep earlier takes (a failed take is still useful for reproducing)
        os.replace(path, os.path.join(out_dir, f"{args.name}-{int(os.path.getmtime(path))}.json"))
    with open(path, "w", encoding="utf-8") as out:
        json.dump({"rotation": devices.rotation, "strokes": strokes}, out)
    print(f"{path}: {len(strokes)} strokes")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--serial", default="R52T800SKWZ")
    parser.add_argument("--script", default=os.path.join(HERE, "demo.yaml"))
    parser.add_argument("--layouts", default=os.path.join(HERE, "layouts", "sm-x800.json"))
    parser.add_argument("--data", default="D:/CyIME-Data/demo-video")
    sub = parser.add_subparsers(dest="command", required=True)
    run = sub.add_parser("run")
    run.add_argument("--segment", action="append")
    run.add_argument("--dry", action="store_true", help="build the monkey scripts without touching the device")
    run.add_argument("--interval", type=int, help="ms from one key down to the next")
    run.add_argument("--record", metavar="DIR", help="screen-record each segment into its own file here")
    check = sub.add_parser("check")
    check.add_argument("--segment", action="append")
    sub.add_parser("clear")
    sub.add_parser("stop")
    sub.add_parser("tts")
    sub.add_parser("devices")
    shot = sub.add_parser("screenshot")
    shot.add_argument("name")
    taps = sub.add_parser("taps")
    taps.add_argument("--idle", type=float, default=10)
    ink = sub.add_parser("record-ink")
    ink.add_argument("name")
    ink.add_argument("--idle", type=float, default=3)
    args = parser.parse_args()
    {"run": cmd_run, "check": cmd_check, "clear": cmd_clear, "stop": cmd_stop, "tts": cmd_tts, "devices": cmd_devices,
     "screenshot": cmd_screenshot, "taps": cmd_taps, "record-ink": cmd_record_ink}[args.command](args)


if __name__ == "__main__":
    main()
