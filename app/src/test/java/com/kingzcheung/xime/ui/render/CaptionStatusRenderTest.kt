package com.kingzcheung.xime.ui.render

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kingzcheung.xime.settings.FrostedGlassConfig
import com.kingzcheung.xime.settings.FrostedGlassProfiles
import com.kingzcheung.xime.ui.caption.CaptionThemed
import com.kingzcheung.xime.ui.caption.SoundRibbon
import com.kingzcheung.xime.ui.caption.StatusRow
import com.kingzcheung.xime.ui.keyboard.LocalFrostedGlassPreview
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
 * 字幕页状态行（语言按钮、状态字、声音线）在手机宽度、六种主题下的样子；第一行是改之前的长状态字作对照。
 * 只在设置了 CYIME_RENDER_OUT=<目录> 时出图。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w420dp-h900dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CaptionStatusRenderTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test fun renderStatusRow() {
        val out = System.getenv("CYIME_RENDER_OUT")
        assumeTrue(!out.isNullOrEmpty())
        KeyboardThemes.initFromConfig(ApplicationProvider.getApplicationContext<Context>())
        val combos = listOf(
            Triple("soft_blue", true, false) to "正在听 · 只记字，不录音",
            Triple("soft_blue", false, false) to "正在听", Triple("soft_blue", true, false) to "正在听",
            Triple("pure_black", true, false) to "正在听", Triple("pure_black", false, false) to "正在听",
            Triple("soft_blue", false, true) to "正在听", Triple("soft_blue", true, true) to "正在听",
        )
        val bands = FloatArray(8) { listOf(.35f, .7f, .9f, .6f, .45f, .3f, .2f, .12f)[it] }
        // The sound line animates forever: drive the clock by hand instead of waiting for idle.
        rule.mainClock.autoAdvance = false
        rule.setContent {
            Column(Modifier.background(Color(0xFF808080)).padding(4.dp)) {
                for ((combo, text) in combos) {
                    val (id, dark, glass) = combo
                    val config = FrostedGlassConfig.defaults(dark, enabled = glass)
                    Text("$id ${if (dark) "深" else "浅"} ${if (glass) "玻璃" else "不透明"}${if (text.length > 3) "（改之前）" else ""}",
                        fontSize = 11.sp, color = Color.White)
                    CompositionLocalProvider(LocalKeyboardPalette provides resolveKeyboardPalette(id, dark),
                        LocalFrostedGlassPreview provides FrostedGlassProfiles(config, config)) {
                        CaptionThemed(dark) {
                            // The page draws its text in onSurface, as the caption screen's Surface does.
                            androidx.compose.material3.Surface(color = MaterialTheme.colorScheme.surface) {
                            Column(Modifier.width(400.dp).padding(vertical = 4.dp)) {
                                StatusRow("中文", MaterialTheme.colorScheme.primary, text, onPickLanguage = {}) { h ->
                                    SoundRibbon(bands, true, MaterialTheme.colorScheme.primary, Modifier.fillMaxWidth().height(h))
                                }
                            }
                            }
                        }
                    }
                }
            }
        }
        rule.mainClock.advanceTimeBy(1500)
        val root = rule.activity.window.decorView
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { root.draw(android.graphics.Canvas(bitmap)) }
        File(out!!).mkdirs()
        File(out, "caption-status-row.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
