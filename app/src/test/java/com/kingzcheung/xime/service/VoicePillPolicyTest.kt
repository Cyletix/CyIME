package com.kingzcheung.xime.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoicePillPolicyTest {
    @Test fun `tapping the text field while the voice bar shows brings the keyboard back`() {
        assertTrue(voicePillExpandsOnShowRequest(voiceCollapsed = true, revivingPill = false, configChange = false))
    }

    @Test fun `keyboard already shown, automatic bar revival and rotation do not expand`() {
        assertFalse(voicePillExpandsOnShowRequest(voiceCollapsed = false, revivingPill = false, configChange = false))
        assertFalse(voicePillExpandsOnShowRequest(voiceCollapsed = true, revivingPill = true, configChange = false))
        assertFalse(voicePillExpandsOnShowRequest(voiceCollapsed = true, revivingPill = false, configChange = true))
    }
}
