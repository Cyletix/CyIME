package com.kingzcheung.xime.ui.caption

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RevealTest {
    private fun shownAll(text: String) = Reveal().apply { update(text, 0); showAll() }

    @Test fun `new words wait their turn and are written out one by one`() {
        val r = Reveal()
        r.update("今天天气", 1000)
        assertEquals(0, r.shown)
        r.step(1100); r.step(1200)
        r.update("今天天气很好", 1300)
        assertEquals(2, r.shown)
        assertEquals(4, r.backlog)
    }

    @Test fun `punctuation added by the second pass does not hide words already on screen`() {
        val r = shownAll("明天上午我来接你去医院")
        r.update("明天上午我来接你，去医院。", 5000)
        assertEquals(12, r.shown) // up to and including 院
        assertEquals(1, r.backlog) // only 。 still to come
        assertTrue(r.glow(8, 5000) > .9f) // the inserted comma glows
        assertEquals(0f, r.glow(9, 5000)) // 去 is unchanged
    }

    @Test fun `a corrected word stays in place and glows instead of disappearing`() {
        val r = shownAll("我想吃反")
        r.update("我想吃饭了", 2000)
        assertEquals(4, r.shown)
        assertTrue(r.glow(3, 2000) > .9f)
        assertEquals(0f, r.glow(0, 2000))
        assertEquals(1f, r.alpha(3, 2000))
        assertEquals(0f, r.glow(3, 2000 + 700))
    }

    @Test fun `a fully rewritten short line is replaced where it stood`() {
        val r = shownAll("嗯")
        r.update("恩", 100)
        assertEquals(1, r.shown)
    }
}
