package com.kingzcheung.xime.ui.keyboard

import org.junit.Assert.assertEquals
import org.junit.Test

class MenuAppearanceLabelTest {
    @Test fun menuShowsCurrentAppearanceInsteadOfNextAction() {
        assertEquals("浅色模式", menuAppearanceLabel(0))
        assertEquals("深色模式", menuAppearanceLabel(1))
        assertEquals("跟随系统", menuAppearanceLabel(2))
    }
}