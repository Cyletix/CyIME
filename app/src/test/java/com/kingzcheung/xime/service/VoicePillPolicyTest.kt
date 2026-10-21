package com.kingzcheung.xime.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoicePillPolicyTest {
    @Test fun `tapping the text field while the voice bar shows brings the keyboard back`() {
        assertTrue(voicePillExpandsOnShowRequest(voiceCollapsed = true, configChange = false))
    }

    @Test fun `keyboard already shown and rotation do not expand`() {
        assertFalse(voicePillExpandsOnShowRequest(voiceCollapsed = false, configChange = false))
        assertFalse(voicePillExpandsOnShowRequest(voiceCollapsed = true, configChange = true))
    }

    @Test fun `only an explicit collapse inside the keyboard keeps listening in the bar`() {
        val keeps = VoiceSessionEnd.entries.filter(::voiceCollapsesInsteadOfEnding).toSet()
        assertEquals(setOf(VoiceSessionEnd.BACK_KEY, VoiceSessionEnd.COLLAPSE_BUTTON), keeps)
    }

    @Test fun `send, focus loss and every other way the input ends stop voice instead of collapsing`() {
        for (end in listOf(VoiceSessionEnd.APP_HIDES_KEYBOARD, VoiceSessionEnd.SYSTEM_HIDE_KEY, VoiceSessionEnd.FOCUS_LEAVES,
            VoiceSessionEnd.FOCUS_TO_OTHER_FIELD, VoiceSessionEnd.SENSITIVE_FIELD, VoiceSessionEnd.HOME,
            VoiceSessionEnd.APP_SWITCH, VoiceSessionEnd.SCREEN_OFF, VoiceSessionEnd.ROTATION,
            VoiceSessionEnd.SWITCH_KEYBOARD, VoiceSessionEnd.OPEN_CAPTIONS)) {
            assertFalse(end.name, voiceCollapsesInsteadOfEnding(end))
        }
    }
}
