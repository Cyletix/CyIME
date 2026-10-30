package com.kingzcheung.xime.ui.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardResponsiveSizingTest {
    private val tolerance = 0.0001f

    @Test
    fun phoneSizedKeyKeepsOriginalSizing() {
        assertEquals(1f, adaptiveKeyContentScale(keyHeightDp = 56f), tolerance)
        assertEquals(1f, adaptiveHintScale(contentScale = 1f), tolerance)
        assertEquals(14f, adaptiveHintOffsetDp(contentScale = 1f), tolerance)
    }

    @Test
    fun largerKeyScalesLabelsAndHintsWithinLimits() {
        assertEquals(1.25f, adaptiveKeyContentScale(keyHeightDp = 70f), tolerance)
        assertEquals(1.7f, adaptiveHintScale(contentScale = 1.5f), tolerance)
        assertEquals(1.5f, adaptiveBubbleScale(contentScale = 1.5f), tolerance)
        assertEquals(24f, adaptiveHintOffsetDp(contentScale = 1.5f), tolerance)
    }

    @Test
    fun smallerKeysAreNotShrunkAndLargeKeysAreClamped() {
        assertEquals(1f, adaptiveKeyContentScale(keyHeightDp = 20f), tolerance)
        assertEquals(1.5f, adaptiveKeyContentScale(keyHeightDp = 120f), tolerance)
    }

    @Test fun phoneLabelsAndFunctionIconsKeepTheirEstablishedSize() {
        assertEquals(16f, KeyboardKeyMetrics.labelSizeSp("あ", 16f, 64f, 48f, 1f), tolerance)
        assertEquals(16f, KeyboardKeyMetrics.labelSizeSp("記号", 16f, 64f, 48f, 1f), tolerance)
        assertEquals(20f, KeyboardKeyMetrics.iconSizeDp(64f, 48f), tolerance)
    }

    @Test fun tabletKanaFunctionLabelsAndIconsGrowTogether() {
        listOf("変換", "123", "記号", "あ", "わ").forEach {
            assertEquals(24f, KeyboardKeyMetrics.labelSizeSp(it, 16f, 150f, 96f, 1f), tolerance)
        }
        assertEquals(30f, KeyboardKeyMetrics.iconSizeDp(150f, 96f), tolerance)
    }

    @Test fun tallNarrowReturnKeyDoesNotEnlargeItsIconBeyondOtherFunctionKeys() {
        assertEquals(KeyboardKeyMetrics.iconSizeDp(52f, 48f), KeyboardKeyMetrics.iconSizeDp(52f, 104f), tolerance)
    }

    @Test fun narrowFloatingKeysDoNotInheritTabletFontSizing() {
        assertEquals(1f, KeyboardKeyMetrics.contentScale(48f, 46f), tolerance)
        assertEquals(16f, KeyboardKeyMetrics.labelSizeSp("あ", 16f, 48f, 46f, 1f), tolerance)
    }

    @Test fun largeAccessibilityFontsAndLongLabelsRemainInsideTheKey() {
        val width = 46f
        val height = 28f
        val fontScale = 1.5f
        val size = KeyboardKeyMetrics.labelSizeSp("変換", 16f, width, height, fontScale, 1.6f)
        assertTrue(size * fontScale * 2f <= width - 4f)
        assertTrue(size * fontScale * 1.4f <= height - 2f + tolerance)
        assertTrue(KeyboardKeyMetrics.iconSizeDp(width, height) <= height - 4f)
    }

    @Test fun lowKeyHintsStayInsideTheVisualBounds() {
        val offset = KeyboardKeyMetrics.hintOffsetDp(24f, 9f, 1.3f, 1f)
        assertTrue(offset + 9f * 1.3f / 2f <= 12f)
    }

    /** 九键退格「清空」：默认高度的键（约 60dp）留字，最小高度的键（约 40dp）只留图标（用户截图 10-30）。 */
    @Test fun iconHintDropsInsteadOfOverlappingTheIcon() {
        fun fits(height: Float, fontScale: Float = 1f): Boolean {
            val offset = KeyboardKeyMetrics.hintOffsetDp(height, 10f, fontScale, 1f)
            return KeyboardKeyMetrics.hintClearsIcon(offset, 10f, fontScale, 20f)
        }
        assertTrue(fits(56f))
        assertTrue(!fits(40f))
        assertTrue(!fits(46f, fontScale = 1.3f))
        // 留字时文字字形下沿一定在图标字形上沿之上。
        for (h in 20..90) for (fs in listOf(0.85f, 1f, 1.15f, 1.3f, 1.5f)) {
            val offset = KeyboardKeyMetrics.hintOffsetDp(h.toFloat(), 10f, fs, 1f)
            if (KeyboardKeyMetrics.hintClearsIcon(offset, 10f, fs, 20f)) assertTrue(offset - 10f * fs / 2f > 7.5f)
        }
    }

    @Test fun equalWeightCellsUseTheSameFontDespitePhysicalPixelRounding() {
        assertEquals(KeyboardKeyMetrics.contentScale(67f, 58.42f),
            KeyboardKeyMetrics.contentScale(67.4f, 58.79f), tolerance)
        assertEquals(KeyboardKeyMetrics.labelSizeSp("変換", 16f, 67f, 58.42f, 1f, 1.2f),
            KeyboardKeyMetrics.labelSizeSp("記号", 16f, 67.4f, 58.79f, 1f, 1.2f), tolerance)
    }
}
