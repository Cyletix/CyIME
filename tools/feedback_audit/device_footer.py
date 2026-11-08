"""Run the compiled footer probe on isolated displays of one explicitly selected tablet."""
import argparse
import hashlib
import json
import os
import re
import subprocess
import sys
import tempfile
from datetime import datetime
from pathlib import Path
from zipfile import ZIP_DEFLATED, ZipFile

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
PKG = Path("com/kingzcheung/xime")
sys.path.insert(0, str(ROOT / "scripts"))
from storage_guard import check_storage


def call(args, **kwargs):
    return subprocess.run(list(map(str, args)), check=True, **kwargs)


def add_classes(jar, root, folder, names):
    for name in names:
        found = list((root / folder).glob(name + ".class")) + list((root / folder).glob(name + "$*.class"))
        if not found:
            raise FileNotFoundError(f"{folder}/{name} (run the footer JVM tests first)")
        for f in found:
            jar.write(f, f.relative_to(root).as_posix())


def verify_pixels(output, mode):
    from PIL import Image
    log = (output / (mode + ".log")).read_text(encoding="utf-8")
    row = re.search(r"card=(\[[^\]]+\]) pill=(\[[^\]]+\]).*expectedFooter=(\d+)", log)
    if not row or "PASS actions=" not in log:
        raise ValueError(f"{mode}: missing geometry or touch result")
    left, _, right, bottom = json.loads(row[1])
    x, y = (round(c) for c in json.loads(row[2]))
    with Image.open(output / (mode + ".png")) as png:
        im = png.convert("RGB")
        fill = im.getpixel((x-27, y))
        points = [(px, py) for py in range(y-5, y+5) for px in range(x-24, x+24)
                  if max(abs(a-b) for a, b in zip(im.getpixel((px, py)), fill)) > 20]
    if not points:
        raise ValueError(f"{mode}: indicator has no visible pixels")
    xs, ys = zip(*points)
    cx = (min(xs) + max(xs) + 1) / 2
    cy = (min(ys) + max(ys) + 1) / 2
    if abs(cx-x) > 1 or abs(cy-y) > 1 or max(xs)-min(xs)+1 not in range(38, 42):
        raise ValueError(f"{mode}: painted pill differs from the measured indicator")
    offset = cy - (bottom - int(row[3]) / 2)
    if mode.startswith("after") and (abs(cx-(left+right)/2) > .5 or abs(offset) > .5):
        raise ValueError(f"{mode}: painted indicator is off centre by {offset} px")
    return {"width_px": max(xs)-min(xs)+1, "height_px": max(ys)-min(ys)+1, "footer_offset_y_px": offset}


def verify_ink_center(output, mode, suffix=""):
    """Find the visible bottom key edge and the independent ink bounds in the screenshot."""
    from PIL import Image
    log = (output / f"{mode}.log").read_text(encoding="utf-8")
    left, top, right, bottom = map(round, json.loads(re.search(r"card=(\[[^\]]+\])", log)[1]))
    x = (left + right) // 2
    with Image.open(output / (mode + suffix + ".png")) as png:
        im = png.convert("RGB")
    def difference(pixel, bg):
        return max(abs(a-b) for a, b in zip(pixel, bg))
    background = im.getpixel((x-26, bottom-4))
    # Samples on both sides of the symbol lie directly underneath the space key.
    ends = []
    for col in (x-28, x-26, x+26, x+28):
        ink = [y for y in range(bottom-65, bottom-3) if difference(im.getpixel((col,y)),background)>12]
        if ink:
            ends.append(max(ink)+1)
    if not ends or max(ends)-min(ends)>2:
        raise ValueError(f"{mode}: bottom key edge is ambiguous {ends}")
    cap_end = max(ends)
    points = [(col,y) for col in range(x-23,x+24) for y in range(cap_end+1,bottom-1)
              if difference(im.getpixel((col,y)),background)>16]
    if not points:
        raise ValueError(f"{mode}{suffix}: no visible footer glyph")
    xs, ys = zip(*points)
    above, below = min(ys)-cap_end, bottom-max(ys)-1
    if abs(above-below)>1 or abs((min(xs)+max(xs)+1)/2-x)>.5:
        raise ValueError(f"{mode}{suffix}: visible whitespace above={above}, below={below}, ink x={min(xs)}..{max(xs)}")
    return {"cap_bottom_px": cap_end, "card_bottom_px": bottom, "above_px": above, "below_px": below,
            "ink_bounds": [min(xs),min(ys),max(xs)+1,max(ys)+1]}


