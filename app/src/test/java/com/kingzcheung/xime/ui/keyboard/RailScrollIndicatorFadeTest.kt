package com.kingzcheung.xime.ui.keyboard

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 九键左栏滚动条要和系统列表滚动条一样自动隐藏（用户 11-03：滚动条不会自动隐藏了）：
 * 静止时不显示，滑动时显示，停手 300 ms 后淡出。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w200dp-h300dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RailScrollIndicatorFadeTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun showsWhileScrollingAndHidesAfterScrollStops() = runTest {
        val scrolling = MutableStateFlow(false)
        val events = mutableListOf<String>()
        val job = launch { railScrollbarVisibility(scrolling, show = { events += "show" }, hide = { events += "hide" }) }
        runCurrent()
        assertEquals("静止时不显示也不计时", emptyList<String>(), events)

        scrolling.value = true; runCurrent()
        assertEquals(listOf("show"), events)

        scrolling.value = false; runCurrent()
        advanceTimeBy(RAIL_SCROLLBAR_FADE_DELAY_MS - 1); runCurrent()
        assertEquals("停手不到 300 ms 还在", listOf("show"), events)
        advanceTimeBy(2); runCurrent()
        assertEquals(listOf("show", "hide"), events)

        // 停手后 300 ms 内又滑：不淡出，接着显示。
        scrolling.value = true; runCurrent()
        scrolling.value = false; runCurrent()
        advanceTimeBy(100); runCurrent()
        scrolling.value = true; runCurrent()
        advanceTimeBy(1_000); runCurrent()
        assertEquals(listOf("show", "hide", "show", "show"), events)

        scrolling.value = false; runCurrent()
        advanceTimeBy(RAIL_SCROLLBAR_FADE_DELAY_MS + 1); runCurrent()
        assertEquals(listOf("show", "hide", "show", "show", "hide"), events)
        job.cancel()
    }

    @Test fun overflowingRailShowsNoScrollbarAtRest() {
        rule.setContent {
            val state = rememberLazyListState()
            LazyColumn(state = state, modifier = Modifier.size(60.dp, 100.dp).background(Color.White)
                .testTag("rail").railScrollIndicator(state, Color.Red)) {
                items(20) { Box(Modifier.fillMaxWidth().height(25.dp)) }
            }
        }
        rule.waitForIdle()
        // 和 T9SideRailRenderTest 一样直接把窗口画进位图：captureToImage 在 Robolectric 里会等窗口帧而超时。
        val bounds = rule.onNodeWithTag("rail").fetchSemanticsNode().boundsInRoot
        val root = rule.activity.window.decorView
        val full = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        rule.runOnIdle { root.draw(Canvas(full)) }
        val left = bounds.left.toInt().coerceIn(0, full.width - 1)
        val top = bounds.top.toInt().coerceIn(0, full.height - 1)
        val right = bounds.right.toInt().coerceIn(left + 1, full.width)
        val bottom = bounds.bottom.toInt().coerceIn(top + 1, full.height)
        var red = 0
        for (x in left until right) for (y in top until bottom) {
            val c = full.getPixel(x, y)
            if (android.graphics.Color.red(c) > 150 && android.graphics.Color.green(c) < 100 &&
                android.graphics.Color.blue(c) < 100) red++
        }
        // 确认确实画出来了（白底），免得空图也算通过。
        val center = full.getPixel((left + right) / 2, (top + bottom) / 2)
        assertEquals("列表背景应已画出", 0xFFFFFFFF.toInt(), center)
        assertEquals("静止时不该有滚动条", 0, red)
    }
}
