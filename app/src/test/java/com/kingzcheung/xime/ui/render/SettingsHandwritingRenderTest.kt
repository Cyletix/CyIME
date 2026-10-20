package com.kingzcheung.xime.ui.render

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kingzcheung.xime.settings.FrostedGlassPreferences
import com.kingzcheung.xime.settings.SettingsPreferences
import com.kingzcheung.xime.ui.settings.HandwritingSettingsContent
import com.kingzcheung.xime.ui.settings.KeyEffectSettingsContent
import com.kingzcheung.xime.ui.settings.SettingsMainContent
import com.kingzcheung.xime.ui.theme.KeyboardThemes
import com.kingzcheung.xime.ui.theme.XimeTheme
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 设置首页、「手写」页、「按键效果」页的对比图：每页每种玻璃一张，横排浅色、深色、纯白、纯黑四个主题，
 * 每个主题再按 360dp（手机）、600dp（大屏手机）、900dp（平板横屏）三种宽度各画一列。
 * 手写页按开发版显示训练与采集；按键效果页先打开按键音，才看得到「按键音色」。
 * 只在设置了环境变量 CYIME_RENDER_OUT=<目录> 时出图（平时跳过，不进普通单测）。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w7920dp-h2000dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsHandwritingRenderTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private val themes = listOf(Triple("soft_blue", false, "浅色"), Triple("soft_blue", true, "深色"),
        Triple("pure_black", false, "纯白"), Triple("pure_black", true, "纯黑"))
    private val widths = listOf(360, 600, 900)
    private val pages = listOf("home", "handwriting", "keyeffect")

    @Composable
    private fun Page(page: String) = when (page) {
        "home" -> SettingsMainContent(onNavigateToSchema = {}, onNavigateToTheme = {}, onNavigateToKeyEffect = {},
            onNavigateToLayoutDisplay = {}, onNavigateToDictionary = {}, onNavigateToPlugins = {},
            onNavigateToSmartPrediction = {}, onNavigateToSpeechToText = {}, onNavigateToAbout = {})
        "handwriting" -> HandwritingSettingsContent(onBack = {}, devTools = true)
        else -> KeyEffectSettingsContent(onBack = {})
    }

    @Test fun renderComparisons() {
        val out = System.getenv("CYIME_RENDER_OUT")
        assumeTrue(!out.isNullOrEmpty())
        val context = ApplicationProvider.getApplicationContext<Context>()
        KeyboardThemes.initFromConfig(context)
        SettingsPreferences.setSoundEnabled(context, true)
        // 按键效果页的示意按键用 ChaiPUA 字体，应用启动时才初始化；渲染前手动初始化。
        com.kingzcheung.xime.ui.keyboard.AppFonts.initialize(context)
        val frames = pages.flatMap { p -> listOf(false, true).map { p to it } }
        val shown = mutableIntStateOf(0)
        rule.setContent {
            val (page, _) = frames[shown.intValue]
            Row(Modifier.background(Color(0xFF808080))) {
                for ((themeId, dark, themeLabel) in themes) for (width in widths) {
                    Column(Modifier.padding(4.dp)) {
                        Text("$themeLabel ${width}dp", fontSize = 12.sp, color = Color.White)
                        Box(Modifier.width(width.dp).fillMaxHeight()) {
                            XimeTheme(darkTheme = dark, themeId = themeId) { Page(page) }
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                }
            }
        }
        // 首页每 0.5 秒刷新状态的循环不会停，自动推进时钟会一直等不到空闲；改成手动推进。
        rule.mainClock.autoAdvance = false
        File(out!!).mkdirs()
        frames.forEachIndexed { index, (page, glass) ->
            rule.runOnIdle {
                FrostedGlassPreferences.saveProfiles(context, FrostedGlassPreferences.readProfiles(context).withEnabled(glass))
                shown.intValue = index
            }
            rule.mainClock.advanceTimeBy(1500)
            rule.waitForIdle()
            // Robolectric 下 captureToImage 等不到 PixelCopy；直接把整个窗口画到位图上。
            val root = rule.activity.window.decorView
            val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            rule.runOnIdle { root.draw(android.graphics.Canvas(bitmap)) }
            File(out, "settings-$page-${if (glass) "glass" else "solid"}.png").outputStream()
                .use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}
