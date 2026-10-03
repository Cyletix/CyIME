package com.kingzcheung.xime.ui.keyboard

import org.junit.Assert.*
import org.junit.Test

class KeyboardAspectGeometryTest {
    private val letters = KeyboardAspectLimits.Letters

    @Test fun wideTabletIsNarrowedAndCenteredWithoutChangingHeight() {
        val size = protectKeyboardSize(1493, 1493, 366, 230, letters)
        assertEquals(750, size.width)
        assertEquals(366, size.height)
        assertEquals(0, size.offsetX)
    }

    @Test fun narrowSavedKeyboardWidensBeforeLosingHeight() {
        val size = protectKeyboardSize(934, 360, 500, -180, letters)
        assertEquals(621, size.width)
        assertEquals(500, size.height)
        assertEquals(0, size.offsetX)
    }

    @Test fun narrowWindowLowersKeyboardWhenWideningIsImpossible() {
        val size = protectKeyboardSize(280, 400, 600, 20, letters)
        assertEquals(280, size.width)
        assertEquals(252, size.height)
        assertEquals(0, size.offsetX)
    }

    @Test fun ordinaryPhoneAndValidManualPositionArePreserved() {
        assertEquals(ProtectedKeyboardSize(360, 280, 0), protectKeyboardSize(360, 360, 280, 0, letters))
        assertEquals(ProtectedKeyboardSize(600, 350, -210), protectKeyboardSize(1200, 600, 350, -210, letters))
    }

    @Test fun confirmedCustomProportionsArePreservedOutsidePreferredRange() {
        val custom = protectKeyboardSize(1600, 1000, 350, 80, letters, customSize = true)
        assertEquals(ProtectedKeyboardSize(1000, 350, 80), custom)
        assertTrue(protectKeyboardSize(1600, 1000, 350, 80, letters).width < custom.width)
        val extreme = protectKeyboardSize(2000, 1900, 250, 0, letters, customSize = true)
        assertEquals(1900, extreme.width)
    }

    @Test fun everyLayoutCanReachBothWindowEdgesAndKeepSavedFullWidth() {
        val layouts = listOf(letters, KeyboardAspectLimits.forLayout(
            com.kingzcheung.xime.settings.LayoutKind.ALPHABETIC, true), KeyboardAspectLimits.T9)
        for (limits in layouts) for (density in listOf(1f, 2.5f)) {
            val bounds = ResizeRect(0f, 0f, 1600f * density, 900f * density)
            var start = ResizeRect(400f * density, 400f * density, 1000f * density, 750f * density)
            for (handle in listOf(ResizeHandle.RIGHT, ResizeHandle.LEFT)) {
                val raw = if (handle == ResizeHandle.RIGHT) start.copy(right = bounds.right)
                    else start.copy(left = bounds.left)
                val result = start.resizeWithReachableWidth(raw, handle, bounds, limits, density, 0f)
                assertEquals(raw, result)
                start = result
            }
            val saved = protectKeyboardSize(1600, 1600, 350, 0, limits, customSize = true)
            assertEquals(1600, saved.width)
            assertEquals(saved, protectKeyboardSize(1600, saved.width, saved.height,
                saved.offsetX, limits, customSize = true))
        }
    }

    @Test fun edgeApproachIsContinuousMonotonicAndReversible() {
        val bounds = ResizeRect(0f, 0f, 1600f, 900f)
        val start = ResizeRect(0f, 400f, 600f, 750f)
        for (limits in listOf(letters, KeyboardAspectLimits.T9)) {
            var previous = start
            for (x in 601..1600) {
                val raw = start.copy(right = x.toFloat())
                val result = start.resizeWithReachableWidth(raw, ResizeHandle.RIGHT, bounds, limits, 1f, 0f)
                assertTrue(result.right >= previous.right)
                assertTrue("No sudden jumps: $previous -> $result", result.right - previous.right < 10f)
                assertEquals(start.left, result.left, 0f)
                assertEquals(start.height, result.height, 0f)
                previous = result
            }
            assertEquals(bounds.right, previous.right, 0f)
            assertEquals(start, start.resizeWithReachableWidth(start, ResizeHandle.RIGHT, bounds, limits, 1f, 0f))
        }
    }

