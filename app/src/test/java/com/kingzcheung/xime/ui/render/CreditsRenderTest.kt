package com.kingzcheung.xime.ui.render

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kingzcheung.xime.settings.FrostedGlassPreferences
import com.kingzcheung.xime.ui.settings.AboutContent
import com.kingzcheung.xime.ui.settings.CreditsContent
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
 * 「关于」页（看新加的「鸣谢」入口）和「鸣谢」页在浅色、深色、玻璃下的样子，每种一张图：左关于、右鸣谢。
 * 只在设置了环境变量 CYIME_RENDER_OUT=<目录> 时出图（平时跳过，不进普通单测）。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w880dp-h2600dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CreditsRenderTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test fun renderAboutAndCredits() {
        val out = System.getenv("CYIME_RENDER_OUT")
        assumeTrue(!out.isNullOrEmpty())
        val context = ApplicationProvider.getApplicationContext<Context>()
        KeyboardThemes.initFromConfig(context)
        val variants = listOf("light" to (false to false), "dark" to (true to false), "glass" to (true to true))
        val shown = mutableIntStateOf(0)
        rule.setContent {
            val (_, mode) = variants[shown.intValue]
            XimeTheme(darkTheme = mode.first) {
                Row {
                    Box(Modifier.width(420.dp).fillMaxHeight()) {
                        AboutContent(onBack = {}, onNavigateToPrivacy = {}, onNavigateToLicenses = {})
                    }
                    Spacer(Modifier.width(40.dp))
                    Box(Modifier.width(420.dp).fillMaxHeight()) { CreditsContent(onBack = {}) }
                }
            }
        }
        rule.mainClock.autoAdvance = false
        File(out!!).mkdirs()
        variants.forEachIndexed { index, (name, mode) ->
            rule.runOnIdle {
                FrostedGlassPreferences.saveProfiles(context, FrostedGlassPreferences.readProfiles(context).withEnabled(mode.second))
                shown.intValue = index
            }
            rule.mainClock.advanceTimeBy(1500)
            rule.waitForIdle()
            val root = rule.activity.window.decorView
            val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            rule.runOnIdle { root.draw(android.graphics.Canvas(bitmap)) }
            File(out, "about-credits-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
