package com.kingzcheung.xime.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DictContributionRulesTest {
    private fun select(vararg rows: String, own: Set<String> = emptySet()) =
        DictContributionRules.select(rows.asSequence(), own)

    @Test fun keepsCommonWordsWithPinyinAndCount() {
        val result = select(
            "#@/db_name\tpinyin_simp",
            "#@/user_id\t1234-abcd",
            "shang shi gong si \t上市公司\tc=7 d=1.2 t=300",
            "tong hua \t通话\tc=3 d=0.5 t=200",
        )
        assertEquals(listOf(
            DictContributionRules.Word("上市公司", "shang shi gong si", 7),
            DictContributionRules.Word("通话", "tong hua", 3),
        ), result.words)
        assertEquals(0, result.dropped)
    }

    @Test fun dropsSentencesNumbersRareAndOwnWords() {
        val result = select(
            "bang wo na ge kuai di \t帮我拿个快递\tc=9 d=1 t=1",      // 整句
            "yi san ba \t138\tc=20 d=1 t=1",                         // 数字
            "wei xin hao \t微信号abc\tc=5 d=1 t=1",                   // 带字母
            "ni hao \t你好，\tc=5 d=1 t=1",                           // 带标点
            "zhang wei \t张伟\tc=2 d=1 t=1",                         // 只选过两次
            "li ming \t李明\tc=30 d=1 t=1",                          // 自己加的
            "hao \t好\tc=50 d=1 t=1",                                // 单字不算词
            "yi ling \t一〇\tc=9 d=1 t=1",                            // 〇 不算汉字（接收端同样不收）
            "shan diao \t删掉\tc=-4 d=0 t=1",                        // 删掉的词
            own = setOf("李明"),
        )
        assertTrue(result.words.isEmpty())
        assertEquals(1, result.tooLong)
        assertEquals(4, result.notHanzi)
        assertEquals(1, result.tooFew)
        assertEquals(1, result.own)
        assertEquals(7, result.dropped)
    }

    @Test fun capsCountAndListSizeAndSortsByCount() {
        val rows = (0 until DictContributionRules.MAX_WORDS + 5).map { i ->
            val text = "" + (0x5000 + i / 100).toChar() + (0x5000 + i % 100).toChar()
            "a b \t$text\tc=${3 + i} d=1 t=1"
        } + "da ci \t大词\tc=5000 d=1 t=1"
        val result = DictContributionRules.select(rows.asSequence(), emptySet())
        assertEquals(DictContributionRules.MAX_WORDS, result.words.size)
        assertEquals(6, result.overLimit)
        assertEquals(DictContributionRules.Word("大词", "da ci", DictContributionRules.MAX_COUNT), result.words.first())
        assertTrue(result.words.zipWithNext().all { (a, b) -> a.count >= b.count })
    }

    @Test fun nonPinyinCodeKeepsWordWithoutPinyin() {
        val result = select("5464 \t你好\tc=4 d=1 t=1", "hao3 \t号码\tc=4 d=1 t=1")
        assertEquals(listOf("", ""), result.words.map { it.pinyin })
        assertEquals(setOf("你好", "号码"), result.words.map { it.text }.toSet())
    }

    @Test fun sameWordSamePinyinAddsUp() {
        val result = select("hao de \t好的\tc=3 d=1 t=1", "hao de\t好的\tc=4 d=1 t=1")
        assertEquals(listOf(DictContributionRules.Word("好的", "hao de", 7)), result.words)
    }

    @Test fun dropsNameShapedWordsMissingFromTheDictionary() {
        val dictionary = setOf("高兴", "小心", "欧阳修")
        val result = DictContributionRules.select(sequenceOf(
            "wang wei \t王伟\tc=9 d=1 t=1",          // 姓＋名，词库里没有
            "zhang zi xuan \t张子轩\tc=9 d=1 t=1",
            "ou yang ming yue \t欧阳明月\tc=9 d=1 t=1", // 复姓
            "xiao mei \t小美\tc=9 d=1 t=1",           // 小＋字
            "gao xing \t高兴\tc=9 d=1 t=1",           // 姓开头但是词库里的词
            "xiao xin \t小心\tc=9 d=1 t=1",
            "tong hua \t通话\tc=9 d=1 t=1",           // 不像人名，不用查
        ), emptySet()) { it in dictionary }
        assertEquals(listOf("小心", "通话", "高兴"), result.words.map { it.text }.sorted())
        assertEquals(4, result.names)
    }

    @Test fun dropsNumberWords() {
        val result = select("yi san ba \t一三八\tc=9 d=1 t=1", "liang qian \t两千\tc=9 d=1 t=1",
            "yao yao ling \t幺幺零\tc=9 d=1 t=1", "yi qi \t一起\tc=9 d=1 t=1")
        assertEquals(listOf("一起"), result.words.map { it.text })
        assertEquals(3, result.numbers)
    }

    @Test fun nameShapes() {
        assertTrue(DictContributionRules.looksLikeName("李明"))
        assertTrue(DictContributionRules.looksLikeName("司马光明"))
        assertFalse(DictContributionRules.looksLikeName("李明明明"))
        assertFalse(DictContributionRules.looksLikeName("通话"))
        assertFalse(DictContributionRules.looksLikeName("小朋友们"))
    }

    @Test fun fileHasHeaderAndRowsOnly() {
        val text = DictContributionRules.encode(listOf(DictContributionRules.Word("通话", "tong hua", 3)),
            "0123456789abcdef", "pinyin_simp", "1.15.64")
        assertEquals("# cyime-dict-contribute 1\n# contributor\t0123456789abcdef\n# schema\tpinyin_simp\n" +
            "# app\t1.15.64\n通话\ttong hua\t3\n", text)
        assertFalse("user_id" in text)
    }
}
