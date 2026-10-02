package com.kingzcheung.xime.ui.keyboard

import com.kingzcheung.xime.service.HardwareCursorAnchor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HardwareCandidateGeometryTest {
    private fun position(
        anchor: HardwareCursorAnchor? = null,
        width: Int = 500,
        height: Int = 400,
        cardWidth: Int = 180,
        cardHeight: Int = 100,
        originX: Int = 0,
        originY: Int = 0,
        margin: Int = 8,
        gap: Int = 8,
        avoidBounds: HardwareCandidateExclusion? = null,
    ) = hardwareCandidatePosition(width, height, cardWidth, cardHeight, anchor, originX, originY, margin, gap, avoidBounds)

    @Test fun followsCursorBelowItsStartInHostCoordinates() {
        val anchor = HardwareCursorAnchor(350f, 270f, 350f, 290f)
        assertEquals(HardwareCandidatePosition(250, 98), position(anchor, originX = 100, originY = 200))
        assertEquals(HardwareCandidatePosition(290, 98), position(anchor.copy(left = 390f, right = 390f), originX = 100, originY = 200))
    }

    @Test fun insufficientSpaceBelowMovesCardAboveCursor() {
        val anchor = HardwareCursorAnchor(100f, 350f, 100f, 365f)
        assertEquals(HardwareCandidatePosition(100, 242), position(anchor))
    }

    @Test fun rightEdgeUsesMeasuredCardWidth() {
        val anchor = HardwareCursorAnchor(490f, 100f, 490f, 120f)
        assertEquals(HardwareCandidatePosition(372, 128), position(anchor, cardWidth = 120))
        assertEquals(HardwareCandidatePosition(192, 128), position(anchor, cardWidth = 300))
    }

    @Test fun missingAndFullyOutsideAnchorsUseStableBottomCenter() {
        val fallback = HardwareCandidatePosition(160, 292)
        val anchors = listOf(
            null,
            HardwareCursorAnchor(-10f, 50f, -1f, 70f),
            HardwareCursorAnchor(501f, 50f, 510f, 70f),
            HardwareCursorAnchor(100f, -20f, 100f, -1f),
            HardwareCursorAnchor(100f, 401f, 100f, 420f),
        )
        anchors.forEach { assertEquals("anchor $it", fallback, position(it)) }
    }

    @Test fun zeroAndPartiallyVisibleAnchorsRemainUsable() {
        assertEquals(HardwareCandidatePosition(8, 8), position(HardwareCursorAnchor(0f, 0f, 0f, 0f)))
        assertEquals(HardwareCandidatePosition(8, 28), position(HardwareCursorAnchor(-3f, 0f, 2f, 20f)))
        assertEquals(HardwareCandidatePosition(8, 292), position(HardwareCursorAnchor(0f, 400f, 0f, 400f)))
    }

    @Test fun portraitLandscapeAndSplitWindowsClampToTheirOwnViewport() {
        for ((width, height) in listOf(400 to 800, 800 to 400, 240 to 380, 380 to 240)) {
            val originX = 600
            val originY = 120
            val anchor = HardwareCursorAnchor((originX + width - 2).toFloat(),
                (originY + height - 30).toFloat(), (originX + width - 2).toFloat(), (originY + height - 10).toFloat())
            assertEquals(HardwareCandidatePosition(width - 180 - 8, height - 30 - 100 - 8),
                position(anchor, width, height, originX = originX, originY = originY))
        }
    }

    @Test fun tinyAndNotYetMeasuredWindowsDoNotProduceInvalidRanges() {
        assertEquals(HardwareCandidatePosition(0, 0), position(width = 5, height = 7, cardWidth = 20, cardHeight = 30))
        assertEquals(HardwareCandidatePosition(0, 0), position(HardwareCursorAnchor(0f, 0f, 0f, 0f), width = 0, height = 0))
        assertEquals(HardwareCandidatePosition(10, 3), position(width = 120, height = 50, cardWidth = 100, cardHeight = 44, margin = 12))
    }

    @Test fun unusableAnchorGeometryFallsBackWithoutMovingToTop() {
        val fallback = position()
        assertEquals(fallback, position(HardwareCursorAnchor(Float.NaN, 50f, 100f, 70f)))
        assertEquals(fallback, position(HardwareCursorAnchor(100f, 50f, Float.POSITIVE_INFINITY, 70f)))
        assertEquals(fallback, position(HardwareCursorAnchor(100f, 80f, 100f, 70f)))
    }

    @Test fun bottomFallbackMovesAboveToolbarWithGap() {
        val toolbar = HardwareCandidateExclusion(140, 340, 360, 392)
        assertEquals(HardwareCandidatePosition(160, 232), position(avoidBounds = toolbar))
        assertEquals(HardwareCandidatePosition(160, 232), position(
            HardwareCursorAnchor(100f, 401f, 100f, 420f), avoidBounds = toolbar))
    }

    @Test fun unaffectedCaretPlacementRemainsExact() {
        val anchor = HardwareCursorAnchor(100f, 100f, 100f, 120f)
        val toolbar = HardwareCandidateExclusion(140, 340, 360, 392)
        assertEquals(position(anchor), position(anchor, avoidBounds = toolbar))
        assertEquals(position(anchor), position(anchor,
            avoidBounds = HardwareCandidateExclusion(10, 10, 10, 30)))
    }

    @Test fun collisionUsesNearestAvailableSideInsteadOfAlwaysBottom() {
        assertEquals(HardwareCandidatePosition(100, 208), position(
            HardwareCursorAnchor(100f, 100f, 100f, 120f),
            avoidBounds = HardwareCandidateExclusion(90, 120, 310, 200)))
        assertEquals(HardwareCandidatePosition(100, 62), position(
            HardwareCursorAnchor(100f, 140f, 100f, 160f),
            avoidBounds = HardwareCandidateExclusion(90, 170, 310, 330)))
        assertEquals(HardwareCandidatePosition(258, 8), position(
            HardwareCursorAnchor(150f, 140f, 150f, 160f), cardHeight = 280,
            avoidBounds = HardwareCandidateExclusion(130, 0, 250, 400)))
        assertEquals(HardwareCandidatePosition(62, 8), position(
            HardwareCursorAnchor(220f, 140f, 220f, 160f), cardHeight = 280,
            avoidBounds = HardwareCandidateExclusion(250, 0, 370, 400)))
    }

    @Test fun exclusionUsesHostCoordinatesIndependentlyOfScreenOrigin() {
        val toolbar = HardwareCandidateExclusion(90, 120, 310, 200)
        assertEquals(HardwareCandidatePosition(100, 208), position(
            HardwareCursorAnchor(700f, 300f, 700f, 320f), originX = 600, originY = 200,
            avoidBounds = toolbar))
    }

    @Test fun impossibleAvoidanceStillKeepsCardInsideSmallViewport() {
        for (width in listOf(180, 240, 400)) {
            for (height in listOf(100, 180, 400)) {
                val result = position(width = width, height = height,
                    avoidBounds = HardwareCandidateExclusion(0, 0, width, height))
                assertTrue(result.x >= 0 && result.x + 180 <= width)
                assertTrue(result.y >= 0 && result.y + 100 <= height)
            }
        }
    }
}
