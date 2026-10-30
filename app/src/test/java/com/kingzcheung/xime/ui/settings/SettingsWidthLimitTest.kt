package com.kingzcheung.xime.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 宽屏设置页：内容按 dp 封顶居中，两侧留空处上下拖动也滚当前页面。 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w1500dp-h1000dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsWidthLimitTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private lateinit var first: LazyListState
    private lateinit var second: LazyListState

    @Composable
    private fun Rows(state: LazyListState, tag: String, register: Boolean) {
        LazyColumn(state = state, modifier = (if (register) Modifier.settingsGutterScroll(state) else Modifier)
            .fillMaxSize().testTag(tag)) {
            items(100) { Text("第 $it 行", Modifier.fillMaxWidth().height(48.dp)) }
        }
    }

    private fun show(width: Dp, register: Boolean = true, stacked: Boolean = false) = rule.setContent {
        MaterialTheme {
            Box(Modifier.requiredSize(width, 600.dp)) {
                SettingsWidthLimit {
                    first = rememberLazyListState()
                    second = rememberLazyListState()
                    Box(Modifier.fillMaxSize()) {
                        Rows(first, "first", register)
                        // 像打开下一页：后组合、一样大的列表压在上面。
                        if (stacked) Rows(second, "second", register)
                    }
                }
            }
        }
    }

    @Test fun contentCapIsTheSameDpOnEveryScreen() {
        assertEquals(384.dp, settingsContentWidth(384.dp))   // 手机竖屏
        assertEquals(832.dp, settingsContentWidth(832.dp))   // 手机横屏：本来就不宽，铺满
        assertEquals(840.dp, settingsContentWidth(934.dp))   // 平板竖屏
        assertEquals(840.dp, settingsContentWidth(1493.dp))  // 平板横屏
    }

    @Test fun narrowWindowHasNoSideMargins() {
        show(800.dp)
        rule.onNodeWithTag("settings-content").assertWidthIsEqualTo(800.dp)
        assertEquals(0, rule.onAllNodesWithTagCount("settings-gutter-start"))
    }

    @Test fun draggingEitherSideMarginScrollsThePage() {
        show(1400.dp)
        rule.onNodeWithTag("settings-content").assertWidthIsEqualTo(840.dp)
        rule.onNodeWithTag("settings-gutter-start").performTouchInput { swipeUp() }
        rule.waitForIdle()
        val afterLeft = first.firstVisibleItemIndex
        assertTrue("左侧上拖应往下翻，实际 $afterLeft", afterLeft > 0)
        rule.onNodeWithTag("settings-gutter-end").performTouchInput { swipeUp() }
        rule.waitForIdle()
        assertTrue("右侧上拖继续往下翻", first.firstVisibleItemIndex > afterLeft)
        val bottom = first.firstVisibleItemIndex
        rule.onNodeWithTag("settings-gutter-start").performTouchInput { swipeDown() }
        rule.waitForIdle()
        assertTrue("下拉往回翻", first.firstVisibleItemIndex < bottom)
    }

    @Test fun sideMarginsScrollTheNewestPage() {
        show(1400.dp, stacked = true)
        rule.onNodeWithTag("settings-gutter-end").performTouchInput { swipeUp() }
        rule.waitForIdle()
        assertTrue(second.firstVisibleItemIndex > 0)
        assertEquals(0, first.firstVisibleItemIndex)
    }

    @Test fun pagesThatDidNotRegisterIgnoreTheMargins() {
        show(1400.dp, register = false)
        rule.onNodeWithTag("settings-gutter-start").performTouchInput { swipeUp() }
        rule.waitForIdle()
        assertEquals(0, first.firstVisibleItemIndex)
    }

    private fun androidx.compose.ui.test.junit4.ComposeTestRule.onAllNodesWithTagCount(tag: String) =
        onAllNodes(androidx.compose.ui.test.hasTestTag(tag)).fetchSemanticsNodes().size
}
