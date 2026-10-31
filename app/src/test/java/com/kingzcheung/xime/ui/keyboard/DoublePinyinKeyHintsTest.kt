package com.kingzcheung.xime.ui.keyboard

import com.kingzcheung.xime.settings.DoublePinyinScheme
import org.junit.Assert.*
import org.junit.Test

class DoublePinyinKeyHintsTest {
    private val flypy = doublePinyinKeyHints(DoublePinyinScheme.FLYPY)

    @Test fun flypyKeysShowTheirInitialsAndFinals() {
        assertEquals(DoublePinyinKeyHint("zh", listOf("ü", "ui")), flypy['v'])
        assertEquals(DoublePinyinKeyHint("sh", emptyList()), flypy['u'])
        assertEquals(DoublePinyinKeyHint(null, listOf("ong", "iong")), flypy['s'])
        assertEquals(DoublePinyinKeyHint(null, listOf("ue", "üe")), flypy['t'])
        assertEquals(DoublePinyinKeyHint(null, listOf("uo")), flypy['o'])
        // a、e 键只代表自己：键上已经写着，不再写一遍。
        assertNull(flypy['a'])
        assertNull(flypy['e'])
    }

    @Test fun sameContentAsTheKeyMapOnTheSchemePage() {
        for (scheme in DoublePinyinScheme.BUILTINS) {
            val hints = doublePinyinKeyHints(scheme)
            for ((key, labels) in scheme.keyLabels()) {
                val expected = labels.second.map { it.replace('v', 'ü') }.filter { it != key.toString() }
                assertEquals("${scheme.id} $key", labels.first, hints[key]?.initial)
                assertEquals("${scheme.id} $key", expected, hints[key]?.finals.orEmpty())
            }
        }
        assertEquals(listOf("ing"), doublePinyinKeyHints(DoublePinyinScheme.BUILTINS.single { it.usesSemicolon })[';']?.finals)
    }

    @Test fun onlySingleLetterKeysOfEitherCaseGetAHint() {
        assertEquals(flypy['v'], flypy.forKey("V"))
        assertEquals(flypy['v'], flypy.forKey("v"))
        assertNull(flypy.forKey("qw"))
        assertNull(flypy.forKey("，"))
    }

    @Test fun everyLetterKeyHidesCornerLabelsEvenWithoutAHint() {
        // 一条规则：开着键位提示时字母键都不画角标，a、e 没有提示也一样；Z X C V 的编辑角标同样不画。
        for (key in listOf("a", "e", "E", "v", "z", "x", "c")) assertTrue(key, flypy.hidesCornerLabels(key))
        // 功能键、标点、数字照常画。
        for (key in listOf("qw", "，", "1", ";")) assertFalse(key, flypy.hidesCornerLabels(key))
        // 微软双拼的分号键代表 ing，按字母键算。
        assertTrue(doublePinyinKeyHints(DoublePinyinScheme.BUILTINS.single { it.usesSemicolon }).hidesCornerLabels(";"))
    }

    @Test fun finalsFitOnOneLineUnlessTheyWouldShrinkTooMuch() {
        // 手机竖屏一个字母键约 34×46 dp，韵母区占键高的 42%。
        val short = fitDoublePinyinFinals(listOf("uo"), 28f, 46f * 0.42f, 10f, 1f)!!
        assertEquals(listOf("uo"), short.lines)
        assertEquals(10f, short.fontSp, 0.01f)
        val long = fitDoublePinyinFinals(listOf("iang", "uang"), 28f, 46f * 0.42f, 10f, 1f)!!
        assertEquals(listOf("iang", "uang"), long.lines)
        assertTrue(long.fontSp <= 10f && long.heightDp <= 46f * 0.42f + 0.01f)
        // 平板宽键放得下就一行。
        val wide = fitDoublePinyinFinals(listOf("iang", "uang"), 70f, 60f * 0.42f, 10f, 1f)!!
        assertEquals(listOf("iang uang"), wide.lines)
        // 系统字号放大时字号相应变小，仍放得下。
        val scaled = fitDoublePinyinFinals(listOf("ong", "iong"), 28f, 46f * 0.42f, 10f, 1.3f)!!
        assertTrue(scaled.lines.maxOf { it.length } * 0.58f * scaled.fontSp * 1.3f <= 28.01f || scaled.fontSp == 6f)
        assertNull(fitDoublePinyinFinals(emptyList(), 28f, 20f, 10f, 1f))
    }
}
