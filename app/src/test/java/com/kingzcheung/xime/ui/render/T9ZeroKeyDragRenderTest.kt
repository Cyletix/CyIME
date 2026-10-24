package com.kingzcheung.xime.ui.render

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Paint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kingzcheung.xime.settings.FrostedGlassConfig
import com.kingzcheung.xime.settings.KeysConfigHelper
import com.kingzcheung.xime.ui.keyboard.CandidateBar
import com.kingzcheung.xime.ui.keyboard.CandidateBarCallbacks
import com.kingzcheung.xime.ui.keyboard.CandidateBarState
import com.kingzcheung.xime.ui.keyboard.CandidateBarVisuals
import com.kingzcheung.xime.ui.keyboard.CandidateDragLine
import com.kingzcheung.xime.ui.keyboard.CandidateDropTargets
import com.kingzcheung.xime.ui.keyboard.KeyboardInputPreferences
import com.kingzcheung.xime.ui.keyboard.LocalKeyboardInputPreferences
import com.kingzcheung.xime.ui.keyboard.NineKeyButton
import com.kingzcheung.xime.ui.keyboard.SwipeableIconKeyButton
import com.kingzcheung.xime.ui.keyboard.T9NextCandidateIconDp
import com.kingzcheung.xime.ui.keyboard.dragToCandidate
import com.kingzcheung.xime.ui.theme.KeyboardThemes
import com.kingzcheung.xime.ui.theme.LocalKeyboardPalette
import com.kingzcheung.xime.ui.theme.keyboardBackground
import com.kingzcheung.xime.ui.theme.resolveKeyboardPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 九键「下一项」0 键：短左滑、较长左滑（没过键盘半宽）仍是键内左箭头；拖出键盘一半高到候选上才是拖动选词，
 * 画出从键到手指的拖线、候选高亮，松手选中这个候选。只在设了 CYIME_RENDER_OUT 时出图。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w420dp-h700dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class T9ZeroKeyDragRenderTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test fun renderDragToCandidate() {
        val out = System.getenv("CYIME_RENDER_OUT")
        assumeTrue(!out.isNullOrEmpty())
        val app = ApplicationProvider.getApplicationContext<Application>()
        KeysConfigHelper.loadConfig(app)
        KeyboardThemes.initFromConfig(app)
        com.kingzcheung.xime.ui.keyboard.AppFonts.initialize(app)
        var theme by mutableStateOf("soft_blue")
        var dark by mutableStateOf(false)
        var glass by mutableStateOf(false)
        var selected: Int? = null
        var previous = 0
        rule.setContent {
            val palette = resolveKeyboardPalette(theme, dark)
            val config = FrostedGlassConfig.defaults(dark, enabled = glass)
            CompositionLocalProvider(LocalKeyboardPalette provides palette,
                LocalKeyboardInputPreferences provides KeyboardInputPreferences(frostedGlass = config)) {
                MaterialTheme {
                    var area by remember { mutableStateOf(Rect.Zero) }
                    Column(Modifier.requiredSize(360.dp, 290.dp).testTag("scene")
                        .then(if (glass) Modifier.background(Brush.linearGradient(
                            listOf(Color(0xFF2E7D32), Color(0xFFF9A825), Color(0xFFC62828), Color(0xFF1565C0)))) else Modifier)
                        .keyboardBackground(KeyboardThemes.getRenderingScheme(theme).keyboardBackground, dark,
                            palette.background, config)) {
                        CandidateBar(
                            state = CandidateBarState.ChineseCandidates(listOf("这到底", "是什么", "原理", "这到底是什么原理"),
                                inputText = "zhedaodi", hasMore = true),
                            visuals = CandidateBarVisuals(Color.Transparent, palette.candidateText,
                                if (dark) Color(0xFF3C4043) else Color(0xFFDADCE0), palette.accent,
                                palette.selectedText, dark, palette.key),
                            callbacks = CandidateBarCallbacks(onCandidateSelect = { selected = it }, onShowMoreCandidates = {}),
                            showPreeditPreview = false,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Box(Modifier.fillMaxWidth().height(246.dp).padding(bottom = 8.dp)
                            .onGloballyPositioned { area = it.boundsInRoot() }) {
                            Row(Modifier.fillMaxSize()) {
                                // 与九键一样：左栏、三列数字键、右栏功能键（退格 / 重输 / 0 / 发送）。
                                Column(Modifier.weight(0.8f).fillMaxHeight()) {
                                    for (label in listOf("，", "。", "？", "符")) NineKeyButton(digit = "", letters = label,
                                        onClick = {}, backgroundColor = palette.function, textColor = palette.functionText,
                                        modifier = Modifier.weight(1f))
                                }
                                for (column in listOf(listOf("1" to "分词", "4" to "GHI", "7" to "PQRS", "" to "123"),
                                    listOf("2" to "ABC", "5" to "JKL", "8" to "TUV", "" to "空格"),
                                    listOf("3" to "DEF", "6" to "MNO", "9" to "WXYZ", "" to "中/En"))) {
                                    Column(Modifier.weight(1f).fillMaxHeight()) {
                                        for ((digit, letters) in column) NineKeyButton(digit = digit, letters = letters,
                                            onClick = {}, backgroundColor = palette.key, textColor = palette.text,
                                            modifier = Modifier.weight(1f))
                                    }
                                }
                                Column(Modifier.weight(0.8f).fillMaxHeight()) {
                                    SwipeableIconKeyButton(icon = rememberVectorPainter(Icons.AutoMirrored.Filled.Backspace),
                                        onClick = {}, backgroundColor = palette.function, iconColor = palette.functionText,
                                        modifier = Modifier.weight(1f))
                                    NineKeyButton(digit = "", letters = "重输", onClick = {},
                                        backgroundColor = palette.function, textColor = palette.functionText,
                                        modifier = Modifier.weight(1f))
                                    NineKeyButton(digit = "", letters = "下一项",
                                        icon = rememberVectorPainter(Icons.AutoMirrored.Filled.KeyboardArrowRight),
                                        iconSizeDp = T9NextCandidateIconDp,
                                        onClick = {}, backgroundColor = palette.key, textColor = palette.text,
                                        modifier = Modifier.weight(1f).testTag("zero").dragToCandidate(true, { area }),
                                        onSwipeLeft = { previous++ })
                                    NineKeyButton(digit = "", letters = "发送", onClick = {},
                                        backgroundColor = palette.function, textColor = palette.functionText,
                                        modifier = Modifier.weight(1f))
                                }
                            }
                            CandidateDragLine(CandidateDropTargets.shared, { area.topLeft }, palette.accent,
                                Modifier.matchParentSize())
                        }
                    }
                }
            }
        }
        fun shot(): Bitmap {
            val scene = rule.onNodeWithTag("scene", true).fetchSemanticsNode().boundsInRoot
            val root = rule.activity.window.decorView
            val full = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            rule.runOnIdle { root.draw(android.graphics.Canvas(full)) }
            return Bitmap.createBitmap(full, scene.left.toInt().coerceAtLeast(0), scene.top.toInt().coerceAtLeast(0),
                scene.width.toInt().coerceAtMost(full.width), scene.height.toInt().coerceAtMost(full.height))
        }
        val drops = CandidateDropTargets.shared
        val rows = mutableListOf<Pair<String, List<Bitmap>>>()
        for ((id, isDark, g) in listOf(Triple("soft_blue", false, false), Triple("soft_blue", true, true),
            Triple("pure_black", true, false), Triple("pure_black", false, true))) {
            rule.runOnIdle { theme = id; dark = isDark; glass = g }
            rule.waitForIdle()
            val frames = mutableListOf<Bitmap>()
            // 短左滑和较长左滑（都没过键盘半宽）：键内左箭头，不画拖线，松手是「上一项」。
            for (distance in listOf(60, 150)) {
                rule.onNodeWithTag("zero", true).performTouchInput {
                    down(center)
                    for (step in 1..10) moveTo(center + Offset(-distance.dp.toPx() * step / 10f, 0f))
                }
                rule.waitForIdle()
                assertNull("没过半个键盘不算拖动", drops.dragLine)
                frames += shot()
                val before = previous
                rule.onNodeWithTag("zero", true).performTouchInput { up() }
                rule.waitForIdle()
                assertEquals("左滑松手是上一项", before + 1, previous)
            }
            // 拖到第二个候选上：拖线、候选高亮，键内不再有左滑预览；松手选中它，不触发上一项。
            val target = rule.onNodeWithTag("bar-candidate:1", true).fetchSemanticsNode().boundsInRoot.center
            val zero = rule.onNodeWithTag("zero", true).fetchSemanticsNode().boundsInRoot
            rule.onNodeWithTag("zero", true).performTouchInput {
                down(center)
                for (step in 1..12) moveTo(center + (target - zero.center) * (step / 12f))
            }
            rule.waitForIdle()
            assertNotNull("拖到候选上画出拖线", drops.dragLine)
            frames += shot()
            val before = previous
            selected = null
            rule.onNodeWithTag("zero", true).performTouchInput { up() }
            rule.waitForIdle()
            assertEquals("松手选中第二个候选", 1, selected)
            assertEquals("拖动选词不触发上一项", before, previous)
            assertNull("松手清掉拖线", drops.dragLine)
            rows += "$id ${if (isDark) "深" else "浅"} ${if (g) "玻璃" else "不透明"}" to frames
        }
        val labelWidth = 300
        val cell = rows.first().second.first()
        val sheet = Bitmap.createBitmap(labelWidth + (cell.width + 16) * 3, 60 + (cell.height + 16) * rows.size,
            Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(sheet)
        canvas.drawColor(0xFF808080.toInt())
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE; textSize = 30f }
        listOf("左滑 60dp", "左滑 150dp（不到半宽）", "拖到第二个候选上").forEachIndexed { index, title ->
            canvas.drawText(title, labelWidth + index * (cell.width + 16f) + 12f, 40f, paint)
        }
        rows.forEachIndexed { row, (label, frames) ->
            val top = 60f + row * (cell.height + 16)
            canvas.drawText(label, 12f, top + cell.height / 2f, paint)
            frames.forEachIndexed { index, frame -> canvas.drawBitmap(frame, labelWidth + index * (cell.width + 16f), top, null) }
        }
        File(out!!).mkdirs()
        File(out, "${System.getenv("CYIME_RENDER_NAME") ?: "t9-zero-key-drag"}.png").outputStream().use {
            sheet.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}

