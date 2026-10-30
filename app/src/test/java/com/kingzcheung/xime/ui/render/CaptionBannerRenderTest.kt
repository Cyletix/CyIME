package com.kingzcheung.xime.ui.render

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
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
import com.kingzcheung.xime.ui.caption.ModelBanner
import com.kingzcheung.xime.model.ModelDownloadState
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
 * 字幕页“去开启通话字幕”提示卡带“不用了”：手机宽（按钮两个并排在字下面）和平板宽（一行）、主题 × 深浅 × 玻璃。
 * 只在设置了 CYIME_RENDER_OUT=<目录> 时出图。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w700dp-h2400dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CaptionBannerRenderTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test fun renderCallCaptionBanner() {
        val out = System.getenv("CYIME_RENDER_OUT")
        assumeTrue(!out.isNullOrEmpty())
        KeyboardThemes.initFromConfig(ApplicationProvider.getApplicationContext<Context>())
        val combos = listOf(Triple("soft_blue", false, false), Triple("soft_blue", true, false), Triple("pure_black", true, false),
            Triple("soft_blue", false, true), Triple("soft_blue", true, true))
        rule.mainClock.autoAdvance = false
        rule.setContent {
            Column(Modifier.background(Color(0xFF808080)).padding(4.dp)) {
                for ((id, dark, glass) in combos) for (width in listOf(360, 620)) {
                    val config = FrostedGlassConfig.defaults(dark, enabled = glass)
                    Text("$id ${if (dark) "深" else "浅"} ${if (glass) "玻璃" else "不透明"} ${width}dp", fontSize = 11.sp, color = Color.White)
                    CompositionLocalProvider(LocalKeyboardPalette provides resolveKeyboardPalette(id, dark),
                        LocalFrostedGlassPreview provides FrostedGlassProfiles(config, config)) {
                        CaptionThemed(dark) {
                            androidx.compose.material3.Surface(color = MaterialTheme.colorScheme.surface) {
                                Column(Modifier.width(width.dp).padding(horizontal = 8.dp)) {
                                    ModelBanner("打电话、微信视频时也想看字幕？开启后通话中会自动在屏幕底部显示", "去开启通话字幕",
                                        ModelDownloadState.Idle, onDismiss = {}) {}
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
        File(out, "caption-call-banner.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