def verify_lock_frames(output, mode):
    from PIL import Image

    def frame(name):
        with Image.open(output / f"{mode}-{name}.png") as picture:
            return picture.convert("RGBA")

    log = (output / f"{mode}.log").read_text(encoding="utf-8")
    left, top, right, bottom = map(round, json.loads(re.search(r"card=(\[[^\]]+\])", log)[1]))
    cx = (left + right) // 2
    rows = list(range(top + 18, bottom - 18))
    columns = list(range(left + 18, right - 18))
    baseline = frame("idle")
    locked = frame("locked")

    def delta(image, reference, x, y):
        return max(abs(a-b) for a, b in zip(image.getpixel((x, y)), reference.getpixel((x, y)))) > 8

    def edges(name, reference):
        im = frame(name)
        if im.getchannel("A").getbbox() != (left, top, right, bottom):
            raise ValueError(f"{name}: visible pixels outside the keyboard while the lock outline is active")
        bottom_x = [x for x in columns if delta(im, reference, x, bottom-1)]
        sides = [[y for y in rows if delta(im, reference, x, y)] for x in (left, right-1)]
        top_x = [x for x in columns if delta(im, reference, x, top)]
        if abs(len(sides[0])-len(sides[1])) > 1:
            raise ValueError(f"{name}: sides have different progress")
        if bottom_x and (abs(bottom_x[0]+bottom_x[-1]+1-2*cx)>1 or
                         bottom_x[-1]-bottom_x[0]+1 != len(bottom_x)):
            raise ValueError(f"{name}: progress did not start at bottom centre")
        if sides[0] and sides[0] != rows[-len(sides[0]):]:
            raise ValueError(f"{name}: side progress did not start from the bottom")
        # A straight section of the one-pixel stroke must leave both adjacent pixel rows untouched.
        adjacent = ([(x,y) for x in (left-1, left+1, right-2, right) for y in rows] +
                    [(x,y) for y in (top-1, top+1, bottom-2) for x in columns])
        if any(delta(im, reference, x, y) for x, y in adjacent):
            raise ValueError(f"{name}: changed pixels beyond the one-pixel outline")
        return [len(bottom_x), len(sides[0]), len(top_x)]

    checks = {name: edges(name, baseline) for name in ("starting", "partial", "retreat", "released", "locked", "unlocked")}
    checks.update({name: edges(name, locked) for name in ("unlock-partial", "unlock-retreat", "still-locked")})
    if not (0 < checks["starting"][0] < len(columns) and checks["starting"][1:] == [0, 0] and
            checks["locked"] == [len(columns), len(rows), len(columns)] and
            all(checks[name] == [0, 0, 0] for name in ("released", "unlocked", "still-locked"))):
        raise ValueError(f"{mode}: wrong start or end of outline {checks}")
    for held, retreat in (("partial", "retreat"), ("unlock-partial", "unlock-retreat")):
        if not (checks[held][0] == len(columns) and checks[held][2] == checks[retreat][2] == 0 and
                0 <= checks[retreat][1] < checks[held][1] < len(rows)):
            raise ValueError(f"{mode}: outline did not retreat along the same path {checks}")
    return {"outline_pixel_counts_bottom_side_top": checks,
            "active_stroke_rgba": frame("partial").getpixel((cx, bottom-1))}


