package com.kingzcheung.xime.ui.keyboard

import com.kingzcheung.xime.settings.SettingsPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 候选栏横拉的阻力和门槛（用户 11-20：拉过门槛松手展开，不够就弹回，两个方向都算）。 */
class CandidatePullGesturesTest {
    private val threshold = 96f

    @Test fun pullFollowsTheFingerAtHalfSpeedInEitherDirection() {
        assertEquals(50f, pulledDistance(100f, threshold), 0.01f)
        assertEquals(-50f, pulledDistance(-100f, threshold), 0.01f)
        assertEquals(0f, pulledDistance(0f, threshold), 0.01f)
    }

    @Test fun pullStopsAtOneAndAHalfThresholds() {
        assertEquals(144f, pulledDistance(1000f, threshold), 0.01f)
        assertEquals(-144f, pulledDistance(-1000f, threshold), 0.01f)
    }

    @Test fun releasesOnlyPastTheThreshold() {
        assertFalse(pullReleases(95f, threshold))
        assertTrue(pullReleases(96f, threshold))
        assertTrue(pullReleases(-96f, threshold))
        assertFalse(pullReleases(-95f, threshold))
        assertFalse(pullReleases(100f, 0f))
    }

    @Test fun defaultPullThresholdIsShorterAndConfigurableRangeIsNormalized() {
        assertEquals(28f, PULL_THRESHOLD_DP, 0f)
        assertEquals(28f, SettingsPreferences.normalizeCandidatePullThresholdDp(Float.NaN), 0f)
        assertEquals(16f, SettingsPreferences.normalizeCandidatePullThresholdDp(4f), 0f)
        assertEquals(64f, SettingsPreferences.normalizeCandidatePullThresholdDp(120f), 0f)
        assertEquals(24f, SettingsPreferences.normalizeCandidatePullThresholdDp(23f), 0f)
    }
}
