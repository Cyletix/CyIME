package com.kingzcheung.xime.ui.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SideSymbolTargetTest {
    @Test fun eachBarFallsBackToTheOldSharedListUntilEditedAlone() {
        val shared = KeyboardInputPreferences(fixedSymbols = "，\n。\n……")
        assertEquals(listOf("，", "。", "……"), shared.symbolsFor(SideSymbolTarget.T9))
        val own = shared.copy(sideSymbols = mapOf("t9" to "@\n#\n\n"))
        assertEquals(listOf("@", "#"), own.symbolsFor(SideSymbolTarget.T9))
        // 其他栏不受九键单独设置影响。
        assertEquals(listOf("，", "。", "……"), own.symbolsFor(SideSymbolTarget.STROKE))
        assertNull(KeyboardInputPreferences().symbolsFor(SideSymbolTarget.COMMON))
    }

    @Test fun fixedSlotBarsAlwaysShowTheirSlotCount() {
        assertEquals(listOf("！", "，", "。"), SideSymbolTarget.HANDWRITING.resolve(listOf("！")))
        assertEquals(listOf("a", "b", "c"), SideSymbolTarget.HANDWRITING.resolve(listOf("a", "b", "c", "d")))
        assertEquals(listOf("；", "：", "！", "？", "，", "。"), SideSymbolTarget.HANDWRITING_ROW.resolve(null))
    }
}
