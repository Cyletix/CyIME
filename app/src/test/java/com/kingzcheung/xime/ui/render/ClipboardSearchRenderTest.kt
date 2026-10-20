package com.kingzcheung.xime.ui.render

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kingzcheung.xime.clipboard.ClipboardItem
import com.kingzcheung.xime.settings.FrostedGlassConfig
import com.kingzcheung.xime.ui.keyboard.CLIPBOARD_SEARCH_PANEL_HEIGHT
import com.kingzcheung.xime.ui.keyboard.ToolPanel
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
 * 剪贴板搜索：键盘尺寸、位置不变；搜索框是工具面板（同一组件、同一位置）；上面是剪贴板（上拉展开的高度，只看结果）。
 * 浅色/深色/纯白/纯黑 × 玻璃，手机/平板宽度，固定/悬浮；全部是假数据。
 * 键盘区用一块同色占位表示（真实键盘不变）。只在设置了 CYIME_RENDER_OUT=<目录> 时出图。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w1500dp-h3000dp-hdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ClipboardSearchRenderTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private val matches = listOf(
        ClipboardItem(1, "东京都新宿区西新宿2丁目8番1号 示例大厦", timestamp = 10, isPinned = true),
        ClipboardItem(11, "送货地址改成公司前台", timestamp = 900),
        ClipboardItem(12, "发票抬头和地址见附件", timestamp = 800),
        ClipboardItem(13, "新的地址：大阪市北区示例 3-1", timestamp = 700),
        ClipboardItem(14, "地址确认后再下单", timestamp = 600),
    )

    @Composable
    private fun SearchScene(theme: String, dark: Boolean, glass: Boolean, width: Int, floating: Boolean, label: String) {
        val palette = resolveKeyboardPalette(theme, dark)
        Column(Modifier.padding(4.dp)) {
            Text(label, fontSize = 10.sp, color = Color.White)
            CompositionLocalProvider(LocalKeyboardPalette provides palette, LocalKeyboardInputPreferences provides
                KeyboardInputPreferences(frostedGlass = FrostedGlassConfig.defaults(dark, enabled = glass))) {
                MaterialTheme(colorScheme = (if (dark) darkColorScheme() else lightColorScheme())
                    .copy(surface = palette.background, onSurface = palette.text)) {
                    val wallpaper = if (glass) Brush.linearGradient(listOf(Color(0xFF2E7D32), Color(0xFFF9A825), Color(0xFF1565C0)))
                        else Brush.linearGradient(listOf(Color(0xFF9E9E9E), Color(0xFF9E9E9E)))
                    Box(Modifier.width(width.dp).background(wallpaper), contentAlignment = Alignment.BottomCenter) {
                        Column(Modifier.width(if (floating) (width * .82f).dp else width.dp)
                            .then(if (floating) Modifier.padding(bottom = 24.dp).clip(RoundedCornerShape(16.dp)) else Modifier)) {
                            ClipboardBoardView(matches, emptyList(), false, false, setOf("text:1"), false, null,
                                onBack = {}, onQuickSend = {}, onSelectText = {}, onSelectImage = {},
                                onSplit = { _, _ -> }, onAddQuick = {}, onPinsChange = {}, onRemove = {},
                                onPhotoAccess = {}, onPick = {}, onSystemPaste = {}, onShare = { _, _ -> },
                                onPullRemote = null, onAddPinned = {}, searchQuery = "dz", onOpenSearch = {},
                                modifier = Modifier.fillMaxWidth().height(200.dp))
                            // 搜索框：就是工具面板（同一组件、同一位置）。
                            ToolPanel(title = "搜索剪贴板", isFocused = false, initialText = "dz",
                                backgroundColor = Color.Transparent, textColor = palette.text, accentColor = palette.accent,
                                cardBgColor = palette.key, onClose = {}, onFocusChange = {},
                                heightDp = CLIPBOARD_SEARCH_PANEL_HEIGHT, hint = "文字或拼音首字母", statusText = "",
                                singleLine = true, modifier = Modifier.background(palette.background))
                            // 键盘：尺寸和位置不变（占位）。
                            Box(Modifier.fillMaxWidth().height(220.dp).background(palette.background),
                                contentAlignment = Alignment.Center) {
                                Text("键盘（26 键 / 九键，尺寸位置不变）", fontSize = 12.sp, color = palette.text.copy(alpha = .6f))
                            }
                        }
                    }
                }
            }
        }
    }

    @Test fun renderSearch() {
        val out = System.getenv("CYIME_RENDER_OUT")
        assumeTrue(!out.isNullOrEmpty())
        KeyboardThemes.initFromConfig(ApplicationProvider.getApplicationContext<Context>())
        val themes = listOf(Triple("soft_blue", false, "浅色"), Triple("soft_blue", true, "深色"),
            Triple("pure_black", false, "纯白"), Triple("pure_black", true, "纯黑"))
        rule.setContent {
            Column(Modifier.background(Color(0xFF606060)).padding(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for (glass in listOf(false, true)) Row {
                    for ((id, dark, name) in themes)
                        SearchScene(id, dark, glass, 360, false, "手机 固定 $name ${if (glass) "玻璃" else "实色"}")
                }
                Row {
                    SearchScene("soft_blue", true, false, 800, false, "平板 固定 深色")
                    SearchScene("pure_black", false, true, 360, true, "手机 悬浮 纯白 玻璃")
                }
                Text("搜索面板高 $CLIPBOARD_SEARCH_PANEL_HEIGHT dp", fontSize = 10.sp, color = Color.White)
            }
        }
        rule.waitForIdle()
        val root = rule.activity.window.decorView
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        rule.runOnIdle { root.draw(android.graphics.Canvas(bitmap)) }
        File(out!!).mkdirs()
        File(out, "clipboard-search-v2.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
