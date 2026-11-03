package com.kingzcheung.xime.ui.theme

import com.kingzcheung.xime.settings.BackgroundMotionEffect
import com.kingzcheung.xime.settings.BackgroundMotionSpeed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** 动态背景的轨迹：盖满不露底、不跳、每帧移动不到一个像素、停住时不动；什么时候不动。 */
class KeyboardBackgroundMotionTest {
    private val frameMs = BackgroundMotionPath.FRAME_INTERVAL_MS

    private fun samples(effect: BackgroundMotionEffect, speed: BackgroundMotionSpeed): List<BackgroundMotionTransform> {
        // 三圈多一点，跨过每一处首尾相接。
        val span = 3 * 3 * speed.shotMs + speed.shotMs
        return (0..span step frameMs).map { BackgroundMotionPath.at(effect, it, speed.shotMs) }
    }

    @Test fun imageAlwaysCoversTheKeyboard() {
        for (effect in BackgroundMotionEffect.entries) for (speed in BackgroundMotionSpeed.entries) {
            samples(effect, speed).forEach { t ->
                assertTrue("$effect 放大不能小于 1", t.scale >= 1f - 1e-6f)
                assertTrue("$effect 放大不超过上限", t.scale <= BackgroundMotionPath.MAX_SCALE + 1e-6f)
                assertTrue("$effect 平移不能露出底色: $t", abs(t.shiftX) <= (t.scale - 1f) / 2f + 1e-5f)
                assertTrue("$effect 平移不能露出底色: $t", abs(t.shiftY) <= (t.scale - 1f) / 2f + 1e-5f)
            }
        }
    }

    @Test fun motionNeverJumps() {
        for (effect in BackgroundMotionEffect.entries) for (speed in BackgroundMotionSpeed.entries) {
            samples(effect, speed).zipWithNext { a, b ->
                assertTrue("$effect $speed 缩放跳了: $a → $b", abs(a.scale - b.scale) < .003f)
                assertTrue("$effect $speed 位置跳了: $a → $b", abs(a.shiftX - b.shiftX) < .003f)
            }
        }
    }

    @Test fun eachFrameMovesLessThanAPixelOnAPhoneKeyboard() {
        // 1080px 宽、约 800px 高的固定键盘；默认（中）和慢速每帧不到 1px，快速不到 1.5px。
        val width = 1080f
        for (effect in BackgroundMotionEffect.entries) for (speed in BackgroundMotionSpeed.entries) {
            val limit = if (speed == BackgroundMotionSpeed.FAST) 1.5f else 1f
            samples(effect, speed).zipWithNext { a, b ->
                // 边缘的点走得最远：缩放带来的半宽变化加上平移。
                val edge = abs(b.scale - a.scale) * width / 2f + abs(b.shiftX - a.shiftX) * width
                assertTrue("$effect $speed 每帧移动 $edge px", edge < limit)
            }
        }
    }

    @Test fun effectsStartWhereTheyShould() {
        val shot = BackgroundMotionSpeed.NORMAL.shotMs
        assertTrue(BackgroundMotionPath.at(BackgroundMotionEffect.ZOOM_IN, 0, shot).isIdentity)
        assertTrue(BackgroundMotionPath.at(BackgroundMotionEffect.CYCLE, 0, shot).isIdentity)
        assertEquals(BackgroundMotionPath.MAX_SCALE, BackgroundMotionPath.at(BackgroundMotionEffect.ZOOM_OUT, 0, shot).scale, 1e-6f)
        assertEquals(BackgroundMotionPath.MAX_SCALE, BackgroundMotionPath.at(BackgroundMotionEffect.ZOOM_IN, shot, shot).scale, 1e-6f)
        assertEquals(1f, BackgroundMotionPath.at(BackgroundMotionEffect.ZOOM_OUT, shot, shot).scale, 1e-6f)
        val pan = BackgroundMotionPath.at(BackgroundMotionEffect.PAN, 0, shot)
        assertEquals(-BackgroundMotionPath.MAX_SHIFT, pan.shiftX, 1e-6f)
        assertEquals(BackgroundMotionPath.MAX_SHIFT, BackgroundMotionPath.at(BackgroundMotionEffect.PAN, shot, shot).shiftX, 1e-6f)
        // 轮换：放大移到左边 → 平移到右边 → 缩回原样。
        assertEquals(-BackgroundMotionPath.MAX_SHIFT, BackgroundMotionPath.at(BackgroundMotionEffect.CYCLE, shot, shot).shiftX, 1e-6f)
        assertEquals(BackgroundMotionPath.MAX_SHIFT, BackgroundMotionPath.at(BackgroundMotionEffect.CYCLE, 2 * shot, shot).shiftX, 1e-6f)
        assertTrue(BackgroundMotionPath.at(BackgroundMotionEffect.CYCLE, 3 * shot, shot).isIdentity)
    }

