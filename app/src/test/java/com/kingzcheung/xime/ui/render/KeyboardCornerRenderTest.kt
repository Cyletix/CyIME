package com.kingzcheung.xime.ui.render

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kingzcheung.xime.rime.T9InputController
import com.kingzcheung.xime.settings.FrostedGlassConfig
import com.kingzcheung.xime.settings.KeysConfigHelper
import com.kingzcheung.xime.settings.SettingsPreferences
import com.kingzcheung.xime.ui.keyboard.CandidateBar
import com.kingzcheung.xime.ui.keyboard.CandidateBarCallbacks
import com.kingzcheung.xime.ui.keyboard.CandidateBarState
import com.kingzcheung.xime.ui.keyboard.CandidateBarVisuals
import com.kingzcheung.xime.ui.keyboard.FloatingKeyboardCardCorner
import com.kingzcheung.xime.ui.keyboard.KeyboardCallbacks
import com.kingzcheung.xime.ui.keyboard.KeyboardInputPreferences
import com.kingzcheung.xime.ui.keyboard.KeyboardLayoutScreen
import com.kingzcheung.xime.ui.keyboard.KeyboardLayoutState
import com.kingzcheung.xime.ui.keyboard.LocalKeyCornerRadius
import com.kingzcheung.xime.ui.keyboard.LocalKeyboardCardTopCorner
import com.kingzcheung.xime.ui.keyboard.LocalKeyboardExplicitWidth
import com.kingzcheung.xime.ui.keyboard.LocalKeyboardInputPreferences
import com.kingzcheung.xime.ui.keyboard.keyboardPanelShape
import com.kingzcheung.xime.ui.keyboard.legibleForTheme
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
 * 键盘卡片四角（用户 10-31）：顶部圆角开/关 × 底部圆角开/关 × 悬浮，每张图一行一种布局（26 键、九键；
 * 改的只是卡片外形，14 键与 26 键同一个卡片，26 键的按键表是全局的，同一张图里不混画 14 键）。
 * 每个主题 × 深浅 × 玻璃 × 视觉样式一张。看两件事：顶部关掉后是直角；霓光描边等样式的候选栏描边
 * 沿着卡片的圆角走，不在顶角被切掉。只在设了 CYIME_RENDER_OUT 时出图；CYIME_RENDER_THEMES 只画其中几个主题。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w1960dp-h900dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class KeyboardCornerRenderTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @After fun reset() { VisualStyles.current = VisualStyle.ORIGINAL }

    private val themes = listOf("soft_blue" to false, "soft_blue" to true, "pure_black" to true, "pure_black" to false,
        "sunset_gradient" to false, "transparent_glass" to true)

    /** 列：名字、是否悬浮、底部圆角、顶部圆角。 */
    private val corners = listOf(
        Corner("top-round (default)", false, false, true),
        Corner("all square", false, false, false),
        Corner("bottom-round only", false, true, false),
        Corner("top+bottom round", false, true, true),
        Corner("floating", true, false, true),
    )
    private data class Corner(val label: String, val floating: Boolean, val bottom: Boolean, val top: Boolean)

    private val layouts = listOf("26", "t9")
    private fun schemaFor(layout: String) = if (layout == "t9") "t9_pinyin" else "rime_ice"

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
        val t9 = org.mockito.kotlin.mock<T9InputController> {
            on { leftPanelState } doReturn T9InputController.LeftPanelState.IDLE
            on { firstOptions } doReturn emptyList()
        }
        KeysConfigHelper.setActiveKeyboardSchema("rime_ice")
        val models = layouts.associateWith { KeyboardViewModel(app) }
        var theme by mutableStateOf("soft_blue")
        var dark by mutableStateOf(false)
        var revision by mutableStateOf(0)
        rule.setContent {
            @Suppress("UNUSED_EXPRESSION") revision
            val palette = resolveKeyboardPalette(theme, dark)
            val config = FrostedGlassConfig.defaults(dark, enabled = glass).legibleForTheme(theme, dark)
            val kbKey = KeysConfigHelper.getKeyboardKeyConfig()
            CompositionLocalProvider(LocalKeyboardExplicitWidth provides true, LocalKeyboardPalette provides palette,
                LocalKeyCornerRadius provides kbKey.cornerRadius.dp,
                LocalKeyboardInputPreferences provides KeyboardInputPreferences(frostedGlass = config)) {
                MaterialTheme {
                    Column(Modifier.testTag("corner-body").background(Color(0xFF808080)).padding(8.dp)) {
                        Row {
                            Box(Modifier.width(40.dp))
                            corners.forEach { Text(it.label, Modifier.requiredWidth(376.dp).padding(start = 4.dp),
                                fontSize = 14.sp, color = Color.White) }
                        }
                        layouts.forEach { layout ->
                            Row(Modifier.padding(top = 8.dp)) {
                                Text(layout, Modifier.width(40.dp), fontSize = 14.sp, color = Color.White)
                                corners.forEach { corner ->
                                    // 卡片外垫一块彩色底，裁掉的角一眼看得出。
                                    Box(Modifier.padding(horizontal = 8.dp).background(Brush.linearGradient(listOf(
                                        Color(0xFF2E7D32), Color(0xFFF9A825), Color(0xFFC62828), Color(0xFF1565C0))))
                                        .padding(top = 8.dp, start = 4.dp, end = 4.dp,
                                            bottom = if (corner.floating) 8.dp else 0.dp)) {
                                        Box(Modifier.requiredSize(352.dp, 244.dp)
                                            .clip(keyboardPanelShape(corner.floating, corner.bottom, corner.top))
                                            .keyboardBackground(KeyboardThemes.getRenderingScheme(theme).keyboardBackground,
                                                dark, palette.background, config)) {
                                            CompositionLocalProvider(LocalKeyboardCardTopCorner provides
                                                if (corner.floating || corner.top) FloatingKeyboardCardCorner else 0.dp) {
                                                Column {
                                                    CandidateBar(
                                                        toolbarActions = com.kingzcheung.xime.keyboard.ToolbarButton.entries.take(5).map {
                                                            com.kingzcheung.xime.keyboard.ToolbarAction(
                                                                com.kingzcheung.xime.keyboard.ToolbarButtonItem.Builtin(it)) {} },
                                                        state = CandidateBarState.Idle,
                                                        visuals = CandidateBarVisuals(Color.Transparent, palette.candidateText,
                                                            if (dark) Color(0xFF3C4043) else Color(0xFFDADCE0), palette.accent,
                                                            palette.selectedText, dark, palette.key),
                                                        callbacks = CandidateBarCallbacks(onCandidateSelect = {}),
                                                        isFloatingMode = corner.floating,
                                                        showPreeditPreview = false,
                                                    )
                                                    KeyboardLayoutScreen(
                                                        keyboardState = if (layout == "t9") KeyboardLayoutState.T9Pinyin
                                                            else KeyboardLayoutState.Chinese,
                                                        uiState = KeyboardUiState(currentSchemaId = schemaFor(layout),
                                                            themeId = theme, isDarkTheme = dark),
                                                        viewModel = models.getValue(layout),
                                                        callbacks = KeyboardCallbacks(onKeyPress = { _, _ -> }, onCandidateSelect = {}),
                                                        onKeyPress = {}, modifier = Modifier.requiredSize(352.dp, 200.dp),
                                                        t9Controller = t9)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        rule.runOnIdle { models.forEach { (layout, vm) -> vm.resetKeyboard(false, schemaFor(layout)) } }
        val dir = File(out!!).apply { mkdirs() }
        for ((t, d) in themes.filter { only == null || it.first in only }) for (style in VisualStyle.entries) {
            SettingsPreferences.getPrefsPublic(app).edit().putString("keyboard_theme", t).commit()
            rule.runOnIdle { VisualStyles.current = style; theme = t; dark = d; revision++ }
            rule.waitForIdle()
            val bounds = rule.onNodeWithTag("corner-body", true).fetchSemanticsNode().boundsInRoot
            val root = rule.activity.window.decorView
            val full = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            rule.runOnIdle { root.draw(Canvas(full)) }
            val crop = Bitmap.createBitmap(full, bounds.left.toInt().coerceAtLeast(0), bounds.top.toInt().coerceAtLeast(0),
                bounds.width.toInt().coerceAtMost(full.width), bounds.height.toInt().coerceAtMost(full.height))
            File(dir, "$t-${if (d) "dark" else "light"}-${if (glass) "glass" else "solid"}-${style.id}.png")
                .outputStream().use { crop.compress(Bitmap.CompressFormat.PNG, 100, it) }
            if (crop !== full) crop.recycle()
            full.recycle()
        }
    }
}
