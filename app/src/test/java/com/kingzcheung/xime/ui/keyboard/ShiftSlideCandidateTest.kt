package com.kingzcheung.xime.ui.keyboard

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 从 Shift 拖到候选栏上松手：选中那个候选（由选词路径按住 Shift 的规则决定上英文还是中文）。 */
class ShiftSlideCandidateTest {

    private val candidates = CandidateDropTargets()
    private val targets = ShiftSlideTargets(candidates).apply {
        keyboardBounds = Rect(0f, 100f, 400f, 400f)
        put("q", "q", Rect(0f, 100f, 40f, 160f)) { }
    }
    private val shift = Rect(0f, 340f, 60f, 400f)

    @Test
    fun `dragging shift onto a candidate selects that candidate`() {
        val picked = mutableListOf<Int>()
        candidates.put("c0", Rect(0f, 40f, 80f, 90f)) { picked += 0 }
        candidates.put("c1", Rect(80f, 40f, 160f, 90f)) { picked += 1 }

        val target = targets.move(shift, Offset(100f, 60f))
        assertEquals(ShiftSlideTargets.CANDIDATE_KEY, target?.key)
        target!!.commit()
        assertEquals(listOf(1), picked)
    }

    @Test
    fun `letters inside the keyboard still win, and empty space selects nothing`() {
        candidates.put("c0", Rect(0f, 40f, 80f, 90f)) { }
        assertEquals("Q", targets.move(shift, Offset(20f, 130f))?.key)
        assertNull(targets.move(shift, Offset(300f, 60f)))
    }

    @Test
    fun `a candidate that left the bar is no longer a target`() {
        candidates.put("c0", Rect(0f, 40f, 80f, 90f)) { }
        candidates.remove("c0")
        assertNull(targets.move(shift, Offset(20f, 60f)))
    }
}
