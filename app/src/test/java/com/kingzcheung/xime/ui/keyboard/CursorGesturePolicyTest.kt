package com.kingzcheung.xime.ui.keyboard

import org.junit.Assert.*
import org.junit.Test

class CursorGesturePolicyTest {
    @Test fun everyCombinationHasExactlyOneSpaceActionAndOneCursorSurface() {
        for (gesture in CursorGestureMode.entries) for (hold in SpaceHoldAction.entries) {
            val prefs = KeyboardInputPreferences(spaceHold = hold, cursorGesture = gesture)
            assertEquals(gesture == CursorGestureMode.SPACE, prefs.effectiveSpaceHold == SpaceHoldAction.CURSOR)
            assertEquals(if (gesture == CursorGestureMode.SPACE) 200L else 300L, prefs.spaceHoldDelayMs)
            if (gesture != CursorGestureMode.SPACE && hold != SpaceHoldAction.CURSOR) assertEquals(hold, prefs.effectiveSpaceHold)
        }
    }
    @Test fun changingCursorSurfaceKeepsTheUsersOtherSpaceAction() {
        val voice = KeyboardInputPreferences(spaceHold = SpaceHoldAction.VOICE_TOGGLE)
        val cursor = voice.copy(cursorGesture = CursorGestureMode.SPACE)
        assertEquals(SpaceHoldAction.CURSOR, cursor.effectiveSpaceHold)
        assertEquals(SpaceHoldAction.VOICE_TOGGLE, cursor.copy(cursorGesture = CursorGestureMode.KEYBOARD).effectiveSpaceHold)
    }
}
