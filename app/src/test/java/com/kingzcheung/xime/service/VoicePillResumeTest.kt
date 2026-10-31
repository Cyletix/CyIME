package com.kingzcheung.xime.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoicePillResumeTest {
    private val wechat = "com.tencent.mm"

    @Test fun `only sending or leaving the field from the user's own bar arms resuming`() {
        val arms = VoiceSessionEnd.entries.filter { voicePillResumeArmsAfter(it, pillByUser = true, enabled = true) }.toSet()
        assertEquals(setOf(VoiceSessionEnd.FIELD_CLEARED, VoiceSessionEnd.FOCUS_LEAVES, VoiceSessionEnd.FOCUS_TO_OTHER_FIELD), arms)
    }

    @Test fun `a bar the app made, or the setting off, never arms`() {
        for (end in VoiceSessionEnd.entries) {
            assertFalse(end.name, voicePillResumeArmsAfter(end, pillByUser = false, enabled = true))
            assertFalse(end.name, voicePillResumeArmsAfter(end, pillByUser = true, enabled = false))
        }
    }

    @Test fun `resumes only in the same app within the window`() {
        val mark = VoicePillResumeMark(wechat, atMs = 1_000L)
        assertTrue(voicePillResumeApplies(mark, wechat, nowMs = 1_000L + 60_000L))
        assertTrue(voicePillResumeApplies(mark, wechat, nowMs = 1_000L + VOICE_PILL_RESUME_WINDOW_MS))
        assertFalse("too late", voicePillResumeApplies(mark, wechat, nowMs = 1_000L + VOICE_PILL_RESUME_WINDOW_MS + 1))
        assertFalse("other app", voicePillResumeApplies(mark, "com.android.chrome", nowMs = 2_000L))
        assertFalse("no field", voicePillResumeApplies(mark, null, nowMs = 2_000L))
        assertFalse("never armed", voicePillResumeApplies(null, wechat, nowMs = 2_000L))
        assertFalse("clock went back", voicePillResumeApplies(mark, wechat, nowMs = 500L))
    }

    @Test fun `password fields, fields without voice, calls and missing permission keep the mic off`() {
        assertTrue(voicePillResumeFieldAllows(sensitive = false, locksNativeInput = false, inCall = false, hasMicPermission = true))
        assertFalse(voicePillResumeFieldAllows(sensitive = true, locksNativeInput = false, inCall = false, hasMicPermission = true))
        assertFalse(voicePillResumeFieldAllows(sensitive = false, locksNativeInput = true, inCall = false, hasMicPermission = true))
        assertFalse(voicePillResumeFieldAllows(sensitive = false, locksNativeInput = false, inCall = true, hasMicPermission = true))
        assertFalse(voicePillResumeFieldAllows(sensitive = false, locksNativeInput = false, inCall = false, hasMicPermission = false))
    }
}
