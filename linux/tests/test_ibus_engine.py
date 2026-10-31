from types import SimpleNamespace
import unittest
from unittest.mock import Mock
from unittest.mock import patch

import ibus_engine
from cyime_linux.ime_settings import ImeSettings


class CandidateFallbackTests(unittest.TestCase):
    def key_engine(self):
        engine = SimpleNamespace(private=False, shift_tap=None, rime=Mock(), session=1,
                                 settings=ImeSettings(), context={}, page=0,
                                 toggle_mode=Mock(), update=Mock(), turn_page=Mock())
        engine.rime.context.return_value = {"preedit": "ni"}
        return engine

    def test_shift_chord_does_not_toggle_language(self):
        engine = self.key_engine()
        key = ibus_engine.Engine.do_process_key_event
        key(engine, ibus_engine.IBus.KEY_Shift_L, 0, 0)
        key(engine, ord("A"), 0, ibus_engine.IBus.ModifierType.SHIFT_MASK)
        key(engine, ibus_engine.IBus.KEY_Shift_L, 0, ibus_engine.IBus.ModifierType.RELEASE_MASK)
        engine.toggle_mode.assert_not_called()
        key(engine, ibus_engine.IBus.KEY_Shift_R, 0, 0)
        with patch.object(ibus_engine.time, "monotonic", return_value=engine.shift_tap[1] + 1):
            key(engine, ibus_engine.IBus.KEY_Shift_R, 0, ibus_engine.IBus.ModifierType.RELEASE_MASK)
        engine.toggle_mode.assert_not_called()

    def test_composing_backspace_never_leaks_to_application(self):
        engine = self.key_engine()
        engine.rime.key.return_value = False
        self.assertTrue(ibus_engine.Engine.do_process_key_event(engine, ibus_engine.IBus.KEY_BackSpace, 0, 16))
        engine.rime.key.assert_called_once_with(1, ibus_engine.IBus.KEY_BackSpace, 0)
        engine.update.assert_called_once()

    def test_tab_passes_through_when_idle_or_disabled(self):
        engine = self.key_engine()
        engine.rime.key.return_value = False
        engine.rime.context.return_value = {"preedit": ""}
        self.assertFalse(ibus_engine.Engine.do_process_key_event(engine, ibus_engine.IBus.KEY_Tab, 0, 0))
        engine.turn_page.assert_not_called()

    def test_popup_failure_keeps_native_candidates_and_reading_visible(self):
        popup = Mock()
        popup.update_candidates.side_effect = RuntimeError("display unavailable")
        context = dict(preedit="ni hao", preview="你好", keys="1234567890")
        engine = SimpleNamespace(
            rime=Mock(), session=1, settings=ImeSettings(theme="custom"),
            page=0, popup=popup, popup_failed=False, focused=True, rect=(0, 0, 0, 0),
            lookup=ibus_engine.IBus.LookupTable.new(6, 0, True, False),
            commit_text=Mock(), update_preedit_text_with_mode=Mock(),
            update_auxiliary_text=Mock(), update_lookup_table=Mock())
        engine.rime.commit.return_value = ""
        engine.rime.context.return_value = context
        engine.rime.candidates.return_value = [("你好", ""), ("你", "")]
        engine.update_popup = lambda value: ibus_engine.Engine.update_popup(engine, value)
        with self.assertLogs(level="ERROR"):
            ibus_engine.Engine.update(engine)
        self.assertTrue(engine.popup_failed)
        self.assertTrue(engine.update_lookup_table.call_args.args[1])
        self.assertTrue(engine.update_auxiliary_text.call_args.args[1])
        self.assertEqual(engine.update_auxiliary_text.call_args.args[0].get_text(), "ni hao")
        self.assertEqual(engine.update_preedit_text_with_mode.call_args.args[0].get_text(), "你好")
        popup.hide.assert_called_once()
        ibus_engine.Engine.update(engine)
        popup.update_candidates.assert_called_once()

    def test_empty_or_unfocused_context_does_not_create_popup(self):
        engine = SimpleNamespace(settings=ImeSettings(theme="custom"), popup=None,
                                 popup_failed=False, focused=True)
        self.assertFalse(ibus_engine.Engine.update_popup(engine, {"preedit": ""}))
        engine.focused = False
        self.assertFalse(ibus_engine.Engine.update_popup(engine, {"preedit": "ni"}))
        self.assertIsNone(engine.popup)
