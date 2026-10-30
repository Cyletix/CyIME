package com.kingzcheung.xime.ui.keyboard

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kingzcheung.xime.settings.ColemakLayout
import com.kingzcheung.xime.settings.Cyletix10Layout
import com.kingzcheung.xime.settings.InputLanguage
import com.kingzcheung.xime.settings.LetterLayouts
import com.kingzcheung.xime.settings.TypewiseLayout
import com.kingzcheung.xime.settings.KeysConfigHelper
import com.kingzcheung.xime.viewmodel.KeyboardUiState
import com.kingzcheung.xime.viewmodel.KeyboardViewModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * 所有字母布局同一条手势规则（用户 10-30：模式太多难免疏忽，统一）：键面右上是上角标、左下是下角标；
 * 默认下拉出上角标、上滑出下角标，打开「反转上下滑」后对调；「长按输入」时长按出上角标。
 * 这里不看配置，只看键面上实际画出来的角标，再真的滑一下，比对上屏的字。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w400dp-h800dp-xhdpi")
class CornerHintGestureTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private val typed = mutableListOf<String>()
    private var preferences by mutableStateOf(KeyboardInputPreferences())
    private var schema by mutableStateOf("rime_ice")
    private var ascii by mutableStateOf(false)

    private data class Board(val schema: String, val english: Boolean = false, val englishArrangement: String? = null)
    /** 每种字母布局：中文 26 键、小鹤双拼 26 键、英文 26 键、Cyletix10/Colemak/蜂窝排列（中英）、14/17/18 键。 */
    private val layouts = listOf(Board("rime_ice"), Board("double_pinyin_flypy"), Board("rime_ice", english = true),
        Board(Cyletix10Layout.ID), Board(ColemakLayout.ID), Board(TypewiseLayout.ID),
        Board("rime_ice", english = true, englishArrangement = Cyletix10Layout.ID),
        Board("pinyin_14jian"), Board("double_pinyin_flypy_14jian"), Board("pinyin_17jian"), Board("pinyin_18jian"))

    private fun show() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        KeysConfigHelper.loadConfig(app)
        com.kingzcheung.xime.ui.theme.KeyboardThemes.initFromConfig(app)
        AppFonts.initialize(app)
        val vm = KeyboardViewModel(app)
        rule.setContent {
            CompositionLocalProvider(LocalKeyboardExplicitWidth provides true, LocalKeyboardInputPreferences provides preferences) {
                MaterialTheme {
                    Box(Modifier.requiredSize(400.dp, 260.dp)) {
                        KeyboardLayout({ typed += it }, vm, KeyboardCallbacks(onKeyPress = { _, _ -> }, onCandidateSelect = {}),
                            KeyboardUiState(currentSchemaId = schema), ascii, Modifier.requiredSize(400.dp, 260.dp))
                    }
                }
            }
        }
        rule.waitForIdle()
    }

    private fun use(board: Board) {
        LetterLayouts.setForTest(InputLanguage.ENGLISH, board.englishArrangement)
        KeysConfigHelper.setActiveKeyboardSchema(board.schema)
        KeysConfigHelper.applyLetterLayouts()
        rule.runOnIdle { schema = board.schema; ascii = board.english }
        rule.waitForIdle()
    }

    @After fun reset() {
        LetterLayouts.setForTest(InputLanguage.ENGLISH, null)
        KeysConfigHelper.setActiveKeyboardSchema("")
        KeysConfigHelper.applyLetterLayouts()
    }

    private fun bounds(tag: String): Rect = rule.onNodeWithTag(tag, true).fetchSemanticsNode().boundsInRoot

    /**
     * 键面上画出来的上角标（比键的中线高）和下角标（比中线低），键名本身不算。
     * 按文字的中心比，蜂窝的六边形键帽比格子高、角标离中线近，不能要求整行字都在中线一侧。
     */
    private fun cornerHints(tag: String, key: String): Pair<String?, String?> {
        val center = bounds(tag).center.y
        val texts = rule.onAllNodes(hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true).fetchSemanticsNodes()
            .mapNotNull { node ->
                node.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }
                    ?.takeIf { it.isNotEmpty() && !it.equals(key, ignoreCase = true) }?.let { it to node.boundsInRoot.center.y }
            }
        val top = texts.filter { (_, y) -> y < center }.map { it.first }
        val bottom = texts.filter { (_, y) -> y > center }.map { it.first }
        assertTrue("$tag 上半最多一个角标：$top", top.size <= 1)
        assertTrue("$tag 下半最多一个角标：$bottom", bottom.size <= 1)
        return top.firstOrNull() to bottom.firstOrNull()
    }

    /** 竖直滑 70dp 松手，返回这一下上屏的字；没上屏返回 null。 */
    private fun swipe(tag: String, pullDown: Boolean): String? {
        typed.clear()
        rule.onNodeWithTag(tag, true).performTouchInput {
            val distance = 70.dp.toPx() * if (pullDown) 1 else -1
            down(center)
            for (step in 1..7) moveTo(center + Offset(0f, distance * step / 7f))
            up()
        }
        rule.waitForIdle()
        return typed.singleOrNull().also { assertTrue("$tag 一次滑动只上屏一个：$typed", typed.size <= 1) }
    }

    private fun letterKeys(english: Boolean) = KeysConfigHelper.getKeyRows(english).flatten()
        .filter { key -> key.isNotEmpty() && key.all { it.lowercaseChar() in 'a'..'z' } }

    private fun checkEveryKey(reverse: Boolean) {
        for (board in layouts) {
            use(board)
            val id = board.toString()
            for (key in letterKeys(board.english)) {
                val tag = "qwerty-key:$key"
                val (top, bottom) = cornerHints(tag, key)
                // 字母布局一律上下两个角标都有：没有下角标就是漏配。
                assertTrue("$id $key 缺上角标", top != null)
                assertTrue("$id $key 缺下角标", bottom != null)
                val pullDown = swipe(tag, pullDown = true)
                val swipeUp = swipe(tag, pullDown = false)
                assertEquals("$id $key 下拉（反转=$reverse）", if (reverse) bottom else top, pullDown)
                assertEquals("$id $key 上滑（反转=$reverse）", if (reverse) top else bottom, swipeUp)
            }
        }
    }

    @Test fun pullDownGivesTheTopHintAndSwipeUpTheBottomHint() {
        show()
        checkEveryKey(reverse = false)
    }

    @Test fun reversedSwipesSwapOnEveryLayout() {
        preferences = KeyboardInputPreferences(reverseSymbolSwipe = true)
        show()
        checkEveryKey(reverse = true)
    }

    @Test fun longPressGivesTheTopHintOnEveryLayout() {
        preferences = KeyboardInputPreferences(symbolInputMode = SymbolInputMode.LONG_PRESS)
        show()
        for (board in layouts) {
            use(board)
            for (key in letterKeys(board.english)) {
                val tag = "qwerty-key:$key"
                val (top, _) = cornerHints(tag, key)
                typed.clear()
                rule.mainClock.autoAdvance = false
                rule.onNodeWithTag(tag, true).performTouchInput { down(center) }
                rule.mainClock.advanceTimeBy(600)
                rule.onNodeWithTag(tag, true).performTouchInput { up() }
                rule.mainClock.advanceTimeBy(100)
                rule.mainClock.autoAdvance = true
                rule.waitForIdle()
                assertEquals("$board $key 长按", listOf(top), typed)
            }
        }
    }
}
