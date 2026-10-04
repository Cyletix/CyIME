package com.kingzcheung.xime.ui.keyboard

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.*
import org.junit.Test

class ShiftSlideTargetsTest {
    private val keyboard = Rect(0f, 0f, 400f, 280f)
    private val shift = Rect(0f, 160f, 60f, 220f)
    private val commits = mutableListOf<String>()
    private fun targets() = ShiftSlideTargets().apply {
        keyboardBounds = keyboard
        put("a", "a", Rect(65f, 90f, 110f, 155f), commits::add)
        put("p", "P", Rect(355f, 10f, 400f, 75f), commits::add)
    }

    @Test fun movementInsideShiftNeverShowsLineEvenPastTouchSlop() {
        val targets = targets()
        for (point in listOf(shift.center, Offset(1f, 161f), Offset(59f, 219f))) {
            assertNull(targets.move(shift, point))
            assertNull(targets.drag)
        }
        assertTrue(commits.isEmpty())
    }

    @Test fun crossingLettersAndHoldingDoesNotCommitUntilRelease() {
        val targets = targets()
        targets.move(shift, Offset(85f, 120f))
        repeat(50) { targets.move(shift, Offset(375f, 40f)) }
        assertTrue(commits.isEmpty())
        assertEquals(shift.center, targets.drag!!.origin)
        assertEquals("P", targets.hovered!!.key)
        targets.hovered!!.commit()
        targets.clearDrag()
        assertEquals(listOf("P"), commits)
        assertNull(targets.drag)
        assertNull(targets.hovered)
    }

    @Test fun outsideKeyboardAndEmptySpaceCancelTargetAndReentryCanSelectAgain() {
        val targets = targets()
        targets.put("overflow", "x", Rect(390f, 20f, 440f, 80f), commits::add)
        targets.move(shift, Offset(85f, 120f))
        for (point in listOf(Offset(-1f, 120f), Offset(420f, 40f), Offset(200f, -1f),
            Offset(200f, 281f), Offset(160f, 120f))) {
            assertNull(targets.move(shift, point))
            assertNull(targets.hovered)
        }
        targets.move(shift, Offset(85f, 120f))!!.commit()
        assertEquals(listOf("A"), commits)
    }

    @Test fun returnToShiftOrSystemCancellationClearsLineAndTarget() {
        val targets = targets()
        targets.move(shift, Offset(85f, 120f))
        targets.move(shift, shift.center)
        assertNull(targets.drag)
        assertNull(targets.hovered)
        targets.move(shift, Offset(85f, 120f))
        targets.clearDrag()
        assertNull(targets.drag)
        assertNull(targets.hovered)
        assertTrue(commits.isEmpty())
    }

    @Test fun pairedLettersSelectOneUppercaseByHalfAndNonlettersNeverBecomeTargets() {
        val targets = targets()
        targets.put("pair", "qw", Rect(120f, 10f, 200f, 75f), commits::add)
        targets.put("symbols", "!@", Rect(210f, 10f, 290f, 75f), commits::add)
        targets.move(shift, Offset(130f, 40f))!!.commit()
        targets.move(shift, Offset(180f, 40f))!!.commit()
        assertNull(targets.move(shift, Offset(250f, 40f)))
        targets.remove("pair")
        assertNull(targets.move(shift, Offset(130f, 40f)))
        assertEquals(listOf("Q", "W"), commits)
    }
}
