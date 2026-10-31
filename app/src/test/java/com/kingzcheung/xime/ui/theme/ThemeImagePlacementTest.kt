package com.kingzcheung.xime.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 「我的图片」调整页：吸附、留白与换尺寸后按同一意思摆放。 */
class ThemeImagePlacementTest {
    private val eps = 0.01f

    private fun assertFrame(expected: ThemeImageFrame, actual: ThemeImageFrame) {
        assertEquals(expected.left, actual.left, eps)
        assertEquals(expected.top, actual.top, eps)
        assertEquals(expected.width, actual.width, eps)
        assertEquals(expected.height, actual.height, eps)
    }

    @Test fun defaultsMatchTheOldCentredCover() {
        // 4:3 图片放进 1000×500 键盘：按宽铺满，上下各截 125。
        assertFrame(ThemeImageFrame(0f, -125f, 1000f, 750f), placeThemeImage(400f, 300f, 1000f, 500f, "cover"))
        assertFrame(ThemeImageFrame(0f, -125f, 1000f, 750f), placeThemeImage(400f, 300f, 1000f, 500f, ThemeImagePlacement.DEFAULT))
        assertTrue(placeThemeImage(400f, 300f, 1000f, 500f, "cover").covers(1000f, 500f))
    }

    @Test fun fitHeightLeavesBothSidesBlankAndKeepsAlignment() {
        val left = placeThemeImage(400f, 300f, 1000f, 500f, ThemeImagePlacement("fit_height", alignX = 0f))
        assertFrame(ThemeImageFrame(0f, 0f, 666.67f, 500f), left)
        assertFalse(left.covers(1000f, 500f))
        val centred = placeThemeImage(400f, 300f, 1000f, 500f, ThemeImagePlacement("fit_height"))
        assertEquals(166.67f, centred.left, eps)
    }

    @Test fun fitWidthLeavesTopAndBottomBlank() {
        val frame = placeThemeImage(300f, 100f, 1000f, 500f, ThemeImagePlacement("fit_width", alignY = 1f))
        assertFrame(ThemeImageFrame(0f, 166.67f, 1000f, 333.33f), frame)
    }

    @Test fun widthOrHeightSnapsToTheKeyboardWithinThreshold() {
        // 宽 990 离 1000 只差 10：贴齐宽度，左边吸到 0。
        val snapped = snapThemeImage(ThemeImageFrame(4f, 50f, 990f, 330f), 1000f, 500f, 12f)
        assertEquals(1000f, snapped.width, eps)
        assertEquals(0f, snapped.left, eps)
        // 高 492 离 500 差 8：贴齐高度，图片窄于键盘时两边留白。
        val tall = snapThemeImage(ThemeImageFrame(100f, 3f, 328f, 492f), 1000f, 500f, 12f)
        assertEquals(500f, tall.height, eps)
        assertEquals(0f, tall.top, eps)
        assertEquals("fit_height", placementOf(tall, 200f, 300f, 1000f, 500f).fit)
    }

    @Test fun edgesAndCentreSnapButNothingElse() {
        val nearLeft = snapThemeImage(ThemeImageFrame(9f, 100f, 300f, 200f), 1000f, 500f, 12f)
        assertEquals(0f, nearLeft.left, eps)
        val nearRight = snapThemeImage(ThemeImageFrame(694f, 100f, 300f, 200f), 1000f, 500f, 12f)
        assertEquals(700f, nearRight.left, eps)
        val nearCentre = snapThemeImage(ThemeImageFrame(345f, 100f, 300f, 200f), 1000f, 500f, 12f)
        assertEquals(350f, nearCentre.left, eps)
        val free = snapThemeImage(ThemeImageFrame(200f, 100f, 300f, 200f), 1000f, 500f, 12f)
        assertEquals(200f, free.left, eps)
        assertEquals(100f, free.top, eps)
    }

    @Test fun smallImagesStayInsideAndLargeOnesNeverShowBlank() {
        val small = clampThemeImage(ThemeImageFrame(-50f, 400f, 300f, 200f), 1000f, 500f)
        assertEquals(0f, small.left, eps)
        assertEquals(300f, small.top, eps)
        val large = clampThemeImage(ThemeImageFrame(30f, -900f, 1200f, 1000f), 1000f, 500f)
        assertEquals(0f, large.left, eps)
        assertEquals(-500f, large.top, eps)
    }

    @Test fun placementRoundTripsAndKeepsItsMeaningAtOtherSizes() {
        val src = 400f to 300f
        // 竖屏键盘里放大 1.5 倍、靠右下，换到横屏宽键盘仍盖满且靠右下。
        val portrait = placeThemeImage(src.first, src.second, 1000f, 700f, ThemeImagePlacement("cover", 1.5f, 1f, 1f))
        val placement = placementOf(portrait, src.first, src.second, 1000f, 700f)
        assertEquals("cover", placement.fit)
        assertEquals(1.5f, placement.zoom, eps)
        assertEquals(1f, placement.alignX, eps)
        assertEquals(1f, placement.alignY, eps)
        val landscape = placeThemeImage(src.first, src.second, 2400f, 600f, placement)
        assertTrue(landscape.covers(2400f, 600f))
        assertEquals(2400f, landscape.right, eps)
        assertEquals(600f, landscape.bottom, eps)
        // 上下对齐换尺寸后仍上下对齐。
        val fit = placeThemeImage(src.first, src.second, 2400f, 600f, ThemeImagePlacement("fit_height", alignX = 0f))
        assertEquals(600f, fit.height, eps)
        assertEquals(0f, fit.left, eps)
    }

    @Test fun storedValuesSurviveEncodingAndRejectGarbage() {
        val placement = ThemeImagePlacement("contain", 0.6f, 0.25f, 1f)
        assertEquals(placement, ThemeImagePlacement.decode(placement.encode()))
        assertEquals(ThemeImagePlacement.DEFAULT, ThemeImagePlacement.decode(null))
        assertEquals(ThemeImagePlacement.DEFAULT, ThemeImagePlacement.decode("stretch,1,0.5,0.5"))
        assertEquals(ThemeImagePlacement.DEFAULT, ThemeImagePlacement.decode("cover,NaN,0.5,0.5"))
        assertEquals(1f, ThemeImagePlacement.decode("cover,1,7,0.5").alignX, eps)
    }
}
