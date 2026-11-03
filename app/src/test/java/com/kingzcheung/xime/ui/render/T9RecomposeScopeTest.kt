package com.kingzcheung.xime.ui.render

import android.app.Application
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composer
import androidx.compose.runtime.CompositionTracer
import androidx.compose.runtime.InternalComposeTracingApi
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kingzcheung.xime.rime.T9InputController
import com.kingzcheung.xime.service.CandidateState
import com.kingzcheung.xime.ui.keyboard.*
import com.kingzcheung.xime.viewmodel.KeyboardUiState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.concurrent.atomic.AtomicInteger

/**
 * 九键每按一个数字只应重组候选栏和左栏，九键键区（T9KeyboardContent）只在 0 键在「0」和「下一项」之间切换时重组。
 * 用 Compose 的组合追踪数 T9KeyboardContent 本身被执行了几次。
 */
@OptIn(InternalComposeTracingApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class, qualifiers = "zh-rCN-w440dp-h700dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class T9RecomposeScopeTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private val contentRuns = AtomicInteger()

    private val tracer = object : CompositionTracer {
        override fun isTraceInProgress() = true
        override fun traceEventStart(key: Int, dirty1: Int, dirty2: Int, info: String) {
            if (info.contains(".T9KeyboardContent (")) contentRuns.incrementAndGet()
        }
        override fun traceEventEnd() {}
    }

    @After fun stopTracing() {
        Composer.setTracer(object : CompositionTracer {
            override fun isTraceInProgress() = false
            override fun traceEventStart(key: Int, dirty1: Int, dirty2: Int, info: String) {}
            override fun traceEventEnd() {}
        })
    }

    @Test fun typingDigitsRecomposesTheKeypadOnlyWhenZeroKeyChanges() {
        AppFonts.initialize(rule.activity.application)
        Composer.setTracer(tracer)
        val controller = mock<T9InputController> {
            on { leftPanelState } doReturn T9InputController.LeftPanelState.IDLE
            on { firstOptions } doReturn emptyList()
        }
        val candidate = mutableStateOf(CandidateState())
        rule.setContent {
            T9KeyboardLayout({}, KeyboardCallbacks(onKeyPress = { _, _ -> }, onCandidateSelect = {}),
                KeyboardUiState(currentSchemaId = "t9_pinyin"), controller, Color.DarkGray, Color.White, Color.Gray,
                modifier = Modifier.width(380.dp).height(250.dp), candidateState = candidate)
        }
        rule.waitForIdle()
        assertTrue("组合追踪没有记到九键键区，测试本身失效", contentRuns.get() > 0)
        rule.onNodeWithContentDescription("下一项", true).assertDoesNotExist()

        // 第一个数字：0 键变「下一项」，键区要重组。
        contentRuns.set(0)
        rule.runOnIdle {
            candidate.value = CandidateState(inputText = "9", preeditText = "w", isComposing = true,
                candidates = listOf("我", "为", "无"), engineRevision = 1)
        }
        rule.waitForIdle()
        rule.onNodeWithContentDescription("下一项", true).assertIsDisplayed()
        assertTrue(contentRuns.get() >= 1)

        // 接着打：0 键不变，键区不重组。
        contentRuns.set(0)
        listOf("94", "946", "9464").forEachIndexed { i, digits ->
            rule.runOnIdle {
                candidate.value = CandidateState(inputText = digits, preeditText = digits, isComposing = true,
                    candidates = listOf("我", "为", "无"), engineRevision = 2L + i)
            }
            rule.waitForIdle()
        }
        assertEquals(0, contentRuns.get())
        rule.onNodeWithContentDescription("下一项", true).assertIsDisplayed()

        // 删空：0 键变回「0」，键区要重组。
        contentRuns.set(0)
        rule.runOnIdle { candidate.value = CandidateState() }
        rule.waitForIdle()
        rule.onNodeWithContentDescription("下一项", true).assertDoesNotExist()
        assertTrue(contentRuns.get() >= 1)
    }
}
