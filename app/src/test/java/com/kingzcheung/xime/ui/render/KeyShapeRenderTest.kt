package com.kingzcheung.xime.ui.render

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
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
import com.kingzcheung.xime.rime.T9InputController
import com.kingzcheung.xime.settings.FrostedGlassConfig
import com.kingzcheung.xime.settings.KeysConfigHelper
import com.kingzcheung.xime.settings.SettingsPreferences
import com.kingzcheung.xime.settings.TypewiseLayout
import com.kingzcheung.xime.ui.keyboard.EditKeyboardLayout
import com.kingzcheung.xime.ui.keyboard.KeyboardCallbacks
import com.kingzcheung.xime.ui.keyboard.KeyboardInputPreferences
import com.kingzcheung.xime.ui.keyboard.KeyboardLayoutScreen
import com.kingzcheung.xime.ui.keyboard.KeyboardLayoutState
import com.kingzcheung.xime.ui.keyboard.LocalKeyCornerRadius
import com.kingzcheung.xime.ui.keyboard.LocalKeyboardExplicitWidth
import com.kingzcheung.xime.ui.keyboard.LocalKeyboardInputPreferences
import com.kingzcheung.xime.ui.keyboard.SymbolKeyboardLayout
import com.kingzcheung.xime.ui.keyboard.legibleForTheme
import com.kingzcheung.xime.ui.settings.KeyShapePicker
import com.kingzcheung.xime.ui.settings.VisualStylePicker
import com.kingzcheung.xime.ui.theme.KeyShape
import com.kingzcheung.xime.ui.theme.KeyShapes
import com.kingzcheung.xime.ui.theme.KeyboardThemes
import com.kingzcheung.xime.ui.theme.LocalKeyboardPalette
import com.kingzcheung.xime.ui.theme.VisualStyle
import com.kingzcheung.xime.ui.theme.VisualStyles
import com.kingzcheung.xime.ui.theme.keyboardBackground
import com.kingzcheung.xime.ui.theme.resolveKeyboardPalette
import com.kingzcheung.xime.viewmodel.KeyboardUiState
import com.kingzcheung.xime.viewmodel.KeyboardViewModel
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 按键形状（圆角 / 方形 / 直角）× 每个主题 × 深浅 × 玻璃开关，各出一张总图：
 * 行是 26 键、14 键、九键、Typewise、数字、常用符号、符号、编辑面板，列是三种形状。
 * 另出视觉样式 × 形状两张，和设置页的选择卡片。只在设了 CYIME_RENDER_OUT 时出图；
 * CYIME_RENDER_THEMES（逗号分隔）只画其中几个主题。每次渲染前在 progress.txt 记下组合和堆占用，卡住时看最后一行。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w420dp-h900dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class KeyShapeRenderTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @After fun reset() {
        KeyShapes.current = KeyShape.ROUNDED
        VisualStyles.current = VisualStyle.ORIGINAL
    }

    private val layouts = listOf("26", "14", "t9", "typewise", "num", "common", "symbol", "edit")
    private val themes = listOf("transparent_glass", "pure_black", "lavender_purple", "soft_blue", "sakura_pink",
        "sunset_gradient", "aurora_gradient", "mint_green", "cream_beige", "olivcelso_desert")

    private fun schemaFor(layout: String) = when (layout) {
        "14" -> "pinyin_14jian"
        "t9", "num" -> "t9_pinyin"
        "typewise" -> TypewiseLayout.ID
        else -> "rime_ice"
    }

    // A fresh activity per group: a stall in one group leaves the other groups' sheets, and progress.txt names it.
    @Test fun renderCoolThemes() = renderKeyShapes(themes.take(5), styles = false)
    @Test fun renderWarmThemes() = renderKeyShapes(themes.drop(5), styles = false)
    @Test fun renderVisualStyles() = renderKeyShapes(emptyList(), styles = true)

    private fun renderKeyShapes(group: List<String>, styles: Boolean) {
        val out = System.getenv("CYIME_RENDER_OUT")
        assumeTrue(!out.isNullOrEmpty())
        val only = System.getenv("CYIME_RENDER_THEMES")?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }
        val app = ApplicationProvider.getApplicationContext<Application>()
        KeysConfigHelper.loadConfig(app); KeyboardThemes.reload(app)
        KeysConfigHelper.applyLetterLayouts()
        com.kingzcheung.xime.ui.keyboard.AppFonts.initialize(app)
        val vm = KeyboardViewModel(app)
        val t9 = org.mockito.kotlin.mock<T9InputController> {
            on { leftPanelState } doReturn T9InputController.LeftPanelState.IDLE
            on { firstOptions } doReturn emptyList()
        }
        var theme by mutableStateOf("soft_blue")
        var dark by mutableStateOf(false)
        var glass by mutableStateOf(false)
        var layout by mutableStateOf("26")
        var revision by mutableStateOf(0)
        rule.setContent {
            @Suppress("UNUSED_EXPRESSION") revision
            val palette = resolveKeyboardPalette(theme, dark)
            val config = FrostedGlassConfig.defaults(dark, enabled = glass).legibleForTheme(theme, dark)
            val kbKey = KeysConfigHelper.getKeyboardKeyConfig()
            val kbShadow = KeysConfigHelper.getKeyboardShadow()
            // KeyboardView provides the key radius around every layout and overlay; do the same here.
            CompositionLocalProvider(LocalKeyboardExplicitWidth provides true, LocalKeyboardPalette provides palette,
                LocalKeyCornerRadius provides kbKey.cornerRadius.dp,
                LocalKeyboardInputPreferences provides KeyboardInputPreferences(frostedGlass = config)) {
                MaterialTheme {
                    Box(Modifier.requiredSize(400.dp, 280.dp).testTag("shape-body")
                        // 玻璃时垫一块彩色宿主，看得出键帽透出的边。
                        .then(if (glass) Modifier.background(Brush.linearGradient(listOf(Color(0xFF2E7D32),
                            Color(0xFFF9A825), Color(0xFFC62828), Color(0xFF1565C0)))) else Modifier)
                        .keyboardBackground(KeyboardThemes.getRenderingScheme(theme).keyboardBackground, dark,
                            palette.background, config)) {
                        val schema = schemaFor(layout)
                        val ui = KeyboardUiState(currentSchemaId = schema, themeId = theme, isDarkTheme = dark)
                        val body = Modifier.requiredSize(400.dp, 280.dp)
                        when (layout) {
                            "symbol" -> SymbolKeyboardLayout(onSelect = {}, onBack = {}, backgroundColor = palette.background,
                                textColor = palette.text, accentColor = palette.accent, keyBgColor = palette.key,
                                onNumber = {}, shadowEnabled = kbShadow.enabled, shadowElevation = kbShadow.elevation.dp,
                                shadowShapeRadius = kbShadow.shapeRadius.dp, modifier = body)
                            "edit" -> EditKeyboardLayout(onAction = {}, onBack = {}, backgroundColor = palette.background,
                                textColor = palette.text, accentColor = palette.accent, keyBgColor = palette.key,
                                keyCornerRadius = kbKey.cornerRadius.dp, shadowEnabled = kbShadow.enabled,
                                shadowElevation = kbShadow.elevation.dp, shadowShapeRadius = kbShadow.shapeRadius.dp,
                                modifier = body)
                            else -> KeyboardLayoutScreen(
                                keyboardState = when (layout) {
                                    "t9" -> KeyboardLayoutState.T9Pinyin
                                    "num" -> KeyboardLayoutState.Number
                                    "common" -> KeyboardLayoutState.CommonSymbol
                                    else -> KeyboardLayoutState.Chinese
                                },
                                uiState = ui, viewModel = vm,
                                callbacks = KeyboardCallbacks(onKeyPress = { _, _ -> }, onCandidateSelect = {}),
                                onKeyPress = {}, modifier = body, t9Controller = t9)
                        }
                    }
                }
            }
        }
        val dir = File(out!!).apply { mkdirs() }
        fun grab(tag: String): Bitmap {
            val bounds = rule.onNodeWithTag(tag, true).fetchSemanticsNode().boundsInRoot
            val root = rule.activity.window.decorView
            val full = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            rule.runOnIdle { root.draw(Canvas(full)) }
            val crop = Bitmap.createBitmap(full, bounds.left.toInt().coerceAtLeast(0), bounds.top.toInt().coerceAtLeast(0),
                bounds.width.toInt().coerceAtMost(full.width), bounds.height.toInt().coerceAtMost(full.height))
            if (crop !== full) full.recycle()
            return crop
        }
        // The step about to render, so a stall names its combination.
        val progress = File(dir, "progress.txt")
        fun show(t: String, d: Boolean, g: Boolean, l: String, shape: KeyShape) {
            progress.appendText("$t ${if (d) "dark" else "light"} ${if (g) "glass" else "solid"} $l ${shape.id} " +
                "${VisualStyles.current.id} heap=${(Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) shr 20}MB\n")
            val s = schemaFor(l)
            KeysConfigHelper.setActiveKeyboardSchema(s)
            SettingsPreferences.getPrefsPublic(app).edit().putString("keyboard_theme", t).commit()
            rule.runOnIdle {
                KeyShapes.current = shape
                vm.resetKeyboard(false, s); theme = t; dark = d; glass = g; layout = l; revision++
            }
            rule.waitForIdle()
        }
        for (t in group.filter { only == null || it in only }) for (d in listOf(false, true)) for (g in listOf(false, true)) {
            val sheet = Sheet(layouts, KeyShape.entries.map { it.id })
            layouts.forEachIndexed { r, l -> KeyShape.entries.forEachIndexed { c, shape ->
                show(t, d, g, l, shape); sheet.put(r, c, grab("shape-body"))
            } }
            sheet.save(File(dir, "$t-${if (d) "dark" else "light"}-${if (g) "glass" else "solid"}.png"))
        }
        // 视觉样式：键帽材质按同一半径画，换形状后描边、高光都要跟着键角走。
        if (styles) for ((t, d) in listOf("soft_blue" to false, "pure_black" to true)) {
            val looks = VisualStyle.entries
            val sheet = Sheet(looks.map { it.id }, KeyShape.entries.map { it.id })
            looks.forEachIndexed { r, style ->
                rule.runOnIdle { VisualStyles.current = style }
                KeyShape.entries.forEachIndexed { c, shape -> show(t, d, false, "26", shape); sheet.put(r, c, grab("shape-body")) }
            }
            rule.runOnIdle { VisualStyles.current = VisualStyle.ORIGINAL }
            sheet.save(File(dir, "styles-$t-${if (d) "dark" else "light"}.png"))
        }
    }

    /** 设置页「主题与外观」里视觉样式和按键形状两组卡片，浅色 / 深色，各选中一种形状。 */
    @Test fun renderShapePicker() {
        val out = System.getenv("CYIME_RENDER_OUT")
        assumeTrue(!out.isNullOrEmpty())
        val app = ApplicationProvider.getApplicationContext<Application>()
        KeysConfigHelper.loadConfig(app); KeyboardThemes.reload(app)
        com.kingzcheung.xime.ui.keyboard.AppFonts.initialize(app)
        var theme by mutableStateOf("soft_blue")
        var dark by mutableStateOf(false)
        rule.setContent {
            val palette = resolveKeyboardPalette(theme, dark)
            CompositionLocalProvider(LocalKeyboardPalette provides palette) {
                MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                    Column(Modifier.requiredWidth(360.dp).testTag("picker-body")
                        .background(MaterialTheme.colorScheme.surface).padding(16.dp)) {
                        VisualStylePicker(VisualStyles.current) {}
                        Box(Modifier.padding(top = 16.dp)) { KeyShapePicker(KeyShapes.current) {} }
                    }
                }
            }
        }
        val dir = File(out!!).apply { mkdirs() }
        for ((t, d) in listOf("soft_blue" to false, "pure_black" to true)) for (shape in KeyShape.entries) {
            rule.runOnIdle { KeyShapes.current = shape; theme = t; dark = d }
            rule.waitForIdle()
            val bounds = rule.onNodeWithTag("picker-body", true).fetchSemanticsNode().boundsInRoot
            val root = rule.activity.window.decorView
            val full = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            rule.runOnIdle { root.draw(Canvas(full)) }
            val crop = Bitmap.createBitmap(full, bounds.left.toInt().coerceAtLeast(0), bounds.top.toInt().coerceAtLeast(0),
                bounds.width.toInt().coerceAtMost(full.width), bounds.height.toInt().coerceAtMost(full.height))
            File(dir, "picker-$t-${shape.id}.png").outputStream().use { crop.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    /** Rows × columns of cells, drawn as they come so only one sheet is held; ASCII labels (Paint has no CJK font here). */
    private class Sheet(private val rows: List<String>, private val columns: List<String>) {
        private var out: Bitmap? = null
        private var w = 0
        private var h = 0
        private val label = 120
        private val head = 48
        private val gap = 8
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 30f; color = android.graphics.Color.WHITE }

        fun put(row: Int, column: Int, cell: Bitmap) {
            val sheet = out ?: run {
                w = cell.width; h = cell.height
                Bitmap.createBitmap(label + columns.size * (w + gap), head + rows.size * (h + gap), Bitmap.Config.ARGB_8888).also {
                    val canvas = Canvas(it)
                    canvas.drawColor(android.graphics.Color.rgb(128, 128, 128))
                    columns.forEachIndexed { c, name -> canvas.drawText(name, (label + c * (w + gap) + 12).toFloat(), 36f, paint) }
                    rows.forEachIndexed { r, name -> canvas.drawText(name, 8f, (head + r * (h + gap) + 40).toFloat(), paint) }
                    out = it
                }
            }
            Canvas(sheet).drawBitmap(cell, (label + column * (w + gap)).toFloat(), (head + row * (h + gap)).toFloat(), null)
            cell.recycle()
        }

        fun save(file: File) {
            val sheet = out ?: return
            file.outputStream().use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
            sheet.recycle()
        }
    }
}