    @Test fun pausedClockKeepsThePosition() {
        val motion = KeyboardBackgroundMotion(BackgroundMotionEffect.CYCLE, BackgroundMotionSpeed.NORMAL.shotMs)
        repeat(80) { motion.advance(BackgroundMotionPath.FRAME_INTERVAL_MS) }
        val before = motion.transform
        motion.covered = true
        assertEquals("停住时位置不变", before, motion.transform)
        // 主线程卡住 5 秒后只走一小步，不跳。
        motion.advance(5_000)
        val step = BackgroundMotionPath.at(BackgroundMotionEffect.CYCLE, 4_100, BackgroundMotionSpeed.NORMAL.shotMs)
        assertEquals(step, motion.transform)
    }

    @Test fun slidesChangeOncePerCycleAtItsStartingPose() {
        val shot = BackgroundMotionSpeed.NORMAL.shotMs
        for (effect in BackgroundMotionEffect.entries) {
            val cycle = BackgroundMotionPath.cycleMs(effect, shot)
            assertEquals(0, BackgroundMotionPath.slideIndex(effect, cycle - 1, shot, 3))
            assertEquals(1, BackgroundMotionPath.slideIndex(effect, cycle, shot, 3))
            assertEquals(2, BackgroundMotionPath.slideIndex(effect, 2 * cycle, shot, 3))
            assertEquals("走完一轮回到第一张", 0, BackgroundMotionPath.slideIndex(effect, 3 * cycle, shot, 3))
            assertEquals("一张图不轮播", 0, BackgroundMotionPath.slideIndex(effect, 5 * cycle, shot, 1))
            // 换图那一刻正好回到这一圈开头的样子，新图从头开始动。
            assertEquals(BackgroundMotionPath.at(effect, 0, shot), BackgroundMotionPath.at(effect, cycle, shot))
        }
    }

    @Test fun slideSourceFollowsTheClock() {
        val shot = BackgroundMotionSpeed.FAST.shotMs
        val motion = KeyboardBackgroundMotion(BackgroundMotionEffect.PAN, shot, listOf("themes/a.jpg", "themes/b.jpg"))
        assertEquals("themes/a.jpg", motion.slideSource)
        repeat((BackgroundMotionPath.cycleMs(BackgroundMotionEffect.PAN, shot) / BackgroundMotionPath.FRAME_INTERVAL_MS).toInt()) {
            motion.advance(BackgroundMotionPath.FRAME_INTERVAL_MS)
        }
        assertEquals("themes/b.jpg", motion.slideSource)
        assertEquals("不轮播时没有图", null, KeyboardBackgroundMotion(BackgroundMotionEffect.PAN, shot, listOf("themes/a.jpg")).slideSource)
    }

    @Test fun gameBatterySaverAndNoAnimationsStopTheMotion() {
        assertTrue(BackgroundMotionGate.allowed(enabled = true, imageBackground = true, game = false, systemAllows = true, surface = true))
        assertFalse("默认关", BackgroundMotionGate.allowed(enabled = false, imageBackground = true, game = false, systemAllows = true, surface = true))
        assertFalse("纯色、渐变不动", BackgroundMotionGate.allowed(enabled = true, imageBackground = false, game = false, systemAllows = true, surface = true))
        assertFalse("游戏里不动", BackgroundMotionGate.allowed(enabled = true, imageBackground = true, game = true, systemAllows = true, surface = true))
        assertFalse("实体键盘浮条、语音悬浮条不动", BackgroundMotionGate.allowed(enabled = true, imageBackground = true, game = false, systemAllows = true, surface = false))
        assertFalse("省电", BackgroundMotionGate.systemAllows(powerSave = true, animationsOn = true))
        assertFalse("关了动画", BackgroundMotionGate.systemAllows(powerSave = false, animationsOn = false))
        assertTrue(BackgroundMotionGate.systemAllows(powerSave = false, animationsOn = true))
    }
}
