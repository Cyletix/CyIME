package com.kingzcheung.xime.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 用户 10-31：长按空格滑动延迟很大。拖动的格子攒着，轮到的队列任务一次做完，后面的任务空跑跳过。 */
class PendingCursorStepsTest {
    private fun run(steps: Int, selecting: Boolean = false) = PendingCursorSteps.Run(steps, selecting)

    @Test fun `steps waiting behind a slow editor are done by the first job and later jobs skip`() {
        val pending = PendingCursorSteps()
        repeat(12) { pending.add(-1, false, 7) }
        assertEquals(listOf(run(-12)), pending.drain(7))
        repeat(11) { assertEquals(emptyList<PendingCursorSteps.Run>(), pending.drain(7)) }
    }

    @Test fun `direction and shift changes stay separate runs in order`() {
        val pending = PendingCursorSteps()
        pending.add(2, false, 1)
        pending.add(1, false, 1)
        pending.add(-1, false, 1)
        pending.add(-2, true, 1)
        pending.add(3, true, 1)
        assertEquals(listOf(run(3), run(-1), run(-2, true), run(3, true)), pending.drain(1))
    }

    @Test fun `steps from another input field are dropped`() {
        val pending = PendingCursorSteps()
        pending.add(3, false, 1)
        pending.add(2, false, 2)
        assertEquals(listOf(run(2)), pending.drain(2))
        pending.add(4, false, 2)
        assertEquals(emptyList<PendingCursorSteps.Run>(), pending.drain(3))
        assertEquals(emptyList<PendingCursorSteps.Run>(), pending.drain(2))
    }

    @Test fun `steps no job ever picked up are not carried into a later drag`() {
        var now = 0L
        val pending = PendingCursorSteps { now }
        pending.add(5, false, 1)
        now += 1_001
        pending.add(-1, false, 1)
        assertEquals(listOf(run(-1)), pending.drain(1))
    }

    @Test fun `composition edit takes the net step count once`() {
        val pending = PendingCursorSteps()
        pending.add(3, false, 1)
        pending.add(-1, false, 1)
        assertEquals(2, pending.drainNet(1))
        assertNull(pending.drainNet(1))
        pending.add(1, false, 1)
        pending.add(-1, false, 1)
        assertNull(pending.drainNet(1))
    }
}
