package com.kingzcheung.xime.ui.keyboard

import org.junit.Assert.*
import org.junit.Test

class KeyboardDefaultGeometryTest {
    private val calibratedPitch = KeyboardPhysicalMetrics.from(1.875f, 266.471f)!!
        .dpFromMillimeters(CALIBRATED_QWERTY_PITCH_MM)
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
        // Phone landscape (about 393dp tall) now fills the width: see phoneLandscapeFillsWidthWithWideCaps.
        for ((width, height) in listOf(1493 to 934, 2200 to 700, 960 to 600, 960 to 552)) {
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

    @Test fun calibratedLandscapeTabletReproducesChosenWidthWithinExistingHeightBounds() {
        val defaults = LetterKeyboardDefaults(862, calibratedMaxCellWidthDp = calibratedPitch,
            maximumHeightDp = keyboardHeightBounds(862, true).last)
        val size = defaultLetterKeyboardSize(1493, 1493, defaults)
        assertEquals(1000, size.width)
        assertEquals(431, size.height)
        assertEquals(0, size.offsetX)
        val cap = caps(size, defaults)
        assertEquals(cap.first, cap.second, 0.2f)
        assertTrue(size.height <= defaults.maximumHeightDp!!)
    }

    @Test fun narrowPhoneAndUnknownDisplayKeepTheirExistingDefaults() {
        for (height in listOf(120, 228, 393, 600, 800, 1500)) {
            val original = LetterKeyboardDefaults(height)
            val calibrated = original.copy(calibratedMaxCellWidthDp = calibratedPitch, maximumHeightDp = height)
            for (width in listOf(280, 320, 360, 393, 412))
                assertEquals("$width x $height", defaultLetterKeyboardSize(width, width, original),
                    defaultLetterKeyboardSize(width, width, calibrated))
        }
        for (pitch in listOf(null, 0f, -1f, Float.NaN, Float.POSITIVE_INFINITY)) {
            val original = LetterKeyboardDefaults(934)
            assertEquals(defaultLetterKeyboardSize(1493, 1493, original),
                defaultLetterKeyboardSize(1493, 1493, original.copy(calibratedMaxCellWidthDp = pitch)))
        }
    }

    @Test fun physicalDefaultsCannotOverrideAnyPreviouslySavedDimensionsOrPosition() {
        val defaults = LetterKeyboardDefaults(934, calibratedMaxCellWidthDp = calibratedPitch,
            maximumHeightDp = keyboardHeightBounds(934, true).last)
        for (width in listOf(360, 728, 1005, 1493)) for (height in listOf(276, 433, 600))
            for (offset in listOf(-244, 0, 244)) {
                val before = protectKeyboardSize(1493, width, height, offset, KeyboardAspectLimits.Letters,
                    customSize = true)
                assertEquals(before, protectKeyboardSize(1493, width, height, offset, KeyboardAspectLimits.Letters,
                    customSize = true, letterDefaults = defaults))
            }
        assertEquals(ProtectedKeyboardSize(1005, 433, -244),
            protectKeyboardSize(1493, 1005, 433, -244, KeyboardAspectLimits.Letters,
                customSize = true, letterDefaults = defaults))
    }

    @Test fun calibratedDefaultKeepsUserGapValuesAndCannotExceedRealHeightBounds() {
        for ((x, y) in listOf(null to null, 0f to 0f, 8f to 16f, 20f to 6f)) {
            val defaults = LetterKeyboardDefaults(1500, spacingX = x, spacingY = y,
                calibratedMaxCellWidthDp = calibratedPitch, maximumHeightDp = 500)
            val size = defaultLetterKeyboardSize(1493, 1493, defaults)
            assertEquals(1000, size.width)
            assertEquals(x, defaults.spacingX)
            assertEquals(y, defaults.spacingY)
            val cap = caps(size, defaults)
            assertEquals(cap.first, cap.second, 0.2f)
            val constrained = defaultLetterKeyboardSize(1493, 1493, defaults.copy(maximumHeightDp = 300))
            assertTrue(constrained.width < size.width)
            assertTrue(constrained.height <= 300)
        }
    }

    @Test fun calibratedWidthBudgetHasNoThresholdJumpAndDoesNotShrinkWhenResolvedAgain() {
        for (height in listOf(393, 600, 934, 1500)) {
            val defaults = LetterKeyboardDefaults(height, calibratedMaxCellWidthDp = calibratedPitch,
                maximumHeightDp = height)
            var previous = defaultLetterKeyboardSize(280, 280, defaults)
            for (available in 281..1600) {
                val next = defaultLetterKeyboardSize(available, available, defaults)
                assertTrue("width $available/$height: $previous -> $next", next.width >= previous.width)
                assertTrue("width jump $available/$height: $previous -> $next", next.width - previous.width <= 3)
                assertTrue("height $available/$height: $previous -> $next", next.height >= previous.height)
                assertTrue("height jump $available/$height: $previous -> $next", next.height - previous.height <= 1)
                assertTrue(next.width <= available)
                assertTrue(next.height <= height)
                assertEquals("re-resolve $available/$height", next,
                    defaultLetterKeyboardSize(available, next.width, defaults))
                previous = next
            }
        }
    }

    @Test fun shortViewportRegressionDoesNotSpendAlmostItsWholeHeightOnTheDefaultKeyboard() {
        val defaults = LetterKeyboardDefaults(393, calibratedMaxCellWidthDp = calibratedPitch,
            maximumHeightDp = 393)
        val left = defaultLetterKeyboardSize(832, 832, defaults)
        val right = defaultLetterKeyboardSize(833, 833, defaults)
        // Was a 4dp jump at this width; phone landscape now fills it, so one dp more is one dp wider.
        assertTrue("$left -> $right", right.width - left.width in 0..1)
        assertEquals(left.height, right.height)
        assertTrue(right.height <= 228) // Existing narrow-window minimum is the only 50% exception.
    }

    @Test fun highPhysicalDpiAndVeryWideViewportsCannotRaiseDefaultHeightToNinetyPercent() {
        for (density in listOf(1f, 1.875f, 3f)) for (xDpi in listOf(120f, 266.471f, 480f, 1000f)) {
            val pitch = KeyboardPhysicalMetrics.from(density, xDpi)!!
                .dpFromMillimeters(CALIBRATED_QWERTY_PITCH_MM)
            for (height in listOf(600, 862, 1200)) for (width in listOf(1000, 2000, 6000)) {
                val defaults = LetterKeyboardDefaults(height, calibratedMaxCellWidthDp = pitch,
                    maximumHeightDp = height)
                val size = defaultLetterKeyboardSize(width, width, defaults)
                assertTrue("$density/$xDpi $width x $height -> $size", size.height <= height / 2)
                assertTrue(size.width in 1..width)
                assertEquals(size, defaultLetterKeyboardSize(width, size.width, defaults))
            }
        }
    }

    /** 11-05: phone landscape fills the width at the same 228dp height (his screenshot about 760x228);
     * caps get wider than tall instead of the keyboard narrowing to about 459dp and centering. */
    @Test fun phoneLandscapeFillsWidthWithWideCaps() {
        for (height in listOf(320, 340, 360, 384, 393, 412, 430))
            for (width in listOf(640, 700, 760, 780, 823, 860, 915))
                for (pitch in listOf(null, calibratedPitch, KeyboardPhysicalMetrics.from(2.8125f, 500f)!!
                    .dpFromMillimeters(CALIBRATED_QWERTY_PITCH_MM))) {
                    val defaults = LetterKeyboardDefaults(height, calibratedMaxCellWidthDp = pitch,
                        maximumHeightDp = pitch?.let { keyboardHeightBounds(height, true).last.coerceAtMost(height) })
                    val size = defaultLetterKeyboardSize(width, width, defaults)
                    assertEquals("$width x $height / $pitch", ProtectedKeyboardSize(width, 228, 0), size)
                    val cap = caps(size, defaults)
                    assertTrue("$width x $height: $cap", cap.first > cap.second)
                    assertTrue("$width x $height: $cap", cap.first / cap.second <= DEFAULT_WIDE_CAP_ASPECT + 0.05f)
                    assertEquals(size, defaultLetterKeyboardSize(width, size.width, defaults))
                }
    }

    /** Tablets, phone portrait and 8-inch tablet landscape keep the square rule exactly. */
    @Test fun squareRuleIsExactFromTheUpperAnchorUp() {
        for (height in DEFAULT_SQUARE_CAP_HEIGHT_DP.toInt()..2400) assertEquals(1f, defaultLetterCapAspect(height))
        for (height in 0..DEFAULT_WIDE_CAP_FULL_HEIGHT_DP.toInt())
            assertEquals(DEFAULT_WIDE_CAP_ASPECT, defaultLetterCapAspect(height))
        var previous = defaultLetterCapAspect(0)
        for (height in 1..2400) {
            val next = defaultLetterCapAspect(height)
            assertTrue(next <= previous)
            assertTrue("$height", previous - next <= 0.025f)
            previous = next
        }
        val tablet = LetterKeyboardDefaults(862, calibratedMaxCellWidthDp = calibratedPitch,
            maximumHeightDp = keyboardHeightBounds(862, true).last)
        assertEquals(ProtectedKeyboardSize(1000, 431, 0), defaultLetterKeyboardSize(1493, 1493, tablet))
        assertEquals(ProtectedKeyboardSize(934, 404, 0),
            defaultLetterKeyboardSize(934, 934, LetterKeyboardDefaults(1421)))
    }

    /** Growing only the available height (phone landscape -> tablet) narrows the default smoothly. */
    @Test fun availableHeightSweepHasNoPhoneTabletStep() {
        for (width in listOf(800, 960, 1200)) for (pitch in listOf(null, calibratedPitch)) {
            fun at(height: Int) = defaultLetterKeyboardSize(width, width, LetterKeyboardDefaults(height,
                calibratedMaxCellWidthDp = pitch, maximumHeightDp = pitch?.let { height }))
            var previous = at(300)
            for (height in 301..1600) {
                val next = at(height)
                assertTrue("$width/$pitch $height: $previous -> $next", kotlin.math.abs(next.width - previous.width) <= 12)
                assertTrue("$width/$pitch $height: $previous -> $next", kotlin.math.abs(next.height - previous.height) <= 1)
                previous = next
            }
        }
    }
}
