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
    private var boardWidth by mutableStateOf(400)

    private fun show(glide: Boolean = true, chinese: Boolean = true, preview: Boolean = false) {
        val app = ApplicationProvider.getApplicationContext<Application>()
        KeysConfigHelper.loadConfig(app)
        com.kingzcheung.xime.ui.theme.KeyboardThemes.initFromConfig(app)
        AppFonts.initialize(app)
        val vm = KeyboardViewModel(app)
        val preferences = KeyboardInputPreferences(glideTyping = glide, chineseGlideTyping = chinese, glidePreview = preview)
        rule.setContent {
            CompositionLocalProvider(LocalKeyboardExplicitWidth provides true, LocalKeyboardInputPreferences provides preferences) {
                MaterialTheme {
                    Box(Modifier.requiredSize(boardWidth.dp, 260.dp)) {
                        KeyboardLayout({ typed += it }, vm, KeyboardCallbacks(onKeyPress = { _, _ -> }, onCandidateSelect = {},
                            onGlideWord = { words, _ -> codes += "en:${words.first().word}" }, onGlideCode = { codes += it }),
                            KeyboardUiState(currentSchemaId = schema, isAsciiMode = ascii), ascii, Modifier.requiredSize(boardWidth.dp, 260.dp))
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

    @Test fun glidingBoardsKeepCornerHintsButNeverTypeThemOnADrag() {
        show()
        for ((id, english) in listOf("rime_ice" to false, "double_pinyin_flypy" to false, "rime_ice" to true)) {
            use(id, english)
            for (key in listOf("q", "f", "m")) {
                assertTrue("$id english=$english $key 角标照常", hints(key).isNotEmpty())
                assertTrue("$id english=$english $key 滑行时不提交角标", pullDown(key).isEmpty())
            }
        }
    }

    @Test fun cornerHintsStayWithoutGlide() {
        show(glide = false, chinese = false)
        assertTrue(hints("q").isNotEmpty())
        assertEquals(1, pullDown("q").size)
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

    private fun glide(word: String, lift: Boolean = true, pause: Long = 0) {
        val host = rule.onRoot().fetchSemanticsNode().boundsInRoot
        fun letter(char: Char) = rule.onNodeWithTag("qwerty-key:$char", true).fetchSemanticsNode().boundsInRoot.center - host.topLeft
        val path = word.map(::letter)
        rule.onRoot().performTouchInput {
            down(path.first())
            path.zipWithNext().forEach { (a, b) -> for (step in 1..6) moveTo(a + (b - a) * (step / 6f)) }
            if (pause > 0) advanceEventTime(pause)
            if (lift) up()
        }
    }

    private fun previewText(): String? = rule.onAllNodes(hasTestTag("glide-preview"), useUnmergedTree = true).fetchSemanticsNodes()
        .firstOrNull()?.config?.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }

    /** 别的用户 11-02（用惯 Gboard）：开着「滑行时实时显示词」时，手指没抬起就显示现在抬手会出的词，抬手后消失。 */
    @Test fun livePreviewShowsTheWordBeforeLiftingAndGoesAwayAfter() {
        show(glide = false, chinese = true, preview = true)
        glide("nihao", lift = false)
        // 第一次识别在刚确认是滑行时开始（只划过 ni），手指停下后最迟再隔一次识别就跟上整条轨迹。
        rule.waitUntil(5000) { previewText() != null }
        rule.waitUntil(5000) { previewText() == "ni hao" }
        assertTrue("还没抬手，不上屏", codes.isEmpty())
        rule.onRoot().performTouchInput { up() }
        rule.waitUntil(5000) { codes.isNotEmpty() }
        assertEquals(listOf("nihao"), codes.toList())
        rule.waitForIdle()
        assertEquals(null, previewText())
    }

    @Test fun noLivePreviewWhileTheSwitchIsOff() {
        show(glide = false, chinese = true, preview = false)
        glide("nihao")
        rule.waitUntil(5000) { codes.isNotEmpty() }
        assertEquals(null, previewText())
    }

    @Test fun chineseGlideTypesThePinyinOfOneWord() {
        show(glide = false, chinese = true)
        glide("nihao")
        rule.waitUntil(5000) { codes.isNotEmpty() }
        assertEquals(listOf("nihao"), codes.toList())
        assertEquals("滑行不出单个字母", emptyList<String>(), typed.toList())
    }

    @Test fun deShortGlideWorksOnNormalAndOneHandBoardsWithoutTypingACorner() {
        show(glide = false, chinese = true)
        for (width in listOf(400, 240)) {
            rule.runOnIdle { boardWidth = width; typed.clear(); codes.clear() }
            rule.waitForIdle()
            glide("de")
            rule.waitUntil(5000) { codes.isNotEmpty() }
            assertEquals("width=$width", listOf("de"), codes.toList())
            assertEquals("短滑行不额外输入 d 或角标", emptyList<String>(), typed.toList())
        }
    }
}
