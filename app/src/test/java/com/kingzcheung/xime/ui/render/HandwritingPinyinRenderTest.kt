package com.kingzcheung.xime.ui.render

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kingzcheung.xime.handwriting.HanziPinyin
import com.kingzcheung.xime.settings.CandidateCommentMode
import com.kingzcheung.xime.settings.FrostedGlassConfig
import com.kingzcheung.xime.settings.SettingsPreferences
import com.kingzcheung.xime.ui.keyboard.CandidateBar
import com.kingzcheung.xime.ui.keyboard.CandidateBarCallbacks
import com.kingzcheung.xime.ui.keyboard.CandidateBarState
import com.kingzcheung.xime.ui.keyboard.CandidateBarVisuals
import com.kingzcheung.xime.ui.keyboard.KeyboardInputPreferences
import com.kingzcheung.xime.ui.keyboard.LocalKeyboardInputPreferences
import com.kingzcheung.xime.ui.theme.KeyboardThemes
import com.kingzcheung.xime.ui.theme.LocalKeyboardPalette
import com.kingzcheung.xime.ui.theme.keyboardBackground
import com.kingzcheung.xime.ui.theme.resolveKeyboardPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 中文手写候选旁显示读音（带声调），和打字候选的注释同一个样子，跟着候选注释的显示／隐藏走。
 * 设了 CYIME_RENDER_OUT=<目录> 时另出一张各主题的对照图 handwriting-pinyin.png。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w900dp-h1400dp-hdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HandwritingPinyinRenderTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private val table by lazy {
        listOf("src/main/assets/handwriting/hanzi_pinyin.tsv", "app/src/main/assets/handwriting/hanzi_pinyin.tsv")
            .map(::File).first { it.isFile }.useLines(Charsets.UTF_8) { HanziPinyin.parse(it) }
    }
    // 多音字排在前面，保证在 392dp 的候选栏里放得下；嫡、敌、笛等几个字都读 dí，按读音数不出是哪个候选的注释。
    private val written = listOf("嫡", "行", "的", "滴", "敌", "嘀", "镝", "笛")
    private val handwriting by lazy {
        CandidateBarState.AssociationOnly(written, written.map { HanziPinyin.reading(table, it) }, highlightIndex = 0)
    }

    private fun context() = ApplicationProvider.getApplicationContext<Context>()

    // 和真机一样带一排工具栏按钮：收起按钮占空闲工具栏的一格，没有按钮时这一格会占掉半条候选栏。
    private fun toolbarActions() = com.kingzcheung.xime.keyboard.ToolbarButton.entries.take(7).map {
        com.kingzcheung.xime.keyboard.ToolbarAction(com.kingzcheung.xime.keyboard.ToolbarButtonItem.Builtin(it)) {}
    }

    @androidx.compose.runtime.Composable
    private fun Bar(theme: String = "soft_blue", dark: Boolean = false, glass: Boolean = false) {
        val palette = resolveKeyboardPalette(theme, dark)
        val config = FrostedGlassConfig.defaults(dark, enabled = glass)
        Box(Modifier.width(392.dp).height(44.dp)
            .then(if (glass) Modifier.background(Brush.linearGradient(
                listOf(Color(0xFF2E7D32), Color(0xFFF9A825), Color(0xFFC62828), Color(0xFF1565C0)))) else Modifier)
            .keyboardBackground(KeyboardThemes.getRenderingScheme(theme).keyboardBackground, dark, palette.background, config)) {
            CompositionLocalProvider(LocalKeyboardPalette provides palette,
                LocalKeyboardInputPreferences provides KeyboardInputPreferences(frostedGlass = config)) {
                CandidateBar(
                    state = handwriting,
                    page = com.kingzcheung.xime.keyboard.KeyboardPage.Main(com.kingzcheung.xime.keyboard.MainType.HANDWRITING),
                    toolbarActions = toolbarActions(),
                    visuals = CandidateBarVisuals(Color.Transparent, palette.candidateText,
                        if (dark) Color(0xFF3C4043) else Color(0xFFDADCE0), palette.accent, palette.selectedText, dark, palette.key),
                    callbacks = CandidateBarCallbacks(onCandidateSelect = {}, onCancelInput = {},
                        onClearAssociation = {}, onHideKeyboard = {}, onBack = {}, onLogoClick = {}),
                    showPreeditPreview = false,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    private fun shown(text: String) = rule.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().size

    @Test fun readingsShowBesideHandwritingCandidates() {
        SettingsPreferences.setCandidateCommentMode(context(), CandidateCommentMode.SHOW)
        KeyboardThemes.initFromConfig(context())
        rule.setContent { Bar() }
        rule.waitForIdle()
        assertTrue(shown("dí") >= 1)
        assertEquals(1, shown("xíng/háng"))
        assertEquals(1, shown("de/dì/dí"))
        // 带读音后候选变宽，手机宽度的候选栏里仍放得下前四个（第四个是 滴 dī）
        assertEquals(1, shown("滴"))
        assertEquals(1, shown("dī"))
    }

    @Test fun hiddenCommentsHideHandwritingReadings() {
        SettingsPreferences.setCandidateCommentMode(context(), CandidateCommentMode.HIDE)
        KeyboardThemes.initFromConfig(context())
        rule.setContent { Bar() }
        rule.waitForIdle()
        assertEquals(1, shown("嫡"))
        assertEquals(0, shown("dí"))
        assertEquals(0, shown("xíng/háng"))
    }

    @Test fun renderHandwritingPinyin() {
        val out = System.getenv("CYIME_RENDER_OUT")
        org.junit.Assume.assumeTrue(!out.isNullOrEmpty())
        SettingsPreferences.setCandidateCommentMode(context(), CandidateCommentMode.SHOW)
        KeyboardThemes.initFromConfig(context())
        val themes = listOf(Triple("soft_blue", false, "柔和蓝·浅"), Triple("soft_blue", true, "柔和蓝·深"),
            Triple("pure_black", false, "黑白·纯白"), Triple("pure_black", true, "黑白·纯黑"))
        rule.setContent {
            Column(Modifier.background(Color(0xFF808080)).padding(4.dp)) {
                for (glass in listOf(false, true)) for ((id, dark, label) in themes) {
                    Row(Modifier.padding(vertical = 2.dp)) {
                        Text("$label ${if (glass) "玻璃" else "不透明"}", fontSize = 10.sp, color = Color.White,
                            modifier = Modifier.width(120.dp))
                        Bar(id, dark, glass)
                    }
                }
            }
        }
        rule.waitForIdle()
        // Robolectric 下 captureToImage 等不到 PixelCopy；直接把整个窗口画到位图上。
        val root = rule.activity.window.decorView
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        rule.runOnIdle { root.draw(android.graphics.Canvas(bitmap)) }
        File(out!!).mkdirs()
        File(out, "handwriting-pinyin.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
