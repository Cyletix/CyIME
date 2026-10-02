package com.kingzcheung.xime.ui.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 键帽视觉策略的纯函数账目：手机 / 14 键 / 九宫格 / 平板 / 悬浮各档的 gutter、缝、缩放。
 *
 * 手机基准、宽度响应及矮键帽留白边界；截图观感仍需要真机验收。
 */
class KeyVisualPolicyTest {
    private val tolerance = 0.01f

    @Test fun phoneQwertyUsesTargetGapsAndEightDpGutter() {
        val m = keyVisualMetrics(KeyVisualPolicy.Qwerty, 360f, 260f, columns = 10f)
        assertEquals(8f, m.gutterX, tolerance)          // 手机左右各 8dp 呼吸空间
        assertEquals(2.25f, m.insetX!!, tolerance)      // 总缝 4.5dp
        assertEquals(2.75f, m.insetY!!, tolerance)      // 总缝 5.5dp
        assertEquals(1.0f, m.scale, tolerance)          // 格短边 34.4dp < 60dp 参考格，不再缩小
        assertEquals(29.9f, m.capShortEdge, tolerance)  // 键帽短边 = 34.4 - 4.5
    }

    @Test fun phoneFourteenKeyKeepsAbsoluteGapsInsteadOfScalingByKeyWidth() {
        val m = keyVisualMetrics(KeyVisualPolicy.FourteenKey, 360f, 260f, columns = 5f)
        assertEquals(8f, m.gutterX, tolerance)
        assertEquals(2.625f, m.insetX!!, tolerance)     // 5dp × 1.05 / 2
        assertEquals(3.15f, m.insetY!!, tolerance)      // 6dp × 1.05 / 2
        assertEquals(1.05f, m.scale, tolerance)         // 格短边 63dp
        assertEquals(56.7f, m.capShortEdge, tolerance)
    }

    @Test fun phoneNineKeyUsesSixDpGapsOnWideKeys() {
        val m = keyVisualMetrics(KeyVisualPolicy.T9, 360f, 260f, columns = 4.41f)
        assertEquals(3.15f, m.insetX!!, tolerance)      // 6dp × 1.05 / 2
        assertEquals(3.15f, m.insetY!!, tolerance)
        assertEquals(56.7f, m.capShortEdge, tolerance)
    }

    @Test fun tabletCapsKeyWidthAndTurnsExtraWidthIntoGutter() {
        val m = keyVisualMetrics(KeyVisualPolicy.Qwerty, 800f, 420f, columns = 10f)
        // 内容宽被 10 × (62 + 4.5) 限制 → 66.5dp/格，多出的宽度全部变成左右 gutter
        assertEquals(67.5f, m.gutterX, tolerance)
        assertEquals(5.75f, m.insetX!!, tolerance)
        assertEquals(5.32f, m.insetY!!, tolerance)
        assertEquals(1.93f, m.scale, tolerance)
        assertTrue("键帽宽度不超过 62dp：${m.capShortEdge}", m.capShortEdge <= 62f)
    }

    @Test fun floatingKeyboardMayShrinkBelowPhoneGaps() {
        val m = keyVisualMetrics(KeyVisualPolicy.Qwerty, 280f, 200f, columns = 10f, allowShrink = true)
        assertEquals(4f, m.gutterX, tolerance)          // 悬浮只保留 4dp
        assertEquals(1.91f, m.insetX!!, tolerance)      // 4.5dp × 0.85 / 2
        assertEquals(2.34f, m.insetY!!, tolerance)      // 5.5dp × 0.85 / 2
        assertEquals(KeyVisualPolicy.ScaleMin, m.scale, tolerance)
    }

    @Test fun nestedScopeWithoutGutterKeepsRealCellWidth() {
        // 与 KeyboardKeySpacingScope(applyGutter = false) 同参：不设键宽上限、不设 gutter 下限
        val policy = KeyVisualPolicy.Qwerty.copy(maxKeyWidth = Float.MAX_VALUE, minGutter = 0f)
        // columns=5、420×280：格 84×93.3，宽键达到上限，但不把内容宽度收窄。
        val m = keyVisualMetrics(policy, 420f, 280f, columns = 5f, rows = 3f, verticalInsetDp = 0f)
        assertEquals(0f, m.gutterX, tolerance)
        assertEquals(2f, m.scale, tolerance)
        assertEquals(6f, m.insetX!!, tolerance)
    }

    @Test fun wideningKeyboardAloneIncreasesBothGapsForLettersAndNineKey() {
        for ((policy, columns) in listOf(KeyVisualPolicy.Qwerty to 10f, KeyVisualPolicy.T9 to (5f * 3f / 3.4f))) {
            val phone = keyVisualMetrics(policy, 360f, 300f, columns)
            val tablet = keyVisualMetrics(policy, 680f, 300f, columns)
            // 高度不变也应明显拉开间距，回归此前被短边卡住的平板横屏情形。
            assertTrue("wider keyboard horizontal gap", tablet.insetX!! > phone.insetX!! * 1.5f)
            assertTrue("wider keyboard vertical gap", tablet.insetY!! > phone.insetY!! * 1.5f)
            assertTrue("visible tablet gap", tablet.insetX * 2f >= 11f)
            assertTrue("tablet row gap", tablet.insetY * 2f >= 10f)
        }
    }

    @Test fun shrinkingTheActualFloatingKeyboardRestoresCompactGaps() {
        val large = keyVisualMetrics(KeyVisualPolicy.T9, 680f, 340f, 4.41f, allowShrink = true)
        val small = keyVisualMetrics(KeyVisualPolicy.T9, 280f, 180f, 4.41f, allowShrink = true)
        assertTrue(large.insetX!! > small.insetX!! * 1.8f)
        assertTrue(large.insetY!! > small.insetY!! * 1.8f)
        assertTrue(small.insetX * 2f < 6f)
    }

    @Test fun wideButShortKeyboardPreservesAtLeastEightyPercentOfKeyHeight() {
        for (height in listOf(64f, 120f, 180f)) {
            val m = keyVisualMetrics(KeyVisualPolicy.Qwerty, 800f, height, 10f)
            val cellHeight = (height - 8f) / 4f
            assertTrue("row gap at height $height", m.insetY!! * 2f <= cellHeight * 0.2f + tolerance)
            assertTrue("visible keycap at height $height", m.capShortEdge >= cellHeight * 0.8f - tolerance)
        }
    }

    @Test fun invalidBoundsFallBackToDeclaredPadding() {
        val m = keyVisualMetrics(KeyVisualPolicy.Qwerty, 0f, 0f, columns = 10f)
        assertEquals(KeyVisualMetrics.Unspecified, m)
        assertNull(m.insetX)
    }
}
