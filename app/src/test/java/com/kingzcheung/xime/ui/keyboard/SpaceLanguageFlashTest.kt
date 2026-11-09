package com.kingzcheung.xime.ui.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class SpaceLanguageFlashTest {
    @Before
    fun reset() = SpaceLanguageFlash.resetForTest()

    @Test
    fun `first keyboard show does not flash`() {
        SpaceLanguageFlash.observe("拼音", 0L)
        assertNull(SpaceLanguageFlash.current)
    }

    @Test
    fun `switching chinese to english flashes the new name then fades`() {
        SpaceLanguageFlash.observe("拼音", 0L)
        SpaceLanguageFlash.observe("英文", 1_000L)
        val flash = SpaceLanguageFlash.current
        assertEquals("英文", flash?.name)
        assertEquals(1f, SpaceLanguageFlash.alpha(flash, "英文", 1_000L), 0f)
        assertEquals(1f, SpaceLanguageFlash.alpha(flash, "英文", 1_000L + SpaceLanguageFlash.HOLD_MS - 1), 0f)
        assertEquals(0.5f, SpaceLanguageFlash.alpha(flash, "英文",
            1_000L + SpaceLanguageFlash.HOLD_MS + SpaceLanguageFlash.FADE_MS / 2), 0.01f)
        assertEquals(0f, SpaceLanguageFlash.alpha(flash, "英文",
            1_000L + SpaceLanguageFlash.HOLD_MS + SpaceLanguageFlash.FADE_MS), 0f)
    }

    @Test
    fun `reopening keyboard in the same language does not flash`() {
        SpaceLanguageFlash.observe("拼音", 0L)
        SpaceLanguageFlash.observe("拼音", 5_000L)
        assertNull(SpaceLanguageFlash.current)
    }

    @Test
    fun `number and symbol keyboards and split right half do not count as a language`() {
        SpaceLanguageFlash.observe("拼音", 0L)
        SpaceLanguageFlash.observe("空格", 100L)
        SpaceLanguageFlash.observe("", 200L)
        SpaceLanguageFlash.observe("拼音", 300L)
        assertNull(SpaceLanguageFlash.current)
    }

    @Test
    fun `a space key showing another name draws nothing`() {
        SpaceLanguageFlash.observe("拼音", 0L)
        SpaceLanguageFlash.observe("英文", 10L)
        assertEquals(0f, SpaceLanguageFlash.alpha(SpaceLanguageFlash.current, "拼音", 10L), 0f)
        assertEquals(0f, SpaceLanguageFlash.alpha(null, "英文", 10L), 0f)
    }

    @Test
    fun `switching back flashes again`() {
        SpaceLanguageFlash.observe("拼音", 0L)
        SpaceLanguageFlash.observe("英文", 10L)
        SpaceLanguageFlash.observe("拼音", 5_000L)
        assertEquals(SpaceLanguageFlash.Flash("拼音", 5_000L), SpaceLanguageFlash.current)
    }
}
