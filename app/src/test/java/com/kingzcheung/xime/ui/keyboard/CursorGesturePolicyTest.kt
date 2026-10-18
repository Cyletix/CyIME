package com.kingzcheung.xime.ui.keyboard

import org.junit.Assert.*
import org.junit.Test

class CursorGesturePolicyTest {
    @Test fun everyCombinationHasExactlyOneSpaceActionAndOneCursorSurface() {
        for (gesture in CursorGestureMode.entries) for (hold in SpaceHoldAction.entries) {
            val prefs = KeyboardInputPreferences(spaceHold = hold, cursorGesture = gesture)
            assertEquals(gesture == CursorGestureMode.SPACE, prefs.effectiveSpaceHold == SpaceHoldAction.CURSOR)
            // 2026-10-12：空格所有长按行为共用“空格长按触发”时长，默认 0.2 秒。
            assertEquals(200L, prefs.spaceHoldDelayMs)
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
