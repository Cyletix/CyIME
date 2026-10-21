package com.kingzcheung.xime.ui.render

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kingzcheung.xime.settings.FrostedGlassConfig
import com.kingzcheung.xime.settings.InputLanguage
import com.kingzcheung.xime.ui.keyboard.HandwritingLanguageHint
import com.kingzcheung.xime.ui.keyboard.handwritingLanguageHint
import com.kingzcheung.xime.ui.theme.KeyboardThemes
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
 * 手写板左上角“当前语言”提示在各主题、深浅色、玻璃开关下的样子（手机宽 400dp、平板宽 900dp），
 * 用真实的提示组件和键盘背景在电脑上渲染成一张对比图。只在设置了 CYIME_RENDER_OUT=<目录> 时出图。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w2400dp-h2400dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HandwritingLanguageHintRenderTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test fun renderLanguageHints() {
        val out = System.getenv("CYIME_RENDER_OUT")
        assumeTrue(!out.isNullOrEmpty())
        val context = ApplicationProvider.getApplicationContext<Context>()
        KeyboardThemes.initFromConfig(context)
        val themes = listOf("soft_blue", "pure_black", "cream_beige")
        val hints = listOf(InputLanguage.CHINESE to false, InputLanguage.ENGLISH to false, InputLanguage.JAPANESE to true)
            .map { (l, t) -> handwritingLanguageHint(l, useTemplates = t, preparing = false, missingKana = false) }
        rule.setContent {
            Column(Modifier.background(Color(0xFF808080)).padding(4.dp)) {
                for (width in listOf(400, 900)) for (glass in listOf(false, true)) for (id in themes) for (dark in listOf(false, true)) {
                    val palette = resolveKeyboardPalette(id, dark)
                    val config = FrostedGlassConfig.defaults(dark, enabled = glass)
                    Row(Modifier.padding(vertical = 2.dp)) {
                        Text("${width}dp $id ${if (dark) "深" else "浅"} ${if (glass) "玻璃" else "不透明"}", fontSize = 10.sp,
                            color = Color.White, modifier = Modifier.width(150.dp))
                        // 手写区约占键盘宽度的 80%（右侧是功能键列）；三种语言并排各占一格只为一张图看全
                        for (hint in hints) Box(Modifier.width((width * 0.8f).dp).height(48.dp)
                            .then(if (glass) Modifier.background(Brush.linearGradient(
                                listOf(Color(0xFF2E7D32), Color(0xFFF9A825), Color(0xFFC62828), Color(0xFF1565C0)))) else Modifier)
                            .keyboardBackground(KeyboardThemes.getRenderingScheme(id).keyboardBackground, dark, palette.background, config)) {
                            HandwritingLanguageHint(hint, palette.text, Modifier.align(Alignment.TopStart))
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
        File(out, "handwriting-language-hint.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
