package com.kingzcheung.xime.service

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.view.inputmethod.InputConnection
import com.kingzcheung.xime.settings.SettingsPreferences
import com.kingzcheung.xime.speech.RecognitionState
import com.kingzcheung.xime.speech.SpeechRecognitionManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.then
import org.mockito.kotlin.whenever

/**
 * 语音松手收尾状态机测试：finishRecognition 停止送音后等待引擎最终结果，
 * 超时才回退提交部分结果（修复"说完立刻松手，尾部语音被截断"）。
 */
@RunWith(MockitoJUnitRunner.Silent::class)
class VoiceRecognitionHandlerFinishTest {

    @Mock
    private lateinit var mockContext: Context

    @Mock
    private lateinit var mockPrefs: SharedPreferences

    @Mock
    private lateinit var mockInputConnection: InputConnection

    @Mock
    private lateinit var mockManager: SpeechRecognitionManager

    @Mock
    private lateinit var mockMainHandler: Handler

    private lateinit var handler: VoiceRecognitionHandler

    private val stateChanges = mutableListOf<InputUIState>()
    private var recordingStoppedCount = 0
    private var voiceCompleteCount = 0
    private var currentState = InputUIState()

    /** mock mainHandler 的 postDelayed 队列，可手动推进超时。 */
    private val posted = mutableListOf<Runnable>()

    private lateinit var onResult: (String) -> Unit
    private lateinit var onPartial: (String) -> Unit
    private lateinit var onState: (RecognitionState) -> Unit
    private lateinit var onError: (String, Boolean) -> Unit

    @Before
    fun setup() {
        whenever(mockContext.getSharedPreferences(any(), anyInt())).thenReturn(mockPrefs)
        // 所有布尔设置返回默认值；isSttUseLocal 置 true 使 resolveProviderName 走本地引擎
        // 早退（不触碰未初始化的 PluginManager/ExtensionManager），warmup 分支为异步线程无副作用
        whenever(mockPrefs.getBoolean(any(), any<Boolean>())).thenAnswer { it.getArgument(1) }
        whenever(mockPrefs.getBoolean(eq(SettingsPreferences.KEY_STT_USE_LOCAL), any<Boolean>()))
            .thenReturn(true)
        whenever(mockMainHandler.postDelayed(any<Runnable>(), anyLong())).thenAnswer {
            posted.add(it.getArgument(0))
            true
        }
        whenever(mockMainHandler.removeCallbacks(any<Runnable>())).thenAnswer {
            posted.remove(it.getArgument<Runnable>(0))
        }

        handler = VoiceRecognitionHandler(
            context = mockContext,
            onRecordingStopped = { recordingStoppedCount++ },
            onStateChanged = { stateChanges.add(it); currentState = it },
            getState = { currentState },
            getInputConnection = { mockInputConnection },
            onVoiceComplete = { voiceCompleteCount++ },
            managerFactory = { mockManager },
            mainHandler = mockMainHandler
        )
        handler.initialize()

        val onResultCaptor = argumentCaptor<(String) -> Unit>()
        val onPartialCaptor = argumentCaptor<(String) -> Unit>()
        val onStateCaptor = argumentCaptor<(RecognitionState) -> Unit>()
        val onErrorCaptor = argumentCaptor<(String, Boolean) -> Unit>()
        verify(mockManager).setCallbacks(
            onResultCaptor.capture(), onPartialCaptor.capture(), onStateCaptor.capture(),
            onErrorCaptor.capture(), any(), any()
        )
        onResult = onResultCaptor.firstValue
        onPartial = onPartialCaptor.firstValue
        onState = onStateCaptor.firstValue
        onError = onErrorCaptor.firstValue
    }

    private fun runTimeouts() {
        val copy = posted.toList()
        posted.clear()
        copy.forEach { it.run() }
    }

    @Test
    fun `收尾时收到最终结果则提交完整结果并取消超时兜底`() {
        onPartial.invoke("你好")
        handler.finishRecognition()

        // 停止送音、进入"正在识别"、安排了超时兜底
        verify(mockManager).stopRecognition()
        assertEquals(RecognitionState.PROCESSING, stateChanges.last().voiceRecognitionState)
        assertEquals(1, posted.size)

        // 引擎吐出最终结果：提交增量部分（partial 已通过 composing 上屏，不追加句读）
        onResult.invoke("你好世界再见")

        verify(mockInputConnection).finishComposingText()
        verify(mockInputConnection).commitText(eq("世界再见"), eq(1))
        assertEquals(1, voiceCompleteCount)
        // 超时兜底已被取消，手动推进不再重复提交
        assertTrue(posted.isEmpty())
        runTimeouts()
        verify(mockInputConnection, never()).commitText(eq("你好"), eq(1))
        assertEquals(1, voiceCompleteCount)
    }

