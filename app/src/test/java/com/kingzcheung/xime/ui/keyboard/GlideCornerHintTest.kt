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
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
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
import java.util.Collections

/**
 * 滑行输入：英文滑行和中文滑行（全拼、双拼 26 键划一个词的编码）是两个开关，中文默认关；
 * 用户 10-31：滑行开着也照常画上下角标（只听两个角标提示开关），开着英文滑行的老用户中文键盘不能变。
 * 竖着滑仍出角标符号；14 键不滑行。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w400dp-h800dp-xhdpi")
class GlideCornerHintTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private val typed: MutableList<String> = Collections.synchronizedList(mutableListOf())
    private val codes: MutableList<String> = Collections.synchronizedList(mutableListOf())
    private var schema by mutableStateOf("rime_ice")
    private var ascii by mutableStateOf(false)

    private fun show(glide: Boolean = true, chinese: Boolean = true) {
        val app = ApplicationProvider.getApplicationContext<Application>()
        KeysConfigHelper.loadConfig(app)
        com.kingzcheung.xime.ui.theme.KeyboardThemes.initFromConfig(app)
        AppFonts.initialize(app)
        val vm = KeyboardViewModel(app)
        val preferences = KeyboardInputPreferences(glideTyping = glide, chineseGlideTyping = chinese)
        rule.setContent {
            CompositionLocalProvider(LocalKeyboardExplicitWidth provides true, LocalKeyboardInputPreferences provides preferences) {
                MaterialTheme {
                    Box(Modifier.requiredSize(400.dp, 260.dp)) {
                        KeyboardLayout({ typed += it }, vm, KeyboardCallbacks(onKeyPress = { _, _ -> }, onCandidateSelect = {},
                            onGlideWord = { words, _ -> codes += "en:${words.first().word}" }, onGlideCode = { codes += it }),
                            KeyboardUiState(currentSchemaId = schema, isAsciiMode = ascii), ascii, Modifier.requiredSize(400.dp, 260.dp))
                    }
                }
            }
        }
        rule.waitForIdle()
    }

    private fun use(id: String, english: Boolean = false) {
        KeysConfigHelper.setActiveKeyboardSchema(id)
        KeysConfigHelper.applyLetterLayouts()
        rule.runOnIdle { schema = id; ascii = english }
        rule.waitForIdle()
    }

    @After fun reset() {
        KeysConfigHelper.setActiveKeyboardSchema("")
        KeysConfigHelper.applyLetterLayouts()
    }

    /** 键面上除键名以外画出来的字（角标）。 */
    private fun hints(key: String): List<String> {
        val tag = "qwerty-key:$key"
        return rule.onAllNodes(hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true).fetchSemanticsNodes()
            .mapNotNull { node -> node.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text } }
            .filter { it.isNotEmpty() && !it.equals(key, ignoreCase = true) }
    }

    private fun pullDown(key: String): List<String> {
        typed.clear()
        rule.onNodeWithTag("qwerty-key:$key", true).performTouchInput {
            down(center)
            for (step in 1..7) moveTo(center + Offset(0f, 70.dp.toPx() * step / 7f))
            up()
        }
        rule.waitForIdle()
        return typed.toList()
    }

    @Test fun glidingBoardsKeepCornerHintsAndVerticalSwipesStillType() {
        show()
        for ((id, english) in listOf("rime_ice" to false, "double_pinyin_flypy" to false, "rime_ice" to true)) {
            use(id, english)
            for (key in listOf("q", "f", "m")) {
                assertTrue("$id english=$english $key 角标照常", hints(key).isNotEmpty())
                assertEquals("$id english=$english $key 下拉仍出一个符号", 1, pullDown(key).size)
            }
        }
    }

    @Test fun cornerHintsStayWithoutGlide() {
        show(glide = false, chinese = false)
        assertTrue(hints("q").isNotEmpty())
    }

    @Test fun englishGlideAloneLeavesTheChineseBoardAsBefore() {
        show(glide = true, chinese = false)
        glide("nihao")
        rule.waitForIdle()
        Thread.sleep(300)
        rule.waitForIdle()
        assertEquals("中文滑行没开，中文 26 键不滑行", emptyList<String>(), codes.toList())
        assertTrue(hints("q").isNotEmpty())
    }

    @Test fun fourteenKeysDoNotGlideAndKeepTheirHints() {
        show()
        use("pinyin_14jian")
        val first = KeysConfigHelper.getKeyRows(false).flatten().first { key -> key.isNotEmpty() && key.all { it in 'a'..'z' } }
        assertTrue("14 键不滑行，角标照旧", hints(first).isNotEmpty())
    }

    private fun glide(word: String) {
        val host = rule.onRoot().fetchSemanticsNode().boundsInRoot
        fun letter(char: Char) = rule.onNodeWithTag("qwerty-key:$char", true).fetchSemanticsNode().boundsInRoot.center - host.topLeft
        val path = word.map(::letter)
        rule.onRoot().performTouchInput {
            down(path.first())
            path.zipWithNext().forEach { (a, b) -> for (step in 1..6) moveTo(a + (b - a) * (step / 6f)) }
            up()
        }
    }

    @Test fun chineseGlideTypesThePinyinOfOneWord() {
        show(glide = false, chinese = true)
        glide("nihao")
        rule.waitUntil(5000) { codes.isNotEmpty() }
        assertEquals(listOf("nihao"), codes.toList())
        assertEquals("滑行不出单个字母", emptyList<String>(), typed.toList())
    }
}
