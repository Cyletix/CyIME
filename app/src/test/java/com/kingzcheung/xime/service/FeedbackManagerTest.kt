package com.kingzcheung.xime.service

import android.content.Context
import android.view.HapticFeedbackConstants
import android.view.View
import org.junit.Test
import org.mockito.kotlin.*

class FeedbackManagerTest {
    private fun manager(mode: HapticMode): FeedbackManager = FeedbackManager(mock<Context>()).also {
        FeedbackManager::class.java.getDeclaredField("hapticMode").apply { isAccessible = true }.set(it, mode)
    }

    @Test fun `cursor steps use text movement feedback and honor disabled mode`() {
        val view = mock<View>()
        manager(HapticMode.Enabled).hapticFeedback(view, type = KeyFeedbackType.CURSOR_STEP)
        verify(view).performHapticFeedback(eq(HapticFeedbackConstants.TEXT_HANDLE_MOVE), any())
        clearInvocations(view)
        manager(HapticMode.Disabled).hapticFeedback(view, type = KeyFeedbackType.CURSOR_STEP)
        verifyNoInteractions(view)
    }

    @Test fun `typing retains keyboard haptics independently of cursor ticks`() {
        val view = mock<View>()
        manager(HapticMode.Enabled).performKeyPressDownEffect("delete", view)
        verify(view).performHapticFeedback(eq(HapticFeedbackConstants.KEYBOARD_TAP), any())
    }
    @Test fun `feedback actions retain semantic types across keyboard layouts`() {
        mapOf(
            "a" to KeyFeedbackType.CHARACTER, "あ" to KeyFeedbackType.CHARACTER,
            "2" to KeyFeedbackType.NUMBER, "，" to KeyFeedbackType.SYMBOL,
            "space" to KeyFeedbackType.SPACE, "enter" to KeyFeedbackType.ENTER,
            "clear_all" to KeyFeedbackType.DELETE, "ime_switch" to KeyFeedbackType.MODE_SWITCH,
            "japanese_convert" to KeyFeedbackType.MODIFIER, "japanese_left" to KeyFeedbackType.NAVIGATION,
            "select_arrow_right" to KeyFeedbackType.NAVIGATION, "select_paragraph_start" to KeyFeedbackType.NAVIGATION,
            "copy" to KeyFeedbackType.EDITING, "toolbar" to KeyFeedbackType.TOOLBAR,
            "cursor_step" to KeyFeedbackType.CURSOR_STEP,
        ).forEach { (action, expected) -> org.junit.Assert.assertEquals(action, expected, KeyFeedbackType.fromKey(action)) }
    }
}
