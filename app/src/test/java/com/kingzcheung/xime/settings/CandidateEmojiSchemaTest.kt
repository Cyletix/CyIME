package com.kingzcheung.xime.settings

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** 「候选里显示表情」（默认关）：26 键雾凇方案自带表情过滤，九键、14 键方案补上同一个过滤和开关（GitHub #2）。 */
class CandidateEmojiSchemaTest {
    private val chinese = File("src/main/assets/rime_chinese")

    @Test fun nineAndFourteenKeySchemasCarryTheEmojiFilterOffByDefault() {
        for (name in listOf("t9_pinyin", "pinyin_14jian", "double_pinyin_flypy_14jian")) {
            val text = File(chinese, "$name.schema.yaml").readText()
            assertTrue(name, text.contains("  - name: emoji\n    reset: 0\n"))
            // 表情排在繁体转换前面，开着繁体输出时词仍会转成正体，表情原样保留（与雾凇 26 键同序）。
            assertTrue(name, text.contains("    - simplifier@emoji\n    - simplifier@traditionalize\n"))
            assertTrue(name, text.contains("\nemoji:\n  option_name: emoji\n  opencc_config: emoji.json\n"))
        }
    }

    @Test fun rimeIceKeepsItsEmojiFilterAndTable() {
        val root = File("build/generated/chinese-assets/rime_ice")
        assertTrue(File(root, "rime_ice.schema.yaml").readText().contains("    - simplifier@emoji"))
        assertTrue(File(root, "opencc/emoji.json").isFile)
        assertTrue(File(root, "opencc/emoji.txt").isFile)
        assertTrue(File(root, "opencc/others.txt").isFile)
    }
}
