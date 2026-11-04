package com.kingzcheung.xime.service

import com.kingzcheung.xime.settings.InputLanguage
import org.junit.Assert.assertEquals
import org.junit.Test

/** 「数字转阿拉伯数字」：读出来的数字转成阿拉伯数字，单字、约数、成语不动。 */
class VoiceNumbersTest {
    private fun check(vararg pairs: Pair<String, String>) = pairs.forEach { (spoken, written) ->
        assertEquals(spoken, written, spokenNumbersToDigits(spoken))
    }

    @Test fun integersWithUnits() = check(
        "三百二十五块" to "325块", "两千零二十六年" to "2026年", "十五" to "15", "一百零八" to "108",
        "一百一十" to "110", "二十一世纪" to "21世纪", "第二十三届" to "第23届", "两百" to "200",
        "一共十二万三千四百五十六元" to "一共123456元", "我考了九十八分" to "我考了98分",
    )

    @Test fun colloquialTrailingDigitAndRoundWan() = check(
        "三百五" to "350", "一万五" to "15000", "一万五千" to "15000", "三万" to "3万", "十万" to "10万",
        "两万" to "2万", "一千五百万" to "1500万", "一亿五千万" to "1.5亿", "一亿零五" to "100000005",
        "五百多" to "500多", "二十几岁" to "20几岁",
    )

    @Test fun digitByDigitNeedsThreeOrMore() = check(
        "一九九八年" to "1998年", "二〇二六年" to "2026年", "零零七" to "007",
        "电话是一三八一二三四五六七八" to "电话是13812345678", "幺三八零零一三八零零零" to "13800138000",
        "一二" to "一二", "九九" to "九九", "七七八八" to "七七八八",
    )

    @Test fun decimalsPercentAndTime() = check(
        "三点五" to "3.5", "零点零五" to "0.05", "二十点五" to "20.5", "两点五" to "2.5",
        "三点五个百分点" to "3.5个百分点", "九十三点五分" to "93.5分",
        "百分之五十" to "50%", "百分之五" to "5%", "百分之百" to "100%", "百分之零点五" to "0.5%",
        "三点十五" to "3点15", "十二点三十分" to "12点30分", "三点零五分" to "3点05分",
        "一点半" to "1点半", "两点半" to "2点半", "十点钟" to "10点钟", "十一点开会" to "11点开会",
    )

    @Test fun singleCharactersAndApproximationsStay() = check(
        "第一" to "第一", "一个" to "一个", "两个人" to "两个人", "快一点" to "快一点", "有一点儿" to "有一点儿",
        "十分好" to "十分好", "十点" to "十点", "第三点" to "第三点", "三点一刻" to "三点一刻",
        "五六个" to "五六个", "三四百" to "三四百", "两三百" to "两三百", "十一二个" to "十一二个",
        "三十五六岁" to "三十五六岁", "三十两银子" to "三十两银子", "几十个" to "几十个", "打八折" to "打八折",
        "零下五度" to "零下五度",
    )

    @Test fun idiomsStay() = check(
        "一心一意" to "一心一意", "三三两两" to "三三两两", "十全十美" to "十全十美", "千方百计" to "千方百计",
        "万一" to "万一", "千万别去" to "千万别去", "一五一十" to "一五一十", "三七二十一" to "三七二十一",
        "一传十十传百" to "一传十十传百", "九九八十一" to "九九八十一", "五十步笑百步" to "五十步笑百步",
        "一点一滴地" to "一点一滴地", "三点一线" to "三点一线", "八九不离十" to "八九不离十", "十有八九" to "十有八九",
        "三心二意" to "三心二意", "十万八千里" to "十万八千里", "二百五" to "二百五",
    )

    @Test fun unreadableRunsStayWhole() = check("一千十" to "一千十", "一万三百" to "一万三百", "百点" to "百点")

    @Test fun arabicTextIsUntouched() = check("3百" to "3百", "共3个" to "共3个", "0.05" to "0.05", "abc" to "abc")

    @Test fun switchOnlyAppliesToChineseAndKeepsDictatedPunctuation() {
        assertEquals("一共三百二十五块。", normalizeVoiceText("一共三百二十五块句号"))
        assertEquals("一共325块。", normalizeVoiceText("一共三百二十五块句号", arabicNumbers = true))
        assertEquals("一共325块，3.5折。",
            normalizeVoiceText("一共三百二十五块，三点五折。", keepMarks = true, arabicNumbers = true))
        assertEquals("三百", normalizeVoiceText("三百", InputLanguage.ENGLISH, arabicNumbers = true))
    }
}
