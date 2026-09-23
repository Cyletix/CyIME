package com.kingzcheung.xime.keyboard

import org.junit.Assert.*
import org.junit.Test

class KeyboardModeNavigationTest {
    @Test fun allSixTransitionsUseTheTwoFixedSlots() {
        for (label in listOf("中文", "ABC", "あいう")) {
            assertEquals(listOf(KeyboardModeTarget("!@#", "symbol"), KeyboardModeTarget("123", "number")),
                (1..2).map { modeSlotTarget(KeyboardInputPage.TEXT, it, label) })
            assertEquals(listOf(KeyboardModeTarget(label, "abc"), KeyboardModeTarget("123", "number")),
                (1..2).map { modeSlotTarget(KeyboardInputPage.SYMBOLS, it, label) })
            assertEquals(listOf(KeyboardModeTarget("!@#", "symbol"), KeyboardModeTarget(label, "abc")),
                (1..2).map { modeSlotTarget(KeyboardInputPage.NUMBERS, it, label) })
        }
    }

    @Test fun commonPageUsesTheOriginalLanguageAndHasNoDuplicateKeys() {
        for (label in listOf("中文", "ABC", "あいう")) {
            val symbols = commonSymbolsFor(label)
            assertEquals(32, symbols.size)
            assertEquals(symbols.size, symbols.distinct().size)
        }
        assertTrue(commonSymbolsFor("ABC").take(8).containsAll(listOf("?", "!", ",", ".")))
        assertTrue(commonSymbolsFor("中文").take(8).containsAll(listOf("？", "！", "，", "。")))
        assertTrue(commonSymbolsFor("あいう").take(8).containsAll(listOf("、", "。", "ー", "・")))
    }

    @Test fun nestedSecondaryPagesNeverBecomeTheReturnDestination() {
        for (main in MainType.entries) {
            val text = KeyboardPage.Main(main)
            val number = KeyboardPage.Panel(PanelType.NUMBER, main)
            val symbol = KeyboardPage.Overlay(OverlayRoute.Symbol, emptyList(), number)
            val tool = KeyboardPage.Overlay(OverlayRoute.Menu, listOf(OverlayRoute.Symbol), symbol)
            for (page in listOf(text, number, symbol, tool)) assertEquals(main, page.textMainType())
        }
    }
}
