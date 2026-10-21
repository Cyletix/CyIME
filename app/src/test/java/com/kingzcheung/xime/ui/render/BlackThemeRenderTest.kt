package com.kingzcheung.xime.ui.render

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kingzcheung.xime.settings.FrostedGlassConfig
import com.kingzcheung.xime.settings.KeysConfigHelper
import com.kingzcheung.xime.settings.SchemaInfo
import com.kingzcheung.xime.ui.keyboard.CandidateBar
import com.kingzcheung.xime.ui.keyboard.CandidateBarCallbacks
import com.kingzcheung.xime.ui.keyboard.CandidateBarState
import com.kingzcheung.xime.ui.keyboard.CandidateBarVisuals
import com.kingzcheung.xime.ui.keyboard.KeyboardCallbacks
import com.kingzcheung.xime.ui.keyboard.KeyboardInputPreferences
import com.kingzcheung.xime.ui.keyboard.KeyboardLayout
import com.kingzcheung.xime.ui.keyboard.LocalKeyboardExplicitWidth
import com.kingzcheung.xime.ui.keyboard.LocalKeyboardInputPreferences
import com.kingzcheung.xime.ui.menubar.SchemaListView
import com.kingzcheung.xime.ui.theme.KeyboardThemes
import com.kingzcheung.xime.ui.theme.LocalKeyboardPalette
import com.kingzcheung.xime.ui.theme.keyboardBackground
import com.kingzcheung.xime.ui.theme.resolveKeyboardPalette
import com.kingzcheung.xime.viewmodel.KeyboardUiState
import com.kingzcheung.xime.viewmodel.KeyboardViewModel
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 黑白主题：候选栏＋26 键、“布局 / 方案”面板；深浅色、玻璃开关。
 * 同一次运行先用 xime.custom.yaml 套上改前的深灰键色出“before”，再删掉出“after”（近黑键帽）。
 * 只在设了 CYIME_RENDER_OUT 时出图。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w1000dp-h900dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BlackThemeRenderTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test fun renderBlackTheme() {
        val out = System.getenv("CYIME_RENDER_OUT")
        assumeTrue(!out.isNullOrEmpty())
        val app = ApplicationProvider.getApplicationContext<Application>()
        val custom = File(app.filesDir, "rime/xime.custom.yaml").apply { parentFile?.mkdirs() }
        fun reload() { KeysConfigHelper.loadConfig(app); KeyboardThemes.reload(app) }
        com.kingzcheung.xime.ui.keyboard.AppFonts.initialize(app)
        val vm = KeyboardViewModel(app)
        val theme = "pure_black"
        var dark by mutableStateOf(true)
        var glass by mutableStateOf(false)
        var mode by mutableStateOf("keys26")
        var revision by mutableStateOf(0)
        val schemas = listOf(SchemaInfo("rime_ice", "拼音", "", "", ""), SchemaInfo("double_pinyin_flypy", "小鹤双拼", "", "", ""),
            SchemaInfo("t9_pinyin", "九键", "", "", ""))
        custom.writeText(BEFORE); reload()
        rule.setContent {
            @Suppress("UNUSED_EXPRESSION") revision
            val palette = resolveKeyboardPalette(theme, dark)
            val config = FrostedGlassConfig.defaults(dark, enabled = glass)
            val schema = if (mode == "t9") "t9_pinyin" else "rime_ice"
            CompositionLocalProvider(LocalKeyboardExplicitWidth provides true, LocalKeyboardPalette provides palette,
                LocalKeyboardInputPreferences provides KeyboardInputPreferences(frostedGlass = config)) {
                MaterialTheme {
                    Box(Modifier.requiredSize(800.dp, 340.dp).testTag("layout-body")
                        // 玻璃时后面是一张彩色页面，看透出去的效果。
                        .then(if (glass) Modifier.background(Brush.linearGradient(listOf(Color(0xFF2E7D32),
                            Color(0xFFF9A825), Color(0xFFC62828), Color(0xFF1565C0)))) else Modifier)
                        .keyboardBackground(KeyboardThemes.getRenderingScheme(theme).keyboardBackground, dark,
                            palette.background, config)) {
                        if (mode == "panel") SchemaListView(schemas, "rime_ice", palette.background, palette.accent, palette.text,
                            palette.key, onSelectSchema = {}, modifier = Modifier.requiredSize(800.dp, 340.dp))
                        else Column(Modifier.requiredSize(800.dp, 340.dp)) {
                            CandidateBar(
                                state = CandidateBarState.ChineseCandidates(listOf("你好", "拟好", "你", "妮", "泥"),
                                    inputText = "nihao", hasMore = true),
                                visuals = CandidateBarVisuals(Color.Transparent, palette.candidateText,
                                    KeyboardThemes.getDividerColor(theme, dark), palette.accent, palette.selectedText, dark,
                                    palette.key),
                                callbacks = CandidateBarCallbacks(onCandidateSelect = {}, onShowMoreCandidates = {}),
                                showPreeditPreview = false, modifier = Modifier.fillMaxWidth().height(44.dp))
                            KeyboardLayout({}, vm, KeyboardCallbacks(onKeyPress = { _, _ -> }, onCandidateSelect = {}),
                                KeyboardUiState(currentSchemaId = schema, themeId = theme, isDarkTheme = dark),
                                false, Modifier.fillMaxWidth().weight(1f))
                        }
                    }
                }
            }
        }
        File(out!!).mkdirs()
        fun shot(name: String) {
            val body = rule.onNodeWithTag("layout-body", true).fetchSemanticsNode().boundsInRoot
            val root = rule.activity.window.decorView
            val full = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            rule.runOnIdle { root.draw(android.graphics.Canvas(full)) }
            val crop = Bitmap.createBitmap(full, body.left.toInt().coerceAtLeast(0), body.top.toInt().coerceAtLeast(0),
                body.width.toInt().coerceAtMost(full.width), body.height.toInt().coerceAtMost(full.height))
            File(out, "$name.png").outputStream().use { crop.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        val defaultLift = com.kingzcheung.xime.ui.keyboard.glassNearBlackLift
        for (stage in listOf("before", "after", "lift2", "lift4")) {
            if (stage == "after") { custom.delete(); reload(); rule.runOnIdle { revision++ } }
            // 玻璃下近黑按键的提亮倍数对比（默认值为 after）。
            com.kingzcheung.xime.ui.keyboard.glassNearBlackLift = when (stage) { "lift2" -> 2f; "lift4" -> 4f; else -> defaultLift }
            for (m in listOf("keys26", "panel")) for (d in listOf(true, false)) for (g in listOf(false, true)) {
                val schema = if (m == "t9") "t9_pinyin" else "rime_ice"
                KeysConfigHelper.setActiveKeyboardSchema(schema)
                rule.runOnIdle { vm.resetKeyboard(false, schema); mode = m; dark = d; glass = g; revision++ }
                rule.waitForIdle()
                shot("$stage-$m-${if (d) "dark" else "light"}-${if (g) "glass" else "solid"}")
            }
        }
        com.kingzcheung.xime.ui.keyboard.glassNearBlackLift = defaultLift
        custom.delete()
    }

    private companion object {
        // 改前的黑白深色：深灰字母键、更浅的灰功能键与回车。
        const val BEFORE = """
color_schemes:
  pure_black:
    key_bg_color_dark: 0x2C2C2E
    special_key_bg_color_dark: 0x48484A
    enter_key_bg_color_dark: 0x48484A
"""
    }
}
