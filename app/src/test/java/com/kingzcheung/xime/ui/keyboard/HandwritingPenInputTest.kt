package com.kingzcheung.xime.ui.keyboard

import android.view.MotionEvent
import androidx.compose.ui.input.pointer.PointerType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HandwritingPenInputTest {
    @Test fun fingerWritesUntilAPenIsNearbyAndAgainAfterThePenLeaves() {
        val presence = StylusPresence()
        assertFalse(presence.rejectsTouch(PointerType.Touch, 5_000))
        presence.mark(10_000)
        assertTrue(presence.rejectsTouch(PointerType.Touch, 10_500))
        assertFalse(presence.rejectsTouch(PointerType.Stylus, 10_500))
        assertFalse(presence.rejectsTouch(PointerType.Touch, 10_000 + StylusPresence.PALM_REJECT_MS))
    }

    @Test fun eraserEndAndHeldSideButtonEraseButAPlainPenOrFingerWrites() {
        assertTrue(isErasing(PointerType.Eraser, 0))
        assertTrue(isErasing(PointerType.Stylus, MotionEvent.BUTTON_STYLUS_PRIMARY))
        assertTrue(isErasing(PointerType.Stylus, MotionEvent.BUTTON_SECONDARY))
        assertFalse(isErasing(PointerType.Stylus, 0))
        assertFalse(isErasing(PointerType.Touch, MotionEvent.BUTTON_STYLUS_PRIMARY))
    }
}
