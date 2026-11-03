package com.kingzcheung.xime.ui.render

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kingzcheung.xime.settings.FrostedGlassConfig
import com.kingzcheung.xime.settings.KeysConfigHelper
import com.kingzcheung.xime.keyboard.ToolbarAction
import com.kingzcheung.xime.keyboard.ToolbarButton
import com.kingzcheung.xime.keyboard.ToolbarButtonItem
import com.kingzcheung.xime.ui.keyboard.AppFonts
import com.kingzcheung.xime.ui.keyboard.CandidateBar
import com.kingzcheung.xime.ui.keyboard.CandidateBarState
import com.kingzcheung.xime.ui.keyboard.CandidateBarCallbacks
import com.kingzcheung.xime.ui.keyboard.CandidateBarVisuals
import com.kingzcheung.xime.ui.keyboard.KeyboardCallbacks
import com.kingzcheung.xime.ui.keyboard.KeyboardInputPreferences
import com.kingzcheung.xime.ui.keyboard.KeyboardLayout
import com.kingzcheung.xime.ui.keyboard.KeyboardResizeOverlay
import com.kingzcheung.xime.ui.keyboard.LocalKeyboardInputPreferences
import com.kingzcheung.xime.ui.keyboard.LocalKeyboardExplicitWidth
import com.kingzcheung.xime.ui.keyboard.KeyboardResizePreviewState
import com.kingzcheung.xime.ui.keyboard.LocalKeyboardResizePreviewState
import com.kingzcheung.xime.ui.keyboard.ResizeRect
import com.kingzcheung.xime.ui.theme.KeyboardThemes
import com.kingzcheung.xime.ui.theme.LocalKeyboardPalette
import com.kingzcheung.xime.ui.theme.keyboardBackground
import com.kingzcheung.xime.ui.theme.resolveKeyboardPalette
import com.kingzcheung.xime.viewmodel.KeyboardUiState
import com.kingzcheung.xime.viewmodel.KeyboardViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.roundToInt

