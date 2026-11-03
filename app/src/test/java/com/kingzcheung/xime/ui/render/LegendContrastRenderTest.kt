package com.kingzcheung.xime.ui.render

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kingzcheung.xime.ui.keyboard.JapaneseLongVowelKey
import com.kingzcheung.xime.ui.keyboard.QwertyStaggerMode
import com.kingzcheung.xime.ui.settings.IllustratedChoiceCard
import com.kingzcheung.xime.ui.settings.JapaneseLongVowelIllustration
import com.kingzcheung.xime.ui.settings.KeyFaceHintsLegend
import com.kingzcheung.xime.ui.settings.LEGEND_MIN_KEY_CONTRAST
import com.kingzcheung.xime.ui.settings.QwertyStaggerIllustration
import com.kingzcheung.xime.ui.settings.SpacePunctuationIllustration
import com.kingzcheung.xime.ui.settings.choiceCardRowHeight
import com.kingzcheung.xime.ui.theme.XimeTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 设置页图例按真键盘配色画（11-03：黑白浅色主题「三行字母错位」四张图白底白键，只看得见两根深色条）。
 * 图例里的键必须和图例底色分得开；设 CYIME_RENDER_OUT 时把整页图例存成 PNG 对照。
 * 设置页图例与玻璃透明度无关（设置页后面没有应用），玻璃开关不影响这里的颜色。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LegendContrastRenderTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private fun contrast(a: Color, b: Color): Float {
        val hi = maxOf(a.luminance(), b.luminance())
        val lo = minOf(a.luminance(), b.luminance())
        return (hi + .05f) / (lo + .05f)
    }

    private fun check(themeId: String, dark: Boolean) {
        rule.setContent {
            XimeTheme(darkTheme = dark, themeId = themeId) {
                Column(Modifier.testTag("legend-page").background(MaterialTheme.colorScheme.surface).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.width(379.dp).choiceCardRowHeight(108.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        QwertyStaggerMode.entries.forEachIndexed { index, mode ->
                            IllustratedChoiceCard(mode.label, mode == QwertyStaggerMode.PHONE, onClick = {},
                                modifier = Modifier.weight(1f)) {
                                Box(Modifier.testTag("stagger-$index")) {
                                    QwertyStaggerIllustration(when (mode) {
                                        QwertyStaggerMode.PHYSICAL -> 1f
                                        QwertyStaggerMode.AUTO -> .5f
                                        else -> 0f
                                    })
                                }
                            }
                        }
                    }
                    SpacePunctuationIllustration(comma = true, period = true)
                    Row(Modifier.width(379.dp).choiceCardRowHeight(108.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        JapaneseLongVowelKey.entries.forEach { mode ->
                            IllustratedChoiceCard(mode.label, mode == JapaneseLongVowelKey.entries.first(), onClick = {},
                                modifier = Modifier.weight(1f)) { JapaneseLongVowelIllustration(mode) }
                        }
                    }
                    Box(Modifier.fillMaxWidth()) { KeyFaceHintsLegend(up = true, down = true, doublePinyin = false, opacity = 1f) }
                }
            }
        }
        rule.waitForIdle()
        val root = rule.activity.window.decorView
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        rule.runOnIdle { root.draw(android.graphics.Canvas(bitmap)) }
        val density = root.resources.displayMetrics.density
        for (index in QwertyStaggerMode.entries.indices) {
            val bounds = rule.onNodeWithTag("stagger-$index", useUnmergedTree = true).fetchSemanticsNode().boundsInWindow
            val unit = bounds.width / 10f
            val rowHeight = bounds.height / 3f
            // 第一排第一个键的中心（这个键不高亮）；图例底色取在示意图左边 3dp 的留白里。
            val key = Color(bitmap.getPixel((bounds.left + unit / 2f).toInt(), (bounds.top + rowHeight / 2f).toInt()))
            val board = Color(bitmap.getPixel((bounds.left - 3f * density).toInt(), (bounds.top + bounds.height / 2f).toInt()))
            val ratio = contrast(key, board)
            assertTrue("$themeId dark=$dark 第 $index 张：键 $key 底 $board 对比 $ratio", ratio >= LEGEND_MIN_KEY_CONTRAST - .02f)
        }
        val out = System.getenv("CYIME_RENDER_OUT") ?: return
        val page = rule.onNodeWithTag("legend-page", useUnmergedTree = true).fetchSemanticsNode().boundsInWindow
        File(out).mkdirs()
        File(out, "legend-$themeId-${if (dark) "dark" else "light"}.png").outputStream().use {
            Bitmap.createBitmap(bitmap, page.left.toInt(), page.top.toInt(), page.width.toInt(), page.height.toInt())
                .compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun pureBlackLight() = check("pure_black", false)

    @Test fun pureBlackDark() = check("pure_black", true)

    @Test fun softBlueLight() = check("soft_blue", false)

    @Test fun iosGradientDark() = check("transparent_glass", true)
}
