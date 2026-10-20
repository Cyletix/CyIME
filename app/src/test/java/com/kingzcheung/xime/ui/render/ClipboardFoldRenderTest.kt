package com.kingzcheung.xime.ui.render

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kingzcheung.xime.clipboard.ClipboardItem
import com.kingzcheung.xime.settings.FrostedGlassConfig
import com.kingzcheung.xime.ui.keyboard.KeyboardInputPreferences
import com.kingzcheung.xime.ui.keyboard.LocalKeyboardInputPreferences
import com.kingzcheung.xime.ui.menubar.ClipboardBoardView
import com.kingzcheung.xime.ui.theme.KeyboardThemes
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 剪贴板固定内容折叠时的「展开 N 条」与工具栏「固定 N」，用真实 ClipboardBoardView 在电脑上渲染。
 * 手机和平板宽度、深浅色、玻璃各一张；固定内容全是假数据。
 * 只在设置了环境变量 CYIME_RENDER_OUT=<目录> 时出图（平时跳过）。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w1400dp-h1600dp-hdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ClipboardFoldRenderTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test fun renderFoldLabels() {
        val out = System.getenv("CYIME_RENDER_OUT")
        assumeTrue(!out.isNullOrEmpty())
        KeyboardThemes.initFromConfig(ApplicationProvider.getApplicationContext<Context>())
        val pinned = (1L..12L).map { ClipboardItem(it, "示例固定内容 $it", timestamp = 100 - it, isPinned = true) }
        val history = pinned + (21L..24L).map { ClipboardItem(it, "最近复制的示例文字 $it", timestamp = 1000 - it) }
        val pins = pinned.mapTo(mutableSetOf()) { "text:${it.id}" }
        rule.setContent {
            Column(Modifier.background(Color(0xFF808080)).padding(4.dp)) {
                for (width in listOf(360, 800)) for (dark in listOf(true, false)) for (glass in listOf(false, true)) {
                    Row(Modifier.padding(vertical = 2.dp)) {
                        Text("${width}dp ${if (dark) "深" else "浅"} ${if (glass) "玻璃" else "实色"}", fontSize = 10.sp,
                            color = Color.White, modifier = Modifier.width(90.dp))
                        CompositionLocalProvider(LocalKeyboardInputPreferences provides
                            KeyboardInputPreferences(frostedGlass = FrostedGlassConfig.defaults(dark, enabled = glass))) {
                            MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                                ClipboardBoardView(history, emptyList(), false, false, pins, false, null,
                                    onBack = {}, onQuickSend = {}, onSelectText = {}, onSelectImage = {},
                                    onSplit = { _, _ -> }, onAddQuick = {}, onPinsChange = {}, onRemove = {},
                                    onPhotoAccess = {}, onPick = {}, onSystemPaste = {}, onShare = { _, _ -> },
                                    onPullRemote = null,
                                    modifier = Modifier.width(width.dp).height(180.dp))
                            }
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
        File(out!!).mkdirs()
        save(File(out, "clipboard-fold-collapsed.png"))
        // 展开后两处都变成「收起」。
        val dividers = rule.onAllNodesWithTag("clipboard-pins-divider").fetchSemanticsNodes().size
        repeat(dividers) { rule.onAllNodesWithTag("clipboard-pins-divider")[it].performClick() }
        rule.waitForIdle()
        save(File(out, "clipboard-fold-expanded.png"))
    }

    private fun save(file: File) {
        // Robolectric 下 captureToImage 等不到 PixelCopy；直接把整个窗口画到位图上。
        val root = rule.activity.window.decorView
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        rule.runOnIdle { root.draw(android.graphics.Canvas(bitmap)) }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
