package com.kingzcheung.xime.service

import android.view.inputmethod.InputConnection
import androidx.compose.runtime.mutableStateOf
import org.junit.Test
import org.mockito.kotlin.*

/**
 * 审计 F05：编辑面板的动作按点的顺序执行。全选、复制、剪切、粘贴、撤销、重做与选择/移光标
 * 进同一个按键队列，不能抢在排队中的扩选前面直接作用于输入框。
 */
class ToolbarEditingOrderTest {
    private val actions = listOf("select_begin", "select_arrow_left", "cut", "copy", "paste",
        "select_all", "undo", "redo", "select_reset")

    @Test fun everyEditingActionWaitsInTheInputQueue() {
        val service = mock<XimeInputMethodService>()
        val router = mock<ImeKeyRouter>()
        val connection = mock<InputConnection>()
        whenever(service.keyRouter).thenReturn(router)
        whenever(service.uiState).thenReturn(mutableStateOf(InputUIState()))
        whenever(service.currentInputConnection).thenReturn(connection)
        val controller = ImeSchemaController(service)

        actions.forEach(controller::handleToolbarEditingAction)

        // 一个动作一个排队任务，谁都没有在排队前直接碰输入框。
        verify(router, times(actions.size)).postRimeJob(any(), any())
        verifyNoInteractions(connection)
        verify(service, never()).dismissPredictionsForPaste()
        verify(service, never()).sendUndoShortcut(any(), any())
    }

    @Test fun nothingIsQueuedWithoutAnEditor() {
        val service = mock<XimeInputMethodService>()
        val router = mock<ImeKeyRouter>()
        whenever(service.keyRouter).thenReturn(router)
        whenever(service.uiState).thenReturn(mutableStateOf(InputUIState()))
        val controller = ImeSchemaController(service)

        actions.forEach(controller::handleToolbarEditingAction)

        verify(router, never()).postRimeJob(any(), any())
    }
}
