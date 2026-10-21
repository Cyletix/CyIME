package com.kingzcheung.xime.ui.render

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
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
import com.kingzcheung.xime.settings.FrostedGlassProfiles
import com.kingzcheung.xime.ui.caption.CaptionLanguage
import com.kingzcheung.xime.ui.caption.CaptionThemed
import com.kingzcheung.xime.ui.caption.LanguagePickerCard
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
 * 字幕「字幕听哪种语言？」卡片在六种主题下的样子：卡片后面垫彩色壁纸和字幕页的提示字，
 * 看按钮里会不会透出后面的字。只在设置了 CYIME_RENDER_OUT=<目录> 时出图。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w1500dp-h1400dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CaptionDialogRenderTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test fun renderLanguagePicker() {
        val out = System.getenv("CYIME_RENDER_OUT")
        assumeTrue(!out.isNullOrEmpty())
        KeyboardThemes.initFromConfig(ApplicationProvider.getApplicationContext<Context>())
        val combos = listOf(
            Triple("soft_blue", false, false), Triple("soft_blue", true, false),
            Triple("pure_black", true, false), Triple("pure_black", false, false),
            Triple("soft_blue", false, true), Triple("soft_blue", true, true),
        )
        rule.setContent {
            Column(Modifier.background(Color(0xFF808080)).padding(4.dp)) {
                for (row in combos.chunked(3)) Row {
                    for ((id, dark, glass) in row) {
                        val config = FrostedGlassConfig.defaults(dark, enabled = glass)
                        Box(Modifier.width(480.dp).padding(4.dp).background(Brush.linearGradient(
                            listOf(Color(0xFF2E7D32), Color(0xFFF9A825), Color(0xFFC62828), Color(0xFF1565C0))))) {
                            // 字幕页的提示字铺满卡片后面：卡片或按钮透明就会看见它。
                            Text("等对方说话，字会出现在这里。".repeat(12), fontSize = 28.sp, color = Color.White)
                            CompositionLocalProvider(LocalKeyboardPalette provides resolveKeyboardPalette(id, dark),
                                LocalFrostedGlassPreview provides FrostedGlassProfiles(config, config)) {
                                CaptionThemed(dark) {
                                    Column(Modifier.padding(24.dp)) {
                                        Text("$id ${if (dark) "深" else "浅"} ${if (glass) "玻璃" else "不透明"}", fontSize = 12.sp, color = Color.Black,
                                            modifier = Modifier.background(Color.White))
                                        LanguagePickerCard(CaptionLanguage.CHINESE,
                                            { if (it == CaptionLanguage.JAPANESE) "153 MB" else null }, {}, {})
                                    }
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
        File(out, "caption-language-picker.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