    @Test
    fun `超时未收到最终结果则回退提交部分结果且忽略迟到的最终结果`() {
        onPartial.invoke("你好")
        handler.finishRecognition()
        runTimeouts()

        // 部分结果已实时上屏，兜底只结束 composing
        verify(mockInputConnection).finishComposingText()
        verify(mockInputConnection, never()).commitText(any(), anyInt())
        assertEquals(1, voiceCompleteCount)

        // 迟到的最终结果被抑制：不再写入输入框（避免重复/错乱）
        onResult.invoke("你好世界再见")
        verify(mockInputConnection, never()).commitText(any(), anyInt())
        assertEquals(2, voiceCompleteCount)
    }

    @Test
    fun `无已识别文本时收尾不等待直接结束`() {
        handler.finishRecognition()

        verify(mockManager).stopRecognition()
        assertEquals(1, voiceCompleteCount)
        assertTrue(posted.isEmpty())
        assertFalse(stateChanges.any { it.voiceRecognitionState == RecognitionState.PROCESSING })
    }

    @Test
    fun `收尾中重复调用幂等`() {
        onPartial.invoke("你好")
        handler.finishRecognition()
        handler.finishRecognition()

        verify(mockManager).stopRecognition()
        assertEquals(1, posted.size)
        // 收尾尚未完成（在等最终结果），不触发 onVoiceComplete
        assertEquals(0, voiceCompleteCount)
    }

    @Test
    fun `会话被丢弃后收尾不提交文本且超时兜底失效`() {
        onPartial.invoke("你好")
        handler.abandonSession()
        handler.finishRecognition()

        verify(mockManager).stopRecognition()
        runTimeouts()
        verify(mockInputConnection, never()).commitText(any(), anyInt())
        verify(mockInputConnection, never()).finishComposingText()
        assertEquals(0, voiceCompleteCount)
    }

    @Test
    fun `收尾期间引擎的IDLE状态不覆盖正在识别显示`() {
        onPartial.invoke("你好")
        handler.finishRecognition()
        assertEquals(RecognitionState.PROCESSING, stateChanges.last().voiceRecognitionState)

        // 引擎 stop 过程中回调 IDLE：保持"正在识别..."显示
        onState.invoke(RecognitionState.IDLE)
        assertEquals(RecognitionState.PROCESSING, stateChanges.last().voiceRecognitionState)

        // 其他状态（如 ERROR）不被过滤
        onState.invoke(RecognitionState.ERROR)
        assertEquals(RecognitionState.ERROR, stateChanges.last().voiceRecognitionState)
    }

    @Test
    fun `部分结果写入composing区域并同步到UI状态`() {
        onPartial.invoke("你好")

        verify(mockInputConnection).setComposingText(eq("你好"), eq(1))
        assertEquals("你好", stateChanges.last().voiceRecognizedText)
    }

    @Test
    fun `录音中收到流式最终结果则上屏该句但会话继续`() {
        onPartial.invoke("你好")
        // 用户尚未松手，流式插件按句回调 final：该句上屏，会话必须继续
        onResult.invoke("你好")

        // 结束 composing，但不结束会话、不停止录音——
        // 若此时触发 onVoiceComplete，松手停止链即失效，录音会一直在后台运行
        verify(mockInputConnection).finishComposingText()
        verify(mockInputConnection, never()).commitText(any(), anyInt())
        verify(mockManager, never()).stopRecognition()
        assertEquals(0, voiceCompleteCount)

        // 随后松手：正常收尾结束会话
        onPartial.invoke("世界")
        handler.finishRecognition()
        verify(mockManager).stopRecognition()
        onResult.invoke("世界")
        assertEquals(1, voiceCompleteCount)
    }

