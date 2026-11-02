package com.kingzcheung.xime.clipboard.sync

import com.kingzcheung.xime.plugin.core.api.ClipboardProfile
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Three devices pushing to each other (用户 11-02 选的做法 A). */
class ClipboardMeshTest {
    private val computer = WindowsPeer("computer", "电脑", "https://127.0.0.1:18740", "a".repeat(64), "b".repeat(43))
    private val tablet = WindowsPeer("tablet", "平板", "https://127.0.0.2:18741", "c".repeat(64), "d".repeat(43))
    private fun json(text: String, stamp: Long?): JSONObject {
        val profile = ClipboardProfile.fromText(text)
        return JSONObject().put("type", "text").put("text", text).put("hash", profile.hash)
            .put("size", profile.size).put("has_data", false).put("data_name", JSONObject.NULL)
            .also { if (stamp != null) it.put("stamp", stamp) }
    }
    private fun unique(text: String) = "$text ${System.nanoTime()}"

    @Test fun newerItemWinsAndAnOlderOneIsNotWritten() {
        val local = ClipboardProfile.fromText(unique("本机"))
        val held = ClipboardClock.noteLocal(local.hash, 0)
        assertFalse("older", ClipboardClock.accept(ClipboardProfile.fromText(unique("旧")).hash, held - 1))
        assertFalse("same text", ClipboardClock.accept(local.hash, held + 5))
        assertFalse("same text is not marked received", ClipboardClock.isReceived(local.hash))
        val newer = ClipboardProfile.fromText(unique("新"))
        assertTrue(ClipboardClock.accept(newer.hash, held + 10))
        assertTrue("received copies are never forwarded", ClipboardClock.isReceived(newer.hash))
        assertEquals("a received copy keeps the sender's number", held + 10, ClipboardClock.noteLocal(newer.hash, 1))
        val legacy = ClipboardProfile.fromText(unique("旧版电脑"))
        assertTrue("an older CyIME without numbers counts as newest", ClipboardClock.accept(legacy.hash, 0))
    }

    @Test fun catchingUpReadsEveryDeviceAndKeepsTheNewest() = runBlocking {
        ClipboardClock.noteLocal(ClipboardProfile.fromText(unique("手机原有")).hash, 1)
        val fromComputer = unique("电脑的")
        val fromTablet = unique("平板的")
        val now = System.currentTimeMillis()
        val transport = WindowsSecureTransport({ peer, path, body, _, _ ->
            assertEquals("/api/clipboard", path); assertNull(body)
            if (peer.id == computer.id) 200 to json(fromComputer, now + 1_000) else 200 to json(fromTablet, now + 2_000)
        }, { computer }, { listOf(computer, tablet) })
        assertEquals(fromTablet, transport.pull()?.text)
        assertNull("both are now seen and the newest is held", transport.pull())
    }

    @Test fun aCopyGoesToEveryDeviceButAReceivedOneIsNotSentOn() = runBlocking {
        val sent = Collections.synchronizedList(mutableListOf<String>())
        val tabletGot = CountDownLatch(1)
        val transport = WindowsSecureTransport({ peer, _, body, _, _ ->
            if (body != null) { sent += peer.id; if (peer.id == tablet.id) tabletGot.countDown() }
            200 to body
        }, { computer }, { listOf(computer, tablet) })
        val copy = ClipboardProfile.fromText(unique("手机复制"))
        ClipboardClock.noteLocal(copy.hash, 0)
        assertTrue(transport.push(copy))
        assertTrue("the tablet gets it too", tabletGot.await(3, TimeUnit.SECONDS))
        assertEquals(setOf(computer.id, tablet.id), sent.toSet())
        sent.clear()
        val received = ClipboardProfile.fromText(unique("平板推来的"))
        assertTrue(ClipboardClock.accept(received.hash, System.currentTimeMillis() + 60_000))
        assertTrue(transport.push(received))
        Thread.sleep(200)
        assertTrue("nothing is forwarded", sent.isEmpty())
    }
}
