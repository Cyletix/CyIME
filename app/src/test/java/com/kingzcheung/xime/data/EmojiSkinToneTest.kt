package com.kingzcheung.xime.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmojiSkinToneTest {

    @Test
    fun `手势给原色加五种肤色`() {
        assertEquals(listOf("👋", "👋🏻", "👋🏼", "👋🏽", "👋🏾", "👋🏿"), EmojiSkinTone.variants("👋"))
    }

    @Test
    fun `肤色取代FE0F`() {
        assertEquals("✌🏽", EmojiSkinTone.variants("✌️")[3])
        assertEquals("🏋🏻‍♂️", EmojiSkinTone.variants("🏋️‍♂️")[1])
    }

    @Test
    fun `职业和发型组合加在人身上`() {
        assertEquals("🧑🏾‍⚕️", EmojiSkinTone.variants("🧑‍⚕️")[4])
        assertEquals("👨🏿‍🦰", EmojiSkinTone.variants("👨‍🦰")[5])
        assertEquals("🙋🏼‍♀️", EmojiSkinTone.variants("🙋‍♀️")[2])
    }

    @Test
    fun `情侣和牵手两人同一肤色`() {
        assertEquals("🧑🏻‍🤝‍🧑🏻", EmojiSkinTone.variants("🧑‍🤝‍🧑")[1])
        assertEquals("👩🏽‍❤️‍💋‍👨🏽", EmojiSkinTone.variants("👩‍❤️‍💋‍👨")[3])
        assertEquals("🤝🏻", EmojiSkinTone.variants("🤝")[1])
    }

    @Test
    fun `没有肤色的表情不弹`() {
        listOf("😀", "❤️", "🐶", "🦾", "👪", "👯", "🤼", "👨‍👩‍👧", "🧑‍🧑‍🧒", "👨‍👦", "🧞", "1️⃣", "🇨🇳")
            .forEach { assertFalse(it, EmojiSkinTone.supports(it)) }
    }

    @Test
    fun `最近使用里带肤色的表情还原原色`() {
        assertEquals("👍", EmojiSkinTone.base("👍🏾"))
        assertEquals("✌️", EmojiSkinTone.base("✌🏻"))
        assertEquals("🧑‍🤝‍🧑", EmojiSkinTone.base("🧑🏿‍🤝‍🧑🏿"))
        assertEquals(EmojiSkinTone.variants("👍"), EmojiSkinTone.variants("👍🏾"))
    }

    @Test
    fun `内置表情里手势和人物都能选肤色`() {
        val all = EmojiData.categories.flatMap { it.emojis }
        listOf("👍", "🙏", "💪", "👶", "🧑‍💻", "🏃", "🛀").forEach {
            assertTrue(it, it in all && EmojiSkinTone.supports(it))
        }
        // 每个变体都和原色不同、彼此不同
        all.forEach { emoji ->
            val variants = EmojiSkinTone.variants(emoji)
            if (variants.isNotEmpty()) assertEquals(emoji, 6, variants.toSet().size)
        }
    }
}
