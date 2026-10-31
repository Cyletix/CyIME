package com.kingzcheung.xime.ui.render

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
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
 * 设置首页在简体、繁體、English、日本語下的样子：看翻译后的标题、说明有没有挤爆、截断。
 * 只在设置了环境变量 CYIME_RENDER_OUT=<目录> 时出图。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppLanguageRenderTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test @Config(qualifiers = "zh-rCN-w420dp-h2600dp-mdpi") fun simplified() = render("zh-CN")
    @Test @Config(qualifiers = "zh-rTW-w420dp-h2600dp-mdpi") fun traditional() = render("zh-TW")
    @Test @Config(qualifiers = "en-rUS-w420dp-h2600dp-mdpi") fun english() = render("en")
    @Test @Config(qualifiers = "ja-rJP-w420dp-h2600dp-mdpi") fun japanese() = render("ja")

    private fun render(name: String) {
        val out = System.getenv("CYIME_RENDER_OUT")
        assumeTrue(!out.isNullOrEmpty())
        val context = ApplicationProvider.getApplicationContext<Context>()
        KeyboardThemes.initFromConfig(context)
        rule.setContent {
            XimeTheme(darkTheme = false) {
                Box(Modifier.width(420.dp).fillMaxHeight()) {
                    SettingsMainContent(onNavigateToSchema = {}, onNavigateToTheme = {}, onNavigateToKeyEffect = {},
                        onNavigateToLayoutDisplay = {}, onNavigateToDictionary = {}, onNavigateToPlugins = {},
                        onNavigateToSmartPrediction = {}, onNavigateToSpeechToText = {}, onNavigateToAbout = {})
                }
            }
        }
        // 首页每 0.5 秒刷新状态，时钟手动推进（同 SettingsHomeRenderTest）。
        rule.mainClock.autoAdvance = false
        rule.mainClock.advanceTimeBy(1500)
        rule.waitForIdle()
        File(out!!).mkdirs()
        val root = rule.activity.window.decorView
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        rule.runOnIdle { root.draw(android.graphics.Canvas(bitmap)) }
        File(out, "settings-home-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
