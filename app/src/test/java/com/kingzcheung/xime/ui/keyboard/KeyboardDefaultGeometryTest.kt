package com.kingzcheung.xime.ui.keyboard

import org.junit.Assert.*
import org.junit.Test

class KeyboardDefaultGeometryTest {
    private fun caps(size: ProtectedKeyboardSize, defaults: LetterKeyboardDefaults): Pair<Float, Float> {
        val metrics = keyboardGridMetrics(KeyVisualPolicy.Qwerty.copy(maxKeyWidth = Float.MAX_VALUE),
            size.width.toFloat(), size.height - 44f, 10f, allowShrink = defaults.floating,
            growthSpacing = defaults.spacingX to defaults.spacingY)
        val gapX = defaults.spacingX?.takeUnless { it == 2f } ?: (metrics.insetX!! * 2)
        val gapY = defaults.spacingY?.takeUnless { it == 2f } ?: (metrics.insetY!! * 2)
        return metrics.cellWidthDp - gapX to metrics.cellHeightDp - gapY - metrics.extraInsetY * 2f
    }

    @Test fun portraitTabletUsesFullAvailableWidthAndSquareLetterCaps() {
        val defaults = LetterKeyboardDefaults(1493)
        val size = defaultLetterKeyboardSize(934, 934, defaults)
        val (width, height) = caps(size, defaults)
        assertEquals(934, size.width)
        assertEquals(0, size.offsetX)
        assertEquals(width, height, 0.15f)
        assertTrue(size.height in 400..420)
    }

    @Test fun narrowPhonesKeepUsableHeightInsteadOfTinySquareKeys() {
        for (width in listOf(280, 320, 360, 393, 412)) {
            val defaults = LetterKeyboardDefaults(800)
            val size = defaultLetterKeyboardSize(width, width, defaults)
            val cap = caps(size, defaults)
            assertEquals(width, size.width)
            assertEquals(276, size.height)
            assertTrue(cap.second > cap.first)
            assertTrue(cap.second >= 48f)
        }
    }

    @Test fun wideOrShortWindowsNarrowAndCenterInsteadOfStretching() {
        for ((width, height) in listOf(1493 to 934, 2200 to 700, 960 to 600, 800 to 393)) {
            val defaults = LetterKeyboardDefaults(height)
            val size = defaultLetterKeyboardSize(width, width, defaults)
            val cap = caps(size, defaults)
            assertTrue(size.width < width)
            assertEquals(0, size.offsetX)
            assertEquals(cap.first, cap.second, 0.2f)
            assertTrue(cap.first <= 88.01f)
        }
    }

    @Test fun increasingSpaceHasNoPhoneTabletBreakpoint() {
        val defaults = LetterKeyboardDefaults(1500)
        var previous = defaultLetterKeyboardSize(280, 280, defaults)
        for (width in 281..1600) {
            val size = defaultLetterKeyboardSize(width, width, defaults)
            assertTrue(size.width >= previous.width)
            assertTrue(size.width - previous.width <= 1)
            assertTrue(size.height >= previous.height)
            assertTrue(size.height - previous.height <= 1)
            previous = size
        }
    }

    @Test fun explicitGapsAndFloatingPaddingStillProduceSquareCaps() {
        for (floating in listOf(false, true))
            for ((x, y) in listOf(null to null, 2f to 2f, 0f to 0f, 8f to 16f, 20f to 6f)) {
                val defaults = LetterKeyboardDefaults(1500, x, y, floating)
                val size = defaultLetterKeyboardSize(934, 934, defaults)
                val cap = caps(size, defaults)
                assertEquals("$defaults", cap.first, cap.second, 0.2f)
            }
    }

    @Test fun preferredFloatingWidthIsRespectedWithoutUsingScreenOrientation() {
        val defaults = LetterKeyboardDefaults(1493, floating = true)
        val size = defaultLetterKeyboardSize(934, 728, defaults)
        assertEquals(728, size.width)
        val cap = caps(size, defaults)
        assertEquals(cap.first, cap.second, 0.2f)
    }

    @Test fun manualDimensionsIgnoreNewDefaultTargets() {
        val limits = KeyboardAspectLimits.Letters
        for (width in listOf(360, 600, 1000)) for (height in listOf(280, 400, 500)) {
            val before = protectKeyboardSize(1600, width, height, 40, limits, customSize = true)
            val after = protectKeyboardSize(1600, width, height, 40, limits, customSize = true,
                letterDefaults = LetterKeyboardDefaults(1000))
            assertEquals(before, after)
        }
    }

    @Test fun defaultsReplaceScreenPercentageHeightOnlyWhenNotCustom() {
        val defaults = LetterKeyboardDefaults(1493)
        val first = protectKeyboardSize(934, 934, 522, 0, KeyboardAspectLimits.Letters,
            letterDefaults = defaults)
        val second = protectKeyboardSize(934, 934, 280, 0, KeyboardAspectLimits.Letters,
            letterDefaults = defaults)
        assertEquals(first, second)
        assertEquals(defaultLetterKeyboardSize(934, 934, defaults), first)
    }

    @Test fun smallTallWideAndFoldedWindowMatrixFitsWithoutRewritingItsInputs() {
        for (width in listOf(180, 280, 360, 600, 800, 934, 1493, 2200))
            for (height in listOf(120, 228, 393, 600, 934, 1493, 2200)) {
                val defaults = LetterKeyboardDefaults(height)
                val size = defaultLetterKeyboardSize(width, width, defaults)
                assertTrue(size.width in 1..width)
                assertTrue(size.height in 1..height)
                assertEquals(size, defaultLetterKeyboardSize(width, size.width, defaults))
            }
        assertEquals(ProtectedKeyboardSize(360, 40, 0),
            defaultLetterKeyboardSize(360, 360, LetterKeyboardDefaults(40)))
    }
}
