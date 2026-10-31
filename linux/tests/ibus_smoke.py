"""Run only under dbus-run-session with a private IBUS_ADDRESS/config directory."""

import os
from contextlib import closing
from pathlib import Path
import signal
import subprocess
import sys
import time

import gi
gi.require_version("IBus", "1.0")
from gi.repository import GLib, IBus


def pump(seconds=0.3):
    deadline = time.monotonic() + seconds
    context = GLib.MainContext.default()
    while time.monotonic() < deadline:
        while context.pending():
            context.iteration(False)
        time.sleep(0.01)


def main():
    if not os.environ.get("CYIME_ISOLATED_IBUS") or not os.environ.get("IBUS_ADDRESS"):
        raise SystemExit("Refusing to test against the real session bus")
    root = Path(__file__).resolve().parents[1]
    log = open(Path(os.environ["XDG_CONFIG_HOME"]) / "ibus-test.log", "wb")
    daemon = subprocess.Popen(["ibus-daemon", "--single", "--emoji-extension", "disable", "--cache", "none", "--address", os.environ["IBUS_ADDRESS"]],
                              stdout=log, stderr=log)
    engine = None
    try:
        IBus.init()
        for _ in range(100):
            bus = IBus.Bus()
            if bus.is_connected():
                break
            pump(0.1)
        else:
            raise RuntimeError("Isolated IBus bus failed to start")
        engine = subprocess.Popen([sys.executable, str(root / "ibus_engine.py"),
            "--shared-dir", os.environ["CYIME_RIME_SHARED"],
            "--user-dir", os.environ["CYIME_RIME_USER"]], stdout=log, stderr=log)
        for _ in range(150):
            engines = bus.list_active_engines()
            if any(item.get_name() == "cyime-rime" for item in engines):
                break
            if engine.poll() is not None:
                raise RuntimeError("IBus frontend exited early")
            pump(0.1)
        else:
            raise RuntimeError("IBus frontend failed to register")
        assert bus.set_global_engine("cyime-rime")
        context = bus.create_input_context("cyime-isolated-smoke")
        context.set_capabilities(IBus.Capabilite.PREEDIT_TEXT | IBus.Capabilite.LOOKUP_TABLE |
                                 IBus.Capabilite.AUXILIARY_TEXT | IBus.Capabilite.FOCUS)
        commits, preedits, candidates, readings, tables = [], [], [], [], []
        context.connect("commit-text", lambda _, text: commits.append(text.get_text()))
        context.connect("update-preedit-text", lambda _, text, cursor, visible: preedits.append(text.get_text()))
        context.connect("update-lookup-table", lambda _, table, visible:
                        candidates.extend(table.get_candidate(i).get_text() for i in range(table.get_number_of_candidates())))
        context.connect("update-lookup-table", lambda _, table, visible: tables.append(table))
        context.connect("update-auxiliary-text", lambda _, text, visible: readings.append(text.get_text()))
        context.focus_in()
        context.set_engine("cyime-rime")
        active = None
        for _ in range(50):
            pump(0.1)
            active = context.get_engine()
            if active and active.get_name() == "cyime-rime":
                break
        assert active and active.get_name() == "cyime-rime", "Input context did not select CyIME"
        for letter in "nihao":
            assert context.process_key_event(ord(letter), 0, IBus.ModifierType.MOD2_MASK)
            pump(0.05)
        assert any("你好" in value for value in candidates), candidates
        assert preedits[-1] == "你好", preedits
        assert readings[-1].replace(" ", "").replace("'", "") == "nihao", readings
        if os.environ.get("CYIME_SMOKE_CUSTOM"):
            from Xlib import X, display
            with closing(display.Display()) as xdisplay:
                popups = [window for window in xdisplay.screen().root.query_tree().children
                          if window.get_wm_name() == "CyIME candidates"]
                assert len(popups) == 1, "No custom candidate window created by the actual engine"
                assert popups[0].get_attributes().map_state == X.IsViewable, "Custom candidates are hidden"
                size = popups[0].get_geometry()
                assert size.width > 150, "Custom candidate window is blank/collapsed"
                pixels = popups[0].get_image(0, 0, size.width, size.height, X.ZPixmap, 0xffffffff)
                assert len(set(pixels.data)) > 16, "Custom candidate window did not paint text/background"
                if os.environ.get("CYIME_SMOKE_CAPTURE"):
                    from PIL import Image
                    Image.frombytes("RGB", (size.width, size.height), pixels.data, "raw", "BGRX").save(os.environ["CYIME_SMOKE_CAPTURE"])
        assert not commits, "Preview must remain uncommitted"
        assert context.process_key_event(IBus.KEY_BackSpace, 0, IBus.ModifierType.MOD2_MASK)
        pump()
        assert "niha" in readings[-1].replace(" ", "").replace("'", ""), readings
        assert not commits, "Backspace must only edit pending Pinyin"
        assert context.process_key_event(ord("o"), 0, 0)
        pump()
        assert context.process_key_event(ord(" "), 0, 0)
        pump()
        assert "你好" in commits, commits
        assert preedits, "No preedit signal"
        assert not context.process_key_event(IBus.KEY_BackSpace, 0, IBus.ModifierType.MOD2_MASK), "Idle Backspace must reach the application"
        for letter in "ceshi":
            assert context.process_key_event(ord(letter), 0, IBus.ModifierType.MOD2_MASK)
        def tap_shift(key=IBus.KEY_Shift_L):
            context.process_key_event(key, 0, IBus.ModifierType.MOD2_MASK)
            context.process_key_event(key, 0, IBus.ModifierType.SHIFT_MASK |
                                      IBus.ModifierType.MOD2_MASK | IBus.ModifierType.RELEASE_MASK)
            pump()
        tap_shift()
        assert commits[-1] == "ceshi", ("Shift must commit raw Pinyin, not Chinese or generated spaces", commits)
        assert preedits[-1] == "", preedits
        for letter in "hello":
            assert not context.process_key_event(ord(letter), 0, IBus.ModifierType.MOD2_MASK), "English must pass through"
        tap_shift(IBus.KEY_Shift_R)
        for letter in "shi":
            assert context.process_key_event(ord(letter), 0, 0)
            pump(0.05)
        assert tables[-1].get_page_size() == 6
        first = [tables[-1].get_candidate(i).get_text() for i in range(6)]
        assert context.process_key_event(IBus.KEY_Page_Down, 0, 0)
        pump()
        assert tables[-1].get_cursor_pos() == 6
        second = [tables[-1].get_candidate(i).get_text() for i in range(6, 12)]
        assert first != second
        assert context.process_key_event(IBus.KEY_ISO_Left_Tab, 0, IBus.ModifierType.SHIFT_MASK)
        pump()
        assert tables[-1].get_cursor_pos() == 0, "Shift+Tab must page backwards"
        assert context.process_key_event(IBus.KEY_Tab, 0, IBus.ModifierType.MOD2_MASK)
        pump()
        assert tables[-1].get_cursor_pos() == 6, "Tab must page forwards"
        assert context.process_key_event(ord("1"), 0, 0)
        pump()
        assert commits[-1] == second[0], (commits, second)
        # Empty context must not call set_cursor_pos on an empty IBus table.
        context.reset()
        pump()
        assert engine.poll() is None, "Empty candidates crashed the engine"
        if os.environ.get("CYIME_SMOKE_CUSTOM"):
            with closing(display.Display()) as xdisplay:
                popups = [window for window in xdisplay.screen().root.query_tree().children
                          if window.get_wm_name() == "CyIME candidates"]
                assert all(window.get_attributes().map_state != X.IsViewable for window in popups), "Empty composition left a stale popup"
        context.destroy()
        print("Isolated IBus: preview, reading, NumLock/Backspace, Shift/raw-English, Tab paging and page-two selection passed")
    finally:
        for process in (engine, daemon):
            if process and process.poll() is None:
                process.send_signal(signal.SIGTERM)
                try:
                    process.wait(timeout=10)
                except subprocess.TimeoutExpired:
                    process.kill()
                    process.wait()
        log.close()


if __name__ == "__main__":
    main()
