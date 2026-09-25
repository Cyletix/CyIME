package com.kingzcheung.xime.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T9 partial commit 段的回滚语义：count 是**撤销段数**，不是正文字符数。
 *
 * 这些段从未写入宿主正文，回滚只能改输入法内部状态；
 * 历史 bug 把段数当字符数调用 deleteBeforeCursor(count) → 误删正文。
 */
class T9PartialRollbackTest {

    private fun segments(vararg texts: String) =
        texts.map { T9PartialSegment(it, "pinyin-$it") }.toMutableList()

    @Test
    fun `回滚一段只移除最后一段`() {
        val list = segments("里", "故")
        val removed = list.rollbackPartialSegments(1)
        assertEquals(listOf("故"), removed.map { it.text })
        assertEquals(listOf("里"), list.map { it.text })
    }

    @Test
    fun `一个退格撤销多段时按段数移除`() {
        val list = segments("九", "宫", "格")
        val removed = list.rollbackPartialSegments(2)
        assertEquals(listOf("格", "宫"), removed.map { it.text })
        assertEquals(listOf("九"), list.map { it.text })
    }

    @Test
    fun `段数与本地残留不一致时只移除已有段且不抛异常`() {
        val list = segments("里")
        val removed = list.rollbackPartialSegments(3)
        assertEquals(1, removed.size)
        assertTrue(list.isEmpty())
    }

    @Test
    fun `非法或零段数不改动状态`() {
        val list = segments("里")
        assertTrue(list.rollbackPartialSegments(0).isEmpty())
        assertTrue(list.rollbackPartialSegments(-2).isEmpty())
        assertEquals(listOf("里"), list.map { it.text })
        assertTrue(mutableListOf<T9PartialSegment>().rollbackPartialSegments(2).isEmpty())
    }

    @Test
    fun `回滚的段带回拼音供调频回退`() {
        val list = mutableListOf(T9PartialSegment("价格", "ji ge"))
        val removed = list.rollbackPartialSegments(1)
        assertEquals("ji ge", removed.single().pinyin)
        assertEquals("价格", removed.single().text)
    }
}
