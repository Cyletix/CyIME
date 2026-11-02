package com.kingzcheung.xime.clipboard.sync

import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** 隔空输入：只发给回了 type 的电脑；同一句重试用同一个 id；电脑拒绝时不重试。 */
class RemoteTypingSenderTest {
    private val tablet = WindowsPeer("tablet", "平板", "https://192.168.2.103:18741", "c".repeat(64), "d".repeat(43))
    private val computer = WindowsPeer("pc", "DESKTOP-PC", "https://192.168.0.20:18740", "a".repeat(64), "b".repeat(43))
    private val typeStatus = JSONObject().put("v", 2).put("push", true).put("type", true)
    private val phoneStatus = JSONObject().put("v", 2).put("push", true)

    private class Call(val peer: WindowsPeer, val path: String, val body: JSONObject?)

    @Test fun sendsToTheComputerThatTakesTypingNotTheTablet() = runBlocking {
        val calls = mutableListOf<Call>()
        val sender = RemoteTypingSender({ listOf(tablet, computer) },
            { if (it == computer) typeStatus else phoneStatus }, { error("not asked") },
            { p, path, body, token, _ -> assertTrue(token); calls += Call(p, path, body); 200 to JSONObject().put("v", 1).put("typed", true) },
            longArrayOf(0))
        val result = sender.deliver("s1", "你好世界")
        assertTrue(result.typed)
        assertEquals("DESKTOP-PC", result.computer)
        assertEquals(1, calls.size)
        assertEquals(computer, calls[0].peer)
        assertEquals("/api/type", calls[0].path)
        assertEquals(1, calls[0].body!!.getInt("v"))
        assertEquals("s1", calls[0].body!!.getString("id"))
        assertEquals("你好世界", calls[0].body!!.getString("text"))
    }

    @Test fun asksTheSavedDevicesWhenNothingIsKnownYet() = runBlocking {
        val asked = mutableListOf<WindowsPeer>()
        val sender = RemoteTypingSender({ listOf(tablet, computer) }, { null },
            { asked += it; if (it == computer) typeStatus else phoneStatus },
            { _, _, _, _, _ -> 200 to JSONObject().put("v", 1).put("typed", false).put("copied", true) }, longArrayOf(0))
        val result = sender.deliver("s1", "放剪贴板")
        assertEquals(listOf(tablet, computer), asked)
        assertFalse(result.typed)
        assertTrue(result.copied)
    }

    @Test fun noComputerTakesTypingSendsNothing() = runBlocking {
        val sender = RemoteTypingSender({ listOf(tablet) }, { phoneStatus }, { phoneStatus },
            { _, _, _, _, _ -> error("must not send") }, longArrayOf(0))
        val result = sender.deliver("s1", "x")
        assertFalse(result.typed || result.copied)
        assertNotNull(result.error)
    }

    @Test fun aLostConnectionIsRetriedWithTheSameId() = runBlocking {
        val ids = mutableListOf<String>()
        var tries = 0
        val sender = RemoteTypingSender({ listOf(computer) }, { typeStatus }, { typeStatus },
            { _, _, body, _, _ -> ids += body!!.getString("id"); if (++tries < 3) throw IOException("reset") else 200 to JSONObject().put("typed", true) },
            longArrayOf(0, 0, 0))
        assertTrue(sender.deliver("same", "重试").typed)
        assertEquals(listOf("same", "same", "same"), ids)
    }

    @Test fun aRefusalIsNotRetried() = runBlocking {
        var tries = 0
        val sender = RemoteTypingSender({ listOf(computer) }, { typeStatus }, { typeStatus },
            { _, _, _, _, _ -> tries++; 403 to null }, longArrayOf(0, 0, 0))
        val result = sender.deliver("s1", "x")
        assertEquals(1, tries)
        assertFalse(result.typed)
        assertTrue(result.error!!.contains("DESKTOP-PC"))
    }
}