def verify_hint_frames(output, mode):
    from PIL import Image
    log = (output / f"{mode}.log").read_text(encoding="utf-8")
    x, y = map(round, json.loads(re.search(r"pill=(\[[^\]]+\])", log)[1]))
    def contrast(suffix, dx, dy):
        with Image.open(output / f"{mode}{suffix}.png") as image:
            return max(abs(a-b) for a, b in zip(image.getpixel((x+dx,y+dy)), image.getpixel((x-25,y))))
    wings = [contrast(suffix, 15, 0) for suffix in ("", "-hint-lock", "-hint-bar")]
    # The lock is taller and narrower than the pill, and the second transition restores the pill.
    arch = max(contrast("-hint-lock", dx, dy) for dx in range(-3,4) for dy in (-5,-4,-3))
    blend = contrast("-hint-fade", 15, 0)
    if not (wings[1] < blend < wings[0]):
        raise ValueError(f"Missing intermediate crossfade, wing contrast {blend}")
    if not (wings[0] > 20 and wings[1] < wings[0] * .65 and wings[2] > wings[0] * .75 and arch > 15):
        raise ValueError(f"Expected pill, lock, pill: wing contrast {wings}, shackle contrast {arch}")
    return {"pill_wing_contrast": wings, "lock_shackle_contrast": arch, "intermediate_wing_contrast": blend}


INPUT_CASES = ("settings", "default", "reversed", "legacy", "long", "completion")


