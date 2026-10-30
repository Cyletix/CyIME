package com.kingzcheung.xime.ui.render

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kingzcheung.xime.settings.FrostedGlassConfig
import com.kingzcheung.xime.ui.keyboard.KeyboardInputPreferences
import com.kingzcheung.xime.ui.keyboard.KeyboardResizeOverlay
import com.kingzcheung.xime.ui.keyboard.LocalKeyboardInputPreferences
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
 * 键盘调节界面的透明度条（底板、描边、标签）和下面的按钮，用真实 KeyboardResizeOverlay 盖在一块画出来的假键盘上，
 * 在电脑上渲染成一张图（真实 KeyboardView 要加载 rime 原生库，电脑上跑不了）。
 * 只在设置了环境变量 CYIME_RENDER_OUT=<目录> 时出图。可选变量：CYIME_RENDER_TAG（文件名里的标记，默认 after）、
 * CYIME_RENDER_THEMES（默认 soft_blue/light,soft_blue/dark）、CYIME_RENDER_AFTER_DRAG=1 时先把每个滑条拖到 40% 再截，
 * 看调完透明度后外框和四个按钮是否还在。手机宽 411dp 四个变体排一行，再一行小键盘 300dp 和横屏矮键盘 600×250dp
 * （看控件跟着键盘大小缩放），平板宽 1200dp 每行一个。
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
        val variants = buildList {
            for ((id, dark) in themes) for (glass in listOf(false, true)) add(Triple(id, dark, glass))
        }
        rule.setContent {
            Column(Modifier.background(Color(0xFF808080))) {
                Row { for ((id, dark, glass) in variants) Variant(411, 300, id, dark, glass) }
                // 控件跟着键盘大小缩放：小键盘一起缩小，横屏矮键盘不撑出框。
                Row { for ((id, dark, glass) in variants.take(2)) { Variant(300, 230, id, dark, glass); Variant(600, 250, id, dark, glass) } }
                for ((id, dark, glass) in variants) Variant(1200, 420, id, dark, glass)
            }
        }
        rule.waitForIdle()
        if (afterDrag) {
            val count = rule.onAllNodesWithTag("keyboard-opacity-slider", useUnmergedTree = true).fetchSemanticsNodes().size
            for (i in 0 until count) {
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

    @Composable
    private fun Variant(width: Int, height: Int, id: String, dark: Boolean, glass: Boolean) {
        val palette = resolveKeyboardPalette(id, dark)
        val glassConfig = FrostedGlassConfig.defaults(dark, enabled = glass)
        val config = Configuration(LocalConfiguration.current).apply { screenWidthDp = width; screenHeightDp = 900 }
        CompositionLocalProvider(LocalConfiguration provides config, LocalDensity provides Density(1f, 1f),
            LocalKeyboardPalette provides palette,
            LocalKeyboardInputPreferences provides KeyboardInputPreferences(frostedGlass = glassConfig)) {
            MaterialTheme {
                // 宿主：玻璃开时垫一张彩色“壁纸”，才看得出半透明；上面留 100dp 当宿主页面。
                Box(Modifier.padding(4.dp).size(width.dp, (height + 100).dp)
                    .then(if (glass) Modifier.background(Brush.linearGradient(
                        listOf(Color(0xFF2E7D32), Color(0xFFF9A825), Color(0xFFC62828), Color(0xFF1565C0))))
                    else Modifier.background(if (dark) Color(0xFF202124) else Color(0xFFE8EAED)))) {
                    Text("${width}dp  $id  ${if (dark) "深色" else "浅色"}  ${if (glass) "玻璃" else "不透明"}",
                        fontSize = 12.sp, color = Color.White, modifier = Modifier.padding(6.dp))
                    // 假键盘：主题背景 + 四排圆角键，和真键盘的颜色一致。
                    Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(height.dp)
                        .keyboardBackground(KeyboardThemes.getRenderingScheme(id).keyboardBackground, dark, palette.background, glassConfig)) {
                        Column(Modifier.fillMaxSize().padding(top = 44.dp, start = 4.dp, end = 4.dp, bottom = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            for (row in listOf("qwertyuiop", "asdfghjkl", "zxcvbnm", "123 空格 。")) {
                                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    for (label in if (row.contains(' ')) row.split(' ') else row.map { it.toString() }) {
                                        Box(Modifier.weight(if (label == "空格") 4f else 1f).fillMaxSize()
                                            .clip(RoundedCornerShape(8.dp)).background(palette.key), contentAlignment = Alignment.Center) {
                                            Text(label, color = palette.text, fontSize = 16.sp)
                                        }
                                    }
                                }
                            }
                        }
                        KeyboardResizeOverlay(height, height, 0, false,
                            initialOpacity = 1f, onHeightChange = {}, onBottomPaddingChange = {},
                            onOpacityChange = {}, onReset = {}, onConfirm = { _, _, _, _ -> }, onCancel = {},
                            onFloatingModeChange = {}, modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }
    }
}
