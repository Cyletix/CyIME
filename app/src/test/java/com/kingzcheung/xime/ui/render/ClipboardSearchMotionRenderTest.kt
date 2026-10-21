package com.kingzcheung.xime.ui.render

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kingzcheung.xime.clipboard.ClipboardItem
import com.kingzcheung.xime.settings.FrostedGlassConfig
import com.kingzcheung.xime.ui.keyboard.CLIPBOARD_SEARCH_PANEL_HEIGHT
import com.kingzcheung.xime.ui.keyboard.KeyboardInputPreferences
import com.kingzcheung.xime.ui.keyboard.LocalKeyboardInputPreferences
import com.kingzcheung.xime.ui.keyboard.ToolPanel
import com.kingzcheung.xime.ui.keyboard.keyboardPanelMotion
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
 * 打开剪贴板搜索的动画逐帧对比（手机 360dp，深色，假数据）。上排“改前”：整个输入法窗口逐帧长高（每帧键盘和
 * 后面的 App 都要重排）；下排“改后”：高度一次到位，结果区只在自己的图层里滑入。键盘为占位。
 * 只在设置了 CYIME_RENDER_OUT 时出图。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w2000dp-h1400dp-hdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ClipboardSearchMotionRenderTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private val items = listOf(
        ClipboardItem(1, "东京都新宿区西新宿2丁目8番1号", timestamp = 10, isPinned = true),
        ClipboardItem(11, "送货地址改成公司前台", timestamp = 900),
        ClipboardItem(12, "发票抬头和地址见附件", timestamp = 800),
        ClipboardItem(13, "新的地址：大阪市北区示例 3-1", timestamp = 700),
        ClipboardItem(14, "地址确认后再下单", timestamp = 600),
    )
    private val results = 220
    private val keyboard = 240
    private val frames = listOf(0L, 48L, 96L, 144L, 192L, 260L)

    @Composable
    private fun Board(modifier: Modifier) = ClipboardBoardView(items, emptyList(), false, false, setOf("text:1"), false, null,
        onBack = {}, onQuickSend = {}, onSelectText = {}, onSelectImage = {}, onSplit = { _, _ -> }, onAddQuick = {},
        onPinsChange = {}, onRemove = {}, onPhotoAccess = {}, onPick = {}, onSystemPaste = {}, onShare = { _, _ -> },
        onPullRemote = null, searchQuery = null, searchChip = false, modifier = modifier)

    @Composable
    private fun Stack(open: Boolean, after: Boolean) {
        val palette = resolveKeyboardPalette("soft_blue", true)
        CompositionLocalProvider(LocalKeyboardPalette provides palette, LocalKeyboardInputPreferences provides
            KeyboardInputPreferences(frostedGlass = FrostedGlassConfig.defaults(true, enabled = false))) {
            MaterialTheme(colorScheme = darkColorScheme().copy(surface = palette.background, onSurface = palette.text)) {
                // The app behind the keyboard: it is pushed up as the window grows.
                Box(Modifier.width(360.dp).height((results + CLIPBOARD_SEARCH_PANEL_HEIGHT + keyboard + 120).dp)
                    .background(Color(0xFF3A3F4A)), contentAlignment = Alignment.BottomCenter) {
                    Column(Modifier.fillMaxWidth()) {
                        if (after) {
                            val reveal = remember { Animatable(0f) }
                            LaunchedEffect(open) { if (open) reveal.animateTo(1f, keyboardPanelMotion(true)) }
                            Box(Modifier.fillMaxWidth().height(results.dp).clipToBounds()) {
                                Board(Modifier.fillMaxSize().graphicsLayer {
                                    translationY = (1f - reveal.value) * size.height; alpha = reveal.value })
                            }
                        } else {
                            val h by animateIntAsState(if (open) results else 0, keyboardPanelMotion(open), label = "before")
                            Board(Modifier.fillMaxWidth().height(h.dp))
                        }
                        ToolPanel(title = "搜索剪贴板", isFocused = false, backgroundColor = palette.background,
                            textColor = palette.text, accentColor = palette.accent, cardBgColor = palette.key,
                            onClose = {}, onFocusChange = {}, heightDp = CLIPBOARD_SEARCH_PANEL_HEIGHT,
                            hint = "文字或拼音首字母", statusText = "", singleLine = true,
                            modifier = Modifier.background(palette.background))
                        Box(Modifier.fillMaxWidth().height(keyboard.dp).background(palette.background),
                            contentAlignment = Alignment.Center) {
                            Text("键盘（占位）", fontSize = 12.sp, color = palette.text.copy(alpha = .6f))
                        }
                    }
                }
            }
        }
    }

    @Test fun renderOpeningFrames() {
        val out = System.getenv("CYIME_RENDER_OUT")
        assumeTrue(!out.isNullOrEmpty())
        KeyboardThemes.initFromConfig(ApplicationProvider.getApplicationContext<Context>())
        File(out!!).mkdirs()
        var open by mutableStateOf(false)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            Row(Modifier.background(Color(0xFF606060)).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column { Text("改前：窗口逐帧长高", fontSize = 11.sp, color = Color.White); Stack(open, after = false) }
                Column { Text("改后：一次到位，结果滑入", fontSize = 11.sp, color = Color.White); Stack(open, after = true) }
            }
        }
        rule.mainClock.advanceTimeByFrame()
        open = true
        var elapsed = 0L
        for ((i, t) in frames.withIndex()) {
            rule.mainClock.advanceTimeBy(t - elapsed); elapsed = t
            val root = rule.activity.window.decorView
            val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            rule.runOnIdle { root.draw(android.graphics.Canvas(bitmap)) }
            val d = root.resources.displayMetrics.density
            val w = ((2 * 360 + 24) * d).toInt().coerceAtMost(bitmap.width)
            val h = ((results + CLIPBOARD_SEARCH_PANEL_HEIGHT + keyboard + 150) * d).toInt().coerceAtMost(bitmap.height)
            File(out, "clipboard-search-motion-$i-${t}ms.png").outputStream().use {
                Bitmap.createBitmap(bitmap, 0, 0, w, h).compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    }
}