    @Test fun splitGapChangesResistanceWithoutChangingT9WhenSplitPreferenceIsEnabled() {
        val kind = com.kingzcheung.xime.settings.LayoutKind.ALPHABETIC
        val split = KeyboardAspectLimits.forLayout(kind, true)
        assertTrue(split.max > letters.max)
        assertEquals(KeyboardAspectLimits.T9, KeyboardAspectLimits.forLayout(
            com.kingzcheung.xime.settings.LayoutKind.T9, true))
        val start = ResizeRect(0f, 0f, 700f, 350f)
        val raw = start.copy(right = 950f)
        val bounds = ResizeRect(0f, 0f, 1600f, 900f)
        val fullResult = start.resizeWithReachableWidth(raw, ResizeHandle.RIGHT, bounds, letters, 1f, 0f)
        val splitResult = start.resizeWithReachableWidth(raw, ResizeHandle.RIGHT, bounds, split, 1f, 0f)
        val t9Result = start.resizeWithReachableWidth(raw, ResizeHandle.RIGHT, bounds, KeyboardAspectLimits.T9, 1f, 0f)
        assertTrue(splitResult.right > fullResult.right)
        assertTrue(t9Result.right < fullResult.right)
    }

    @Test fun nearbyDragIsUnchangedAndCanCrossThePreferredBoundary() {
        val initial = ResizeRect(100f, 100f, 800f, 450f)
        val inside = initial.copy(right = initial.right - 8f)
        assertEquals(inside, initial.resistKeyboardAspectDrag(inside, letters, 1f, 0f))
        val justOutside = initial.copy(right = initial.right + 20f)
        val result = initial.resistKeyboardAspectDrag(justOutside, letters, 1f, 0f)
        assertEquals(justOutside.right, result.right, 0.1f)
        assertTrue(result.width > letters.maxWidth(result.height))
    }

    @Test fun resistanceGrowsSmoothlyAndReturningIsFree() {
        val near = ResizeRect(100f, 100f, 820f, 450f)
        val far = near.copy(right = 1300f)
        val nearMove = near.resistKeyboardAspectDrag(near.copy(right = near.right + 20f), letters, 1f, 0f).right - near.right
        val farMove = far.resistKeyboardAspectDrag(far.copy(right = far.right + 20f), letters, 1f, 0f).right - far.right
        assertTrue(nearMove > 19f)
        assertTrue(farMove > 0f && farMove < 5f) // Same 20px gesture: below a quarter at a remote shape.
        val returning = far.copy(right = far.right - 20f)
        assertEquals(returning, far.resistKeyboardAspectDrag(returning, letters, 1f, 0f))
    }

    @Test fun repeatedDragsOnlyStopAtExtremeAndCanAlwaysReturn() {
        var rect = ResizeRect(100f, 100f, 800f, 450f)
        repeat(500) {
            rect = rect.resistKeyboardAspectDrag(rect.copy(right = rect.right + 20f), letters, 1f, 0f)
            assertTrue(rect.hasKeyboardAspect(letters.extremes, 1f, 0f))
        }
        assertTrue(rect.width > letters.maxWidth(rect.height) * 1.8f)
        val returning = rect.copy(right = rect.right - 20f)
        assertEquals(returning, rect.resistKeyboardAspectDrag(returning, letters, 1f, 0f))
    }

    @Test fun resistanceChangesGraduallyAcrossTheWholeOutwardRange() {
        for (limits in listOf(letters, KeyboardAspectLimits.Keypad)) {
            var previousResponse = 1f
            for (percent in 0..99) {
                val width = 16f + 300f * limits.max * (1f + percent / 100f)
                val rect = ResizeRect(0f, 0f, width, 360f)
                val moved = rect.resistKeyboardAspectDrag(rect.copy(right = width + 2f), limits, 1f, 0f)
                val response = (moved.width - width) / 2f
                assertTrue("response must decrease gradually at $percent%: $response", response <= previousResponse + 0.001f)
                assertTrue("no sudden resistance step at $percent%", previousResponse - response < 0.03f)
                if (percent <= 5) assertTrue(response > 0.985f)
                if (percent >= 75) assertTrue(response < 0.1f)
                previousResponse = response
            }
        }
    }

    @Test fun fastAndSlowTouchEventsFollowTheSameResistedPath() {
        for (density in listOf(1f, 1.875f, 3f)) for (vertical in listOf(false, true)) {
            val initial = ResizeRect(100f * density, 500f * density, 800f * density, 860f * density)
            fun drag(events: Int): ResizeRect {
                var rect = initial
                repeat(events) {
                    val delta = 700f * density / events
                    val proposed = if (vertical) rect.copy(top = rect.top - delta)
                        else rect.copy(right = rect.right + delta)
                    rect = rect.resistKeyboardAspectDrag(proposed, letters, density, 0f)
                }
                return rect
            }
            val reference = drag(700)
            for (events in listOf(1, 7, 35, 140)) {
                val result = drag(events)
                assertEquals(reference.width / density, result.width / density, 0.1f)
                assertEquals(reference.height / density, result.height / density, 0.1f)
            }
        }
    }