    @Test
    fun `录音中引擎报错则停止录音并结束会话且丢弃迟到结果`() {
        onError.invoke("网络断开", false)

        // 录音中报错：必须显式停止录音并结束会话，否则 UI 恢复后松手停止链失效
        verify(mockManager).stopRecognition()
        assertEquals(1, voiceCompleteCount)

        // 错误后迟到的部分/最终结果不再写入输入框（迟到 final 仍走一次幂等完成）
        onPartial.invoke("你好")
        onResult.invoke("你好世界")
        verify(mockInputConnection, never()).setComposingText(any(), anyInt())
        verify(mockInputConnection, never()).commitText(any(), anyInt())
    }

    private val editor = StringBuilder()

    private fun startToolbar() {
        whenever(mockInputConnection.getTextBeforeCursor(anyInt(), anyInt())).thenAnswer {
            editor.takeLast(it.getArgument<Int>(0)).toString()
        }
        whenever(mockInputConnection.commitText(any(), anyInt())).thenAnswer {
            editor.append(it.getArgument<CharSequence>(0)); true
        }
        whenever(mockInputConnection.deleteSurroundingText(anyInt(), anyInt())).thenAnswer {
            editor.delete((editor.length - it.getArgument<Int>(0)).coerceAtLeast(0), editor.length); true
        }
        currentState = currentState.copy(voiceSticky = true, isVoiceMode = true)
        handler.startRecognition()
        onState(RecognitionState.LISTENING)
    }

    @Test fun `toolbar stop immediately releases button while late final remains accepted`() {
        startToolbar(); onPartial("你好")
        handler.finishRecognition()
        assertEquals(1, recordingStoppedCount)
        assertEquals(0, voiceCompleteCount)
        onResult("你好世界")
        assertEquals("你好世界", editor.toString())
        assertEquals(1, voiceCompleteCount)
    }

    @Test fun `local empty final after a completed sentence does not wait for timeout`() {
        startToolbar(); onResult("完整一句")
        handler.finishRecognition(); onResult(""); onState(RecognitionState.IDLE)
        assertEquals(1, voiceCompleteCount)
        assertEquals("完整一句", editor.toString())
        assertTrue(posted.isEmpty())
    }

    @Test fun `typing after stop rejects stale correction`() {
        startToolbar(); onPartial("你好")
        handler.finishRecognition(); handler.abandonPendingOnManualInput()
        mockInputConnection.commitText("手动", 1)
        onResult("你好世界")
        assertEquals("你好手动", editor.toString())
    }

    @Test fun `toolbar streams partial and sentence results before stop without duplicates`() {
        startToolbar()
        onPartial("你好")
        assertEquals("你好", editor.toString())
        onResult("你好世界")
        assertEquals("你好世界", editor.toString())
        assertEquals(0, voiceCompleteCount)
        onPartial("继续")
        assertEquals("你好世界 继续", editor.toString())
        handler.finishRecognition()
        onResult("继续说话")
        assertEquals("你好世界 继续说话", editor.toString())
        assertEquals(1, voiceCompleteCount)
        repeat(2) { onResult("继续说话") }
        assertEquals("你好世界 继续说话", editor.toString())
    }

    @Test fun `toolbar partial revisions replace only its own visible tail`() {
        startToolbar()
        onPartial("泥好世界")
        assertEquals("泥好世界", editor.toString())
        onPartial("你好世界")
        assertEquals("你好世界", editor.toString())
        handler.finishRecognition(); onResult("你好世界")
        assertEquals("你好世界", editor.toString())
    }

    @Test fun `toolbar speech preserves intervening manual text`() {
        startToolbar()
        onPartial("你好")
        mockInputConnection.commitText("手动文字", 1)
        onPartial("你好世界")
        assertEquals("你好手动文字世界", editor.toString())
        handler.finishRecognition(); onResult("你好世界")
        assertEquals("你好手动文字世界", editor.toString())
        verify(mockInputConnection, never()).deleteSurroundingText(anyInt(), anyInt())
    }

    @Test fun `toolbar speech does not overwrite a manual selection`() {
        startToolbar()
        onPartial("你好")
        whenever(mockInputConnection.getSelectedText(0)).thenReturn("用户选中的文字")
        onPartial("你好世界")
        handler.finishRecognition(); onResult("你好世界")
        assertEquals("你好", editor.toString())
        verify(mockInputConnection, never()).deleteSurroundingText(anyInt(), anyInt())
    }

