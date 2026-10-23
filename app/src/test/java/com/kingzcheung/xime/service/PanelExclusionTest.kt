package com.kingzcheung.xime.service

import androidx.compose.runtime.mutableStateOf
import com.kingzcheung.xime.clipboard.ClipboardManager
import com.kingzcheung.xime.rime.InputCommandQueue
import com.kingzcheung.xime.settings.IncognitoState
import com.kingzcheung.xime.viewmodel.KeyboardViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.*

/**
 * The clipboard edit form and clipboard search never stay open together. With both open the
 * letters went to search while chosen candidates filled the form, and closing the form reopened the
 * board under the search: two overlapping clipboards (user, 2026-10-23).
 */
class PanelExclusionTest {
    private val service = mock<XimeInputMethodService>()
    private val uiState = mutableStateOf(InputUIState())
    private val savedManual = IncognitoState.manual.also { IncognitoState.manual = false }

    init {
        val clipboard = mock<ClipboardManager>()
        whenever(clipboard.boardQuery).thenReturn(MutableStateFlow(""))
        mapOf(
            "uiState" to uiState, "clipboardManager" to clipboard,
            "candidateState" to mutableStateOf(CandidateState()),
            "inputCommands" to mock<InputCommandQueue>(), "privacy" to InputPrivacy {},
        ).forEach { (name, value) ->
            XimeInputMethodService::class.java.getDeclaredField(name).apply {
                isAccessible = true
                set(service, value)
            }
        }
        whenever(service.uiState).thenReturn(uiState)
        whenever(service.clipboardManager).thenReturn(clipboard)
        whenever(service.keyboardViewModel).thenReturn(mock<KeyboardViewModel>())
        whenever(service.editorEnterKeyText()).thenReturn("发送")
        doCallRealMethod().whenever(service).openClipboardSearch()
        doNothing().whenever(service).fitClipboardSearchOnShortScreen()
        doCallRealMethod().whenever(service).closeClipboardSearch(any(), any())
    }

    @After fun restore() {
        IncognitoState.clipboardSearch = false
        IncognitoState.manual = savedManual
    }

    @Test fun openingSearchClosesTheEditForm() {
        service.openQuickSend(itemId = 4, text = "地址", code = "dz")
        service.openClipboardSearch()
        assertFalse(uiState.value.showQuickSendForm)
        assertEquals(InputTarget.ClipboardSearch, uiState.value.inputTarget)
        assertEquals("完成", uiState.value.enterKeyText)
    }

    @Test fun openingTheEditFormClosesSearch() {
        service.openClipboardSearch()
        service.openQuickSend(text = "识别结果", ocr = true)
        assertFalse(uiState.value.clipboardSearchVisible)
        assertFalse(IncognitoState.clipboardSearch)
        assertTrue(uiState.value.showQuickSendForm)
        assertEquals(InputTarget.QuickSend(code = false), uiState.value.inputTarget)
    }
}
