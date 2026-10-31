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
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * B站反馈（10-31）的候选栏开关，默认都关、关着时样子不变：
 * 「联想时关闭键放右边」——上屏后的联想不在第一个联想词旁放「<」，改在右边放 ⊗；
 * 「候选词间距加宽」——候选之间约空出一个字。
 * 设了 CYIME_RENDER_OUT=<目录> 时出对照图 candidate-feedback-0.png（原样）、-1.png（关闭键放右边）、
 * -2.png（再加宽间距），各主题。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w900dp-h1400dp-hdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CandidateBarFeedbackTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val association = CandidateBarState.AssociationOnly(listOf("题", "卷", "服", "版", "了", "一下"), hasMore = true)
    private val composing = CandidateBarState.ChineseCandidates(listOf("一下", "题", "卷", "服", "版", "了"),
        inputText = "yixia", hasMore = true)

    @Before fun reset() {
        KeyboardThemes.initFromConfig(context)
        SettingsPreferences.getPrefsPublic(context).edit()
            .remove(SettingsPreferences.KEY_SHOW_CANDIDATE_CANCEL_BUTTON)
            .remove(SettingsPreferences.KEY_ASSOCIATION_CANCEL_ON_RIGHT)
            .remove(SettingsPreferences.KEY_WIDE_CANDIDATE_SPACING).commit()
    }

    private fun toolbarActions() = com.kingzcheung.xime.keyboard.ToolbarButton.entries.take(7).map {
        com.kingzcheung.xime.keyboard.ToolbarAction(com.kingzcheung.xime.keyboard.ToolbarButtonItem.Builtin(it)) {}
    }

    @androidx.compose.runtime.Composable
    private fun Bar(state: CandidateBarState, theme: String = "soft_blue", dark: Boolean = false, glass: Boolean = false,
                    onCancel: () -> Unit = {}) {
        val palette = resolveKeyboardPalette(theme, dark)
        val config = FrostedGlassConfig.defaults(dark, enabled = glass)
        Box(Modifier.width(400.dp).height(44.dp)
            .then(if (glass) Modifier.background(Brush.linearGradient(
                listOf(Color(0xFF2E7D32), Color(0xFFF9A825), Color(0xFFC62828), Color(0xFF1565C0)))) else Modifier)
            .keyboardBackground(KeyboardThemes.getRenderingScheme(theme).keyboardBackground, dark, palette.background, config)) {
            CompositionLocalProvider(LocalKeyboardPalette provides palette,
                LocalKeyboardInputPreferences provides KeyboardInputPreferences(frostedGlass = config)) {
                CandidateBar(
                    state = state,
                    toolbarActions = toolbarActions(),
                    visuals = CandidateBarVisuals(Color.Transparent, palette.candidateText,
                        if (dark) Color(0xFF3C4043) else Color(0xFFDADCE0), palette.accent, palette.selectedText, dark, palette.key),
                    callbacks = CandidateBarCallbacks(onCandidateSelect = {}, onCancelInput = onCancel, onShowMoreCandidates = {},
                        onClearAssociation = {}, onHideKeyboard = {}, onBack = {}, onLogoClick = {}, onAssociationSelect = {}),
                    showPreeditPreview = false,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    private fun count(description: String) =
        rule.onAllNodesWithContentDescription(description, useUnmergedTree = true).fetchSemanticsNodes().size
    private fun countTag(tag: String) = rule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().size

    @Test fun associationCancelStaysLeftByDefault() {
        rule.setContent { Bar(association) }
        rule.waitForIdle()
        assertFalse(SettingsPreferences.isAssociationCancelOnRight(context))
        assertEquals(1, count("取消输入"))
        assertEquals(0, countTag("association-cancel-right"))
    }

    @Test fun switchMovesOnlyTheAssociationCancelToTheRight() {
        SettingsPreferences.setAssociationCancelOnRight(context, true)
        var cancelled = 0
        rule.setContent { Column { Bar(association, onCancel = { cancelled++ }); Bar(composing) } }
        rule.waitForIdle()
        // 联想：左边没有「<」，右边一个 ⊗；打字中的「<」照旧在左边。
        assertEquals(1, count("取消输入"))
        assertEquals(1, countTag("association-cancel-right"))
        val close = rule.onNodeWithTag("association-cancel-right", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val first = rule.onNodeWithTag("bar-association:0", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("⊗ 在第一个联想词右边", close.left > first.right)
        val hide = rule.onAllNodesWithTag("toolbar-hide", useUnmergedTree = true).fetchSemanticsNodes().single().boundsInRoot
        assertTrue("收起键盘仍在最右", hide.left >= close.right - 0.5f)
        assertTrue("和打字的「<」不在同一行", close.top < 44f * rule.density.density)
        rule.onNodeWithTag("association-cancel-right", useUnmergedTree = true).performClick()
        assertEquals(1, cancelled)
    }

    @Test fun switchDoesNothingWhenTheCancelButtonIsOff() {
        SettingsPreferences.setShowCandidateCancelButton(context, false)
        SettingsPreferences.setAssociationCancelOnRight(context, true)
        rule.setContent { Bar(association) }
        rule.waitForIdle()
        assertEquals(0, count("取消输入"))
        assertEquals(0, countTag("association-cancel-right"))
    }

    /** 相邻两个候选的起点距离（像素，留白算在候选里）：打字候选和联想各量一次。 */
    private fun gaps(): Pair<Float, Float> {
        fun gap(a: String, b: String): Float {
            val left = rule.onNodeWithTag(a, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val right = rule.onNodeWithTag(b, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            return right.left - left.left
        }
        return gap("bar-candidate:1", "bar-candidate:2") to gap("bar-association:0", "bar-association:1")
    }

    @Test fun wideSpacingIsOffByDefaultAndWidensBothRows() {
        assertFalse(SettingsPreferences.isWideCandidateSpacing(context))
        rule.setContent { Column { Bar(composing); Bar(association) } }
        rule.waitForIdle()
        val (typingBefore, associationBefore) = gaps()
        rule.runOnIdle { SettingsPreferences.setWideCandidateSpacing(context, true) }
        rule.waitForIdle()
        val (typingAfter, associationAfter) = gaps()
        // 默认 19sp：每侧 4dp → 4 + 5.7dp，候选之间多出约 11dp（这里 hdpi，1dp = 1.5px）。
        val extra = 2 * 19f * 0.3f * rule.density.density
        assertEquals(typingBefore + extra, typingAfter, 2f)
        assertEquals(associationBefore + extra, associationAfter, 2f)
    }

    @Test fun renderCandidateFeedback() {
        val out = System.getenv("CYIME_RENDER_OUT")
        org.junit.Assume.assumeTrue(!out.isNullOrEmpty())
        com.kingzcheung.xime.ui.keyboard.AppFonts.initialize(context)
        val themes = listOf(Triple("soft_blue", false, "柔和蓝·浅"), Triple("soft_blue", true, "柔和蓝·深"),
            Triple("pure_black", false, "黑白·纯白"), Triple("pure_black", true, "黑白·纯黑"))
        val variants = listOf("原样" to false, "关闭键放右边" to true, "加宽间距" to true)
        val root = rule.activity.window.decorView
        val shown = androidx.compose.runtime.mutableIntStateOf(-1)
        rule.setContent {
            val index = shown.intValue
            if (index >= 0) androidx.compose.runtime.key(index) {
                Column(Modifier.background(Color(0xFF808080)).padding(4.dp)) {
                    for (glass in listOf(false, true)) for ((id, dark, label) in themes) {
                        for ((name, state) in listOf("联想" to association, "打字" to composing)) {
                            Row(Modifier.padding(vertical = 2.dp)) {
                                Text("${variants[index].first} $label ${if (glass) "玻璃" else "不透明"} $name", fontSize = 9.sp,
                                    color = Color.White, modifier = Modifier.width(150.dp))
                                Bar(state, id, dark, glass)
                            }
                        }
                    }
                }
            }
        }
        File(out!!).mkdirs()
        variants.forEachIndexed { index, (_, right) ->
            rule.runOnIdle {
                SettingsPreferences.setAssociationCancelOnRight(context, right)
                SettingsPreferences.setWideCandidateSpacing(context, index == 2)
                shown.intValue = index
            }
            rule.waitForIdle()
            val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            rule.runOnIdle { root.draw(android.graphics.Canvas(bitmap)) }
            File(out, "candidate-feedback-$index.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
