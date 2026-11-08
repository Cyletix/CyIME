package com.kingzcheung.xime.ui.keyboard

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kingzcheung.xime.settings.KeysConfigHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * B站反馈 11-08：开了 26 键数字行，表情页「种类栏和上面工具栏中间的缝」能按出数字。
 * 数字行比 48 dp 的最小触摸区矮，Compose 会把它上沿外几 dp 的点按也算给它；那条缝在工具栏里、
 * 面板挡板之外。面板盖着时键盘不能接任何点按，不盖时数字行照旧。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w800dp-h800dp-mdpi")
class CoveredKeyboardTouchTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private val typed = StringBuilder()

    /** 和键盘一样：上面工具栏，下面数字行，面板从工具栏下沿盖住键盘。 */
    private fun show(covered: Boolean) {
        val app = ApplicationProvider.getApplicationContext<Application>()
        KeysConfigHelper.loadConfig(app)
        com.kingzcheung.xime.ui.theme.KeyboardThemes.initFromConfig(app)
        AppFonts.initialize(app)
        rule.setContent {
            CompositionLocalProvider(LocalKeyboardInputPreferences provides KeyboardInputPreferences(numberRow = true)) {
                MaterialTheme {
                    Box(Modifier.requiredSize(360.dp, 240.dp)) {
                        Column(Modifier.fillMaxSize()) {
                            // 工具栏：探点处没有按钮，和真实工具栏图标之间、下沿那条缝一样。
                            Box(Modifier.fillMaxWidth().height(TOOLBAR_DP.dp))
                            Box(Modifier.fillMaxWidth().weight(1f)) {
                                Column(Modifier.fillMaxSize().blockCoveredKeyboardTouches(covered)) {
                                    NumberKeyRow(
                                        keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
                                        splitAt = -1, splitGap = 0.dp,
                                        keyBackgroundColor = Color.White, keyTextColor = Color.Black,
                                        shadowEnabled = false, shadowElevation = 0.dp, shadowShapeRadius = 0.dp,
                                        onInput = { typed.append(it) },
                                        onKeyPressDown = null, onKeyRelease = null,
                                        modifier = Modifier.height(NUMBER_ROW_DP.dp),
                                    )
                                }
                            }
                        }
                        KeyboardPanelReveal(covered, Modifier.fillMaxSize().padding(top = TOOLBAR_DP.dp)) {
                            Box(Modifier.fillMaxSize())
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
    }

    private fun tap(at: Offset) {
        rule.onRoot().performTouchInput { down(at); advanceEventTime(60L); up() }
        rule.waitForIdle()
    }

    /** 第一个数字键上沿往上 [above] dp，落在工具栏里。 */
    private fun aboveFirstKey(above: Float): Offset {
        val key = rule.onNodeWithTag("number-row-key:0", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("数字键要比 48 dp 矮才有这条缝", key.height < 48f)
        assertEquals(TOOLBAR_DP.toFloat(), key.top, 1f)
        return Offset(key.center.x, key.top - above)
    }

    @Test fun uncoveredNumberRowKeepsItsTouchTarget() {
        show(covered = false)
        tap(aboveFirstKey(3f))
        assertEquals("1", typed.toString())
    }

    @Test fun panelOverKeyboardLeavesNoGapToTheNumberRow() {
        show(covered = true)
        // 工具栏下沿那条缝：面板挡板之外，数字行最小触摸区之内。
        tap(aboveFirstKey(3f))
        // 面板里、数字键正上方：挡板接住。
        val key = rule.onNodeWithTag("number-row-key:0", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        tap(key.center)
        assertEquals("", typed.toString())
    }

    private companion object {
        const val TOOLBAR_DP = 40
        const val NUMBER_ROW_DP = 32
    }
}
