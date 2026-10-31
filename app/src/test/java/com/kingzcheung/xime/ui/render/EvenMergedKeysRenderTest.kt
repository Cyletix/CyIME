package com.kingzcheung.xime.ui.render

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import com.kingzcheung.xime.rime.T9InputController
import com.kingzcheung.xime.settings.FrostedGlassConfig
import com.kingzcheung.xime.settings.CustomKeyboardLayouts
import com.kingzcheung.xime.settings.KeysConfigHelper
import com.kingzcheung.xime.settings.SettingsPreferences
import com.kingzcheung.xime.ui.keyboard.KeyboardCallbacks
import com.kingzcheung.xime.ui.keyboard.KeyboardInputPreferences
import com.kingzcheung.xime.ui.keyboard.KeyboardLayoutScreen
import com.kingzcheung.xime.ui.keyboard.KeyboardLayoutState
import com.kingzcheung.xime.ui.keyboard.LocalKeyCornerRadius
import com.kingzcheung.xime.ui.keyboard.LocalKeyboardInputPreferences
import com.kingzcheung.xime.ui.keyboard.legibleForTheme
import com.kingzcheung.xime.ui.theme.KeyShape
import com.kingzcheung.xime.ui.theme.KeyShapes
import com.kingzcheung.xime.ui.theme.KeyboardThemes
import com.kingzcheung.xime.ui.theme.LocalKeyboardPalette
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
 * 「合并键均匀排布」改前 / 改后：自建 18 键（参考图的 Q WE RT Y U IO P）和 20 键布局，手机竖屏 320dp、360dp 宽，
 * 键盘体高 232dp。列是 宽度-strict（严格对齐 26 键）/ 宽度-even（均匀排布），后面的提交加上大圆角列。
 * 每个主题组合一张总图。只在设了 CYIME_RENDER_OUT 时出图。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w420dp-h900dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EvenMergedKeysRenderTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @After fun reset() { KeyShapes.current = KeyShape.ROUNDED }

    private val eighteenId = "custom_pinyin_" + "1".repeat(32)
    private val twentyId = "custom_pinyin_" + "2".repeat(32)
    private val layouts = listOf(
        Triple(eighteenId, "k18", "q,we,rt,y,u,io,p/a,sd,fg,h,jk,l/z,xc,v,bn,m"),
        Triple(twentyId, "k20", "q,we,r,t,yu,i,op/as,d,f,g,h,jk,l/z,x,cv,b,n,m"),
    )
    /** 宽度、均匀排布、按键形状。 */
    private val columns = listOf(320, 360).flatMap { w ->
        listOf(Triple(w, false, KeyShape.ROUNDED), Triple(w, true, KeyShape.ROUNDED))
    }
    private val looks = listOf(Triple("soft_blue", false, false), Triple("pure_black", true, false),
        Triple("sakura_pink", false, true))

    @Test fun renderEvenMergedKeys() {
        val out = System.getenv("CYIME_RENDER_OUT")
        assumeTrue(!out.isNullOrEmpty())
        val app = ApplicationProvider.getApplicationContext<Application>()
        File(app.filesDir, "custom-keyboard-layouts.json").writeText(org.json.JSONArray().apply {
            layouts.forEach { (id, name, rows) -> put(org.json.JSONObject().put("formatVersion", 2).put("id", id)
                .put("name", name).put("rows", rows).put("redVowels", false).put("redMoved", false)) }
        }.toString())
        CustomKeyboardLayouts.load(app)
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
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
        var schema by mutableStateOf(eighteenId)
        var width by mutableStateOf(360)
        var even by mutableStateOf(false)
        var revision by mutableStateOf(0)
        rule.setContent {
            @Suppress("UNUSED_EXPRESSION") revision
            val palette = resolveKeyboardPalette(theme, dark)
            val config = FrostedGlassConfig.defaults(dark, enabled = glass).legibleForTheme(theme, dark)
            val kbKey = KeysConfigHelper.getKeyboardKeyConfig()
            CompositionLocalProvider(LocalKeyboardPalette provides palette,
                LocalKeyCornerRadius provides kbKey.cornerRadius.dp,
                LocalKeyboardInputPreferences provides KeyboardInputPreferences(frostedGlass = config, evenMergedKeys = even)) {
                MaterialTheme {
                    // 固定 360 宽的画布，320 的键盘靠左画，右边留灰。
                    Box(Modifier.requiredSize(360.dp, 232.dp).testTag("even-body").background(Color(0xFF808080))) {
                        Box(Modifier.requiredSize(width.dp, 232.dp)
                            .then(if (glass) Modifier.background(Brush.linearGradient(listOf(Color(0xFF2E7D32),
                                Color(0xFFF9A825), Color(0xFFC62828), Color(0xFF1565C0)))) else Modifier)
                            .keyboardBackground(KeyboardThemes.getRenderingScheme(theme).keyboardBackground, dark,
                                palette.background, config)) {
                            val ui = KeyboardUiState(currentSchemaId = schema, themeId = theme, isDarkTheme = dark)
                            KeyboardLayoutScreen(keyboardState = KeyboardLayoutState.Chinese,
                                uiState = ui, viewModel = vm,
                                callbacks = KeyboardCallbacks(onKeyPress = { _, _ -> }, onCandidateSelect = {}),
                                onKeyPress = {}, modifier = Modifier.requiredSize(width.dp, 232.dp), t9Controller = t9)
                        }
                    }
                }
            }
        }
        val dir = File(out!!).apply { mkdirs() }
        fun grab(): Bitmap {
            val bounds = rule.onNodeWithTag("even-body", true).fetchSemanticsNode().boundsInRoot
            val root = rule.activity.window.decorView
            val full = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            rule.runOnIdle { root.draw(Canvas(full)) }
            val crop = Bitmap.createBitmap(full, bounds.left.toInt().coerceAtLeast(0), bounds.top.toInt().coerceAtLeast(0),
                bounds.width.toInt().coerceAtMost(full.width), bounds.height.toInt().coerceAtMost(full.height))
            if (crop !== full) full.recycle()
            return crop
        }
        for ((t, d, g) in looks) {
            val sheet = Sheet(layouts.map { it.second }, columns.map { (w, on, shape) ->
                "$w-${if (on) "even" else "strict"}${if (shape == KeyShape.ROUNDED) "" else "-" + shape.id}" })
            layouts.forEachIndexed { r, (id, _, _) -> columns.forEachIndexed { c, (w, on, shape) ->
                KeysConfigHelper.setActiveKeyboardSchema(id)
                SettingsPreferences.getPrefsPublic(app).edit().putString("keyboard_theme", t).commit()
                rule.runOnIdle {
                    vm.resetKeyboard(false, id); theme = t; dark = d; glass = g; schema = id
                    width = w; even = on; KeyShapes.current = shape; revision++
                }
                rule.waitForIdle()
                sheet.put(r, c, grab())
            } }
            sheet.save(File(dir, "even-merged-$t-${if (d) "dark" else "light"}-${if (g) "glass" else "solid"}.png"))
        }
    }

    /** Rows × columns of cells; ASCII labels (Paint has no CJK font here). */
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
                    canvas.drawColor(android.graphics.Color.rgb(64, 64, 64))
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
