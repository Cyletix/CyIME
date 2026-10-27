package com.kingzcheung.xime.service

import com.kingzcheung.xime.settings.EngineProfile
import com.kingzcheung.xime.settings.InputLanguage
import com.kingzcheung.xime.settings.InputLayout
import com.kingzcheung.xime.settings.InputProfile
import com.kingzcheung.xime.settings.InputScheme
import com.kingzcheung.xime.settings.LayoutKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 跨布局一致性（用户规则：同一件事在 26 键、14 键、九键、自定义布局上行为一样，不一样就是 bug）。
 *
 * 每个布局给同一种组字状态，逐键核对：
 * - 屏上编码 = 九键左栏标的音节 = 空格上屏的高亮候选；
 * - 回车、符号、滑动数字、切语言、开数字面板都上屏屏上那段读音（不加空格、不上屏首选、不交原始键码）；
 * - 只有空格（和点选）选候选；语音、手写前上屏的也是空格会上屏的那个候选。
 * 规则来源：docs/development/key-behavior-audit-20261024.md。
 */
class KeyConsistencyContractTest {
    private data class Layout(
        val name: String,
        val profile: InputProfile,
        val engineInput: String,
        val shown: String,
        val reading: String,
        val candidates: List<String>,
        val comments: List<String>,
    ) {
        val t9 get() = profile.layout.kind == LayoutKind.T9
        val merged get() = usesMergedLetterKeys(profile.layout)
        fun state(focus: Int = 0): CandidateState {
            val base = CandidateState(candidates = candidates, candidateComments = comments, inputText = engineInput,
                preeditText = shown, isComposing = true, engineRevision = 11)
            return if (focus == 0) base else base.copy(candidateFocus = CandidateFocus.at(focus, base))
        }
    }

    private fun profile(scheme: InputScheme, layout: InputLayout, id: String) =
        InputProfile(InputLanguage.CHINESE, scheme, layout, EngineProfile.Rime(id))

    // 自定义布局里有双字母键（bn、ui…）时，布局种类是 MERGED，引擎收到的是键的代表字母。
    private val customMerged = InputLayout("custom_pinyin_0123456789abcdef0123456789abcdef", "我的布局", LayoutKind.MERGED)

    private val layouts = listOf(
        Layout("26 键拼音", profile(InputScheme.PINYIN, InputLayout.QWERTY, "rime_ice"),
            "nihao", "ni hao", "nihao", listOf("你好", "拟好"), listOf("", "")),
        Layout("26 键小鹤", profile(InputScheme.DOUBLE_PINYIN, InputLayout.QWERTY, "double_pinyin_flypy"),
            "nihc", "ni hc", "nihc", listOf("你好", "拟好"), listOf("", "")),
        Layout("14 键拼音", profile(InputScheme.PINYIN, InputLayout.MERGED14, "pinyin_14jian"),
            "BUGAO", "ni'hao", "nihao", listOf("你好", "拟好"), listOf("", "")),
        Layout("14 键小鹤", profile(InputScheme.DOUBLE_PINYIN, InputLayout.MERGED14, "double_pinyin_flypy_14jian"),
            "BUGC", "ni'hc", "nihc", listOf("你好", "拟好"), listOf("", "")),
        Layout("自定义双字母键", profile(InputScheme.PINYIN, customMerged, customMerged.id),
            "bigao", "ni'hao", "nihao", listOf("你好", "拟好"), listOf("", "")),
        Layout("九键", profile(InputScheme.PINYIN, InputLayout.T9, "t9_pinyin"),
            "64426", "ni'hao", "nihao", listOf("你好", "米糕", "蜜柑"), listOf("ni hao", "mi gao", "mi gan")),
    )

    private fun Layout.enter(state: CandidateState = state()) =
        rawCompositionCommitText(engineInput, state.shownPreedit(profile), t9, merged)

    private fun Layout.asTyped(state: CandidateState = state()) = compositionCommitPrefix(true, engineInput,
        state.shownPreedit(profile), t9, merged, "", false, null) { error("$name：符号键不该上屏首选") }

    private fun Layout.space(state: CandidateState = state()) = state.candidates[state.spaceCandidateIndex(profile)]

