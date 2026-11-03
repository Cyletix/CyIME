package com.kingzcheung.xime.ui.render

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kingzcheung.xime.keyboard.ToolbarAction
import com.kingzcheung.xime.keyboard.ToolbarButton
import com.kingzcheung.xime.keyboard.ToolbarButtonItem
import com.kingzcheung.xime.settings.FrostedGlassConfig
import com.kingzcheung.xime.settings.SettingsPreferences
import com.kingzcheung.xime.speech.RecognitionState
import com.kingzcheung.xime.ui.keyboard.CandidateBarVisuals
import com.kingzcheung.xime.ui.keyboard.KeyboardInputPreferences
import com.kingzcheung.xime.ui.keyboard.LocalKeyboardInputPreferences
import com.kingzcheung.xime.ui.keyboard.ToolbarActionButton
import com.kingzcheung.xime.ui.settings.ProfileChoiceRow
import com.kingzcheung.xime.ui.theme.LiquidGlass
import com.kingzcheung.xime.ui.theme.XimeTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.roundToInt

/**
 * 液态玻璃扩到面板件（11-03「支持更多组件」）：键盘里开着玻璃和液态玻璃时，工具栏胶囊、布局/方案卡片画按键同款细亮边；
 * 设置页的同款卡片（没有键盘偏好）和没露出来的工具栏胶囊一点不变。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LiquidGlassSurfacesRenderTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Before fun liquidOn() {
        SettingsPreferences.setGlassLiquid(context, true)
        LiquidGlass.rebind(context)
    }

    @After fun liquidOff() {
        SettingsPreferences.setGlassLiquid(context, false)
        LiquidGlass.rebind(context)
    }

    /** 键盘里：当前外观开着玻璃。 */
    @Composable
    private fun InKeyboard(glass: Boolean, content: @Composable () -> Unit) {
        if (!glass) content()
        else CompositionLocalProvider(LocalKeyboardInputPreferences provides
            KeyboardInputPreferences(frostedGlass = FrostedGlassConfig(enabled = true)), content = content)
    }

    /**
     * 整个窗口画进一张图再按节点裁：Robolectric 下 captureToImage 等不到空闲会超时（同 T9SideRailRenderTest）。
     */
    private fun shots(vararg tags: String): Map<String, Bitmap> {
        val root = rule.activity.window.decorView
        val full = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        rule.runOnIdle { root.draw(android.graphics.Canvas(full)) }
        return tags.associateWith { tag ->
            val b = rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInWindow
            val left = b.left.roundToInt().coerceIn(0, full.width - 1)
            val top = b.top.roundToInt().coerceIn(0, full.height - 1)
            Bitmap.createBitmap(full, left, top, b.width.roundToInt().coerceIn(1, full.width - left),
                b.height.roundToInt().coerceIn(1, full.height - top))
        }
    }

    private fun Bitmap.color(x: Int, y: Int): Color = Color(getPixel(x, y))

    /** 细亮边 0.75dp、只画在路径里面，落在上沿往下 0~2 像素。 */
    private fun edgeLuminance(image: Bitmap, x: Int, top: Int): Float = (top..top + 2).maxOf { image.color(x, it).luminance() }

    /** 先确认真的画出来了（不是一张空图），否则「一样」「更亮」都没有意义。 */
    private fun assertPainted(image: Bitmap, x: Int, y: Int, expected: Color, what: String) {
        val actual = image.color(x, y)
        val close = actual.alpha > .99f && kotlin.math.abs(actual.red - expected.red) < .02f &&
            kotlin.math.abs(actual.green - expected.green) < .02f && kotlin.math.abs(actual.blue - expected.blue) < .02f
        assertTrue("$what 没画出来：($x,$y) 是 $actual，应为 $expected", close)
    }

    private val container = Color(0xFF3A3640)

    @Test fun schemeCardsGetTheRimOnlyInsideTheGlassKeyboard() {
        rule.setContent {
            XimeTheme(darkTheme = true, themeId = "soft_blue") {
                Column(Modifier.background(Color.Black)) {
                    listOf("glass" to true, "plain" to false).forEach { (name, glass) ->
                        InKeyboard(glass) {
                            ProfileChoiceRow("方案", "row-$name", listOf("a" to "拼音", "b" to "小鹤双拼"), "a",
                                Modifier.width(360.dp), itemTagPrefix = "$name:",
                                containerColor = container, contentColor = Color.White,
                                accentColor = Color(0xFFCDB8FF)) {}
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
        val shot = shots("glass:a", "glass:b", "plain:a", "plain:b")
        val glass = shot.getValue("glass:b")
        val plain = shot.getValue("plain:b")
        assertEquals(plain.width, glass.width)
        // 卡片左侧留白处是卡片底色。
        assertPainted(glass, 4 * 3, glass.height / 2, container, "玻璃键盘里的方案卡")
        assertPainted(plain, 4 * 3, plain.height / 2, container, "设置页方案卡")
        // 没选中的卡：左上沿（朝光一侧）是亮边，比原来的灰描边亮。
        val x = (14 * 3) + 6
        val glassTop = edgeLuminance(glass, x, 0)
        val plainTop = edgeLuminance(plain, x, 0)
        assertTrue("液态玻璃卡片上沿 $glassTop 应比灰描边 $plainTop 亮", glassTop > plainTop + .03f)
        // 选中的卡保留强调色描边，两边一样。
        val chosenGlass = shot.getValue("glass:a")
        val chosenPlain = shot.getValue("plain:a")
        for (y in listOf(0, 1, chosenGlass.height / 2)) for (px in listOf(1, chosenGlass.width / 2)) {
            assertEquals("选中卡 ($px,$y)", chosenPlain.getPixel(px, y), chosenGlass.getPixel(px, y))
        }
    }

    @Test fun toolbarPillGetsTheRimWhenOnAndNothingAtRest() {
        val visuals = CandidateBarVisuals(Color.Black, Color.White, Color.Gray, accentColor = Color(0xFFCDB8FF), isDarkTheme = true)
        rule.setContent {
            XimeTheme(darkTheme = true, themeId = "soft_blue") {
                Column(Modifier.background(Color.Black)) {
                    listOf("glass" to true, "plain" to false).forEach { (name, glass) ->
                        InKeyboard(glass) {
                            listOf("on" to true, "rest" to false).forEach { (state, active) ->
                                Box(Modifier.width(60.dp).background(Color.Black).testTag("$name-$state")) {
                                    ToolbarActionButton(ToolbarAction(ToolbarButtonItem.Builtin(ToolbarButton.CLIPBOARD), active) {},
                                        visuals, Color.White, 0f, RecognitionState.IDLE, modifier = Modifier.fillMaxWidth())
                                }
                            }
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
        val shot = shots("glass-on", "plain-on", "glass-rest", "plain-rest")
        shot.forEach { (tag, image) -> assertPainted(image, 1, 1, Color.Black, "工具栏格 $tag 的黑底") }
        // 60dp 格：胶囊 48×32dp，上沿在 y=4dp；取上沿直线段的起点（x=6+16dp）。
        val x = (6 + 16) * 3
        val y = 4 * 3
        fun top(tag: String): Float = edgeLuminance(shot.getValue(tag), x, y)
        assertTrue("开启的工具栏胶囊应有亮边", top("glass-on") > top("plain-on") + .03f)
        // 没开启、没按下时胶囊不露出来，也不画亮边。
        assertEquals(shot.getValue("plain-rest").getPixel(x, y), shot.getValue("glass-rest").getPixel(x, y))
    }
}
