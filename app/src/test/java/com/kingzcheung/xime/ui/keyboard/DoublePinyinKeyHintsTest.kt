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

    /** 键面上的上角标：26 键默认（xime.yaml qwerty 段）和「Z X C V 改成撤销/剪切/复制/粘贴」。 */
    private val defaultUp = ("qwertyuiop".toList().zip("1234567890".map(Char::toString)) +
        "asdfghjklzxcvbnm".toList().zip(listOf("～", "／", "：", "；", "“", "”", "－", "（", "）", "＊", "＠", "、", "？", "！", "％", "＃"))).toMap()
    private val editUp = mapOf('z' to "撤销", 'x' to "剪切", 'c' to "复制", 'v' to "粘贴")

    /** 三层互不重叠：按同一套估宽，上层两段、下层两段各自放得下，三层高度加起来不超过键高。 */
    private fun assertNoOverlap(name: String, hint: DoublePinyinKeyHint?, up: String?, down: String?,
                                w: Float, h: Float, fs: Float, l: DoublePinyinCornerLayout) {
        val inner = w - 2 * CORNER_SIDE_PADDING_DP
        val top = (cornerTextEm(hint?.initial) + cornerTextEm(up)) * l.topSp * fs +
            (if (hint?.initial != null && up != null) 3f else 0f)
        assertTrue("$name 上层 $top > $inner", top <= inner + 0.01f)
        assertTrue("$name 三层高度", l.topBandDp + l.letterSp * 1.2f * fs + l.bottomBandDp + 2 * LETTER_CLEARANCE_DP <= h + 0.01f)
        assertTrue("$name 上层行高", 2f + l.topSp * 1.2f * fs <= l.topBandDp + 0.01f)
        val finalsW = l.finals?.lines?.maxOf { cornerTextEm(it) } ?.times(l.finals.fontSp * fs) ?: 0f
        val bottom = cornerTextEm(down) * l.downSp * fs + (if (down != null && l.finals != null) 3f else 0f) + finalsW
        assertTrue("$name 下层 $bottom > $inner", bottom <= inner + 0.01f)
        assertTrue("$name 韵母高", (l.finals?.heightDp ?: 0f) <= l.bottomBandDp + 0.01f)
        if (down != null) assertTrue("$name 下角标高", 2f + l.downSp * 1.2f * fs <= l.bottomBandDp + 0.01f)
        assertTrue("$name 字号", l.topSp >= 6f && l.downSp >= 6f && (l.finals?.fontSp ?: 6f) >= 6f && l.letterSp >= MIN_LETTER_SP)
        assertEquals(l.finals != null && down != null, l.finalsAtEnd)
    }

    @Test fun keyHintsAndCornersShareTheKeyWithoutOverlapOnPhoneAndTablet() {
        // 手机竖屏约 34×46 dp（角标 9sp、字母 18sp），平板横屏约 80×58 dp；系统字号 1.0 和 1.15。
        val sizes = listOf(Triple(34f, 46f, 1f), Triple(34f, 50f, 1.15f), Triple(80f, 58f, 1f), Triple(80f, 58f, 1.15f))
        for (scheme in DoublePinyinScheme.BUILTINS) {
            val hints = doublePinyinKeyHints(scheme)
            for ((w, h, fs) in sizes) for (edit in listOf(false, true)) for (key in ('a'..'z') + ';') {
                if (key == ';' && !scheme.usesSemicolon) continue
                val up = (if (edit) editUp[key] else null) ?: defaultUp[key]
                val name = "${scheme.id} $key ${w}x$h fs=$fs edit=$edit"
                val layout = fitDoublePinyinWithCorners(hints[key]?.initial, hints[key]?.finals.orEmpty(), up, null,
                    w, h, 9f, 18f, fs)
                assertNotNull("$name 放不下", layout)
                assertNoOverlap(name, hints[key], up, null, w, h, fs, layout!!)
                // 平板上字母、角标都是原字号。
                if (w >= 80f && fs == 1f) assertTrue(name, layout.letterSp == 18f && (edit || layout.topSp == 9f))
                // 字母框上下都离开角标层 1dp，按像素取整也不会叠上（10-31 出图测试叠了 1px）。
                val letterTop = h / 2f + layout.letterOffsetDp - layout.letterSp * 1.2f * fs / 2f
                val letterBottom = letterTop + layout.letterSp * 1.2f * fs
                assertTrue("$name 字母离上层", letterTop - layout.topBandDp >= 0.99f)
                assertTrue("$name 字母离下层", h - layout.bottomBandDp - letterBottom >= 0.99f)
            }
        }
    }

    @Test fun downCornersShrinkOrFallBackButNeverOverlap() {
        val flypyKeys = doublePinyinKeyHints(DoublePinyinScheme.FLYPY)
        for (down in listOf("'", "撤", "撤销", "金钅𠂊勺")) for ((w, h) in listOf(34f to 46f, 80f to 58f)) for (key in 'a'..'z') {
            val hint = flypyKeys[key]
            val layout = fitDoublePinyinWithCorners(hint?.initial, hint?.finals.orEmpty(), defaultUp[key], down, w, h, 9f, 18f, 1f)
                ?: continue // 放不下时这个键只画键位提示，不画角标，也不重叠。
            assertNoOverlap("$key $down ${w}x$h", hint, defaultUp[key], down, w, h, 1f, layout)
        }
        // 平板上一个字的下角标都放得下。
        for (key in 'a'..'z') assertNotNull(key.toString(), fitDoublePinyinWithCorners(flypyKeys[key]?.initial,
            flypyKeys[key]?.finals.orEmpty(), defaultUp[key], "撤", 80f, 58f, 9f, 18f, 1f))
        // 太小的键（短边 20dp）放不下就退回。
        assertNull(fitDoublePinyinWithCorners("zh", listOf("iang", "uang"), "粘贴", "撤销", 20f, 24f, 9f, 18f, 1f))
    }
}
