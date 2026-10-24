package com.kingzcheung.xime.ui.render

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Paint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kingzcheung.xime.settings.FrostedGlassConfig
import com.kingzcheung.xime.settings.KeysConfigHelper
import com.kingzcheung.xime.ui.keyboard.KeyboardInputPreferences
import com.kingzcheung.xime.ui.keyboard.LocalKeyboardInputPreferences
import com.kingzcheung.xime.ui.keyboard.NineKeyButton
import com.kingzcheung.xime.ui.keyboard.SwipeableIconKeyButton
import com.kingzcheung.xime.ui.keyboard.drawSwipeBubble
import com.kingzcheung.xime.ui.keyboard.rememberSwipeBubbleController
import com.kingzcheung.xime.ui.keyboard.rememberSwipeBubbleDrawData
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
 * 九键有候选时 0 键变「下一项」：平时的箭头，以及左滑「上一项」时的样子（键内渐变还是上方浮窗）。
 * 旁边放退格键对照图标大小。各主题 × 玻璃开关 × 手机/平板键宽，拼成一张图。只在设了 CYIME_RENDER_OUT 时出图。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w600dp-h500dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class T9ZeroKeyRenderTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test fun renderZeroKey() {
        val out = System.getenv("CYIME_RENDER_OUT")
        assumeTrue(!out.isNullOrEmpty())
        val app = ApplicationProvider.getApplicationContext<Application>()
        KeysConfigHelper.loadConfig(app)
        KeyboardThemes.initFromConfig(app)
        com.kingzcheung.xime.ui.keyboard.AppFonts.initialize(app)
        var theme by mutableStateOf("soft_blue")
        var dark by mutableStateOf(false)
        var glass by mutableStateOf(false)
        var keyWidth by mutableIntStateOf(64)
        var keyHeight by mutableIntStateOf(52)
        rule.setContent {
            val palette = resolveKeyboardPalette(theme, dark)
            val config = FrostedGlassConfig.defaults(dark, enabled = glass)
            CompositionLocalProvider(LocalKeyboardPalette provides palette,
                LocalKeyboardInputPreferences provides KeyboardInputPreferences(frostedGlass = config)) {
                MaterialTheme {
                    val bubble = rememberSwipeBubbleController()
                    var origin by remember { mutableStateOf(Rect.Zero) }
                    // 与九键布局相同：气泡底色取功能键色，三角取功能键文字色。
                    val data = rememberSwipeBubbleDrawData(bubble.state, bubble.keyBounds, palette.function, palette.text,
                        palette.functionText, keyWidth = if (bubble.state.isSwiping || bubble.state.isPressed)
                            bubble.keyBounds.width else 0f, keyboardWidth = origin.width)
                    Box(Modifier.requiredSize((keyWidth * 2 + 48).dp, (keyHeight + 80).dp).testTag("cell")
                        .then(if (glass) Modifier.background(Brush.linearGradient(
                            listOf(Color(0xFF2E7D32), Color(0xFFF9A825), Color(0xFFC62828), Color(0xFF1565C0)))) else Modifier)
                        .keyboardBackground(KeyboardThemes.getRenderingScheme(theme).keyboardBackground, dark,
                            palette.background, config)
                        .onGloballyPositioned { origin = it.boundsInRoot() }
                        .drawWithContent { drawContent(); data?.let { drawSwipeBubble(it) } }) {
                        Row(Modifier.padding(start = 24.dp, top = 72.dp, end = 24.dp, bottom = 8.dp)) {
                            SwipeableIconKeyButton(icon = rememberVectorPainter(Icons.AutoMirrored.Filled.Backspace),
                                onClick = {}, backgroundColor = palette.function, iconColor = palette.functionText,
                                modifier = Modifier.width(keyWidth.dp).fillMaxHeight())
                            NineKeyButton(digit = "", letters = "下一项",
                                icon = rememberVectorPainter(Icons.AutoMirrored.Filled.KeyboardArrowRight),
                                iconSizeDp = com.kingzcheung.xime.ui.keyboard.T9NextCandidateIconDp,
                                onClick = {}, backgroundColor = palette.key, textColor = palette.text,
                                modifier = Modifier.width(keyWidth.dp).fillMaxHeight().testTag("zero"),
                                onSwipeLeft = {},
                                onSwipeStateChange = { state, bounds ->
                                    bubble.update(state, Rect(bounds.left - origin.left, bounds.top - origin.top,
                                        bounds.right - origin.left, bounds.bottom - origin.top))
                                })
                        }
                    }
                }
            }
        }
        fun shot(): Bitmap {
            val cell = rule.onNodeWithTag("cell", true).fetchSemanticsNode().boundsInRoot
            val root = rule.activity.window.decorView
            val full = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            rule.runOnIdle { root.draw(android.graphics.Canvas(full)) }
            return Bitmap.createBitmap(full, cell.left.toInt().coerceAtLeast(0), cell.top.toInt().coerceAtLeast(0),
                cell.width.toInt().coerceAtMost(full.width), cell.height.toInt().coerceAtMost(full.height))
        }
        val rows = mutableListOf<Triple<String, Bitmap, Bitmap>>()
        for ((w, h) in listOf(64 to 52, 112 to 72)) for ((id, isDark, label) in listOf(
            Triple("soft_blue", false, "柔和蓝·浅"), Triple("soft_blue", true, "柔和蓝·深"),
            Triple("pure_black", false, "黑白·纯白"), Triple("pure_black", true, "黑白·纯黑"),
            Triple("cream_beige", true, "奶油米白·深"))) for (g in listOf(false, true)) {
            rule.runOnIdle { keyWidth = w; keyHeight = h; theme = id; dark = isDark; glass = g }
            rule.waitForIdle()
            val idle = shot()
            rule.onNodeWithTag("zero", true).performTouchInput {
                down(center)
                for (step in 1..7) moveTo(center + Offset(-10.dp.toPx() * step, 0f))
            }
            rule.waitForIdle()
            val swiping = shot()
            rule.onNodeWithTag("zero", true).performTouchInput { up() }
            rule.waitForIdle()
            rows += Triple("${w}dp $label ${if (g) "玻璃" else "不透明"}", idle, swiping)
        }
        val labelWidth = 360
        val cellWidth = rows.maxOf { it.second.width }
        val rowHeight = rows.maxOf { it.second.height } + 8
        val sheet = Bitmap.createBitmap(labelWidth + cellWidth * 2 + 24, rowHeight * rows.size + 8, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(sheet)
        canvas.drawColor(0xFF808080.toInt())
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE; textSize = 30f }
        rows.forEachIndexed { index, (label, idle, swiping) ->
            val top = 8f + index * rowHeight
            canvas.drawText(label, 12f, top + rowHeight / 2f, paint)
            canvas.drawBitmap(idle, labelWidth.toFloat(), top, null)
            canvas.drawBitmap(swiping, labelWidth + cellWidth + 12f, top, null)
        }
        File(out!!).mkdirs()
        File(out, "${System.getenv("CYIME_RENDER_NAME") ?: "t9-zero-key"}.png").outputStream().use {
            sheet.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
