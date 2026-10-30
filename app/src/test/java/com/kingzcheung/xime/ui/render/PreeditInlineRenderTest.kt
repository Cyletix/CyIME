package com.kingzcheung.xime.ui.render

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kingzcheung.xime.settings.FrostedGlassConfig
import com.kingzcheung.xime.settings.KeysConfigHelper
import com.kingzcheung.xime.ui.keyboard.CandidateBar
import com.kingzcheung.xime.ui.keyboard.CandidateBarCallbacks
import com.kingzcheung.xime.ui.keyboard.CandidateBarState
import com.kingzcheung.xime.ui.keyboard.CandidateBarVisuals
import com.kingzcheung.xime.ui.keyboard.KeyboardInputPreferences
import com.kingzcheung.xime.ui.keyboard.LocalKeyboardInputPreferences
import com.kingzcheung.xime.ui.keyboard.legibleForTheme
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
 * 拼音在候选栏内（候选前面）：上方宿主输入框一条，键盘候选栏一条，看拼音不再压住输入框的按钮。
 * 主题 × 深浅色 × 玻璃开关，短拼音 ge 和长拼音两种。只在设了 CYIME_RENDER_OUT 时出图。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w400dp-h900dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PreeditInlineRenderTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test fun renderInlinePreedit() {
        val out = System.getenv("CYIME_RENDER_OUT")
        assumeTrue(!out.isNullOrEmpty())
        val app = ApplicationProvider.getApplicationContext<Application>()
        KeysConfigHelper.loadConfig(app); KeyboardThemes.reload(app)
        com.kingzcheung.xime.ui.keyboard.AppFonts.initialize(app)
        var theme by mutableStateOf("transparent_glass")
        var dark by mutableStateOf(true)
        var glass by mutableStateOf(true)
        var code by mutableStateOf("ge")
        rule.setContent {
            val palette = resolveKeyboardPalette(theme, dark)
            val config = FrostedGlassConfig.defaults(dark, enabled = glass).legibleForTheme(theme, dark)
            CompositionLocalProvider(LocalKeyboardPalette provides palette,
                LocalKeyboardInputPreferences provides KeyboardInputPreferences(frostedGlass = config)) {
                MaterialTheme {
                    Column(Modifier.requiredSize(400.dp, 104.dp).testTag("preedit-body")) {
                        // 宿主底部输入框：左边「+」，中间输入框。
                        val page = if (dark) Color(0xFF1A1A1A) else Color(0xFFF2F2F2)
                        val field = if (dark) Color(0xFF2E2E2E) else Color.White
                        val mark = if (dark) Color(0xFFDDDDDD) else Color(0xFF333333)
                        Box(Modifier.fillMaxWidth().height(60.dp).background(page)) {
                            Box(Modifier.offset(12.dp, 28.dp).size(24.dp, 4.dp).background(mark))
                            Box(Modifier.offset(22.dp, 18.dp).size(4.dp, 24.dp).background(mark))
                            Box(Modifier.offset(48.dp, 12.dp).size(300.dp, 36.dp).background(field, RoundedCornerShape(18.dp)))
                        }
                        Column(Modifier.fillMaxWidth().height(44.dp)
                            .keyboardBackground(KeyboardThemes.getRenderingScheme(theme).keyboardBackground, dark,
                                palette.background, config)) {
                            CandidateBar(
                                state = CandidateBarState.ChineseCandidates(listOf("个", "各", "咯", "歌", "哥", "格"),
                                    inputText = code, hasMore = true),
                                visuals = CandidateBarVisuals(Color.Transparent, palette.candidateText,
                                    KeyboardThemes.getDividerColor(theme, dark), palette.accent, palette.selectedText, dark,
                                    palette.key),
                                callbacks = CandidateBarCallbacks(onCandidateSelect = {}, onShowMoreCandidates = {},
                                    onCancelInput = {}),
                                onEditPreedit = {}, modifier = Modifier.fillMaxWidth().height(44.dp))
                        }
                    }
                }
            }
        }
        File(out!!).mkdirs()
        fun shot(name: String) {
            val body = rule.onNodeWithTagBounds("preedit-body")
            val root = rule.activity.window.decorView
            val full = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            rule.runOnIdle { root.draw(android.graphics.Canvas(full)) }
            val crop = Bitmap.createBitmap(full, body.left.toInt().coerceAtLeast(0), body.top.toInt().coerceAtLeast(0),
                body.width.toInt().coerceAtMost(full.width), body.height.toInt().coerceAtMost(full.height))
            File(out, "$name.png").outputStream().use { crop.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        for (t in listOf("transparent_glass", "pure_black", "soft_blue")) for (d in listOf(true, false))
            for (g in listOf(true, false)) for (c in listOf("ge", "wo'xiang'qu'shang'xue'le")) {
                rule.runOnIdle { theme = t; dark = d; glass = g; code = c }
                rule.waitForIdle()
                shot("$t-${if (d) "dark" else "light"}-${if (g) "glass" else "flat"}-${if (c == "ge") "short" else "long"}")
            }
    }

    private fun androidx.compose.ui.test.junit4.ComposeTestRule.onNodeWithTagBounds(tag: String) =
        onNode(androidx.compose.ui.test.hasTestTag(tag), true).fetchSemanticsNode().boundsInRoot
}
