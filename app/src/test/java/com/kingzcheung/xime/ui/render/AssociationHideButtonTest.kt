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
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kingzcheung.xime.settings.FrostedGlassConfig
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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 上屏后的联想（连续联想会一直接着出）里收起键盘按钮还在，并且和空闲工具栏落在同一格；
 * 打字候选和展开候选时不加（用户反馈 10-30：开了连续联想收起按钮就没了）。
 * 设了 CYIME_RENDER_OUT=<目录> 时另出一张各主题的对照图 association-hide.png。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w900dp-h1400dp-hdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AssociationHideButtonTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private val association = CandidateBarState.AssociationOnly(listOf("的", "了", "吗", "一下", "什么", "怎么样", "是不是", "可以"))
    private val composing = CandidateBarState.ChineseCandidates(listOf("你好", "拟好", "你", "妮"), inputText = "nihao", hasMore = true)

    private fun toolbarActions() = com.kingzcheung.xime.keyboard.ToolbarButton.entries.take(7).map {
        com.kingzcheung.xime.keyboard.ToolbarAction(com.kingzcheung.xime.keyboard.ToolbarButtonItem.Builtin(it)) {}
    }

    @androidx.compose.runtime.Composable
    private fun Bar(state: CandidateBarState, theme: String = "soft_blue", dark: Boolean = false, glass: Boolean = false,
                    expanded: Boolean = false) {
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
                    candidatePageExpanded = expanded,
                    toolbarActions = toolbarActions(),
                    visuals = CandidateBarVisuals(Color.Transparent, palette.candidateText,
                        if (dark) Color(0xFF3C4043) else Color(0xFFDADCE0), palette.accent, palette.selectedText, dark, palette.key),
                    callbacks = CandidateBarCallbacks(onCandidateSelect = {}, onCancelInput = {}, onShowMoreCandidates = {},
                        onClearAssociation = {}, onHideKeyboard = {}, onBack = {}, onLogoClick = {}),
                    showPreeditPreview = false,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    @Test fun associationKeepsHideButtonInTheIdleCell() {
        KeyboardThemes.initFromConfig(ApplicationProvider.getApplicationContext<Context>())
        rule.setContent {
            Column {
                Bar(CandidateBarState.Idle)
                Bar(association)
                Bar(composing)
            }
        }
        rule.waitForIdle()
        val hides = rule.onAllNodesWithTag("toolbar-hide", useUnmergedTree = true).fetchSemanticsNodes()
        // 空闲和联想各一个，打字候选没有。
        assertEquals(2, hides.size)
        val (idle, assoc) = hides.map { it.boundsInRoot }
        assertEquals(idle.left, assoc.left, 0.5f)
        assertEquals(idle.right, assoc.right, 0.5f)
        assertEquals(idle.top + 44f * rule.density.density, assoc.top, 0.5f)
    }

    @Test fun expandedAssociationShowsOnlyTheBackButton() {
        KeyboardThemes.initFromConfig(ApplicationProvider.getApplicationContext<Context>())
        rule.setContent { Bar(association, expanded = true) }
        rule.waitForIdle()
        assertEquals(0, rule.onAllNodesWithTag("toolbar-hide", useUnmergedTree = true).fetchSemanticsNodes().size)
    }

    @Test fun renderAssociationHideButton() {
        val out = System.getenv("CYIME_RENDER_OUT")
        org.junit.Assume.assumeTrue(!out.isNullOrEmpty())
        KeyboardThemes.initFromConfig(ApplicationProvider.getApplicationContext<Context>())
        val themes = listOf(Triple("soft_blue", false, "柔和蓝·浅"), Triple("soft_blue", true, "柔和蓝·深"),
            Triple("pure_black", false, "黑白·纯白"), Triple("pure_black", true, "黑白·纯黑"))
        rule.setContent {
            Column(Modifier.background(Color(0xFF808080)).padding(4.dp)) {
                for (glass in listOf(false, true)) for ((id, dark, label) in themes) {
                    for ((name, state) in listOf("空闲" to CandidateBarState.Idle, "联想" to association, "打字" to composing)) {
                        Row(Modifier.padding(vertical = 2.dp)) {
                            Text("$label ${if (glass) "玻璃" else "不透明"} $name", fontSize = 10.sp, color = Color.White,
                                modifier = Modifier.width(140.dp))
                            Bar(state, id, dark, glass)
                        }
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
        File(out, "association-hide.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
