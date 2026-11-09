package com.kingzcheung.xime.ui.keyboard

import com.kingzcheung.xime.keyboard.KeyboardPage
import com.kingzcheung.xime.keyboard.MainType
import com.kingzcheung.xime.keyboard.OverlayRoute
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 回归 #185（11-08 修）：表情、剪贴板等面板盖住手写板时，工具栏的手写按钮不能和面板按钮同时亮。 */
class HandwritingToolbarActiveTest {
    private val handwriting = KeyboardPage.Main(MainType.HANDWRITING)

    @Test fun litWhileHandwritingIsShown() {
        assertTrue(handwritingToolbarActive(handwriting, handwritingShown = true))
        // 临时手写（在别的键盘上点手写查字）同样亮
        assertTrue(handwritingToolbarActive(KeyboardPage.Main(MainType.FULL), handwritingShown = true))
    }

    @Test fun offWhenHandwritingIsNotShown() {
        assertFalse(handwritingToolbarActive(KeyboardPage.Main(MainType.FULL), handwritingShown = false))
    }

    @Test fun offWhileAPanelCoversHandwriting() {
        for (route in listOf(OverlayRoute.Emoji, OverlayRoute.Clipboard(), OverlayRoute.Symbol, OverlayRoute.Edit, OverlayRoute.Menu)) {
            val covered = KeyboardPage.Overlay(route, emptyList(), handwriting)
            assertFalse("$route 盖着手写板时手写按钮不该亮", handwritingToolbarActive(covered, handwritingShown = true))
        }
    }
}
