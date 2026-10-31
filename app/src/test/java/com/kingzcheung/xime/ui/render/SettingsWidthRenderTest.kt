package com.kingzcheung.xime.ui.render

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kingzcheung.xime.ui.settings.SettingsMainContent
import com.kingzcheung.xime.ui.settings.SettingsWidthLimit
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
 * 设置首页在手机/平板、横竖屏下的内容宽度（内容最宽 840dp，两侧留空），以及平板横屏在左侧留空处上拖后的样子。
 * 只在设置了环境变量 CYIME_RENDER_OUT=<目录> 时出图（平时跳过，不进普通单测）。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "zh-rCN-w1500dp-h1500dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsWidthRenderTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private data class Size(val name: String, val width: Int, val height: Int, val dark: Boolean = false)

    @Test fun renderWidths() {
        val out = System.getenv("CYIME_RENDER_OUT")
        assumeTrue(!out.isNullOrEmpty())
        val context = ApplicationProvider.getApplicationContext<Context>()
        KeyboardThemes.initFromConfig(context)
        val sizes = listOf(
            Size("phone-portrait", 384, 832),
            Size("phone-landscape", 832, 384),
            Size("small-phone-landscape", 640, 360),
            Size("tablet-portrait", 934, 1493),
            Size("tablet-landscape", 1493, 934),
            Size("tablet-landscape-dark", 1493, 934, dark = true),
        )
        val shown = mutableIntStateOf(0)
        rule.setContent {
            val size = sizes[shown.intValue]
            key(shown.intValue) {
                XimeTheme(darkTheme = size.dark) {
                    Box(Modifier.requiredSize(size.width.dp, size.height.dp)) {
                        SettingsWidthLimit {
                            SettingsMainContent(onNavigateToSchema = {}, onNavigateToTheme = {}, onNavigateToKeyEffect = {},
                                onNavigateToLayoutDisplay = {}, onNavigateToDictionary = {}, onNavigateToPlugins = {},
                                onNavigateToSmartPrediction = {}, onNavigateToSpeechToText = {}, onNavigateToAbout = {})
                        }
                    }
                }
            }
        }
        // 首页每 0.5 秒刷新状态的循环不会停，自动推进时钟会一直等不到空闲；改成手动推进。
        rule.mainClock.autoAdvance = false
        File(out!!).mkdirs()
        fun capture(name: String, size: Size) {
            // Robolectric 下 captureToImage 等不到 PixelCopy；直接把整个窗口画到位图上，再裁出这一种尺寸。
            val root = rule.activity.window.decorView
            val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            rule.runOnIdle { root.draw(android.graphics.Canvas(bitmap)) }
            val cropped = Bitmap.createBitmap(bitmap, 0, 0, minOf(size.width, root.width), minOf(size.height, root.height))
            File(out, "settings-width-$name.png").outputStream().use { cropped.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        sizes.forEachIndexed { index, size ->
            rule.runOnIdle { shown.intValue = index }
            rule.mainClock.advanceTimeBy(1500)
            rule.waitForIdle()
            capture(size.name, size)
        }
        rule.runOnIdle { shown.intValue = sizes.indexOfFirst { it.name == "tablet-landscape" } }
        rule.mainClock.advanceTimeBy(1500)
        rule.waitForIdle()
        rule.onNodeWithTag("settings-gutter-start").performTouchInput { swipeUp() }
        rule.mainClock.advanceTimeBy(1500)
        rule.waitForIdle()
        capture("tablet-landscape-after-margin-drag", sizes.first { it.name == "tablet-landscape" })
    }
}
