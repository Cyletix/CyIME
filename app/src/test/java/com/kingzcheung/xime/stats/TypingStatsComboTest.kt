package com.kingzcheung.xime.stats

import com.kingzcheung.xime.stats.TypingStatsData.Combo
import com.kingzcheung.xime.stats.TypingStatsData.Source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TypingStatsComboTest {
    private fun combo(source: Source = Source.PINYIN, lang: String = "zh", langName: String = "中文",
        scheme: String = "pinyin", schemeName: String = "拼音", layout: String = "qwerty", layoutName: String = "26键",
        custom: Boolean = false, direct: Boolean = false, hardware: Boolean = false) =
        TypingStatsCombo.of(source, lang, langName, scheme, schemeName, layout, layoutName, custom, direct, hardware)

    @Test
    fun combosSeparateLanguageSchemeAndLayout() {
        assertEquals(TypingStatsCombo("kb/zh/pinyin/qwerty", "中文 · 拼音 · 26键"), combo())
        assertEquals(TypingStatsCombo("kb/zh/pinyin/t9", "中文 · 拼音 · 九键"), combo(Source.T9, layout = "t9", layoutName = "九键"))
        assertEquals("kb/zh/pinyin/qwerty_14", combo(layout = "qwerty_14", layoutName = "14键").id)
        // 九键双拼和 26 键双拼是两种
        assertEquals("kb/zh/double_pinyin/t9", combo(Source.T9, scheme = "double_pinyin", schemeName = "双拼", layout = "t9", layoutName = "九键").id)
        assertEquals("kb/zh/double_pinyin/qwerty", combo(Source.DOUBLE_PINYIN, scheme = "double_pinyin", schemeName = "双拼").id)
        // 各语言自己的 26 键合成一种；英文、西班牙语不写方案
        assertEquals(TypingStatsCombo("kb/en/direct/qwerty", "英文 · 26键"),
            combo(Source.ENGLISH, "en", "英文", "direct", "直接输入", "qwerty_en", direct = true))
        assertEquals("kb/es/direct/qwerty", combo(Source.ENGLISH, "es", "西班牙语", "direct", "直接输入", "qwerty_spanish", direct = true).id)
        // 内置字母布局记名字
        assertEquals(TypingStatsCombo("kb/en/direct/colemak", "英文 · Colemak"),
            combo(Source.ENGLISH, "en", "英文", "direct", "直接输入", "colemak", "Colemak", direct = true))
    }

    @Test
    fun userNamedLayoutsVoiceHandwritingAndHardwareKeyboard() {
        assertEquals(TypingStatsCombo("kb/zh/double_pinyin/custom", "中文 · 双拼 · 自定义布局"),
            combo(scheme = "double_pinyin", schemeName = "双拼", layout = "custom_pinyin_0123", layoutName = "我的秘密布局", custom = true))
        assertEquals(TypingStatsCombo.VOICE, combo(Source.VOICE))
        assertEquals(TypingStatsCombo("hand/zh", "中文 · 手写"), combo(Source.HANDWRITING, layout = "handwriting"))
        assertEquals(TypingStatsCombo("hw/zh/pinyin", "中文 · 拼音 · 实体键盘"), combo(hardware = true))
        assertEquals(TypingStatsCombo("hw/en", "英文 · 实体键盘"), combo(Source.ENGLISH, "en", "英文", "direct", "直接输入", direct = true, hardware = true))
        // id 里只留安全字符，存盘的一行不会被拆坏
        assertEquals("kb/zh/pinyin/a_b_c", combo(layout = "a=b\nc").id)
    }

    @Test
    fun comboCountsRoundTripAndMerge() {
        val data = TypingStatsData()
        val c = data.combo("kb/zh/pinyin/t9", "中文 · 拼音 · 九键")!!
        c.counts[Combo.CHARS] = 500; c.counts[Combo.ACTIVE_MS] = 600_000; c.counts[Combo.ACTIVE_CHARS] = 450
        data.markComboDay(c, 100); data.markComboDay(c, 100); data.markComboDay(c, 101)
        assertEquals(2, c.days)
        val back = TypingStatsData.decode(data.encode())
        assertEquals(data.encode(), back.encode())
        assertEquals("中文 · 拼音 · 九键", back.combos.getValue("kb/zh/pinyin/t9").label)
        assertEquals(45L, back.combos.getValue("kb/zh/pinyin/t9").speed())

        // 读盘前记下的新计数和盘上的旧计数同一天：天数不重复算
        val fresh = TypingStatsData()
        val f = fresh.combo("kb/zh/pinyin/t9", "中文 · 拼音 · 九键")!!
        f.counts[Combo.CHARS] = 20; fresh.markComboDay(f, 101)
        val merged = back.plus(fresh).combos.getValue("kb/zh/pinyin/t9")
        assertEquals(520, merged.chars)
        assertEquals(2, merged.days)
        assertEquals(101, merged.counts[Combo.LAST_DAY])
        val nextDay = TypingStatsData().apply { markComboDay(combo("kb/zh/pinyin/t9", "x")!!, 102) }
        assertEquals(3, back.plus(nextDay).combos.getValue("kb/zh/pinyin/t9").days)
    }

    @Test
    fun oldFilesWithoutCombosStillLoadAndBrokenComboLinesAreSkipped() {
        val back = TypingStatsData.decode("v=1\ntotal=5\nc/kb/zh/pinyin/qwerty=7,x;中文 · 拼音 · 26键\nc/=1;空")
        assertEquals(5, back.total[TypingStatsData.Day.CHARS])
        assertEquals(1, back.combos.size)
        assertEquals(7, back.combos.getValue("kb/zh/pinyin/qwerty").chars)
        assertTrue(TypingStatsData.decode("v=1\ntotal=5").combos.isEmpty())
    }

    @Test
    fun comboKindsAreCapped() {
        val data = TypingStatsData()
        repeat(TypingStatsData.MAX_COMBOS) { assertTrue(data.combo("kb/x/$it", "x") != null) }
        assertNull(data.combo("kb/x/extra", "x"))
        assertTrue(data.combo("kb/x/0", "x") != null)
    }

    @Test
    fun shareTextHasOnlyBuiltInNamesAndNumbers() {
        val data = TypingStatsData()
        data.combo("kb/zh/pinyin/qwerty", "中文 · 拼音 · 26键")!!.apply {
            counts[Combo.CHARS] = 34_000; counts[Combo.ACTIVE_MS] = 60_000; counts[Combo.ACTIVE_CHARS] = 62
            counts[Combo.KEYS] = 300; counts[Combo.DELETES] = 20; counts[Combo.KEY_CHARS] = 100
            counts[Combo.DAYS] = 12
        }
        data.combo("kb/en/direct/qwerty", "英文 · 26键")!!.apply {
            counts[Combo.CHARS] = 2_000; counts[Combo.ACTIVE_MS] = 60_000; counts[Combo.ACTIVE_CHARS] = 180; counts[Combo.DAYS] = 3
        }
        data.combo("voice", "语音")!!.apply { counts[Combo.CHARS] = 900; counts[Combo.DAYS] = 2 }
        data.combo("kb/zh/pinyin/t9", "中文 · 拼音 · 九键")!!.apply { counts[Combo.CHARS] = 5 }
        val text = TypingStatsText.shareText(data, "手机", "1.15.40")
        assertEquals("""
            我的 CyIME 打字速度(手机)
            中文 · 拼音 · 26键：62 字/分，码长 2.8，使用 12 天，3.4 万 字
            英文 · 26键：180 字母/分，使用 3 天，2,000 字
            语音：使用 2 天，900 字
            仅含统计数字，不含输入内容 · CyIME 1.15.40
        """.trimIndent(), text)
        // 不到 100 字的不列，全都不够时照样列
        assertFalse(text.contains("九键"))
        val few = TypingStatsData().apply { combo("kb/zh/pinyin/t9", "中文 · 拼音 · 九键")!!.counts[Combo.CHARS] = 5 }
        assertTrue(TypingStatsText.shareText(few, "平板", "1").contains("中文 · 拼音 · 九键：使用 0 天，5 字"))
    }
}
