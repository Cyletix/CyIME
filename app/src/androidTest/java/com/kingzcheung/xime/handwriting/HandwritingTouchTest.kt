package com.kingzcheung.xime.handwriting

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.kingzcheung.xime.ui.keyboard.HandwritingKeyboardLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** 真实触摸（含抖动和跨区域移动），不使用 performClick 绕过手势分发。 */
class HandwritingTouchTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun functionKeysOwnTheirTouchesAtBothHeightsAndNeverWriteInk(): Unit = runBlocking {
        assertTrue(withContext(Dispatchers.IO) {
            HandwritingEngine.initialize(InstrumentationRegistry.getInstrumentation().targetContext)
        })
        val events = mutableListOf<String>()
        val height = mutableStateOf(300.dp)
        rule.setContent {
            MaterialTheme {
                HandwritingKeyboardLayout(onKeyPress = { events += it },
                    onRecognition = { events += "result" }, bottomPaddingDp = 0,
                    keyTextColor = Color.Black,
                    modifier = Modifier.fillMaxWidth().height(height.value).background(Color.White))
            }
        }
        rule.mainClock.autoAdvance = false
        val canvas = rule.onNodeWithTag("handwriting-canvas")
        fun jitter(key: String) {
            rule.onNodeWithTag("handwriting-key:$key").performTouchInput {
                down(center); moveTo(center + Offset(13f, 0f)); moveTo(center + Offset(13f, 5f)); up()
            }
            rule.mainClock.advanceTimeByFrame()
        }
        fun blank(): Boolean {
            rule.mainClock.advanceTimeByFrame()
            val pixels = canvas.captureToImage().toPixelMap()
            return (20 until pixels.width - 20 step 4).all { x ->
                (20 until pixels.height - 20 step 4).all { y -> pixels[x, y].red > 0.95f }
            }
        }
        fun stroke() {
            canvas.performTouchInput { down(Offset(60f, 70f)); moveTo(Offset(100f, 70f)); moveTo(Offset(220f, 70f)); up() }
            rule.mainClock.advanceTimeByFrame()
        }
        val keys = listOf("delete", "space", "number", "symbol", "ime_switch", "，", "。", "enter")
        for (panelHeight in listOf(300.dp, 180.dp)) {
            rule.runOnIdle { height.value = panelHeight }
            rule.mainClock.advanceTimeByFrame()
            rule.runOnIdle { events.clear() }
            keys.forEach(::jitter)
            rule.mainClock.advanceTimeBy(1200)
            rule.runOnIdle { assertEquals("空白时每次轻微移动的点击只能执行对应按键", keys, events) }
            assertTrue("按功能键不能留下任何笔迹", blank())
        }
        rule.runOnIdle { height.value = 300.dp; events.clear() }
        rule.mainClock.advanceTimeByFrame()
        stroke(); assertFalse("实际落笔应渲染", blank())
        jitter("delete")
        rule.mainClock.advanceTimeBy(1500)
        assertTrue("删除清除整轮未识别笔迹", blank())
        rule.runOnIdle { assertTrue("不能删宿主文字或继续识别", events.isEmpty()) }

        // 起点在按钮：拖进画布也不能变成书写。
        val key = rule.onNodeWithTag("handwriting-key:delete")
        val delta = canvas.fetchSemanticsNode().positionInRoot - key.fetchSemanticsNode().positionInRoot
        key.performTouchInput { down(center); moveTo(delta + Offset(80f, 80f)); up() }
        rule.mainClock.advanceTimeBy(1500)
        assertTrue(blank())
        rule.runOnIdle { assertTrue(events.isEmpty()) }

        // 起点在画布：拖到删除键也不能点击删除，笔迹限制在画布内。
        canvas.performTouchInput { down(Offset(80f, 80f)); moveTo(Offset(width + 60f, 80f)); up() }
        rule.mainClock.advanceTimeByFrame()
        rule.runOnIdle { assertTrue(events.isEmpty()) }
        jitter("delete")
        assertTrue(blank())

        for (mode in listOf("number", "symbol", "ime_switch")) {
            rule.runOnIdle { events.clear() }
            stroke(); jitter(mode)
            rule.mainClock.advanceTimeBy(1500)
            rule.runOnIdle { assertEquals(listOf(mode), events) }
            assertTrue(blank())
        }
        rule.runOnIdle { events.clear() }
        stroke(); jitter("space")
        rule.waitUntil(20_000) { events.contains("space") }
        rule.runOnIdle { assertEquals("空格须在识别结果后执行一次", listOf("result", "space"), events) }
        assertTrue(blank())
    }
}
