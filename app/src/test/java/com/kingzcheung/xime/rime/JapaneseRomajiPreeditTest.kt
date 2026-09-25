package com.kingzcheung.xime.rime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 日语未拼完罗马音的显示判定（纯字符串规则，不需要引擎）。
 *
 * 依据随包方案 `japanese.schema.yaml` 的 preedit_format：末尾是元音即为完整假名；
 * 末尾剩辅音串说明还在等后续元音，此时方案的单字母回显规则
 * （`xform/n/ん/`、`xform/k/っ/` …，方案文件 506-530 行）会把它显示成 っ / ん。
 */
class JapaneseRomajiPreeditTest {

    /** 假读音映射：只用来验证拼接与边界，真读音由引擎给出。 */
    private val reading: (String) -> String = { input ->
        when (input) {
            "ka" -> "か"
            "kaki" -> "かき"
            "KATA" -> "カタ"
            else -> input
        }
    }

    @Test
    fun `以元音结尾的输入已经拼完`() {
        listOf("a", "ka", "kya", "xtsu", "kakina", "KATA", "ka-").forEach {
            assertNull("$it 已是完整假名，不该改写", pendingRomajiTail(it))
        }
    }

    @Test
    fun `末尾辅音串才是要按原字母显示的待拼部分`() {
        assertEquals("k", pendingRomajiTail("k"))
        assertEquals("n", pendingRomajiTail("n"))
        assertEquals("k", pendingRomajiTail("kak"))
        // 多字母声母整体显示：旧实现只把最后一个字母当尾部，屏幕上会出现 っh
        assertEquals("sh", pendingRomajiTail("sh"))
        assertEquals("ky", pendingRomajiTail("ky"))
        assertEquals("ts", pendingRomajiTail("ts"))
        assertEquals("K", pendingRomajiTail("KATAK"))
        // n 后面接辅音时这个 n 已经读成 ん，只剩后面的辅音待拼
        assertEquals("k", pendingRomajiTail("nk"))
        assertEquals("k", pendingRomajiTail("nnk"))
        // 双写辅音的第一个是促音 っ，只有最后一个还在等元音
        assertEquals("k", pendingRomajiTail("kk"))
        // 空格是方案的分隔符：尾部只看分隔符之后的字母
        assertEquals("k", pendingRomajiTail("ka k"))
    }

    @Test
    fun `nn 是撥音，算拼完`() {
        assertNull(pendingRomajiTail("nn"))
        assertNull(pendingRomajiTail("kann"))
        assertNull(pendingRomajiTail("NN"))
        // んか / っか 都已成音；末尾单独的 n 才是待拼（可能在等第二个 n）
        assertNull(pendingRomajiTail("nka"))
        assertNull(pendingRomajiTail("kka"))
        assertEquals("n", pendingRomajiTail("nnn"))
    }

    @Test
    fun `空输入与非字母结尾不改写`() {
        assertNull(pendingRomajiTail(""))
        assertNull(pendingRomajiTail("ka "))
        assertNull(pendingRomajiTail("ka."))
    }

    @Test
    fun `显示串等于已拼完部分读音加原字母尾部`() {
        assertEquals("k", romajiTailDisplay("k", reading))
        assertEquals("かk", romajiTailDisplay("kak", reading))
        assertEquals("かきk", romajiTailDisplay("kakik", reading))
        assertEquals("カタK", romajiTailDisplay("KATAK", reading))
        assertEquals("sh", romajiTailDisplay("sh", reading))
        assertEquals("かsh", romajiTailDisplay("kash", reading))
        assertNull("完整假名不改写", romajiTailDisplay("kann", reading))
        assertNull("以元音结尾不改写", romajiTailDisplay("kakina", reading))
    }

    @Test
    fun `退格与显示同源：未拼完只删一个字母`() {
        assertEquals(0, romajiDeleteStart("k"))
        assertEquals(2, romajiDeleteStart("kak"))
        assertEquals(1, romajiDeleteStart("sh"))
        assertEquals(3, romajiDeleteStart("kash"))
        assertEquals(4, romajiDeleteStart("KATAK"))
    }

    @Test
    fun `退格与显示同源：已拼完成则整体删掉最后一个假名`() {
        assertEquals(0, romajiDeleteStart(""))
        assertEquals(0, romajiDeleteStart("ka"))
        assertEquals(0, romajiDeleteStart("kya"))
        assertEquals(2, romajiDeleteStart("kann"))
        assertEquals(0, romajiDeleteStart("nn"))
        assertEquals(2, romajiDeleteStart("ka-"))
        assertEquals(2, romajiDeleteStart("KATA"))
        // 促音属于上一个假名：kakka 一次只删 ka，留着 かっ
        assertEquals(3, romajiDeleteStart("kakka"))
        assertEquals(1, romajiDeleteStart("kka"))
        assertEquals(3, romajiDeleteStart("kakk"))
    }
}
