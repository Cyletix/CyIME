package com.kingzcheung.xime.ui.render

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kingzcheung.xime.settings.FrostedGlassConfig
import com.kingzcheung.xime.settings.KeysConfigHelper
import com.kingzcheung.xime.ui.keyboard.KeyboardCallbacks
import com.kingzcheung.xime.ui.keyboard.KeyboardInputPreferences
import com.kingzcheung.xime.ui.keyboard.KeyboardLayout
import com.kingzcheung.xime.ui.keyboard.LocalKeyboardExplicitWidth
import com.kingzcheung.xime.ui.keyboard.LocalKeyboardInputPreferences
import com.kingzcheung.xime.ui.theme.KeyboardThemes
import com.kingzcheung.xime.ui.theme.keyboardBackground
import com.kingzcheung.xime.ui.theme.resolveKeyboardPalette
import com.kingzcheung.xime.viewmodel.KeyboardUiState
import com.kingzcheung.xime.viewmodel.KeyboardViewModel
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 双拼键位提示：小鹤、微软（分号键）双拼 26 键，开关关/开对照；手机/平板宽度，浅、深、纯黑主题，玻璃开关。
 * 开着时键上写 zh/ch/sh（右上）和韵母（字母下面），不画上下角标。只在设了 CYIME_RENDER_OUT 时出图。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w1000dp-h900dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DoublePinyinKeyHintsRenderTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private fun shot(out: String, name: String, tag: String) {
        val body = rule.onNodeWithTag(tag, true).fetchSemanticsNode().boundsInRoot
        val root = rule.activity.window.decorView
        val full = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        rule.runOnIdle { root.draw(android.graphics.Canvas(full)) }
        val crop = Bitmap.createBitmap(full, body.left.toInt().coerceAtLeast(0), body.top.toInt().coerceAtLeast(0),
            body.width.toInt().coerceAtMost(full.width), body.height.toInt().coerceAtMost(full.height))
        File(out, "$name.png").outputStream().use { crop.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun renderDoublePinyinKeyHints() {
        val out = System.getenv("CYIME_RENDER_OUT")
        assumeTrue(!out.isNullOrEmpty())
        val app = ApplicationProvider.getApplicationContext<Application>()
        KeysConfigHelper.loadConfig(app)
        KeyboardThemes.initFromConfig(app)
        com.kingzcheung.xime.ui.keyboard.AppFonts.initialize(app)
        val vm = KeyboardViewModel(app)
        var schema by mutableStateOf("double_pinyin_flypy")
        var theme by mutableStateOf("soft_blue")
        var dark by mutableStateOf(false)
        var glass by mutableStateOf(false)
        var hints by mutableStateOf(true)
        var width by mutableIntStateOf(360)
        var height by mutableIntStateOf(240)
        rule.setContent {
            val palette = resolveKeyboardPalette(theme, dark)
            val config = FrostedGlassConfig.defaults(dark, enabled = glass)
            CompositionLocalProvider(LocalKeyboardExplicitWidth provides true,
                LocalKeyboardInputPreferences provides KeyboardInputPreferences(frostedGlass = config, doublePinyinKeyHints = hints)) {
                MaterialTheme {
                    Box(Modifier.requiredSize(width.dp, height.dp).testTag("layout-body")
                        .keyboardBackground(KeyboardThemes.getRenderingScheme(theme).keyboardBackground, dark,
                            palette.background, config)) {
                        KeyboardLayout({}, vm, KeyboardCallbacks(onKeyPress = { _, _ -> }, onCandidateSelect = {}),
                            KeyboardUiState(currentSchemaId = schema, themeId = theme, isDarkTheme = dark),
                            false, Modifier.requiredSize(width.dp, height.dp))
                    }
                }
            }
        }
        File(out!!).mkdirs()
        fun shown(text: String) = rule.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        for (id in listOf("double_pinyin_flypy", "double_pinyin_microsoft")) {
            KeysConfigHelper.setActiveKeyboardSchema(id)
            for ((w, h) in listOf(360 to 240, 800 to 300)) for ((themeId, isDark) in listOf("soft_blue" to false,
                "soft_blue" to true, "pure_black" to true)) for (g in listOf(false, true)) for (on in listOf(false, true)) {
                if (!on && (isDark || g)) continue
                rule.runOnIdle { schema = id; width = w; height = h; theme = themeId; dark = isDark; glass = g; hints = on }
                rule.waitForIdle()
                shot(out, "$id-$w-$themeId-${if (isDark) "dark" else "light"}-${if (g) "glass" else "solid"}-${if (on) "hints" else "off"}",
                    "layout-body")
                // 开着：zh 在小鹤 V 键、微软 V 键；关着：一个都不显示。
                assertTrue("$id 键位提示 zh", shown("zh") == on)
                if (id == "double_pinyin_flypy" && on) assertTrue(shown("ong iong") || shown("ong") && shown("iong"))
            }
        }
        // 全拼不显示。
        KeysConfigHelper.setActiveKeyboardSchema("rime_ice")
        rule.runOnIdle { schema = "rime_ice"; width = 360; height = 240; theme = "soft_blue"; dark = false; glass = false; hints = true }
        rule.waitForIdle()
        assertFalse("全拼不显示双拼键位提示", shown("zh"))
        shot(out, "rime_ice-360-hints-ignored", "layout-body")
    }

    /**
     * 「和角标一起显示」：声母、上角标在上，字母居中，韵母、下角标在下。手机、平板宽度，ZXCV 编辑角标开/关；
     * 每个字母键上的这些字互不重叠（按文字框算，比字形本身更严）。设了 CYIME_RENDER_OUT 时另外出图。
     */
    @Test fun keyHintsWithCornersNeverOverlap() {
        val out = System.getenv("CYIME_RENDER_OUT")
        val app = ApplicationProvider.getApplicationContext<Application>()
        KeysConfigHelper.loadConfig(app)
        KeyboardThemes.initFromConfig(app)
        com.kingzcheung.xime.ui.keyboard.AppFonts.initialize(app)
        val vm = KeyboardViewModel(app)
        var schema by mutableStateOf("double_pinyin_flypy")
        var edit by mutableStateOf(false)
        var width by mutableIntStateOf(360)
        var height by mutableIntStateOf(240)
        rule.setContent {
            val palette = resolveKeyboardPalette("soft_blue", false)
            val config = FrostedGlassConfig.defaults(false, enabled = false)
            CompositionLocalProvider(LocalKeyboardExplicitWidth provides true,
                LocalKeyboardInputPreferences provides KeyboardInputPreferences(frostedGlass = config,
                    doublePinyinKeyHints = true, doublePinyinKeyHintsWithCorners = true, zxcvEditShortcuts = edit)) {
                MaterialTheme {
                    Box(Modifier.requiredSize(width.dp, height.dp).testTag("layout-body")
                        .keyboardBackground(KeyboardThemes.getRenderingScheme("soft_blue").keyboardBackground, false,
                            palette.background, config)) {
                        KeyboardLayout({}, vm, KeyboardCallbacks(onKeyPress = { _, _ -> }, onCandidateSelect = {}),
                            KeyboardUiState(currentSchemaId = schema, themeId = "soft_blue", isDarkTheme = false),
                            false, Modifier.requiredSize(width.dp, height.dp))
                    }
                }
            }
        }
        out?.let { File(it).mkdirs() }
        // 字母键上会出现的字：字母、声母和韵母（只含拼音字母和空格）、26 键默认上角标、ZXCV 编辑角标。
        val cornerLabels = setOf("～", "／", "：", "；", "“", "”", "－", "（", "）", "＊", "＠", "、", "？", "！", "％", "＃",
            "~", "/", ":", ";", "-", "(", ")", "*", "@", "?", "!", "%", "#", "撤销", "剪切", "复制", "粘贴") + (0..9).map { "$it" }
        fun onLetterKey(text: String) = text.isNotEmpty() && (text.all { it in 'a'..'z' || it in 'A'..'Z' || it == 'ü' || it == ' ' } ||
            text in cornerLabels)
        val failures = mutableListOf<String>()
        for (id in listOf("double_pinyin_flypy", "double_pinyin_microsoft")) {
            KeysConfigHelper.setActiveKeyboardSchema(id)
            for ((w, h) in listOf(360 to 240, 800 to 300)) for (e in listOf(false, true)) {
                rule.runOnIdle { schema = id; width = w; height = h; edit = e }
                rule.waitForIdle()
                out?.let { shot(it, "$id-$w-corners-${if (e) "edit" else "plain"}", "layout-body") }
                val texts = rule.onAllNodes(androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(
                    androidx.compose.ui.semantics.SemanticsProperties.Text), useUnmergedTree = true).fetchSemanticsNodes()
                    .map { it.config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString("") to it.boundsInRoot }
                    .filter { (text, b) -> onLetterKey(text) && b.width > 0f && b.height > 0f }
                if (texts.none { it.first == "zh" }) failures += "$id $w 没有声母 zh"
                if (texts.none { it.first == "1" }) failures += "$id $w 没有上角标 1"
                // 角标和声母按它们的固定框算（框里的字居中、超出裁掉）；字母、韵母按文字框算。
                val corners = rule.onAllNodesWithTag(com.kingzcheung.xime.ui.keyboard.DOUBLE_PINYIN_CORNER_TAG, useUnmergedTree = true)
                    .fetchSemanticsNodes().mapIndexed { i, node -> "角标框$i" to node.boundsInRoot }
                if (corners.isEmpty()) failures += "$id $w 没有角标框"
                val boxes = texts.filter { it.first !in cornerLabels && it.first !in setOf("zh", "ch", "sh") } + corners
                for (i in boxes.indices) for (j in i + 1 until boxes.size) {
                    val (ta, a) = boxes[i]; val (tb, b) = boxes[j]
                    val overlap = a.intersect(b)
                    if (overlap.width > 0.5f && overlap.height > 0.5f) failures += "$id ${w}dp edit=$e「$ta」$a 和「$tb」$b 重叠"
                }
            }
        }
        // 所有图出完再判，一处失败也能拿到整套图。
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }
}
