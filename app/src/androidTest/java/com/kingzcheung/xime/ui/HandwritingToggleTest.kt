package com.kingzcheung.xime.ui

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kingzcheung.xime.viewmodel.KeyboardViewModel
import com.kingzcheung.xime.ui.keyboard.KeyboardLayoutState
import com.kingzcheung.xime.ui.keyboard.KeyboardDispatchAction
import com.kingzcheung.xime.keyboard.KeyboardPage
import com.kingzcheung.xime.keyboard.MainType
import com.kingzcheung.xime.keyboard.PanelType
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HandwritingToggleTest {
    private fun model() = KeyboardViewModel(ApplicationProvider.getApplicationContext<Application>())

    @Test fun handwritingReturnsToExactTypingLayout() {
        for (layout in listOf(KeyboardLayoutState.Chinese, KeyboardLayoutState.English,
            KeyboardLayoutState.T9Pinyin, KeyboardLayoutState.Stroke)) {
            val vm = model()
            vm.setKeyboardState(layout)
            vm.enterTemporaryHandwriting()
            assertEquals(KeyboardPage.Main(MainType.HANDWRITING), vm.page.value)
            vm.dispatch(KeyboardDispatchAction.AsciiModeChanged(false, "pinyin_simp"))
            assertEquals(KeyboardPage.Main(MainType.HANDWRITING), vm.page.value)
            assertTrue(vm.exitTemporaryHandwriting())
            assertEquals(layout, vm.keyboardState.value)
            assertEquals(KeyboardPage.Main(MainType.FULL), vm.page.value)
            assertFalse(vm.exitTemporaryHandwriting())
        }
    }

    @Test fun handwritingPreservesNumberPanelReturnTarget() {
        val vm = model()
        vm.setKeyboardState(KeyboardLayoutState.T9Pinyin)
        vm.enterPanel(PanelType.NUMBER)
        val previousPage = vm.page.value
        vm.enterTemporaryHandwriting()
        vm.enterPanel(PanelType.COMMON_SYMBOL)
        assertTrue(vm.exitTemporaryHandwriting())
        assertEquals(previousPage, vm.page.value)
        assertEquals(KeyboardLayoutState.Number, vm.keyboardState.value)
        vm.exitPanel()
        assertEquals(KeyboardLayoutState.T9Pinyin, vm.keyboardState.value)
    }

    @Test fun explicitSchemaSelectionDiscardsTemporaryReturn() {
        val vm = model()
        vm.enterTemporaryHandwriting()
        vm.discardTemporaryHandwriting()
        vm.switchMain(MainType.FULL)
        vm.dispatch(KeyboardDispatchAction.AsciiModeChanged(true, "easy_en"))
        assertFalse(vm.exitTemporaryHandwriting())
        assertEquals(KeyboardLayoutState.English, vm.keyboardState.value)
    }
    @Test fun languageChosenInsideNumberPanelBecomesItsNewReturnTarget() {
        val vm = model()
        vm.setKeyboardState(KeyboardLayoutState.T9Pinyin)
        vm.enterPanel(PanelType.NUMBER)
        vm.switchMain(MainType.FULL)
        vm.dispatch(KeyboardDispatchAction.AsciiModeChanged(true, "t9_pinyin"))
        vm.enterPanel(PanelType.NUMBER)
        vm.exitPanel()
        assertEquals("选过英文后不能被旧数字面板记忆拉回九键", KeyboardLayoutState.English, vm.keyboardState.value)
    }

}