def run(serial, output, runtime_apk, compare, input_cases=(), bottom_calibration=False, menu_checks=False):
    sdk = Path(os.environ["ANDROID_HOME"])
    platform = sdk / "platforms/android-35/android.jar"
    adb = ["adb", "-s", serial]
    base = ROOT / "app/build/tmp/kotlin-classes"
    package = "com.cyletix.cyime"
    installed = call(adb + ["shell", "pm", "path", package], capture_output=True, text=True).stdout.splitlines()
    apk = next((line[len("package:"):] for line in installed if line.startswith("package:") and line.endswith("/base.apk")), "")
    if not re.fullmatch(r"/data/app/[A-Za-z0-9/~+=._-]+/base\.apk", apk):
        raise ValueError("Daily app base APK path unavailable")
    output.mkdir(parents=True, exist_ok=False)
    check_storage(ROOT)
    with (output / "compile.log").open("w") as log:
        env = os.environ.copy()
        if os.name == "nt" and Path("D:/CyIME-Data/uds").is_dir():
            env["JAVA_TOOL_OPTIONS"] = (env.get("JAVA_TOOL_OPTIONS", "") +
                                        " -Djdk.net.unixdomain.tmpdir=D:/CyIME-Data/uds").strip()
        call([ROOT / ("gradlew.bat" if os.name == "nt" else "gradlew"), "--offline", "--console=plain",
              "-I", HERE / "probes.gradle", ":app:compileDebugUnitTestKotlin"], cwd=ROOT, env=env,
             stdout=log, stderr=subprocess.STDOUT)
    with tempfile.TemporaryDirectory(dir=output) as tmp:
        work = Path(tmp)
        call(["javac", "--release", "8", "-cp", platform, "-d", work, HERE / "device/FloatingFooterProbe.java"])
        # The installed Release has R8-optimized framework classes. Use full, unminified
        # AndroidX metadata for this shell-only Compose window. No APK is installed.
        runtime = work / "runtime.jar"
        with ZipFile(runtime_apk) as source, ZipFile(runtime, "w", ZIP_DEFLATED, compresslevel=1) as target:
            for name in source.namelist():
                if re.fullmatch(r"classes\d*\.dex", name):
                    target.writestr(name, source.read(name))
        old = base / "release"
        original = work / "baseline-classes.jar"
        baseline = work / "baseline.jar"
        if compare:
            with ZipFile(original, "w", ZIP_DEFLATED, compresslevel=1) as target:
                for f in (old / PKG).rglob("*.class"):
                    target.write(f, f.relative_to(old).as_posix())
            with (output / "baseline-d8.log").open("w") as log:
                call([sdk / "build-tools/36.0.0" / ("d8.bat" if os.name == "nt" else "d8"), "--min-api", "33",
                      "--lib", platform, "--output", baseline, original], stdout=log, stderr=subprocess.STDOUT)
        with runtime_apk.open("rb") as f:
            (output / "runtime.txt").write_text(f"{runtime_apk}\n{hashlib.file_digest(f,'sha256').hexdigest()}\n"
                                               f"Baseline classes: {old}\n", encoding="utf-8")
        for variant in (("before", "after") if compare else ("after",)):
            jar = work / (variant + "-classes.jar")
            with ZipFile(jar, "w", ZIP_DEFLATED) as bundle:
                for f in work.glob("FloatingFooterProbe*.class"):
                    bundle.write(f, f.name)
                add_classes(bundle, base / "debugUnitTest", PKG / "audit",
                            ["MenuFeedbackSceneKt", "LayoutMenuSceneKt"] if menu_checks else ["FixedBottomSceneKt"] if bottom_calibration else
                            ["InputOptionsSceneKt"] if input_cases else ["FloatingFooterSceneKt"])
                if variant == "after":
                    # Kotlin internal member names include the variant. Keep the whole current
                    # app module together instead of linking debug callers to release callbacks.
                    fresh = base / "debug"
                    for f in (fresh / PKG).rglob("*.class"):
                        bundle.write(f, f.relative_to(fresh).as_posix())
            dex = work / (variant + ".jar")
            with (output / (variant + "-d8.log")).open("w") as log:
                call([sdk / "build-tools/36.0.0" / ("d8.bat" if os.name == "nt" else "d8"), "--min-api", "33",
                      "--lib", platform, "--output", dex, jar], stdout=log, stderr=subprocess.STDOUT)
            # mktemp owns this run's local/tmp tree; the daily IME and its data are never written.
            remote = call(adb + ["shell", "mktemp", "-d", "/data/local/tmp/cyime-footer.XXXXXX"],
                          capture_output=True, text=True).stdout.strip()
            if not re.fullmatch(r"/data/local/tmp/cyime-footer\.[A-Za-z0-9]+", remote):
                raise ValueError("Unexpected adb scratch path")
            try:
                dependencies = [baseline, runtime] if variant == "before" else [runtime]
                for path in dependencies:
                    call(adb + ["push", path, remote + "/" + path.name], capture_output=True)
                    call(adb + ["shell", "chmod", "444", remote + "/" + path.name])
                call(adb + ["push", dex, remote + "/probe.jar"], capture_output=True)
                call(adb + ["shell", "chmod", "444", remote + "/probe.jar"])
                shapes = ["phone", "landscape", "tablet"]
                if variant == "after":
                    shapes.extend(["phone-edge", "phone-light-edge"])
                if input_cases:
                    shapes = ["phone-input-" + name for name in input_cases]
                if bottom_calibration:
                    selected = ("buttons", "gestures", "resize") if bottom_calibration == "all" else (bottom_calibration,)
                    shapes = ["phone-bottom-" + name for name in selected]
                if menu_checks:
                    shapes = []
                    if menu_checks in ("all", "speech"):
                        shapes.append("phone-menu-speech")
                    if menu_checks in ("all", "toolbar"):
                        shapes.extend(["phone-menu-toolbar", "tablet-menu-toolbar"])
                    if menu_checks in ("all", "layouts"):
                        shapes.append("phone-menu-layouts")
                failures = []
                for shape in shapes:
                    mode = variant + "-" + shape
                    with (output / (mode + ".log")).open("w") as log:
                        classpath = ":".join([remote + "/probe.jar", *[remote + "/" + p.name for p in dependencies], apk])
                        native_dir = apk.rsplit("/", 1)[0] + "/lib/arm64"
                        try:
                            call(adb + ["shell", "env", "CLASSPATH=" + classpath, "LD_LIBRARY_PATH=" + native_dir, "app_process",
                                        "/system/bin", "FloatingFooterProbe", remote, mode],
                                 stdout=log, stderr=subprocess.STDOUT, timeout=35)
                        except subprocess.CalledProcessError:
                            if not (input_cases or bottom_calibration or menu_checks):
                                raise
                            for suffix in ("keyboard", "failure"):
                                subprocess.run([*adb, "pull", f"{remote}/{mode}-{suffix}.png",
                                                str(output / f"{mode}-{suffix}.png")], capture_output=True)
                            failures.append(mode)
                            continue
                    call(adb + ["pull", f"{remote}/{mode}-keyboard.png", output / (mode + ".png")], stdout=subprocess.DEVNULL)
                    if input_cases or bottom_calibration or menu_checks:
                        print(mode, "OK", flush=True)
                        continue
                    frames = []
                    if mode == "after-tablet":
                        frames = ["hint-fade", "hint-lock", "hint-bar"]
                    if mode.endswith("-edge"):
                        frames = ["idle", "starting", "partial", "retreat", "released", "locked", "unlock-partial",
                                  "unlock-retreat", "still-locked", "unlocked"]
                    for frame in frames:
                        name = f"{mode}-{frame}.png"
                        call(adb + ["pull", f"{remote}/{name}", output / name], stdout=subprocess.DEVNULL)
                    measured = verify_pixels(output, mode)
                    if variant == "after":
                        states = [""]
                        if mode == "after-tablet":
                            states.extend(["-hint-fade", "-hint-lock", "-hint-bar"])
                        if mode.endswith("-edge"):
                            states.append("-locked")
                        measured["visible_gap"] = {state or "bar": verify_ink_center(output, mode, state) for state in states}
                    if mode.endswith("-edge"):
                        measured.update(verify_lock_frames(output, mode))
                    if mode == "after-tablet":
                        measured.update(verify_hint_frames(output, mode))
                    (output / (mode + "-pixels.json")).write_text(json.dumps(measured, indent=2), encoding="utf-8")
                    print(mode, "OK", flush=True)
                if failures:
                    raise RuntimeError("See device logs for: " + ", ".join(failures))
            finally:
                call(adb + ["shell", "rm", "-r", remote])


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--runtime-apk", type=Path, help="existing unminified ARM64 debug APK",
                        default=ROOT / "app/build/outputs/apk/editions/1.15.66/standard/debug/CyIME-1.15.66-debug-arm64-v8a.apk")
    group = parser.add_mutually_exclusive_group()
    group.add_argument("--menu-checks", nargs="?", const="all", choices=("all", "speech", "toolbar", "layouts"),
                       help="check speech model row actions and compact/wide toolbar targets")
    group.add_argument("--bottom-calibration", nargs="?", const="all", choices=("all", "buttons", "gestures", "resize"),
                       help="check fixed bottom calibration on a Samsung tablet with its keyboard buttons enabled")
    group.add_argument("--compare", action="store_true", help="also run the local unoptimized Release class snapshot")
    group.add_argument("--input-checks", nargs="+", choices=("all", *INPUT_CASES),
                       help="check all input cases, or select the affected scenarios")
    parser.add_argument("--output", type=Path, default=Path("D:/CyIME-Data/eval") /
                        ("floating-footer-" + datetime.now().strftime("%Y%m%d-%H%M%S")))
    options = parser.parse_args()
    cases = options.input_checks or ()
    run(options.serial, options.output, options.runtime_apk, options.compare,
        INPUT_CASES if "all" in cases else cases, options.bottom_calibration, options.menu_checks)
