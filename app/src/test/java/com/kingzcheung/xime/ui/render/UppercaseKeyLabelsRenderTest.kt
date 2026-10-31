package com.kingzcheung.xime.ui.render

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kingzcheung.xime.settings.FrostedGlassConfig
import com.kingzcheung.xime.settings.KeysConfigHelper
import com.kingzcheung.xime.settings.SettingsPreferences
import com.kingzcheung.xime.ui.keyboard.KeyboardCallbacks
import com.kingzcheung.xime.ui.keyboard.KeyboardInputPreferences
import com.kingzcheung.xime.ui.keyboard.KeyboardLayout
import com.kingzcheung.xime.ui.keyboard.LocalKeyboardExplicitWidth
import com.kingzcheung.xime.ui.keyboard.LocalKeyboardInputPreferences
import com.kingzcheung.xime.ui.theme.KeyboardThemes
import com.kingzcheung.xime.ui.theme.LocalKeyboardPalette
import com.kingzcheung.xime.ui.theme.keyboardBackground
import com.kingzcheung.xime.ui.theme.resolveKeyboardPalette
import com.kingzcheung.xime.viewmodel.KeyboardUiState
import com.kingzcheung.xime.viewmodel.KeyboardViewModel
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 「字母键显示大写」（10-31 反馈：喜欢单纯大写的键，嫌小写加上下角标太杂）。
 * 每张图：列是 默认小写 / 显示大写，行是 中文、英文、英文 Shift（14 键是 中文、中文 Shift）。
 * 每个主题 × 深浅 × 玻璃，角标开 / 两个角标开关都关（干净键盘）各一张。
 * 只在设了 CYIME_RENDER_OUT 时出图；CYIME_RENDER_THEMES 只画其中几个主题。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w1000dp-h1200dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UppercaseKeyLabelsRenderTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private data class Variant(val label: String, val ascii: Boolean, val shifted: Boolean)

    private val passes = linkedMapOf(
        "26" to ("rime_ice" to listOf(Variant("中文", false, false), Variant("英文", true, false), Variant("英文 Shift", true, true))),
        "14" to ("pinyin_14jian" to listOf(Variant("中文", false, false), Variant("中文 Shift", false, true))),
    )
    private val columns = listOf("默认（小写）" to false, "字母键显示大写" to true)

    @After fun restoreHints() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        SettingsPreferences.setSwipeUpHintsEnabled(app, true)
        SettingsPreferences.setSwipeDownHintsEnabled(app, true)
        KeysConfigHelper.setActiveKeyboardSchema("rime_ice")
    }

    // 一组一个 activity：一次画太多张在 Robolectric 里会卡住（见 KeyShapeRenderTest）。
    @Test fun renderSolid() = render(glass = false)
    @Test fun renderGlass() = render(glass = true)

    private fun render(glass: Boolean) {
        val out = System.getenv("CYIME_RENDER_OUT")
        assumeTrue(!out.isNullOrEmpty())
        val only = System.getenv("CYIME_RENDER_THEMES")?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }
        val app = ApplicationProvider.getApplicationContext<Application>()
        KeysConfigHelper.loadConfig(app); KeyboardThemes.reload(app)
        KeysConfigHelper.applyLetterLayouts()
        com.kingzcheung.xime.ui.keyboard.AppFonts.initialize(app)
        val rows = 3
        val models = List(rows * columns.size) { KeyboardViewModel(app) }
        var pass by mutableStateOf("26")
        var theme by mutableStateOf("soft_blue")
        var dark by mutableStateOf(false)
        var revision by mutableStateOf(0)
        rule.setContent {
            @Suppress("UNUSED_EXPRESSION") revision
            val palette = resolveKeyboardPalette(theme, dark)
            val config = FrostedGlassConfig.defaults(dark, enabled = glass).legibleForTheme(theme, dark)
            val (schema, variants) = passes.getValue(pass)
            CompositionLocalProvider(LocalKeyboardExplicitWidth provides true, LocalKeyboardPalette provides palette) {
                MaterialTheme {
                    Column(Modifier.testTag("upper-body").background(Color(0xFF808080)).padding(8.dp)) {
                        Row {
                            Box(Modifier.width(72.dp))
                            columns.forEach { Text(it.first, Modifier.requiredWidth(376.dp).padding(start = 4.dp),
                                fontSize = 14.sp, color = Color.White) }
                        }
                        variants.forEachIndexed { row, variant ->
                            Row(Modifier.padding(top = 8.dp)) {
                                Text(variant.label, Modifier.width(72.dp), fontSize = 13.sp, color = Color.White)
                                columns.forEachIndexed { column, (_, upper) ->
                                    CompositionLocalProvider(LocalKeyboardInputPreferences provides
                                        KeyboardInputPreferences(frostedGlass = config, uppercaseKeyLabels = upper)) {
                                        // 玻璃下垫一块彩色底，透出来的字是否看得清一眼可见。
                                        Box(Modifier.padding(horizontal = 8.dp)
                                            .then(if (glass) Modifier.background(Brush.linearGradient(listOf(Color(0xFF2E7D32),
                                                Color(0xFFF9A825), Color(0xFFC62828), Color(0xFF1565C0)))) else Modifier)
                                            .requiredSize(360.dp, 230.dp)
                                            .keyboardBackground(KeyboardThemes.getRenderingScheme(theme).keyboardBackground,
                                                dark, palette.background, config)) {
                                            KeyboardLayout({}, models[row * columns.size + column],
                                                KeyboardCallbacks(onKeyPress = { _, _ -> }, onCandidateSelect = {}),
                                                KeyboardUiState(currentSchemaId = schema, themeId = theme, isDarkTheme = dark,
                                                    isAsciiMode = variant.ascii),
                                                variant.ascii, Modifier.requiredSize(360.dp, 230.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        val dir = File(out!!).apply { mkdirs() }
        fun shown(text: String) = rule.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().size
        val themes = KeyboardThemes.themes.map { it.id }.filter { only == null || it in only }
        for (p in passes.keys) {
            val (schema, variants) = passes.getValue(p)
            KeysConfigHelper.setActiveKeyboardSchema(schema)
            rule.runOnIdle {
                pass = p
                variants.forEachIndexed { row, variant -> columns.indices.forEach { column ->
                    val vm = models[row * columns.size + column]
                    vm.resetKeyboard(variant.ascii, schema); vm.setShifted(variant.shifted)
                } }
                revision++
            }
            rule.waitForIdle()
            // 26 键：两行不按 Shift，只有「显示大写」那一列写大写 Q；Shift 行两列都是大写。小写 q 只在默认列的前两行。
            if (p == "26") {
                assertTrue("显示大写列和 Shift 行有大写 Q", shown("Q") == 4)
                assertTrue("默认列不按 Shift 的两行仍是小写 q", shown("q") == 2)
            }
            for (hints in listOf(true, false)) {
                SettingsPreferences.setSwipeUpHintsEnabled(app, hints)
                SettingsPreferences.setSwipeDownHintsEnabled(app, hints)
                for (t in themes) for (d in listOf(false, true)) {
                    rule.runOnIdle { theme = t; dark = d; revision++ }
                    rule.waitForIdle()
                    val bounds = rule.onNodeWithTag("upper-body", true).fetchSemanticsNode().boundsInRoot
                    val root = rule.activity.window.decorView
                    val full = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
                    rule.runOnIdle { root.draw(android.graphics.Canvas(full)) }
                    val crop = Bitmap.createBitmap(full, bounds.left.toInt().coerceAtLeast(0), bounds.top.toInt().coerceAtLeast(0),
                        bounds.width.toInt().coerceAtMost(full.width), bounds.height.toInt().coerceAtMost(full.height))
                    File(dir, "$p-$t-${if (d) "dark" else "light"}-${if (glass) "glass" else "solid"}-" +
                        "${if (hints) "hints" else "clean"}.png").outputStream().use { crop.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    if (crop !== full) crop.recycle()
                    full.recycle()
                }
            }
        }
    }
}
