package com.kingzcheung.xime.ui.settings

import com.kingzcheung.xime.ui.keyboard.KeyboardInputPreferences
import com.kingzcheung.xime.ui.keyboard.SymbolInputMode
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
        assertTrue(t.contains("1.0 秒内"))
        assertTrue(t.contains("下拉字母键|输入键上方的角标；上拉输入下方的角标。"))
        assertTrue(t.contains("删除键下拉|"))
        // Z X C V 默认关：只说怎么打开。
        assertTrue(t.contains("在「按键与手势」打开「Z X C V 角标做撤销、剪切、复制、粘贴」，默认关。"))
    }

    @Test fun directionsFollowReverseLongPressAndHoldSettings() {
        val reversed = text(KeyboardInputPreferences(reverseSymbolSwipe = true, symbolKeyHoldSeconds = 1.5f))
        assertTrue(reversed.contains("上拉字母键|输入键上方的角标；下拉输入下方的角标。"))
        assertTrue(reversed.contains("删除键上拉|"))
        assertTrue(reversed.contains("1.5 秒内"))
        val longPress = text(KeyboardInputPreferences(symbolInputMode = SymbolInputMode.LONG_PRESS, zxcvEditShortcuts = true))
        assertTrue(longPress.contains("长按字母键|"))
        assertTrue(longPress.contains("长按 Z X C V|撤销、剪切、复制、粘贴"))
    }
}
