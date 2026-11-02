package com.kingzcheung.xime.settings

import com.kingzcheung.xime.data.SymbolData
import com.kingzcheung.xime.ui.keyboard.KeyboardInputPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 「希腊字母」（默认关，用户 11-02 转来的请求）：打 xita、pi、alpha 时第 3 个候选给出 θ Θ、π Π、α Α。
 * 一个 Lua 滤镜（app/data/cyime_greek_letters.lua），九键、14 键方案直接带，26 键雾凇和小鹤双拼由 tasks-chinese.gradle.kts 插入。
 */
class GreekLettersSchemaTest {
    private val chinese = File("src/main/assets/rime_chinese")
    private val generated = File("build/generated/chinese-assets/rime_ice")
    private val filter = "    - lua_filter@*cyime_greek_letters\n"

    private fun assertFilterPlaced(name: String, text: String) {
        assertTrue(name, text.split(filter).size == 2)
        // 在调整次序的滤镜（简拼、单音节）之后：第 3 位就是最终位置；繁体转换之前，和符号名同一段。
        assertTrue(name, text.indexOf("    - simplifier@cyime_symbols\n") < text.indexOf(filter))
        assertTrue(name, text.indexOf(filter) < text.indexOf("    - simplifier@traditionalize"))
        // 方案开关里不登记，选项只由设置写，读不到就是关。
        assertFalse(name, text.contains("- name: cyime_greek_letters"))
    }

    @Test fun nineAndFourteenKeySchemasCarryTheFilter() {
        assertFalse(KeyboardInputPreferences().greekLetters)
        for (name in listOf("t9_pinyin", "pinyin_14jian", "double_pinyin_flypy_14jian")) {
            assertFilterPlaced(name, File(chinese, "$name.schema.yaml").readText())
        }
        val gradle = File("build-logic/tasks-chinese.gradle.kts").readText()
        assertTrue(gradle.contains("withGreekLetters(withSymbolNames(withDatetimeHints(it)))"))
    }

    @Test fun fullPinyinAndDoublePinyinGetTheFilterAtBuild() {
        assertTrue(File(generated, "lua/cyime_greek_letters.lua").isFile)
        assertFilterPlaced("rime_ice", File(generated, "rime_ice.schema.yaml").readText())
        assertFilterPlaced("double_pinyin_flypy", File(generated, "double_pinyin_flypy.schema.yaml").readText())
    }

    @Test fun namesCoverTheAskedSpellingsAndSkipCommonOneSyllableReadings() {
        val lua = File("data/cyime_greek_letters.lua").readText()
        val names = Regex("""\{ "(.)", "(.)", "([a-z ]+)" \}""").findAll(lua)
            .flatMap { m -> m.groupValues[3].split(' ').map { it to m.groupValues[1] } }.toList()
        assertEquals("同一个名字只能对应一个字母", names.size, names.map { it.first }.toSet().size)
        val byName = names.toMap()
        assertEquals(24, byName.values.toSet().size)
        assertEquals("θ", byName["xita"])
        assertEquals("π", byName["pi"])
        assertEquals("α", byName["alpha"])
        assertEquals("α", byName["afa"])
        assertEquals("λ", byName["lanmuda"])
        assertEquals("ω", byName["omega"])
        // 派、套、肉、凯 是常打的字：只为一个读音就让它们后面总多出希腊字母不值得。
        for (common in listOf("pai", "tao", "rou", "kai", "niu", "miu")) assertFalse(common, common in byName)
        // 关着时整个滤镜被跳过（tags_match），不进 Lua 循环。
        assertTrue(lua.contains("return { init = init, func = filter, tags_match = tags_match }"))
    }

    @Test fun symbolPanelPutsGreekRightAfterMath() {
        val ids = SymbolData.categories.map { it.id }
        assertEquals(listOf("punctuationSymbols", "englishSymbols", "mathematicalSymbols", "greekAlphabet"), ids.take(4))
    }
}
