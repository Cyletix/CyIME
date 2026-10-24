package com.kingzcheung.xime.ui.render

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.Image
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kingzcheung.xime.settings.FrostedGlassConfig
import com.kingzcheung.xime.settings.InputLanguage
import com.kingzcheung.xime.settings.SchemaInfo
import com.kingzcheung.xime.ui.keyboard.LanguageMenuCard
import com.kingzcheung.xime.ui.keyboard.frostedKeyColor
import com.kingzcheung.xime.ui.theme.KeyboardPalette
import com.kingzcheung.xime.ui.theme.KeyboardThemes
import com.kingzcheung.xime.ui.theme.LocalKeyboardPalette
import com.kingzcheung.xime.ui.theme.resolveKeyboardPalette
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 语言菜单在四个主题 × 深浅色 × 不透明 / 玻璃有实时模糊 / 玻璃无模糊下的样子：真实 LanguageMenuCard
 * 盖在一块写满字母的键盘上，键盘后面垫彩色“App 画面”。模糊那一列用软件盒式模糊模拟系统窗口模糊。
 * 只在设置了环境变量 CYIME_RENDER_OUT=<目录> 时出图（平时跳过）。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w860dp-h1900dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LanguageMenuRenderTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private val schemas = listOf(
        SchemaInfo("zh", "中文", "", "", "", language = InputLanguage.CHINESE),
        SchemaInfo("en", "English", "", "", "", language = InputLanguage.ENGLISH),
        SchemaInfo("ja", "日本語", "", "", "", language = InputLanguage.JAPANESE))

    /** 菜单后面的画面（App 文字 + 键盘），用 android Canvas 画成位图，好做真实的软件模糊。 */
    private fun behind(palette: KeyboardPalette, glass: FrostedGlassConfig, w: Int, h: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        paint.shader = android.graphics.LinearGradient(0f, 0f, w.toFloat(), h.toFloat(),
            intArrayOf(0xFF2E7D32.toInt(), 0xFFF9A825.toInt(), 0xFFC62828.toInt(), 0xFF1565C0.toInt()), null,
            android.graphics.Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        paint.shader = null
        paint.textSize = 13f
        paint.color = android.graphics.Color.BLACK
        for (i in 0 until 12) canvas.drawText("App 里的文字 Text behind", 0f, 14f + i * 17f, paint)
        val panel = if (glass.enabled) palette.background.copy(alpha = glass.backgroundOpacity) else palette.background
        paint.color = panel.toArgb()
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        paint.textSize = 15f
        paint.textAlign = android.graphics.Paint.Align.CENTER
        listOf("QWERTYUIOP", "ASDFGHJKL", "ZXCVBNM", "我的你是在").forEachIndexed { r, row ->
            row.forEachIndexed { c, ch ->
                val x = 4f + c * 26f
                val y = 4f + r * 50f
                paint.color = frostedKeyColor(palette.key, palette.text, glass).toArgb()
                canvas.drawRoundRect(x, y, x + 22f, y + 46f, 5f, 5f, paint)
                paint.color = palette.text.toArgb()
                canvas.drawText(ch.toString(), x + 11f, y + 29f, paint)
            }
        }
        return bitmap
    }

    /** 三次盒式模糊，近似系统窗口模糊（高斯）。 */
    private fun blurred(source: Bitmap, radius: Int): Bitmap {
        val w = source.width
        val h = source.height
        var px = IntArray(w * h).also { source.getPixels(it, 0, w, 0, 0, w, h) }
        fun pass(src: IntArray, horizontal: Boolean): IntArray {
            val out = IntArray(src.size)
            val n = if (horizontal) w else h
            val lines = if (horizontal) h else w
            for (line in 0 until lines) for (i in 0 until n) {
                var a = 0; var r = 0; var g = 0; var b = 0; var count = 0
                for (k in (i - radius).coerceAtLeast(0)..(i + radius).coerceAtMost(n - 1)) {
                    val p = src[if (horizontal) line * w + k else k * w + line]
                    a += p ushr 24; r += (p shr 16) and 255; g += (p shr 8) and 255; b += p and 255; count++
                }
                out[if (horizontal) line * w + i else i * w + line] =
                    ((a / count) shl 24) or ((r / count) shl 16) or ((g / count) shl 8) or (b / count)
            }
            return out
        }
        repeat(3) { px = pass(pass(px, true), false) }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    @Test fun renderLanguageMenu() {
        val out = System.getenv("CYIME_RENDER_OUT")
        assumeTrue(!out.isNullOrEmpty())
        val context = ApplicationProvider.getApplicationContext<Context>()
        KeyboardThemes.initFromConfig(context)
        val themes = listOf("transparent_glass" to "iOS 渐变", "pure_black" to "黑白",
            "lavender_purple" to "薰衣草紫", "soft_blue" to "柔和蓝")
        val cellW = 270.dp
        val cellH = 210.dp
        val menuW = 170.dp
        val menuH = 170.dp
        val menuX = 90.dp
        val menuY = 20.dp
        rule.setContent {
            Column(Modifier.background(Color(0xFF808080)).padding(4.dp)) {
                for ((id, name) in themes) for (dark in listOf(false, true)) Row {
                    val palette = resolveKeyboardPalette(id, dark)
                    for ((mode, label) in listOf("plain" to "不透明", "blur" to "玻璃·有模糊", "noblur" to "玻璃·无模糊")) {
                        val glass = FrostedGlassConfig.defaults(dark, enabled = mode != "plain")
                        Column(Modifier.padding(2.dp)) {
                            Text("$name·${if (dark) "深" else "浅"} $label", fontSize = 10.sp, color = Color.White)
                            Box(Modifier.size(cellW, cellH).clip(RoundedCornerShape(0.dp)).background(Brush.linearGradient(
                                listOf(Color(0xFF2E7D32), Color(0xFFF9A825), Color(0xFFC62828), Color(0xFF1565C0))))) {
                                val sharp = remember(id, dark, mode) { behind(palette, glass, 270, 210) }
                                Image(sharp.asImageBitmap(), null, Modifier.fillMaxSize())
                                if (mode == "blur") {
                                    // 模拟菜单下方的系统模糊窗：后面的 App 和按键都被模糊。
                                    val soft = remember(id, dark) { blurred(sharp, glass.blurRadiusDp.toInt()) }
                                    Box(Modifier.offset(menuX, menuY).size(menuW, menuH).clip(RoundedCornerShape(12.dp))) {
                                        Image(soft.asImageBitmap(), null, Modifier.offset(-menuX, -menuY).requiredSize(cellW, cellH))
                                    }
                                }
                                CompositionLocalProvider(LocalKeyboardPalette provides palette) {
                                    LanguageMenuCard(schemas = schemas, selectedId = "zh", width = menuW, height = menuH,
                                        rowHeight = 44.dp, glass = glass, blurBehind = mode == "blur",
                                        scroll = rememberScrollState(), onViewport = {}, onChoose = {},
                                        modifier = Modifier.offset(menuX, menuY))
                                }
                            }
                        }
                    }
                }
            }
        }
        rule.mainClock.advanceTimeBy(1000L)
        rule.waitForIdle()
        val root = rule.activity.window.decorView
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        rule.runOnIdle { root.draw(android.graphics.Canvas(bitmap)) }
        File(out!!).mkdirs()
        File(out, "language-menu.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
