package com.kingzcheung.xime.service

import android.view.inputmethod.InputConnection
import androidx.compose.runtime.mutableStateOf
import org.junit.Test
import org.mockito.kotlin.*

/** 审计 F08：工具面板打开后换了输入框（含不经 onFinishInput 的 restartInput），旧结果不能写进新正文。 */
class ToolPanelEditorChangeTest {
    private fun service(readiness: InputReadiness, admission: Long?): Pair<XimeInputMethodService, InputConnection> {
        val service = mock<XimeInputMethodService>()
        val connection = mock<InputConnection>()
        mapOf(
            "uiState" to mutableStateOf(InputUIState(toolPanelVisible = true)),
            "inputReadiness" to readiness,
            "toolPanelAdmission" to admission,
            "toolPanelSelection" to (0 to 4),
        ).forEach { (name, value) ->
            XimeInputMethodService::class.java.getDeclaredField(name).apply {
                isAccessible = true
                set(service, value)
            }
        }
        whenever(service.currentInputConnection).thenReturn(connection)
        doCallRealMethod().whenever(service).commitToolPanelItem(any())
        return service to connection
    }

    @Test fun resultFromThePreviousEditorIsDropped() {
        val readiness = InputReadiness().apply { completeStartup() }
        val opened = readiness.ticket()
        readiness.newEditorSession() // restartInput：同一个连接对象，新的正文
        val (service, connection) = service(readiness, opened)

        service.commitToolPanelItem("旧输入框的生成结果")

        verify(connection, never()).setSelection(any(), any())
        verify(connection, never()).commitText(any(), any())
        verify(service).closeToolPanel()
    }

    @Test fun panelClosedWithoutAnEditorIdentityCommitsNothing() {
        val readiness = InputReadiness().apply { completeStartup() }
        val (service, connection) = service(readiness, null)

        service.commitToolPanelItem("结果")

        verify(connection, never()).commitText(any(), any())
    }
}
