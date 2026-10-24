package com.kingzcheung.xime.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoicePillPolicyTest {
    @Test fun `tapping the text field while the voice bar shows brings the keyboard back`() {
        assertTrue(voicePillExpandsOnShowRequest(voiceCollapsed = true, selfShow = false, configChange = false))
    }

    @Test fun `keyboard already shown, the bar showing itself and rotation do not expand`() {
        assertFalse(voicePillExpandsOnShowRequest(voiceCollapsed = false, selfShow = false, configChange = false))
        assertFalse(voicePillExpandsOnShowRequest(voiceCollapsed = true, selfShow = true, configChange = false))
        assertFalse(voicePillExpandsOnShowRequest(voiceCollapsed = true, selfShow = false, configChange = true))
    }

    @Test fun `scrolling away the keyboard and the collapse button keep listening`() {
        val keeps = VoiceSessionEnd.entries.filter(::voiceKeepsListeningAfter).toSet()
        assertEquals(setOf(VoiceSessionEnd.COLLAPSE_BUTTON, VoiceSessionEnd.APP_HIDES_KEYBOARD), keeps)
    }

    @Test fun `back, sending, focus loss and every other way the input ends stop voice`() {
        for (end in listOf(VoiceSessionEnd.BACK_KEY, VoiceSessionEnd.FIELD_CLEARED, VoiceSessionEnd.FOCUS_LEAVES,
            VoiceSessionEnd.FOCUS_TO_OTHER_FIELD, VoiceSessionEnd.SENSITIVE_FIELD, VoiceSessionEnd.HOME,
            VoiceSessionEnd.APP_SWITCH, VoiceSessionEnd.SCREEN_OFF, VoiceSessionEnd.ROTATION,
            VoiceSessionEnd.SWITCH_KEYBOARD, VoiceSessionEnd.OPEN_CAPTIONS)) {
            assertFalse(end.name, voiceKeepsListeningAfter(end))
        }
    }

    @Test fun `the field turning empty after having text means it was sent`() {
        assertTrue(voiceFieldCleared(hadText = true, textNow = ""))
        assertFalse("still has text", voiceFieldCleared(hadText = true, textNow = "好"))
        assertFalse("was empty from the start", voiceFieldCleared(hadText = false, textNow = ""))
        assertFalse("could not read the field", voiceFieldCleared(hadText = true, textNow = null))
    }
}
