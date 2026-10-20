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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kingzcheung.xime.clipboard.ClipboardItem
import com.kingzcheung.xime.settings.FrostedGlassConfig
import com.kingzcheung.xime.ui.keyboard.ClipboardSearchPanel
import com.kingzcheung.xime.ui.keyboard.KeyboardInputPreferences
import com.kingzcheung.xime.ui.keyboard.LocalKeyboardInputPreferences
import com.kingzcheung.xime.ui.menubar.ClipboardBoardView
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
 * 剪贴板搜索的样子：键盘上方的搜索栏（工具面板样式）和回车后剪贴板里只看结果的样子，
 * 手机/平板宽度 × 深浅色 × 玻璃；全部是假数据。只在设置了 CYIME_RENDER_OUT=<目录> 时出图。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w1400dp-h4400dp-hdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ClipboardSearchRenderTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private val pinned = listOf(
        ClipboardItem(1, "东京都新宿区西新宿2丁目8番1号 示例大厦", timestamp = 10, isPinned = true),
        ClipboardItem(2, "test.user@example.com", timestamp = 9, isPinned = true),
    )
    private val matches = pinned.take(1) + listOf(
        ClipboardItem(11, "送货地址改成公司前台", timestamp = 900),
        ClipboardItem(12, "发票抬头和地址见附件", timestamp = 800),
        ClipboardItem(13, "新的地址：大阪市北区示例 3-1", timestamp = 700),
    )

    @Test fun renderSearch() {
        val out = System.getenv("CYIME_RENDER_OUT")
        assumeTrue(!out.isNullOrEmpty())
        KeyboardThemes.initFromConfig(ApplicationProvider.getApplicationContext<Context>())
        val history = pinned + (21L..26L).map { ClipboardItem(it, "最近复制的示例文字 $it", timestamp = 1000 - it) }
        rule.setContent {
            Column(Modifier.background(Color(0xFF808080)).padding(4.dp)) {
                for (width in listOf(360, 800)) for (dark in listOf(true, false)) for (glass in listOf(false, true)) {
                    val palette = resolveKeyboardPalette("soft_blue", dark)
                    Row(Modifier.padding(vertical = 3.dp)) {
                        Text("${width}dp ${if (dark) "深" else "浅"} ${if (glass) "玻璃" else "实色"}", fontSize = 10.sp,
                            color = Color.White, modifier = Modifier.width(90.dp))
                        CompositionLocalProvider(LocalKeyboardPalette provides palette, LocalKeyboardInputPreferences provides
                            KeyboardInputPreferences(frostedGlass = FrostedGlassConfig.defaults(dark, enabled = glass))) {
                            MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                                val panelBg = if (glass) Brush.horizontalGradient(listOf(Color(0xFF2E7D32), Color(0xFF1565C0)))
                                    else Brush.linearGradient(listOf(palette.background, palette.background))
                                Column(Modifier.width(width.dp).background(panelBg)) {
                                    // 1. 键盘上方的搜索栏（下面就是平常的 26 键或九键，不变）。
                                    ClipboardSearchPanel(textColor = palette.text, accentColor = palette.accent,
                                        cardBgColor = palette.key, search = { matches }, onSelect = {}, onClose = {},
                                        onFocusChange = {}, initialResults = matches, initialQuery = "dz")
                                    Text("（下方为原来的 26 键 / 九键，不变）", fontSize = 10.sp, color = palette.text.copy(alpha = .6f),
                                        modifier = Modifier.padding(6.dp))
                                    // 2. 回车后：剪贴板只看搜索结果，左上角的「🔍 dz ✕」可清除；右上多一个放大镜。
                                    ClipboardBoardView(matches, emptyList(), false, false,
                                        setOf("text:1"), false, null,
                                        onBack = {}, onQuickSend = {}, onSelectText = {}, onSelectImage = {},
                                        onSplit = { _, _ -> }, onAddQuick = {}, onPinsChange = {}, onRemove = {},
                                        onPhotoAccess = {}, onPick = {}, onSystemPaste = {}, onShare = { _, _ -> },
                                        onPullRemote = null, onAddPinned = {}, searchQuery = "dz", onOpenSearch = {},
                                        modifier = Modifier.width(width.dp).height(170.dp))
                                    // 3. 平常的剪贴板：只多一个放大镜，其余不动。
                                    ClipboardBoardView(history, emptyList(), false, false,
                                        pinned.mapTo(mutableSetOf()) { "text:${it.id}" }, false, null,
                                        onBack = {}, onQuickSend = {}, onSelectText = {}, onSelectImage = {},
                                        onSplit = { _, _ -> }, onAddQuick = {}, onPinsChange = {}, onRemove = {},
                                        onPhotoAccess = {}, onPick = {}, onSystemPaste = {}, onShare = { _, _ -> },
                                        onPullRemote = null, onAddPinned = {}, onOpenSearch = {},
                                        modifier = Modifier.width(width.dp).height(240.dp))
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
        File(out, "clipboard-search.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
