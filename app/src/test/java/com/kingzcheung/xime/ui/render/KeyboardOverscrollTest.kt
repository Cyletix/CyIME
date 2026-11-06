package com.kingzcheung.xime.ui.render

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.OverscrollEffect
import androidx.compose.foundation.OverscrollFactory
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kingzcheung.xime.ui.keyboard.NoKeyboardOverscroll
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * 安卓 12 用户（11-06）：键盘里的列表滑着滑着就点不动，九键左栏标点点了没用，剪贴板拖动卡住。
 * 拉伸回弹没收完时列表会吞掉按下（见 [NoKeyboardOverscroll]）。这里用一直「回弹中」的效果模拟那种手机：
 * 键盘窗口外层就算给了这种效果，键盘里的竖滑、横滑列表也照样能点。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w900dp-h1400dp-hdpi")
class KeyboardOverscrollTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private val marks = listOf("，", "。", "？", "！", "、", "：", "；", "“", "”", "…", "～", "（", "）")

    @OptIn(ExperimentalFoundationApi::class)
    private class StuckOverscroll : OverscrollEffect {
        override fun applyToScroll(delta: Offset, source: NestedScrollSource, performScroll: (Offset) -> Offset): Offset =
            performScroll(delta)
        override suspend fun applyToFling(velocity: Velocity, performFling: suspend (Velocity) -> Velocity) {
            performFling(velocity)
        }
        override val isInProgress: Boolean get() = true
    }

    @OptIn(ExperimentalFoundationApi::class)
    private data class StuckFactory(val effect: OverscrollEffect = StuckOverscroll()) : OverscrollFactory {
        override fun createOverscrollEffect(): OverscrollEffect = effect
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    private fun Stuck(content: @Composable () -> Unit) {
        val factory = remember { StuckFactory() }
        CompositionLocalProvider(LocalOverscrollFactory provides factory) { content() }
    }

    @Composable
    private fun Lists(onColumn: (Int) -> Unit, onRow: (Int) -> Unit) {
        Column {
            // 九键左栏那样的竖条标点
            LazyColumn(Modifier.width(48.dp).height(160.dp)) {
                items(marks.size) { i ->
                    Text(marks[i], Modifier.testTag("column:$i").clickable { onColumn(i) }.padding(12.dp))
                }
            }
            // 剪贴板预览、符号分类那样的横条
            LazyRow(Modifier.width(200.dp).height(44.dp)) {
                items(marks.size) { i ->
                    Text(marks[i], Modifier.testTag("row:$i").clickable { onRow(i) }.padding(12.dp))
                }
            }
        }
    }

    /** 对照：不经过键盘窗口时，同样的列表确实一下也点不上，证明模拟得出那种手机。 */
    @Test fun stuckOverscrollSwallowsTapsOutsideTheKeyboard() {
        val taps = mutableListOf<String>()
        rule.setContent { Stuck { Lists({ taps += "c$it" }, { taps += "r$it" }) } }
        rule.onNodeWithTag("column:1").performClick()
        rule.onNodeWithTag("row:2").performClick()
        rule.runOnIdle { assertEquals(emptyList<String>(), taps) }
    }

    @Test fun keyboardListsStayTappableWhileOverscrollIsStuck() {
        val taps = mutableListOf<String>()
        rule.setContent { Stuck { NoKeyboardOverscroll { Lists({ taps += "c$it" }, { taps += "r$it" }) } } }
        rule.onNodeWithTag("column:1").performClick()
        rule.onNodeWithTag("column:3").performClick()
        rule.onNodeWithTag("row:2").performClick()
        rule.runOnIdle { assertEquals(listOf("c1", "c3", "r2"), taps) }
    }
}