    @Test fun proportionalResizeOutsidePreferredRangeDoesNotAddResistance() {
        val initial = ResizeRect(0f, 0f, 1096f, 360f) // Key body 1080 x 300.
        val proposed = initial.copy(right = 1204f, bottom = 390f) // Same ratio, 10% larger.
        val result = initial.resistKeyboardAspectDrag(proposed, letters, 1f, 0f)
        assertEquals(proposed.right, result.right, 0.01f)
        assertEquals(proposed.bottom, result.bottom, 0.01f)
    }

    @Test fun windowAndSavedSizeMatrixIsBoundedAndIdempotent() {
        // Phone, narrow multi-window, unfolded square, portrait/landscape tablet and desktop window.
        for (limits in listOf(letters, KeyboardAspectLimits.Keypad))
            for (available in listOf(180, 280, 360, 600, 720, 934, 1493, 2200))
                for (width in listOf(200, 360, 700, 1493, 3000))
                    for (height in listOf(160, 228, 280, 400, 650, 1000)) {
                        val size = protectKeyboardSize(available, width, height, 5000, limits)
                        assertTrue(size.width in 1..available)
                        assertTrue(size.height in 1..height)
                        assertTrue(kotlin.math.abs(size.offsetX) <= (available - size.width) / 2)
                        assertTrue(size.width >= limits.minWidth(size.height.toFloat()) - 1f)
                        assertTrue(size.width <= limits.maxWidth(size.height.toFloat()) + 1f)
                        assertEquals(size, protectKeyboardSize(available, size.width, size.height, size.offsetX, limits))
                    }
    }

    @Test fun rotationUsesWindowNotOrientationSpecificAspectAndDoesNotAlterPreferences() {
        val savedWidth = 1000
        val savedHeight = 450
        val wide = protectKeyboardSize(1493, savedWidth, savedHeight, 0, letters)
        val portrait = protectKeyboardSize(934, savedWidth, savedHeight, 0, letters)
        assertEquals(wide.height, portrait.height)
        assertEquals(wide, protectKeyboardSize(1493, savedWidth, savedHeight, 0, letters))
        assertEquals(1000, savedWidth)
        assertEquals(450, savedHeight)
    }

    @Test fun floatingDragBarAndFixedBottomSpaceDoNotChangeKeyProportions() {
        for (density in listOf(1f, 1.875f, 3f)) for (extraDp in listOf(0, 24, 60, 80)) {
            val extra = extraDp * density
            val initial = ResizeRect(200f * density, 100f * density, 800f * density, (450f + extraDp) * density)
            assertTrue(initial.hasKeyboardAspect(letters, density, extra))
            val tooWide = initial.copy(right = 1600f * density)
            val limited = initial.resistKeyboardAspectDrag(tooWide, letters, density, extra)
            assertEquals(initial.left, limited.left, 0f)
            assertEquals(initial.top, limited.top, 0f)
            assertEquals(initial.bottom, limited.bottom, 0f)
            assertTrue(limited.hasKeyboardAspect(letters.extremes, density, extra))
            assertTrue(limited.width > letters.maxWidth((limited.height - extra) / density) * density)
            assertTrue(limited.right < tooWide.right)
        }
    }

    @Test fun allEdgesAndCornersRespectOnlyExtremeLimits() {
        val bounds = ResizeRect(0f, 0f, 1600f, 1600f)
        val initial = ResizeRect(500f, 500f, 1100f, 860f)
        for (limits in listOf(letters, KeyboardAspectLimits.Keypad))
            for (edge in ResizeHandle.entries.filter { it != ResizeHandle.NONE })
                for (delta in listOf(-1000f, 1000f)) {
                    val proposal = initial.dragBy(edge, delta, delta, bounds, 180f, 160f)
                    val result = initial.resistKeyboardAspectDrag(proposal, limits, 1f, 0f)
                    assertTrue("$limits $edge $delta $result", result.hasKeyboardAspect(limits.extremes, 1f, 0f))
                    if (proposal.left == initial.left) assertEquals(initial.left, result.left, 0f)
                    if (proposal.right == initial.right) assertEquals(initial.right, result.right, 0f)
                    if (proposal.top == initial.top) assertEquals(initial.top, result.top, 0f)
                    if (proposal.bottom == initial.bottom) assertEquals(initial.bottom, result.bottom, 0f)
                }
    }
}
