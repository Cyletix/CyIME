package com.kingzcheung.xime.ui.settings

import com.kingzcheung.xime.ui.keyboard.KeyboardInputPreferences
import com.kingzcheung.xime.ui.keyboard.SymbolInputMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 「按键技巧」页跟着实际设置写方向和时限，不写死默认值（用户 10-31）。 */
class KeyTipsTest {
    private fun text(settings: KeyboardInputPreferences) =
        keyTipGroups(settings).flatMap { g -> g.tips.map { it.how + "|" + it.what } }.joinToString("\n")

    @Test fun defaultsDescribeShiftCtrlAndDefaultDirections() {
        val t = text(KeyboardInputPreferences())
        assertTrue(t.contains("连点两下 Shift"))
        assertTrue(t.contains("按住 !@#"))
        assertTrue(t.contains("按住 !@# 点回车|发送消息"))
        assertTrue(t.contains("按住 !@# 长按空格拖动|选中文字"))
        assertTrue(t.contains("1.0 秒内"))
        assertTrue(t.contains("下拉字母键|输入键上方的角标；上拉输入下方的角标。"))
        assertTrue(t.contains("删除键下拉|"))
        // Z X C V 角标 11-02 并进「按键符号」，不在 Ctrl 技巧里出现。
        assertFalse(t.contains("Z X C V"))
    }

    @Test fun directionsFollowReverseLongPressAndHoldSettings() {
        val reversed = text(KeyboardInputPreferences(reverseSymbolSwipe = true, symbolKeyHoldSeconds = 1.5f))
        assertTrue(reversed.contains("上拉字母键|输入键上方的角标；下拉输入下方的角标。"))
        assertTrue(reversed.contains("删除键上拉|"))
        assertTrue(reversed.contains("1.5 秒内"))
        val longPress = text(KeyboardInputPreferences(symbolInputMode = SymbolInputMode.LONG_PRESS))
        assertTrue(longPress.contains("长按字母键|"))
    }
}
