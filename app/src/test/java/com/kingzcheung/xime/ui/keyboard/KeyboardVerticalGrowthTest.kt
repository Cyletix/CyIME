package com.kingzcheung.xime.ui.keyboard

import org.junit.Assert.*
import org.junit.Test

class KeyboardVerticalGrowthTest {
    private fun grid(width: Float, height: Float, keypad: Boolean = false,
        floating: Boolean = false, spacing: Pair<Float?, Float?> = null to null): KeyVisualMetrics =
        keyboardGridMetrics((if (keypad) KeyVisualPolicy.T9 else KeyVisualPolicy.Qwerty)
            .copy(maxKeyWidth = Float.MAX_VALUE), width, height, if (keypad) 5f * 3f / 3.4f else 10f,
            allowShrink = floating, growthSpacing = spacing)

    private fun cap(m: KeyVisualMetrics, spacing: Pair<Float?, Float?> = null to null) =
        m.cellHeightDp - resolvedVisualGap(spacing.second, m.insetY!!) - 2f * m.extraInsetY

    @Test fun firstAndLastCapsStayAtBodyEdgesWithoutHiddenBlankSpace() {
        for (width in listOf(280f, 360f, 680f, 934f, 1200f))
            for (height in listOf(140f, 184f, 240f, 420f, 600f, 1000f))
                for (keypad in listOf(false, true)) for (floating in listOf(false, true)) {
                    val m = grid(width, height, keypad, floating)
                    val halfGap = m.insetY!!
                    val firstTop = m.topSpaceDp + halfGap + m.extraInsetY
                    val lastBottom = m.topSpaceDp + 4 * m.cellHeightDp - halfGap - m.extraInsetY
                    assertEquals("no displaced first row at $width x $height", halfGap, firstTop, 0.001f)
                    assertEquals("only existing bottom inset and half-gap", height - 8f - halfGap, lastBottom, 0.001f)
                    assertEquals(height, 4f * m.cellHeightDp + m.topSpaceDp + m.bottomSpaceDp + 8f, 0.001f)
                }
    }

    @Test fun phoneNormalHeightKeepsItsOriginalKeySize() {
        val m = grid(360f, 232f)
        assertEquals(56f, m.cellHeightDp, 0.001f)
        assertEquals(50.5f, cap(m), 0.001f)
        assertEquals(5.5f, m.insetY!! * 2f, 0.001f)
    }

    @Test fun tabletDefaultIsSquareInTheActualRendererInsteadOfShortPhoneCaps() {
        val size = defaultLetterKeyboardSize(934, 934, LetterKeyboardDefaults(1493))
        val m = grid(size.width.toFloat(), size.height - 44f)
        val width = m.cellWidthDp - m.insetX!! * 2f
        assertTrue("tablet caps must not saturate at the old phone limit", cap(m) > 75f)
        assertEquals(width, cap(m), 0.2f)
        assertEquals(0f, m.topSpaceDp, 0f)
        assertEquals(0f, m.bottomSpaceDp, 0f)
    }

    @Test fun increasingHeightDoesNotInflateGapsOrAddOuterPadding() {
        for (width in listOf(280f, 360f, 680f, 1200f)) for (keypad in listOf(false, true)) {
            val normal = grid(width, 240f, keypad)
            for (height in listOf(320f, 420f, 600f, 1000f)) {
                val tall = grid(width, height, keypad)
                assertEquals(normal.insetY!!, tall.insetY!!, 0.001f)
                assertEquals(normal.insetX!!, tall.insetX!!, 0.001f)
                assertEquals((height - 240f) / 4f, cap(tall) - cap(normal), 0.001f)
                assertEquals(0f, tall.extraInsetY + tall.topSpaceDp + tall.bottomSpaceDp, 0f)
            }
        }
    }

    @Test fun tabletAutomaticGapGrowthIsHalvedAndExplicitGapsArePreserved() {
        for (keypad in listOf(false, true)) {
            val base = if (keypad) 6f else 5.5f
            val maximum = if (keypad) 12f else 11f
            val m = grid(1200f, 400f, keypad)
            assertEquals(base + (maximum - base) * .5f, m.insetY!! * 2f, 0.001f)
            for (spacing in listOf(0f to 0f, 6f to 10f, 8f to 16f)) {
                val explicit = grid(1200f, 400f, keypad, spacing = spacing)
                assertEquals(spacing.second!!, explicit.cellHeightDp - cap(explicit, spacing), 0.001f)
                assertEquals(0f, explicit.extraInsetY + explicit.topSpaceDp + explicit.bottomSpaceDp, 0f)
            }
        }
    }

    @Test fun shortBodiesKeepTouchRowsAndAtLeastEightyPercentForCaps() {
        for (width in listOf(200f, 360f, 1200f)) for (height in listOf(40f, 80f, 140f, 184f)) {
            val m = grid(width, height)
            assertEquals((height - 8f) / 4f, m.cellHeightDp, 0.001f)
            assertTrue(cap(m) >= m.cellHeightDp * .8f - .001f)
        }
    }

    @Test fun dockedAndFloatingUseIdenticalEdgesAtTheSameDimensions() {
        for (height in listOf(184f, 240f, 420f, 900f)) {
            assertEquals(grid(420f, height), grid(420f, height, floating = true))
        }
    }

    @Test fun continuousResizingHasNoCrossAxisGapsOrPositionJumps() {
        var previous: KeyVisualMetrics? = null
        for (step in 0..500) {
            val m = grid(320f + step * .5f, 184f + step * .5f)
            previous?.let {
                assertTrue(kotlin.math.abs(cap(m) - cap(it)) < .5f)
                assertEquals(it.topSpaceDp, m.topSpaceDp, 0f)
                assertEquals(it.bottomSpaceDp, m.bottomSpaceDp, 0f)
            }
            previous = m
        }
    }
}