    @Test fun `toolbar accepts final only engines after stop`() {
        startToolbar()
        handler.finishRecognition()
        assertEquals(0, voiceCompleteCount)
        onResult("最终识别结果")
        verify(mockInputConnection).commitText(eq("最终识别结果"), eq(1))
        assertEquals(1, voiceCompleteCount)
    }

    @Test fun `toolbar timeout finishes the visible partial once and rejects late results`() {
        startToolbar()
        onResult("第一句话")
        onPartial("第二句")
        assertEquals("第一句话 第二句", editor.toString())
        handler.finishRecognition(); runTimeouts()
        assertEquals("第一句话 第二句", editor.toString())
        repeat(2) { onResult("第二句话") }
        assertEquals("第一句话 第二句", editor.toString())
    }

    @Test fun `toolbar abandoning session rejects all late results`() {
        startToolbar()
        onPartial("不要提交")
        handler.abandonSession()
        repeat(2) { onResult("不要提交") }
        onPartial("不要提交")
        assertEquals("不要提交", editor.toString())
        verify(mockInputConnection, times(1)).commitText(any(), anyInt())
        verify(mockInputConnection, never()).setComposingText(any(), anyInt())
    }

    @Test fun `toolbar stop replay does not duplicate the previous final`() {
        startToolbar()
        onResult("完整的一句")
        handler.finishRecognition()
        onResult("完整的一句")
        verify(mockInputConnection).commitText(eq("完整的一句"), eq(1))
    }
    @Test fun `输入会话变化后迟到语音不能写入新输入框`() {
        currentState = currentState.copy(inputSessionId = 1)
        startToolbar()
        onPartial("第一句")
        org.mockito.Mockito.clearInvocations(mockInputConnection)
        currentState = currentState.copy(inputSessionId = 2)
        onPartial("第一句残留")
        onResult("第一句残留最终结果")
        verify(mockInputConnection, never()).commitText(any(), anyInt())
        verify(mockInputConnection, never()).deleteSurroundingText(anyInt(), anyInt())
    }

    @Test fun `partial final and sequential sentences use spaces without terminal punctuation`() {
        startToolbar()
        onPartial("你好，world！")
        assertEquals("你好 world", editor.toString())
        onResult("你好，world。")
        assertEquals("你好 world", editor.toString())
        onPartial("How are")
        assertEquals("你好 world How are", editor.toString())
        handler.finishRecognition()
        onResult("How are you？")
        assertEquals("你好 world How are you", editor.toString())
        assertEquals(1, voiceCompleteCount)
    }

    @Test fun `final revisions remove punctuation already present in partial without deleting words`() {
        startToolbar()
        onPartial("Hello, wor")
        onPartial("Hello, world!")
        handler.finishRecognition()
        onResult("Hello world.")
        assertEquals("Hello world", editor.toString())
    }


    @Test fun `local idle cannot commit a preview before the final callback`() {
        startToolbar(); onPartial("未校正预览")
        handler.finishRecognition(); onState(RecognitionState.IDLE)
        assertEquals(0, voiceCompleteCount)
        onResult("已校正文本")
        assertEquals("已校正文本", editor.toString())
        assertEquals(1, voiceCompleteCount)
    }

    @Test fun `empty multilingual final removes its unsupported preview`() {
        startToolbar(); onPartial("不应上屏的预览")
        handler.finishRecognition(); onResult("")
        assertEquals("", editor.toString())
        assertEquals(1, voiceCompleteCount)
    }

    @Test fun `ITN final replaces Chinese decimal preview without losing point or dictated punctuation`() {
        startToolbar(); onPartial("零点零五")
        onPartial("0.05句号")
        assertEquals("0.05。", editor.toString())
        handler.finishRecognition(); onResult("0.05句号。")
        assertEquals("0.05。", editor.toString())
    }

    @Test fun `release and timeout never normalize already converted punctuation twice`() {
        startToolbar(); onPartial("结果逗号0.05句号")
        handler.commitPendingOnRelease()
        assertEquals("结果，0.05。", editor.toString())
    }

    @Test fun `composing release retains explicit terminal punctuation`() {
        onPartial("0.05句号")
        handler.commitPendingOnRelease()
        verify(mockInputConnection).setComposingText(eq("0.05。"), eq(1))
        verify(mockInputConnection, never()).deleteSurroundingText(anyInt(), anyInt())
    }
}
