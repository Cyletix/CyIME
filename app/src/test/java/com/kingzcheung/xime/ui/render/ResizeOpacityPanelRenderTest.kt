package com.kingzcheung.xime.ui.render

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kingzcheung.xime.settings.FrostedGlassConfig
import com.kingzcheung.xime.ui.keyboard.KeyboardCallbacks
import com.kingzcheung.xime.ui.keyboard.KeyboardInputPreferences
import com.kingzcheung.xime.ui.keyboard.KeyboardResizeOverlay
import com.kingzcheung.xime.viewmodel.KeyboardUiState
import com.kingzcheung.xime.ui.keyboard.KeyboardView
import com.kingzcheung.xime.ui.keyboard.LocalKeyboardInputPreferences
import com.kingzcheung.xime.ui.theme.KeyboardThemes
import com.kingzcheung.xime.viewmodel.KeyboardViewModel
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 键盘调节界面的透明度条（底板、描边、标签）和下面的按钮，用真实 KeyboardView + KeyboardResizeOverlay 在电脑上渲染成一张图。
 * 只在设置了环境变量 CYIME_RENDER_OUT=<目录> 时出图。可选变量：CYIME_RENDER_TAG（文件名里的标记，默认 after）、
 * CYIME_RENDER_THEMES（默认 soft_blue/light,soft_blue/dark）、CYIME_RENDER_AFTER_DRAG=1 时先把每个滑条拖到 40% 再截，
 * 看调完透明度后外框和四个按钮是否还在。手机宽 411dp 四个变体排一行，平板宽 1200dp 每行一个。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w1700dp-h3000dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ResizeOpacityPanelRenderTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test fun renderOpacityPanel() {
        val out = System.getenv("CYIME_RENDER_OUT")
        assumeTrue(!out.isNullOrEmpty())
        val context = ApplicationProvider.getApplicationContext<Context>()
        KeyboardThemes.initFromConfig(context)
        val themes = (System.getenv("CYIME_RENDER_THEMES") ?: "soft_blue/light,soft_blue/dark").split(",")
            .map { it.trim().split("/") }.map { it[0] to (it[1] == "dark") }
        val afterDrag = System.getenv("CYIME_RENDER_AFTER_DRAG") == "1"
        val tag = System.getenv("CYIME_RENDER_TAG") ?: "after"
        val app = ApplicationProvider.getApplicationContext<Application>()
        val variants = buildList {
            for ((id, dark) in themes) for (glass in listOf(false, true)) add(Triple(id, dark, glass))
        }
        rule.setContent {
            Column(Modifier.background(Color(0xFF808080))) {
                Row { for ((id, dark, glass) in variants) Variant(app, 411, 300, id, dark, glass) }
                for ((id, dark, glass) in variants) Variant(app, 1200, 420, id, dark, glass)
            }
        }
        rule.waitForIdle()
        if (afterDrag) {
            val sliders = rule.onAllNodesWithTag("keyboard-opacity-slider", useUnmergedTree = true).fetchSemanticsNodes()
            for (i in sliders.indices) {
                rule.onAllNodesWithTag("keyboard-opacity-slider", useUnmergedTree = true)[i]
                    .performSemanticsAction(SemanticsActions.SetProgress) { it(0.4f) }
            }
            rule.waitForIdle()
        }
        // Robolectric 下 captureToImage 等不到 PixelCopy；直接把整个窗口画到位图上。
        val root = rule.activity.window.decorView
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        rule.runOnIdle { root.draw(android.graphics.Canvas(bitmap)) }
        File(out!!).mkdirs()
        File(out, "resize-opacity-$tag${if (afterDrag) "-dragged" else ""}.png").outputStream()
            .use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @androidx.compose.runtime.Composable
    private fun Variant(app: Application, width: Int, height: Int, id: String, dark: Boolean, glass: Boolean) {
        val vm = remember { KeyboardViewModel(app) }
        val opacity = remember { mutableFloatStateOf(1f) }
        val floating = remember { mutableStateOf(false) }
        val config = Configuration(LocalConfiguration.current).apply { screenWidthDp = width; screenHeightDp = 900 }
        CompositionLocalProvider(LocalConfiguration provides config, LocalDensity provides Density(1f, 1f),
            LocalKeyboardInputPreferences provides KeyboardInputPreferences(frostedGlass = FrostedGlassConfig.defaults(dark, enabled = glass))) {
            MaterialTheme {
                Box(Modifier.padding(4.dp).size(width.dp, (height + 150).dp)
                    .background(if (dark) Color(0xFF202124) else Color(0xFFE8EAED))) {
                    KeyboardView(vm, KeyboardUiState(isDarkTheme = dark, themeId = id, isFloatingMode = floating.value,
                        keyboardOpacity = opacity.floatValue, keyboardHeightDp = height,
                        floatingOffsetY = 0, floatingMinOffsetY = 24, floatingScreenHeightDp = 900),
                        KeyboardCallbacks(onKeyPress = { _, _ -> }, onCandidateSelect = {}),
                        modifier = Modifier.fillMaxSize(),
                        resizeOverlay = {
                            KeyboardResizeOverlay(height, height, 0, floating.value,
                                initialOpacity = opacity.floatValue, onHeightChange = {}, onBottomPaddingChange = {},
                                onOpacityChange = { opacity.floatValue = it }, onReset = {},
                                onConfirm = { _, _, _, _ -> }, onCancel = {},
                                onFloatingModeChange = { floating.value = it },
                                modifier = Modifier.fillMaxSize())
                        })
                }
            }
        }
    }
}
