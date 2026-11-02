package com.kingzcheung.xime.ui.render

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kingzcheung.xime.ui.keyboard.CornerInputChoice
import com.kingzcheung.xime.ui.settings.CornerInputIllustration
import com.kingzcheung.xime.ui.settings.IllustratedChoiceCard
import com.kingzcheung.xime.ui.settings.choiceCardRowHeight
import com.kingzcheung.xime.ui.theme.XimeTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 带示意图的选项卡片不能切字（11-02：「按键角标的符号和数字」三张卡固定 176dp 高，第三行「长按 → …」被切掉一半）。
 * 手机竖屏设置页一排三张卡（328dp 宽），普通字号和调大字号都要把三行看全。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChoiceCardClipTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private fun assertCornerCardsShowEveryLine(fontScale: Float) {
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                XimeTheme(darkTheme = true, themeId = "soft_blue") {
                    Row(Modifier.width(328.dp).choiceCardRowHeight(176.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CornerInputChoice.entries.forEach { choice ->
                            IllustratedChoiceCard(choice.label, choice == CornerInputChoice.SWIPE_BUBBLE, onClick = {},
                                modifier = Modifier.weight(1f), titleMaxLines = 2) {
                                CornerInputIllustration(choice, reversed = true)
                            }
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
        // 每张卡都有一行「长按 → …」，是最容易被切的一行。
        val lines = rule.onAllNodes(hasText("长按"), useUnmergedTree = true).fetchSemanticsNodes()
        assertEquals(3, lines.size)
        lines.forEach { node ->
            val full = node.size.height.toFloat()
            val visible = node.boundsInRoot.height
            assertTrue("字号 ×$fontScale：「长按」只露出 $visible / $full 像素", visible >= full - 1f)
        }
    }

    @Test fun normalFontShowsEveryLine() = assertCornerCardsShowEveryLine(1f)

    @Test fun largeFontShowsEveryLine() = assertCornerCardsShowEveryLine(1.3f)
}
