package com.kingzcheung.xime.service

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.view.inputmethod.InputConnection
import com.kingzcheung.xime.settings.SettingsPreferences
import com.kingzcheung.xime.speech.RecognitionState
import com.kingzcheung.xime.speech.SpeechRecognitionManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever

/**
 * 隔空输入：说完的话发到电脑，手机输入框只用 composing 预览、说完清掉，不上屏（commitText 一次都不调）。
 * 收尾规则与本机上屏相同：工具栏模式 stop 时重发的上一句只发一次，松手时发出预览中的那句。
 */
@RunWith(MockitoJUnitRunner.Silent::class)
class VoiceRemoteTypingTest {
    @Mock private lateinit var mockContext: Context
    @Mock private lateinit var mockPrefs: SharedPreferences
    @Mock private lateinit var mockInputConnection: InputConnection
    @Mock private lateinit var mockManager: SpeechRecognitionManager
    @Mock private lateinit var mockMainHandler: Handler

    private lateinit var handler: VoiceRecognitionHandler
    private var currentState = InputUIState(currentSchemaId = "rime_ice")
    private var voiceCompleteCount = 0
    private val posted = mutableListOf<Runnable>()
    private val sent = mutableListOf<String>()
    private var remoteOn = true
    private var composing = ""

    private lateinit var onResult: (String) -> Unit
    private lateinit var onPartial: (String) -> Unit
    private lateinit var onState: (RecognitionState) -> Unit

    @Before
    fun setup() {
        whenever(mockContext.getSharedPreferences(any(), anyInt())).thenReturn(mockPrefs)
        whenever(mockPrefs.getBoolean(any(), any<Boolean>())).thenAnswer { it.getArgument(1) }
        whenever(mockPrefs.getBoolean(eq(SettingsPreferences.KEY_STT_USE_LOCAL), any<Boolean>())).thenReturn(true)
        whenever(mockManager.finishTimeoutMs).thenReturn(8000L)
        whenever(mockMainHandler.postDelayed(any<Runnable>(), anyLong())).thenAnswer { posted.add(it.getArgument(0)); true }
        whenever(mockMainHandler.removeCallbacks(any<Runnable>())).thenAnswer { posted.remove(it.getArgument<Runnable>(0)) }
        whenever(mockInputConnection.setComposingText(any(), anyInt())).thenAnswer {
            composing = it.getArgument<CharSequence>(0).toString(); true
        }

        handler = VoiceRecognitionHandler(
            context = mockContext,
            onStateChanged = { currentState = it },
            getState = { currentState },
            getInputConnection = { mockInputConnection },
            onVoiceComplete = { voiceCompleteCount++ },
            managerFactory = { mockManager },
            mainHandler = mockMainHandler,
            remoteTyping = { if (remoteOn) { text: String -> sent += text } else null },
        )
        handler.initialize()
        val results = argumentCaptor<(String) -> Unit>()
        val partials = argumentCaptor<(String) -> Unit>()
        val states = argumentCaptor<(RecognitionState) -> Unit>()
        verify(mockManager).setCallbacks(results.capture(), partials.capture(), states.capture(), any(), any(), any())
        onResult = results.firstValue
        onPartial = partials.firstValue
        onState = states.firstValue
    }

    private fun start(toolbar: Boolean) {
        currentState = currentState.copy(voiceSticky = toolbar, isVoiceMode = true)
        handler.startRecognition()
        onState(RecognitionState.LISTENING)
    }

    @Test fun `toolbar sentences go to the computer and only preview on the phone`() {
        start(toolbar = true)
        onPartial("你好")
        assertEquals("你好", composing)
        onResult("你好世界")
        assertEquals("", composing)
        onPartial("今天")
        onResult("今天天气不错")
        handler.finishRecognition(); onResult("今天天气不错")
        assertEquals(listOf("你好世界", "今天天气不错"), sent)
        assertEquals("", composing)
        assertEquals(1, voiceCompleteCount)
        verify(mockInputConnection, never()).commitText(any(), anyInt())
    }

    @Test fun `release sends the previewed sentence once`() {
        start(toolbar = false)
        onPartial("按住说的话")
        handler.commitPendingOnRelease()
        onResult("按住说的话")
        assertEquals(listOf("按住说的话"), sent)
        assertEquals("", composing)
        verify(mockInputConnection, never()).commitText(any(), anyInt())
    }

    @Test fun `english sentences get one space between them`() {
        start(toolbar = true)
        onResult("hello there")
        onResult("how are you")
        assertEquals(listOf("hello there", " how are you"), sent)
    }

    @Test fun `switch off types on the phone as before`() {
        remoteOn = false
        start(toolbar = false)
        onPartial("你好")
        handler.finishRecognition()
        onResult("你好世界")
        assertTrue(sent.isEmpty())
        verify(mockInputConnection).commitText(eq("世界"), anyInt())
    }
}