    @Test fun enterSymbolsDigitsLanguageAndPanelsAllCommitTheReadingShownOnScreen() {
        for (layout in layouts) with(layout) {
            assertEquals("$name 回车", reading, enter())
            // 符号键、滑动数字、切语言、开数字/符号面板都走同一个 asTyped 前缀。
            assertEquals("$name 符号", "$reading，", asTyped() + "，")
            assertEquals("$name 滑动数字", "${reading}2", asTyped() + "2")
            assertEquals("$name 切语言/开面板", enter(), asTyped())
            assertFalse("$name 不加空格", enter().contains(' '))
        }
    }

    @Test fun mergedKeyLayoutsNeverCommitTheKeyCodes() {
        val merged = layouts.filter { it.merged }
        assertEquals(listOf("14 键拼音", "14 键小鹤", "自定义双字母键"), merged.map { it.name })
        for (layout in merged) with(layout) {
            assertFalse("$name 回车上屏了键码 ${enter()}", enter().equals(engineInput, ignoreCase = true))
        }
        // 显示的读音还没出来时退回小写编码，和 14 键一样。
        assertEquals("bigao", rawCompositionCommitText("bigao", "", t9 = false,
            merged14 = usesMergedLetterKeys(customMerged)))
        assertFalse(usesMergedLetterKeys(InputLayout.QWERTY))
        assertFalse(usesMergedLetterKeys(InputLayout.T9))
    }

    @Test fun spaceCommitsTheHighlightedCandidateAndOnlySpacePicksCandidates() {
        for (layout in layouts) with(layout) {
            assertEquals("$name 空格", candidates.first(), space())
            // 语音、手写前：高亮没动时照旧按首选（引擎整句预览），不选别的候选。
            assertNull("$name 开语音", state().modeChangeCandidateIndex(profile))
            assertEquals("$name 开语音", "首选", compositionCommitPrefix(false, engineInput, shown, t9, merged,
                "", false, null) { "首选" })
        }
    }

    @Test fun nineKeyHighlightMovesCompositionColumnEnterSymbolsSpaceAndVoiceTogether() {
        val t9 = layouts.single { it.t9 }
        for (index in t9.candidates.indices) {
            val state = t9.state().let { start -> (0 until index).fold(start) { s, _ -> s.advanceT9Candidate(t9.profile) } }
            assertEquals(index, state.highlightedCandidateIndex)
            val reading = t9.comments[index].replace(" ", "")
            // 屏上编码 = 高亮候选的读音。
            assertEquals(reading, t9.enter(state))
            assertEquals(reading, t9.asTyped(state))
            assertEquals(t9.candidates[index], t9.space(state))
            // 左栏：换了高亮就标高亮候选的第一个音节；没换就不标（照旧）。
            val column = state.highlightedT9Syllable(t9.profile)
            if (index == 0) assertNull(column)
            else assertEquals(t9.comments[index].substringBefore(' '), column)
            if (column != null) assertTrue(state.shownPreedit(t9.profile).startsWith(column))
            // 开语音、手写前上屏的就是空格会上屏的那个。
            assertEquals(if (index == 0) null else index, state.modeChangeCandidateIndex(t9.profile))
        }
    }

    @Test fun otherLayoutsHaveNoHiddenHighlightThatSpaceCouldDisagreeWith() {
        for (layout in layouts.filterNot { it.t9 }) with(layout) {
            val moved = state().advanceT9Candidate(profile)
            assertEquals("$name 没有「下一项」", 0, moved.spaceCandidateIndex(profile))
            assertEquals("$name 编码不变", shown, moved.shownPreedit(profile))
            assertNull(moved.highlightedT9Syllable(profile))
            assertNull(moved.modeChangeCandidateIndex(profile))
        }
    }

    @Test fun idleKeysNeverInventAComposition() {
        for (layout in layouts) with(layout) {
            val idle = CandidateState()
            assertEquals("$name 空闲回车", "", rawCompositionCommitText("", idle.shownPreedit(profile), t9, merged))
            assertEquals("$name 空闲符号", "", compositionCommitPrefix(true, "", "", t9, merged, "", false, null) { "首选" })
            assertNull(idle.modeChangeCandidateIndex(profile))
        }
    }
}
