package com.kingzcheung.xime.keyboard

import org.junit.Assert.*
import org.junit.Test

class EditorPreparationPageTest {
    private val keyboard = KeyboardPage.Main(MainType.FULL)
    private fun ocr() = KeyboardPage.Overlay(OverlayRoute.Ocr, emptyList(), keyboard)

    @Test fun galleryReturnSurvivesEnginePreparationCompletingAfterPanelOpened() {
        var page: KeyboardPage = keyboard // onStartInput resets the previous editor
        page = ocr() // picker returns and onStartInputView opens the image preview
        page = page.afterEditorPreparation(keyboard) // delayed FIFO preparation completes
        assertEquals(OverlayRoute.Ocr, (page as KeyboardPage.Overlay).route)
        assertEquals(keyboard, page.behind)
    }

    @Test fun closingOcrBeforePreparationCompletesDoesNotReopenIt() {
        var page: KeyboardPage = ocr()
        page = (page as KeyboardPage.Overlay).behind
        page = page.afterEditorPreparation(keyboard)
        assertEquals(keyboard, page)
    }

    @Test fun switchingToAnotherToolWinsOverAnEarlierGalleryRequest() {
        val current = KeyboardPage.Overlay(OverlayRoute.Clipboard(0), emptyList(), keyboard)
        val result = current.afterEditorPreparation(keyboard) as KeyboardPage.Overlay
        assertEquals(OverlayRoute.Clipboard(0), result.route)
    }

    @Test fun preparedHandwritingAndNumberLayoutsRemainBehindTheVisibleTool() {
        for (prepared in listOf(KeyboardPage.Main(MainType.HANDWRITING),
                KeyboardPage.Panel(PanelType.NUMBER, MainType.FULL))) {
            val result = ocr().afterEditorPreparation(prepared) as KeyboardPage.Overlay
            assertEquals(OverlayRoute.Ocr, result.route)
            assertEquals(prepared, result.behind)
        }
    }

    @Test fun nestedToolNavigationAndReturnToTextArePreserved() {
        val current = KeyboardPage.Overlay(OverlayRoute.Clipboard(1), listOf(OverlayRoute.Symbol),
            KeyboardPage.Panel(PanelType.NUMBER, MainType.FULL), returnToTextOnClose = true)
        val result = current.afterEditorPreparation(keyboard) as KeyboardPage.Overlay
        assertEquals(current.route, result.route)
        assertEquals(current.backStack, result.backStack)
        assertTrue(result.returnToTextOnClose)
        assertEquals(keyboard, result.behind)
    }

    @Test fun aNewEditorKeepsItsOwnDefaultInsteadOfThePreviousEditorsOcr() {
        // The synchronous onStartInput reset is deliberately unchanged; no old overlay survives it.
        val newEditor = keyboard
        val numberKeyboard = KeyboardPage.Panel(PanelType.NUMBER, MainType.FULL)
        assertEquals(numberKeyboard, newEditor.afterEditorPreparation(numberKeyboard))
    }
}
