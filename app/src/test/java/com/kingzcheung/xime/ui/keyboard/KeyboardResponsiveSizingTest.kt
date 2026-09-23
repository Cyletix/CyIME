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

    @Test fun equalWeightCellsUseTheSameFontDespitePhysicalPixelRounding() {
        assertEquals(KeyboardKeyMetrics.contentScale(67f, 58.42f),
            KeyboardKeyMetrics.contentScale(67.4f, 58.79f), tolerance)
        assertEquals(KeyboardKeyMetrics.labelSizeSp("変換", 16f, 67f, 58.42f, 1f, 1.2f),
            KeyboardKeyMetrics.labelSizeSp("記号", 16f, 67.4f, 58.79f, 1f, 1.2f), tolerance)
    }
}