/**
 * 键盘调节界面的透明度条（底板、描边、标签）和下面的按钮，用真实 KeyboardResizeOverlay 盖在一块画出来的假键盘上，
 * 在电脑上渲染成一张图（真实 KeyboardView 要加载 rime 原生库，电脑上跑不了）。
 * 只在设置了环境变量 CYIME_RENDER_OUT=<目录> 时出图。可选变量：CYIME_RENDER_TAG（文件名里的标记，默认 after）、
 * CYIME_RENDER_THEMES（默认 soft_blue/light,soft_blue/dark）、CYIME_RENDER_AFTER_DRAG=1 时先把每个滑条拖到 40% 再截，
 * 看调完透明度后外框和四个按钮是否还在。手机宽 411dp 四个变体排一行，再一行小键盘 300dp 和横屏矮键盘 600×250dp
 * （看控件跟着键盘大小缩放），平板宽 1200dp 每行一个。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w1700dp-h3000dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ResizeOpacityPanelRenderTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test fun renderOpacityPanel() {
        val out = System.getenv("CYIME_RENDER_OUT")
        assumeTrue(!out.isNullOrEmpty())
        val context = ApplicationProvider.getApplicationContext<Context>()
        KeyboardThemes.initFromConfig(context)
        val themes = (System.getenv("CYIME_RENDER_THEMES") ?: "soft_blue/light,soft_blue/dark").split(",")
            .map { it.trim().split("/") }.map { it[0] to (it[1] == "dark") }
        val afterDrag = System.getenv("CYIME_RENDER_AFTER_DRAG") == "1"
        val tag = System.getenv("CYIME_RENDER_TAG") ?: "after"
        val variants = buildList {
            for ((id, dark) in themes) for (glass in listOf(false, true)) add(Triple(id, dark, glass))
        }
        rule.setContent {
            Column(Modifier.background(Color(0xFF808080))) {
                Row { for ((id, dark, glass) in variants) Variant(411, 300, id, dark, glass) }
                // 控件跟着键盘大小缩放：小键盘一起缩小，横屏矮键盘不撑出框。
                Row { for ((id, dark, glass) in variants.take(2)) { Variant(300, 230, id, dark, glass); Variant(600, 250, id, dark, glass) } }
                for ((id, dark, glass) in variants) Variant(1200, 420, id, dark, glass)
            }
        }
        rule.waitForIdle()
        if (afterDrag) {
            val count = rule.onAllNodesWithTag("keyboard-opacity-slider", useUnmergedTree = true).fetchSemanticsNodes().size
            for (i in 0 until count) {
                rule.onAllNodesWithTag("keyboard-opacity-slider", useUnmergedTree = true)[i]
                    .performSemanticsAction(SemanticsActions.SetProgress) { it(0.4f) }
            }
            rule.waitForIdle()
        }
        // Robolectric 下 captureToImage 等不到 PixelCopy；直接把整个窗口画到位图上。
        val root = rule.activity.window.decorView
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        rule.runOnIdle { root.draw(android.graphics.Canvas(bitmap)) }
        File(out!!).mkdirs()
        File(out, "resize-opacity-$tag${if (afterDrag) "-dragged" else ""}.png").outputStream()
            .use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /**
     * 手指按住滑条拖动时：只留滑条（底板只包这一行），外框、把手、提示、按钮和遮罩都不画（11-03「就只留透明度那个条」）。
     * 每个变体按住时各出一张 resize-opacity-dragging-<n>.png，同一张图里其他变体是松手的样子，正好对照。
     */
    @Test fun renderWhileDragging() {
        val out = System.getenv("CYIME_RENDER_OUT")
        assumeTrue(!out.isNullOrEmpty())
        val context = ApplicationProvider.getApplicationContext<Context>()
        KeyboardThemes.initFromConfig(context)
        val themes = (System.getenv("CYIME_RENDER_THEMES") ?: "soft_blue/light,soft_blue/dark").split(",")
            .map { it.trim().split("/") }.map { it[0] to (it[1] == "dark") }
        val variants = buildList {
            for ((id, dark) in themes) for (glass in listOf(false, true)) add(Triple(id, dark, glass))
        }
        rule.setContent {
            Column(Modifier.background(Color(0xFF808080))) {
                Row { for ((id, dark, glass) in variants) Variant(411, 300, id, dark, glass) }
            }
        }
        rule.waitForIdle()
        File(out!!).mkdirs()
        val root = rule.activity.window.decorView
        for (i in variants.indices) {
            val slider = rule.onAllNodesWithTag("keyboard-opacity-slider", useUnmergedTree = true)[i]
            slider.performTouchInput { down(center); moveBy(Offset(width * 0.15f, 0f)) }
            rule.waitForIdle()
            val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            rule.runOnIdle { root.draw(android.graphics.Canvas(bitmap)) }
            File(out, "resize-opacity-dragging-$i.png").outputStream()
                .use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            slider.performTouchInput { up() }
            rule.waitForIdle()
        }
    }

    @Composable
    private fun Variant(width: Int, height: Int, id: String, dark: Boolean, glass: Boolean) {
        val palette = resolveKeyboardPalette(id, dark)
        val glassConfig = FrostedGlassConfig.defaults(dark, enabled = glass)
        val config = Configuration(LocalConfiguration.current).apply {
            screenWidthDp = width
            screenHeightDp = 900
            smallestScreenWidthDp = minOf(width, screenHeightDp)
        }
        CompositionLocalProvider(LocalConfiguration provides config, LocalDensity provides Density(1f, 1f),
            LocalKeyboardPalette provides palette,
            LocalKeyboardInputPreferences provides KeyboardInputPreferences(frostedGlass = glassConfig)) {
            MaterialTheme {
                // 宿主：玻璃开时垫一张彩色“壁纸”，才看得出半透明；上面留 100dp 当宿主页面。
                Box(Modifier.padding(4.dp).size(width.dp, (height + 100).dp)
                    .then(if (glass) Modifier.background(Brush.linearGradient(
                        listOf(Color(0xFF2E7D32), Color(0xFFF9A825), Color(0xFFC62828), Color(0xFF1565C0))))
                    else Modifier.background(if (dark) Color(0xFF202124) else Color(0xFFE8EAED)))) {
                    Text("${width}dp  $id  ${if (dark) "深色" else "浅色"}  ${if (glass) "玻璃" else "不透明"}",
                        fontSize = 12.sp, color = Color.White, modifier = Modifier.padding(6.dp))
                    // 假键盘：主题背景 + 四排圆角键，和真键盘的颜色一致。
                    Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(height.dp)
                        .keyboardBackground(KeyboardThemes.getRenderingScheme(id).keyboardBackground, dark, palette.background, glassConfig)) {
                        Column(Modifier.fillMaxSize().padding(top = 44.dp, start = 4.dp, end = 4.dp, bottom = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            for (row in listOf("qwertyuiop", "asdfghjkl", "zxcvbnm", "123 空格 。")) {
                                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    for (label in if (row.contains(' ')) row.split(' ') else row.map { it.toString() }) {
                                        Box(Modifier.weight(if (label == "空格") 4f else 1f).fillMaxSize()
                                            .clip(RoundedCornerShape(8.dp)).background(palette.key), contentAlignment = Alignment.Center) {
                                            Text(label, color = palette.text, fontSize = 16.sp)
                                        }
                                    }
                                }
                            }
                        }
                        KeyboardResizeOverlay(height, height, 0, false,
                            initialOpacity = 1f, onHeightChange = {}, onBottomPaddingChange = {},
                            onOpacityChange = {}, onReset = {}, onConfirm = { _, _, _, _ -> }, onCancel = {},
                            onFloatingModeChange = {}, modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }
    }

    @Test fun sliderAndActionsShareTheirEdgesAndLeaveTheGripsClear() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        KeyboardThemes.initFromConfig(app)
        KeysConfigHelper.loadConfig(app)
        KeysConfigHelper.setActiveKeyboardSchema("pinyin_14jian")
        AppFonts.initialize(app)
        val vm = KeyboardViewModel(app)
        val theme = "pure_black"
        val palette = resolveKeyboardPalette(theme, true)
        val sizes = listOf(388 to 280, 246 to 180, 800 to 260)
        val scenario = mutableIntStateOf(0)
        var confirmedOpacity = -1f
        rule.setContent {
            val (width, height) = sizes[scenario.intValue]
            val floating = scenario.intValue == 1
            val split = scenario.intValue == 2
            val config = Configuration(LocalConfiguration.current).apply {
                smallestScreenWidthDp = if (split) 800 else 388
                screenWidthDp = smallestScreenWidthDp
                screenHeightDp = if (split) 1000 else 844
            }
            CompositionLocalProvider(LocalDensity provides Density(1.5f, if (floating) 1.3f else 1f),
                LocalConfiguration provides config, LocalKeyboardExplicitWidth provides true,
                LocalKeyboardPalette provides palette,
                LocalKeyboardInputPreferences provides KeyboardInputPreferences(splitKeyboardEnabled = split)) {
                MaterialTheme {
                    Box(Modifier.size(width.dp, height.dp).background(palette.background).testTag("preview")) {
                        Column {
                            CandidateBar(CandidateBarState.Idle,
                                toolbarActions = listOf(ToolbarButton.EMOJI, ToolbarButton.EDIT, ToolbarButton.CLIPBOARD,
                                    ToolbarButton.VOICE).map { ToolbarAction(ToolbarButtonItem.Builtin(it)) {} },
                                visuals = CandidateBarVisuals(palette.background, palette.text, palette.function,
                                    palette.accent, isDarkTheme = true), callbacks = CandidateBarCallbacks({}))
                            KeyboardLayout({}, vm, KeyboardCallbacks(onKeyPress = { _, _ -> }, onCandidateSelect = {}),
                                KeyboardUiState(currentSchemaId = "pinyin_14jian", isDarkTheme = true, themeId = theme),
                                false, Modifier.fillMaxSize())
                        }
                        key(scenario.intValue) {
                            KeyboardResizeOverlay(height, height, 0, floating, isSplitKeyboard = split,
                                onHeightChange = {}, onBottomPaddingChange = {}, onOpacityChange = {}, onReset = {},
                                onConfirm = { _, _, _, value -> confirmedOpacity = value }, onCancel = {},
                                onFloatingModeChange = {}, onSplitKeyboardChange = {})
                        }
                    }
                }
            }
        }
        fun bounds(tag: String) = rule.onNodeWithTag(tag, true).fetchSemanticsNode().boundsInRoot
        for (i in sizes.indices) {
            rule.runOnIdle { scenario.intValue = i }
            rule.waitForIdle()
            val slider = rule.onNodeWithTag("keyboard-opacity-slider")
            slider.performSemanticsAction(SemanticsActions.SetProgress) { it(0.35f) }
            val out = System.getenv("CYIME_RENDER_OUT")
            if (out != null || i == 0) {
                if (i == 0) slider.performSemanticsAction(SemanticsActions.SetProgress) { it(0f) }
                rule.waitForIdle()
                val root = rule.activity.window.decorView
                val full = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
                rule.runOnIdle { root.draw(android.graphics.Canvas(full)) }
                val frame = bounds("preview")
                val shot = Bitmap.createBitmap(full, frame.left.toInt(), frame.top.toInt(), frame.width.toInt(), frame.height.toInt())
                if (i == 0) {
                    // 即使两个容器等宽，Slider 内置的半个滑块留白也可能让画出来的轨道变短。
                    val row = bounds("keyboard-resize-actions")
                    val left = (row.left - frame.left).roundToInt()
                    val right = (row.right - frame.left).roundToInt() - 1
                    for (centerY in listOf(bounds("keyboard-opacity-thumb").center.y, row.center.y)) {
                        val y = (centerY - frame.top).toInt()
                        val background = shot.getPixel(left - 4, y)
                        val painted = (left - 4..right + 4).filter { shot.getPixel(it, y) != background }
                        assertEquals("painted left edge", left.toFloat(), painted.first().toFloat(), 1f)
                        if (centerY == row.center.y) {
                            assertEquals("painted right edge", right.toFloat(), painted.last().toFloat(), 1f)
                        } else {
                            // 滑条这一行右端是「透明度 N%」：文字框贴齐按钮右沿（下面按语义边界检查），
                            // 字形自带几像素右侧留白，所以只要求画出来的部分不越界、离右沿不超过 6dp。
                            assertTrue("painted right edge of the opacity row: ${painted.last()} vs $right",
                                painted.last() <= right + 1 && painted.last() >= right - (6 * 1.5f).roundToInt())
                        }
                    }
                }
                if (out != null) File(out, "resize-${listOf("14-key", "floating", "split")[i]}.png").outputStream().use {
                    shot.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                if (i == 0) slider.performSemanticsAction(SemanticsActions.SetProgress) { it(0.35f) }
            }
            val label = bounds("keyboard-opacity-overlay-label")
            val thumb = bounds("keyboard-opacity-thumb")
            val actions = bounds("keyboard-resize-actions")
            val upperGrip = bounds(listOf("keyboard-resize-fixed-move-bar", "keyboard-resize-floating-move-pill",
                "keyboard-resize-split-hint")[i])
            val lowerGrip = bounds(if (i == 1) "keyboard-resize-move-bar" else "keyboard-resize-padding-bar")
            // 「透明度 N%」放在轨道右边（用户 2026-11-01：细轨道不能被文字截成两段），和滑块同一条中线。
            assertTrue("the value sits right of the thumb at ${sizes[i]}: label=$label, thumb=$thumb", label.left > thumb.right)
            assertEquals("the value is level with the track at ${sizes[i]}: label=$label, thumb=$thumb", label.center.y, thumb.center.y, 1f)
            val reset = rule.onNodeWithContentDescription("重置").fetchSemanticsNode().boundsInRoot
            val confirm = rule.onNodeWithContentDescription("确认").fetchSemanticsNode().boundsInRoot
            // Slider 的无障碍命中范围会向外延伸，视觉边线取承载轨道的面板。
            val track = bounds("keyboard-resize-opacity-panel")
            assertEquals(reset.left, track.left, 1f)
            assertEquals(confirm.right, track.right, 1f)
            assertEquals("the value ends at the row's right edge", track.right, label.right, 1f)
            if (i != 1) {
                val frame = bounds("preview")
                assertEquals("upper and lower insets", upperGrip.top - frame.top, frame.bottom - lowerGrip.bottom, 1f)
            }
            assertTrue("upper grip has its own space", upperGrip.bottom <= bounds("keyboard-opacity-slider").top)
            assertTrue("lower grip has its own space", actions.bottom <= lowerGrip.top)
            rule.onNodeWithContentDescription("确认").performClick()
            rule.runOnIdle { assertEquals(0.65f, confirmedOpacity, 0.01f) }
        }
    }

    @Test fun draggingThePaddingHintUsesTheBottomHandleGesture() {
        val padding = mutableIntStateOf(36)
        var confirmedHeight = 0
        var confirmedPadding = -1
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                Box(Modifier.absoluteOffset(12.dp, 18.dp).size(360.dp, 420.dp)) {
                    val bounds = ResizeRect(0f, 0f, 360f, 420f)
                    val rect = remember { mutableStateOf(bounds.copy(top = 124f)) }
                    val preview = KeyboardResizePreviewState(rect = rect.value, bounds = bounds,
                        onRectChange = { rect.value = it }, bottomPaddingDp = padding.intValue,
                        onBottomPaddingChange = { padding.intValue = it })
                    CompositionLocalProvider(LocalKeyboardResizePreviewState provides preview) {
                        KeyboardResizeOverlay(260, 260, 36, false, defaultBottomPaddingDp = 36,
                            onHeightChange = {}, onBottomPaddingChange = {}, onOpacityChange = {}, onReset = {},
                            onConfirm = { height, blank, _, _ -> confirmedHeight = height; confirmedPadding = blank }, onCancel = {})
                    }
                }
            }
        }
        rule.onNodeWithTag("keyboard-resize-padding-bar").performTouchInput {
            down(center)
            for (i in 1..8) moveTo(center - Offset(0f, i * 6f))
            up()
        }
        rule.runOnIdle { assertTrue("dragging the label must increase the bottom inset", padding.intValue > 36) }
        val dragged = padding.intValue
        rule.onNodeWithContentDescription("减少底部留白").performTouchInput { click(center) }
        rule.runOnIdle { assertEquals(dragged - 2, padding.intValue) }
        rule.onNodeWithContentDescription("增加底部留白").performTouchInput { click(center) }
        rule.runOnIdle { assertEquals(dragged, padding.intValue) }
        rule.onNodeWithTag("keyboard-resize-padding-bar").performTouchInput {
            down(center)
            for (i in 1..12) moveTo(center + Offset(0f, i * 10f))
            up()
        }
        rule.onNodeWithContentDescription("确认").performClick()
        rule.runOnIdle { assertEquals(0, confirmedPadding); assertEquals(260, confirmedHeight) }
        rule.onNodeWithContentDescription("重置").performClick()
        rule.runOnIdle { assertEquals(36, padding.intValue) }
    }
}
