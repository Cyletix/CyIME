package com.kingzcheung.xime.ui.keyboard

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LetterLongPressMenuTest {
    @Test fun `letter keys show no case bubble unless the option is on`() {
        // 默认关：长按字母不弹大小写，上下拖动输入角标不被打断（用户 10-20）。
        for (key in listOf("a", "e", "Q", "qw", "er")) {
            assertFalse(key, letterLongPressMenuAllowed(key, enabled = false))
            assertTrue(key, letterLongPressMenuAllowed(key, enabled = true))
        }
    }

    @Test fun `other keys keep their long press`() {
        for (key in listOf("'", "earth", "space", ",", "1")) assertTrue(key, letterLongPressMenuAllowed(key, enabled = false))
        assertFalse(KeyboardInputPreferences().letterLongPressMenu)
    }
}
