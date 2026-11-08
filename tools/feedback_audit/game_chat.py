"""Read the active Android editor's native text size and IME geometry without editing its draft."""
import argparse
import io
import json
import re
import struct
import subprocess
from pathlib import Path
from zipfile import BadZipFile, ZipFile


def match(pattern, text):
    found = re.search(pattern, text, re.MULTILINE)
    return found.group(1) if found else None


def active_field(dump, package):
    if not re.search(r"^\s*mInputShown=true\b", dump, re.M):
        raise ValueError("Open the chat editor and show the keyboard first.")
    if not re.search(r"^\s*packageName=" + re.escape(package) + r"\s", dump, re.M):
        raise ValueError(f"The current editor is not in {package}.")
    ime = match(r"^\s*mCurMethodId=(\S+)", dump) or match(r"^\s*mCurId=(\S+)", dump)
    if not ime:
        raise ValueError("The system did not identify the active input method.")
    view = re.search(r"^\s*mServedView=([\w.$]+)\{([0-9a-fA-F]+)\b", dump, re.M)
    return ime, (view[1], int(view[2], 16)) if view else None


def view_metrics(blob, identity):
    # Android ViewHierarchyEncoder writes typed key/value pairs, nested view maps,
    # then the property dictionary. Decode in memory; export only size and geometry.
    data = io.BytesIO(blob)
    objects = []
    formats = {b"Z": ">?", b"B": ">b", b"S": ">h", b"I": ">i", b"J": ">q", b"F": ">f", b"D": ">d"}

    def number(fmt):
        return struct.unpack(fmt, data.read(struct.calcsize(fmt)))[0]

    def value(tag):
        if tag in formats:
            return number(formats[tag])
        if tag == b"R":
            return data.read(number(">H")).decode("utf-8", errors="replace")
        if tag != b"M":
            raise ValueError("Unknown view hierarchy encoding.")
        obj = {}
        while data.read(1) == b"S":
            key = number(">h")
            if key == 0:
                objects.append(obj)
                return obj
            obj[key] = value(data.read(1))
        raise ValueError("Incomplete view hierarchy.")

    while tag := data.read(1):
        value(tag)
    if not objects:
        return None
    keys = {name: key for key, name in objects[-1].items() if isinstance(name, str)}
    for obj in objects[:-1]:
        if (obj.get(keys.get("meta:__name__")), obj.get(keys.get("meta:__hash__"))) == identity:
            return {label: obj.get(keys.get(prop)) for label, prop in (
                ("width_px", "layout:width"), ("height_px", "layout:height"), ("text_size_px", "text:textSize"))}
    return None


def capture(serial, package):
    def adb(*args):
        return subprocess.check_output(["adb", "-s", serial, "exec-out", *args], timeout=25)

    def ime_dump():
        return adb("dumpsys", "input_method").decode(errors="replace")

    before = ime_dump()
    ime, identity = active_field(before, package)
    displays = adb("dumpsys", "window", "displays").decode(errors="replace")
    # The IME frame is in screen coordinates, whereas contentTopInsets is window-local.
    package_title = r"(?:^|\s)" + re.escape(package) + "/"
    for display in re.split(r"(?=^[ \t]*Display: mDisplayId=)", displays, flags=re.M):
        focus = match(r"mCurrentFocus=Window\{([^}\n]+)\}", display)
        if not focus:
            continue
        named_host = re.search(package_title, focus) is not None
        activity = match(r"mFocusedApp=ActivityRecord\{([^}\n]+)\}", display)
        if named_host or (identity and activity and re.search(package_title, activity)):
            break
    else:
        raise ValueError(f"The focused application window is not in {package}.")
    frame = match(r"InsetsSource[^\n]*\btype=ime frame=(\[[^\]]+\]\[[^\]]+\])", display)
    metrics = None
    if identity:
        archive = adb("cmd", "window", "dump-visible-window-views")
        try:
            with ZipFile(io.BytesIO(archive)) as views:
                for item in views.infolist():
                    # A game's chat dialog can have its own title. The focused window token
                    # and the served View identity must both match in that case.
                    focused_window = item.filename.split(" ", 1)[0] == focus.split(" ", 1)[0]
                    if focused_window or (named_host and re.search(package_title, item.filename)):
                        metrics = view_metrics(views.read(item), identity)
                        if metrics is not None:
                            break
        except BadZipFile:
            pass  # Some Android versions do not provide encoded windows.
    if not named_host and metrics is None:
        raise ValueError("The focused dialog could not be matched to the game's editor.")
    after = ime_dump()
    if active_field(after, package) != (ime, identity):
        raise ValueError("The input method or editor changed during capture. Repeat this sample.")
    return {"package": package, "ime": ime, "editor_class": identity[0] if identity else None,
            "editor": metrics, "ime_frame_on_screen": frame,
            "ime_content_top_in_window_px": match(r"\bcontentTopInsets=(\d+)", after),
            "fullscreen": match(r"\bmIsFullscreen=(true|false)", after)}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--package", default="com.tencent.tmgp.sgame")
    parser.add_argument("--output", type=Path, help="New JSON file. Without this option, print to stdout.")
    args = parser.parse_args()
    try:
        report = json.dumps(capture(args.serial, args.package), ensure_ascii=False, indent=2)
        if args.output:
            with args.output.open("x", encoding="utf-8") as out:
                out.write(report + "\n")
        print(report)
    except (OSError, ValueError, struct.error, subprocess.SubprocessError) as exc:
        parser.exit(1, f"{exc}\n")
