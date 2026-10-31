package com.kingzcheung.xime.ui.render

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kingzcheung.xime.runtime.adaptation.PerformanceTier
import com.kingzcheung.xime.settings.FrostedGlassPreferences
import com.kingzcheung.xime.settings.PerformanceProfile
import com.kingzcheung.xime.ui.settings.PerformanceSettingsContent
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
 * 「性能与省电」页：档位卡片顶上的配置高低刻度条，加上一直列着的「自定义」。
 * 每种状态（中配、高配、自定义 73 分、自定义 15 分）在浅色、深色、玻璃下各一张。只在设置了 CYIME_RENDER_OUT 时出图。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w392dp-h1300dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PerformanceScaleRenderTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test fun renderScaleStates() {
        val out = System.getenv("CYIME_RENDER_OUT")
        assumeTrue(!out.isNullOrEmpty())
        val context = ApplicationProvider.getApplicationContext<Context>()
        KeyboardThemes.initFromConfig(context)
        val high = PerformanceProfile.presets.getValue(PerformanceTier.HIGH)
        val states: List<Pair<String, () -> Unit>> = listOf(
            "medium" to { PerformanceProfile.applyPreset(context, PerformanceTier.MEDIUM) },
            "high" to { PerformanceProfile.applyPreset(context, PerformanceTier.HIGH) },
            "custom-73" to { PerformanceProfile.applyCustom(context, high.copy(keyGlow = false)) },
            "custom-15" to { PerformanceProfile.applyCustom(context, high.copy(keyAnimation = false, speechRefinerIdleMinutes = 1, speechEngineIdleMinutes = 1)) },
        )
        val themes = listOf("light" to (false to false), "dark" to (true to false), "glass" to (true to true))
        val theme = mutableIntStateOf(0)
        val shown = mutableIntStateOf(0)
        rule.setContent {
            XimeTheme(darkTheme = themes[theme.intValue].second.first) {
                // 页面在组合时读一次设置：换状态时换 key 重新组合。
                key(theme.intValue, shown.intValue) {
                    Box(Modifier.fillMaxSize()) { PerformanceSettingsContent(onBack = {}, onOpenSetupGuide = {}) }
                }
            }
        }
        File(out!!).mkdirs()
        themes.forEachIndexed { themeIndex, (themeName, mode) ->
            states.forEachIndexed { stateIndex, (stateName, setUp) ->
                rule.runOnIdle {
                    FrostedGlassPreferences.saveProfiles(context, FrostedGlassPreferences.readProfiles(context).withEnabled(mode.second))
                    setUp()
                    theme.intValue = themeIndex
                    shown.intValue = stateIndex + 1 + themeIndex * states.size
                }
                rule.waitForIdle()
                val root = rule.activity.window.decorView
                val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
                rule.runOnIdle { root.draw(android.graphics.Canvas(bitmap)) }
                File(out, "performance-scale-$themeName-$stateName.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
    }
}
