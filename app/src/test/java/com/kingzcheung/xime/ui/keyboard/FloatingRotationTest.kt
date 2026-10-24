package com.kingzcheung.xime.ui.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class FloatingRotationTest {
    private val hostW = 800f
    private val hostH = 1200f
    private val cardW = 600f
    private val cardH = 320f
    private val inset = 24f

    /** 按 offset 摆好的卡片转过 [deg] 后，局部点 (vx, vy)（相对中心）在宿主里的位置。 */
    private fun point(offX: Float, offY: Float, deg: Float, vx: Float, vy: Float): Pair<Float, Float> {
        val cx = hostW / 2f + offX
        val cy = hostH - offY - cardH / 2f
        val r = Math.toRadians(deg.toDouble())
        return (cx + vx * cos(r).toFloat() - vy * sin(r).toFloat()) to (cy + vx * sin(r).toFloat() + vy * cos(r).toFloat())
    }

    private fun onScreen(p: Pair<Float, Float>) =
        p.first >= -0.01f && p.first <= hostW + 0.01f && p.second >= -0.01f && p.second <= hostH - inset + 0.01f

    @Test fun toolbarAndDragButtonStayOnScreenAtEveryExtreme() {
        for (deg in listOf(-90f, -60f, -30f, 15f, 45f, 90f)) {
            val b = floatingRotatedOffsetBounds(hostW, hostH, cardW, cardH, deg, inset)
            assertTrue("$deg feasible", b.feasible)
            for (x in listOf(b.minX, b.maxX)) for (y in listOf(b.minY, b.maxY)) {
                val top = -cardH / 2f
                for (vx in listOf(-cardW / 2f, cardW / 2f)) {
                    assertTrue("toolbar $deg", onScreen(point(x, y, deg, vx, top)))
                    assertTrue("toolbar bottom $deg", onScreen(point(x, y, deg, vx, top + FLOATING_ROTATE_TOP_STRIP_DP)))
                }
                assertTrue("drag button $deg", onScreen(point(x, y, deg, 0f, cardH / 2f - FLOATING_DRAG_BAR_HEIGHT_DP / 2f)))
            }
        }
    }

    @Test fun bottomCornerMayLeaveScreenWhenTilted() {
        val deg = 20f
        val b = floatingRotatedOffsetBounds(hostW, hostH, cardW, cardH, deg, inset)
        // 贴着最低处时，转下去的那个底角已经出了屏幕，但拖动按钮还在屏内。
        val corner = point(0f, b.minY, deg, cardW / 2f, cardH / 2f)
        assertTrue(corner.second > hostH - inset)
        assertTrue(onScreen(point(0f, b.minY, deg, 0f, cardH / 2f - FLOATING_DRAG_BAR_HEIGHT_DP / 2f)))
    }

    @Test fun wideCardCannotTurnUprightOnShortScreen() {
        // 横屏手机：卡片比屏高还宽，竖起来时顶部条放不下，旋转停在放得下的角度。
        val feasible = { d: Float -> floatingRotatedOffsetBounds(900f, 400f, 700f, 260f, d, 0f).feasible }
        assertFalse(feasible(90f))
        val limited = limitFloatingRotation(0f, 90f, feasible)
        assertTrue(limited > 10f && limited < 90f)
        assertTrue(feasible(limited))
        assertFalse(feasible(limited + 1f))
    }

    @Test fun snapsNearStraightAndQuarterTurns() {
        assertEquals(0f, snapFloatingRotation(3f), 0f)
        assertEquals(90f, snapFloatingRotation(87f), 0f)
        assertEquals(-90f, snapFloatingRotation(-120f), 0f)
        assertEquals(30f, snapFloatingRotation(30f), 0f)
    }

    @Test fun angleDeltaWrapsAcrossHalfTurn() {
        assertEquals(20f, floatingAngleDelta(170f, -170f), 0.001f)
        assertEquals(-20f, floatingAngleDelta(-170f, 170f), 0.001f)
    }

    @Test fun touchQuadFollowsRotation() {
        val card = ResizeRect(100f, 200f, 500f, 400f)
        val body = floatingRotatedTouchQuad(card, 90f)
        // 转 90°：左上角 (100,200) 转到 (400,100)。
        assertEquals(400f, body[0], 0.01f)
        assertEquals(100f, body[1], 0.01f)
    }

    @Test fun touchStripsCoverTheRotatedCardWithFewRects() {
        val card = ResizeRect(100f, 200f, 700f, 500f)
        val strips = floatingRotatedTouchStrips(card, 30f)
        assertTrue(strips.size <= 12)
        // 卡片里的点都在某一条里：斜边处不会漏触摸。
        for (u in listOf(0.02f, 0.5f, 0.98f)) for (v in listOf(0.02f, 0.5f, 0.98f)) {
            val (x, y) = rotatePoint(card.left + card.width * u, card.top + card.height * v, card.centerX, card.centerY, 30f)
            assertTrue("$u,$v", strips.any { x >= it.left && x <= it.right && y >= it.top && y <= it.bottom })
        }
        // 外接框的角落不在任何一条里：露出的空角留给应用。
        val quad = floatingRotatedTouchQuad(card, 30f)
        val minX = minOf(quad[0], quad[2], quad[4], quad[6]) + 2f
        val minY = minOf(quad[1], quad[3], quad[5], quad[7]) + 2f
        assertFalse(strips.any { minX >= it.left && minX <= it.right && minY >= it.top && minY <= it.bottom })
        assertEquals(listOf(card), floatingRotatedTouchStrips(card, 0f))
    }
}
