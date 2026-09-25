package com.kingzcheung.xime.ui.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 键帽视觉策略的纯函数账目：手机 / 14 键 / 九宫格 / 平板 / 悬浮各档的 gutter、缝、缩放。
 *
 * 期望值按 [KeyVisualPolicy] 与 [keyVisualMetrics] 的定义手算；
 * 真机观感微调只改 KeyVisualPolicy.kt 里的常数，本用例随之更新。
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
        assertEquals(2.49f, m.insetX!!, tolerance)
        assertEquals(3.05f, m.insetY!!, tolerance)
        assertEquals(1.11f, m.scale, tolerance)
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
        // columns=5、420×280：格 84×93.3，按 60dp 参考格缩放封顶 1.2
        val m = keyVisualMetrics(policy, 420f, 280f, columns = 5f, rows = 3f, verticalInsetDp = 0f)
        assertEquals(0f, m.gutterX, tolerance)
        assertEquals(1.2f, m.scale, tolerance)
        assertEquals(2.7f, m.insetX!!, tolerance)
    }

    @Test fun invalidBoundsFallBackToDeclaredPadding() {
        val m = keyVisualMetrics(KeyVisualPolicy.Qwerty, 0f, 0f, columns = 10f)
        assertEquals(KeyVisualMetrics.Unspecified, m)
        assertNull(m.insetX)
    }
}
