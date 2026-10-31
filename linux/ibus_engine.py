#!/usr/bin/env python3
"""CyIME IBus frontend. The host desktop renders candidates; Rime does decoding."""

import argparse
import logging
from pathlib import Path
import signal
import os
import subprocess
import sys
import time

import gi
gi.require_version("IBus", "1.0")
from gi.repository import GLib, IBus

from cyime_linux.core import default_state_dir
from cyime_linux.rime import Rime
from cyime_linux.ime_settings import load, presentation


class Engine(IBus.Engine):
    rime = None

    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
        self.session = self.rime.session()
        self.settings = load()
        self.rime.options(self.session, self.settings)
        self.lookup = IBus.LookupTable.new(self.settings.page_size, 0, True, False)
        self.lookup.set_orientation(IBus.Orientation.HORIZONTAL)
        self.candidate_keys = "1234567890"
        self.private = False
        self.page = 0
        self.context = {}
        self.popup = None
        self.popup_failed = False
        self.focused = False
        self.rect = (0, 0, 0, 0)
        self.shift_tap = None
        self.properties = IBus.PropList()
        for key, label in (("mode", "中文"), ("settings", "输入法设置")):
            self.properties.append(IBus.Property.new(key, IBus.PropType.NORMAL,
                IBus.Text.new_from_string(label), "preferences-desktop-keyboard" if key == "settings" else "",
                IBus.Text.new_from_string(label), True, True, IBus.PropState.UNCHECKED, None))

    def do_process_key_event(self, keyval, keycode, state):
        if self.private:
            return False
        shortcuts = IBus.ModifierType.CONTROL_MASK | IBus.ModifierType.MOD1_MASK | IBus.ModifierType.SUPER_MASK
        if keyval in (IBus.KEY_Shift_L, IBus.KEY_Shift_R):
            if state & IBus.ModifierType.RELEASE_MASK:
                tap, self.shift_tap = self.shift_tap, None
                if tap and tap[0] == keyval and time.monotonic() - tap[1] < 0.5 and not state & shortcuts:
                    self.toggle_mode()
            else:
                self.shift_tap = (keyval, time.monotonic()) if not state & shortcuts else None
            return False
        self.shift_tap = None
        if state & IBus.ModifierType.RELEASE_MASK:
            return False
        composing = bool(self.rime.context(self.session)["preedit"])
        if composing and not state & shortcuts:
            if keyval == IBus.KEY_BackSpace:
                self.page = 0
                self.rime.key(self.session, keyval, 0)
                self.update()
                return True
            if (keyval in (IBus.KEY_Page_Up, IBus.KEY_Page_Down) or
                self.settings.page_keys and keyval in (IBus.KEY_minus, IBus.KEY_equal) or
                self.settings.tab_pages and keyval in (IBus.KEY_Tab, IBus.KEY_ISO_Left_Tab)):
                previous = keyval in (IBus.KEY_Page_Up, IBus.KEY_minus, IBus.KEY_ISO_Left_Tab) or (
                    keyval == IBus.KEY_Tab and state & IBus.ModifierType.SHIFT_MASK)
                self.turn_page(-1 if previous else 1)
                return True
            if ord("1") <= keyval <= ord("9"):
                index = keyval - ord("1")
                if index < len(self.context.get("candidates", [])):
                    self.select(index)
                return True
            if keyval == IBus.KEY_space and self.context.get("candidates"):
                self.select(self.context["selected"])
                return True
            if keyval in (IBus.KEY_Up, IBus.KEY_Down) and self.context.get("candidates"):
                self.highlight = max(0, min(len(self.context["candidates"]) - 1,
                    self.context["selected"] + (-1 if keyval == IBus.KEY_Up else 1)))
                self.update(reset_highlight=False)
                return True
        self.page = 0
        # IBus and Rime use the same X11 modifier bits for ordinary keys.
        handled = self.rime.key(self.session, keyval, int(state) & 0x40001fff & ~int(IBus.ModifierType.MOD2_MASK))
        self.update()
        return handled

    def toggle_mode(self):
        english = not self.rime.get_option(self.session, "ascii_mode")
        if english and self.rime.context(self.session)["preedit"]:
            # Read raw code, not formatted preedit (which inserts syllable spaces).
            raw = self.rime.input(self.session)
            self.rime.clear(self.session)
            self.commit_text(IBus.Text.new_from_string(raw))
        self.rime.set_option(self.session, "ascii_mode", english)
        self.page = 0
        self.update()
        prop = self.properties.get(0)
        prop.set_label(IBus.Text.new_from_string("英文" if english else "中文"))
        self.update_property(prop)

    def do_property_activate(self, name, state):
        if name == "mode":
            self.toggle_mode()
        elif name == "settings":
            subprocess.Popen([sys.executable, str(Path(__file__).with_name("run.py")), "settings"],
                             start_new_session=True)

    def update(self, reset_highlight=True):
        committed = self.rime.commit(self.session)
        if committed:
            self.commit_text(IBus.Text.new_from_string(committed))
        context = self.rime.context(self.session)
        if reset_highlight:
            self.highlight = 0
        start = self.page * self.settings.page_size
        all_rows = self.rime.candidates(self.session, 0, start + self.settings.page_size + 1) if context["preedit"] else []
        rows = all_rows[start:]
        context.update(candidates=rows[:self.settings.page_size], last=len(rows) <= self.settings.page_size,
                       page=self.page, page_size=self.settings.page_size, selected=self.highlight)
        if context["candidates"] and (self.page or self.highlight):
            context["preview"] = context.get("confirmed", "") + context["candidates"][self.highlight][0] + context.get("suffix", "")
        self.context = context
        inline, reading = presentation(context, self.settings)
        text = IBus.Text.new_from_string(inline)
        text.append_attribute(IBus.AttrType.UNDERLINE, IBus.AttrUnderline.SINGLE, 0, len(inline))
        self.update_preedit_text_with_mode(text, len(inline), bool(inline), IBus.PreeditFocusMode.CLEAR)
        custom = self.update_popup(context)
        self.update_auxiliary_text(IBus.Text.new_from_string(reading), bool(reading) and not custom)
        self.lookup.clear()
        self.lookup.set_page_size(self.settings.page_size)
        self.candidate_keys = context["keys"]
        for text, comment in all_rows:
            self.lookup.append_candidate(IBus.Text.new_from_string(text + ("  " + comment if comment else "")))
        if context["candidates"]:
            self.lookup.set_cursor_pos(start + context["selected"])
        self.update_lookup_table(self.lookup, bool(context["candidates"]) and not custom)

    def update_popup(self, context):
        custom = (not self.popup_failed and self.settings.theme != "system" and
                  os.environ.get("XDG_SESSION_TYPE", "x11") == "x11")
        if custom and context["preedit"] and self.focused:
            try:
                if not self.popup:
                    from cyime_linux.candidate_window import CandidateWindow
                    self.popup = CandidateWindow(self.select, self.turn_page)
                self.popup.anchor = self.rect
                self.popup.update_candidates(context, self.settings)
                return True
            except Exception:
                self.popup_failed = True
                logging.exception("Custom candidate window failed; using native IBus candidates")
        if self.popup:
            self.popup.hide()
        return False

    def select(self, index):
        if 0 <= index < len(self.context.get("candidates", [])):
            self.rime.select(self.session, self.page * self.settings.page_size + index)
            self.page = 0
            self.update()

    def turn_page(self, direction):
        if direction < 0 and self.page > 0 or direction > 0 and not self.context.get("last", True):
            self.page += direction
            self.update()

    def do_set_cursor_location(self, x, y, w, h):
        self.rect = (x, y, w, h)
        if self.popup and not self.popup_failed:
            self.popup.anchor = self.rect
            self.popup.position()

    def do_candidate_clicked(self, index, button, state):
        if button == 1 and 0 <= index < len(self.candidate_keys):
            self.select(index)

    def do_page_up(self):
        self.turn_page(-1)

    def do_page_down(self):
        self.turn_page(1)

    def do_reset(self):
        self.rime.clear(self.session)
        self.page = 0
        self.update()

    def do_focus_out(self):
        self.shift_tap = None
        self.focused = False
        self.do_reset()
        if self.popup:
            self.popup.hide()

    def do_focus_in(self):
        self.focused = True
        self.settings = load()
        self.page = 0
        self.rime.options(self.session, self.settings)
        self.register_properties(self.properties)
        self.update()

    def do_set_content_type(self, purpose, hints):
        self.private = purpose in (IBus.InputPurpose.PASSWORD, IBus.InputPurpose.PIN)
        if self.private:
            self.do_reset()

    def do_destroy(self):
        if self.popup:
            self.popup.destroy()
        self.rime.destroy(self.session)
        IBus.Engine.do_destroy(self)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--ibus", action="store_true")
    parser.add_argument("--shared-dir", type=Path, default=Path("/usr/share/rime-data"))
    parser.add_argument("--user-dir", type=Path, default=default_state_dir() / "rime")
    parser.add_argument("--schema", default="cyime_pinyin")
    args = parser.parse_args()
    rime = Rime(args.shared_dir, args.user_dir, schema=args.schema)
    Engine.rime = rime
    IBus.init()
    bus = IBus.Bus()
    if not bus.is_connected():
        rime.close()
        raise SystemExit("IBus session bus is unavailable")
    loop = GLib.MainLoop()
    factory = IBus.Factory.new(bus.get_connection())
    factory.add_engine("cyime-rime", Engine.__gtype__)
    bus.connect("disconnected", lambda *_: loop.quit())
    if args.ibus:
        bus.request_name("org.freedesktop.IBus.CyIME", 0)
    else:
        component = IBus.Component.new("org.freedesktop.IBus.CyIME", "CyIME Rime frontend", "0.1",
            "GPL-3.0-or-later", "CyIME contributors", "https://github.com/Cyletix/CyIME", "", "cyime")
        description = IBus.EngineDesc.new("cyime-rime", "CyIME 拼音", "CyIME Rime Pinyin", "zh_CN",
            "GPL-3.0-or-later", "CyIME contributors", "input-keyboard", "us")
        component.add_engine(description)
        if not bus.register_component(component):
            raise SystemExit("Unable to register the CyIME IBus component")
    signal.signal(signal.SIGTERM, lambda *_: loop.quit())
    signal.signal(signal.SIGINT, lambda *_: loop.quit())
    try:
        loop.run()
    finally:
        factory.destroy()
        rime.close()


if __name__ == "__main__":
    main()
