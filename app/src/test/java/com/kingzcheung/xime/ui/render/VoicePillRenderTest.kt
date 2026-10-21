package com.kingzcheung.xime.ui.render

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kingzcheung.xime.settings.FrostedGlassConfig
import com.kingzcheung.xime.ui.keyboard.HardwareToolbarPosition
import com.kingzcheung.xime.ui.keyboard.KeyboardInputActions
import com.kingzcheung.xime.ui.keyboard.KeyboardInputPreferences
import com.kingzcheung.xime.ui.keyboard.LocalKeyboardInputPreferences
import com.kingzcheung.xime.ui.keyboard.VoiceCollapsedPill
import com.kingzcheung.xime.ui.theme.KeyboardThemes
import com.kingzcheung.xime.ui.theme.LocalKeyboardPalette
import com.kingzcheung.xime.ui.theme.keyboardBackground
import com.kingzcheung.xime.ui.theme.resolveKeyboardPalette
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 语音悬浮条在各主题 × 玻璃开关、贴底与拖到左边两种位置下的样子，用真实 VoiceCollapsedPill 渲染成一张对比图。
 * 只在设置了环境变量 CYIME_RENDER_OUT=<目录> 时出图（平时跳过）。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w2200dp-h1400dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VoicePillRenderTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test fun renderVoicePill() {
        val out = System.getenv("CYIME_RENDER_OUT")
        assumeTrue(!out.isNullOrEmpty())
        val context = ApplicationProvider.getApplicationContext<Context>()
        KeyboardThemes.initFromConfig(context)
        com.kingzcheung.xime.ui.keyboard.AppFonts.initialize(context)
        val themes = listOf(Triple("soft_blue", false, "柔和蓝·浅"), Triple("soft_blue", true, "柔和蓝·深"),
            Triple("pure_black", true, "黑白·纯黑"))
        val positions = listOf(HardwareToolbarPosition(0.5f, 1f) to "贴底居中", HardwareToolbarPosition(0f, 0.45f) to "拖到左边")
        rule.setContent {
            Column(Modifier.background(Color(0xFF808080)).padding(4.dp)) {
                for ((position, where) in positions) Row {
                    for (glass in listOf(false, true)) for ((id, dark, label) in themes) {
                        val palette = resolveKeyboardPalette(id, dark)
                        val config = FrostedGlassConfig.defaults(dark, enabled = glass)
                        Column(Modifier.padding(2.dp)) {
                            Text("$label ${if (glass) "玻璃" else "不透明"} $where", fontSize = 10.sp, color = Color.White)
                            // 手机竖屏宽度，背后垫一张彩色“壁纸”代表 App 画面。
                            Box(Modifier.size(360.dp, 300.dp).background(Brush.linearGradient(
                                listOf(Color(0xFF2E7D32), Color(0xFFF9A825), Color(0xFFC62828), Color(0xFF1565C0))))) {
                                CompositionLocalProvider(LocalKeyboardPalette provides palette,
                                    LocalKeyboardInputPreferences provides KeyboardInputPreferences(frostedGlass = config)) {
                                    VoiceCollapsedPill(
                                        listening = true, amplitude = { 0.2f }, languageLabel = "中",
                                        languageActions = KeyboardInputActions(), onSwitchLanguage = {}, onShowKeyboard = {},
                                        onBackspace = {}, onMic = {},
                                        backgroundColor = palette.background, contentColor = palette.candidateText,
                                        selectedTextColor = palette.selectedText, accentColor = palette.accent,
                                        frostedGlass = config,
                                        surface = { Modifier.keyboardBackground(KeyboardThemes.getRenderingScheme(id).keyboardBackground,
                                            dark, palette.background, config, translucentSurface = true) },
                                        onBoundsChanged = {}, position = position,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
        val root = rule.activity.window.decorView
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        rule.runOnIdle { root.draw(android.graphics.Canvas(bitmap)) }
        File(out!!).mkdirs()
        File(out, "voice-pill.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
