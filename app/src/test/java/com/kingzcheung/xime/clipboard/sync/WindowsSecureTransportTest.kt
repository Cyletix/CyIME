package com.kingzcheung.xime.clipboard.sync

import com.kingzcheung.xime.plugin.core.api.ClipboardProfile
import com.kingzcheung.xime.plugin.core.sync.ClipboardSyncSession
import com.kingzcheung.xime.plugin.core.sync.SyncClipboardPort
import com.kingzcheung.xime.plugin.core.sync.SyncClipboardSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class WindowsSecureTransportTest {
    @Test fun transferFailureKeepsSafeStatusWithoutDiscardingThePairing() = runBlocking {
        for (code in listOf(401, 409, 413, 503)) {
            val transport = WindowsSecureTransport({ _, _, _, _, _ -> code to null }) { device }
            for (push in listOf(false, true)) {
                val error = runCatching { if (push) transport.push(ClipboardProfile.fromText("fixture")) else transport.pull() }.exceptionOrNull()
                assertTrue(error is WindowsSecureTransport.TransferFailure)
                assertEquals(code, (error as WindowsSecureTransport.TransferFailure).status)
                assertFalse(error.message.orEmpty().contains("fixture"))
            }
        }
    }
    private val device = WindowsPeer("fixture", "电脑", "https://127.0.0.1:18740", "a".repeat(64), "b".repeat(43))
    private fun json(text: String): JSONObject {
        val profile = ClipboardProfile.fromText(text)
        return JSONObject().put("type", "text").put("text", text).put("hash", profile.hash)
            .put("size", profile.size).put("has_data", false).put("data_name", JSONObject.NULL)
    }

    @Test fun computerDirectionIsMappedToPhoneAndPauseDoesNotPretendUploadSucceeded() = runBlocking {
        var receiveOnComputer = false
        var sendOnComputer = true
        val transport = WindowsSecureTransport({ _, path, _, _, _ ->
            val flags = JSONObject().put("v", 2).put("send_enabled", sendOnComputer)
                .put("receive_enabled", receiveOnComputer).put("sync_paused", true)
            if (path == "/api/status") 200 to flags else 403 to flags
        }) { device }
        assertNull(transport.testConnection())
        assertFalse(transport.sendAllowed)
        assertTrue(transport.receiveAllowed)
        try { transport.push(ClipboardProfile.fromText("不能发送")); fail("forbidden push was acknowledged") }
        catch (_: WindowsSecureTransport.SyncPaused) { }
        sendOnComputer = false
        assertNull(transport.pull())
        assertFalse(transport.receiveAllowed)
        receiveOnComputer = true
        assertNull(transport.testConnection())
        assertTrue(transport.sendAllowed)
        assertFalse(transport.receiveAllowed)
    }

    @Test fun receiveOnlySessionIsNotBlockedByAPhoneCopy() = runBlocking {
        var requests = 0
        var clipboardText = "手机原有内容"
        var revision = 1L
        var writes = 0
        val transport = WindowsSecureTransport({ _, path, body, _, _ ->
            if (path == "/api/status") 200 to JSONObject().put("v", 2).put("send_enabled", true).put("receive_enabled", false)
            else { assertNull("receive-only must not upload", body); requests++; 200 to json("电脑复制的内容") }
        }) { device }
        assertNull(transport.testConnection())
        val port = object : SyncClipboardPort {
            override fun read() = SyncClipboardSnapshot(clipboardText, revision)
            override fun writeIfUnchanged(expected: SyncClipboardSnapshot, text: String): Boolean {
                if (expected != read()) return false
                clipboardText = text; revision++; writes++; return true
            }
        }
        val session = ClipboardSyncSession(transport, port, { 0L }, allowPush = { transport.sendAllowed })
        session.localChanged(port.read())
        session.pump { transport.receiveAllowed }
        assertEquals(1, writes); assertEquals(1, requests)
        assertFalse(session.hasPendingPush)
        assertEquals("电脑复制的内容", clipboardText)
        session.close()
    }

    @Test fun textTransferRunsOffTheClipboardThreadAndResumesOnItsOwner() = runBlocking {
        val owner = Thread.currentThread()
        var clipboardText = "手机 中文𠀀😀\n换行"
        var sequence = 1L
        var delivered: String? = null
        var suppliedTag: String? = null
        val transport = WindowsSecureTransport({ _, path, body, authenticated, etag ->
            assertNotSame("blocking network must leave the clipboard/UI thread", owner, Thread.currentThread())
            assertTrue(authenticated)
            assertEquals("/api/clipboard", path)
            if (body != null) {
                delivered = body.getString("text")
                200 to body
            } else {
                suppliedTag = etag
                200 to json("电脑后续 中文🙂")
            }
        }) { device }
        val port = object : SyncClipboardPort {
            override fun read(): SyncClipboardSnapshot {
                assertSame("clipboard read stays on owner", owner, Thread.currentThread())
                return SyncClipboardSnapshot(clipboardText, sequence)
            }
            override fun writeIfUnchanged(expected: SyncClipboardSnapshot, text: String): Boolean {
                assertSame("clipboard write stays on owner", owner, Thread.currentThread())
                if (read() != expected) return false
                clipboardText = text; sequence++
                return true
            }
        }
        val errors = mutableListOf<Exception>()
        val session = ClipboardSyncSession(transport, port, { 1L }, errors::add, 65536)
        session.localChanged(port.read())
        session.pump { false }
        assertEquals(clipboardText, delivered)
        assertFalse(session.hasPendingPush)
        session.pump { true }
        assertEquals(ClipboardProfile.fromText(delivered!!).hash, suppliedTag)
        assertEquals("电脑后续 中文🙂", clipboardText)
        assertTrue(errors.isEmpty())
        session.close()
    }

    @Test fun changingDeviceWhileTheRequestIsInFlightRejectsItsReply() = runBlocking {
        val peer = AtomicReference(device)
        val entered = CountDownLatch(1)
        val changed = CountDownLatch(1)
        val transport = WindowsSecureTransport({ _, _, _, _, _ ->
            entered.countDown()
            check(changed.await(3, TimeUnit.SECONDS))
            200 to json("旧电脑文本")
        }) { peer.get() }
        val changer = Thread {
            if (entered.await(3, TimeUnit.SECONDS)) peer.set(device.copy(token = "c".repeat(43)))
            changed.countDown()
        }
        changer.start()
        try {
            val result = runCatching { transport.pull() }
            assertTrue("old reply must not be applied to new pairing", result.isFailure)
        } finally { changed.countDown(); changer.join(4000) }
    }

    @Test fun heartbeatDoesNotResetTransferEtagAndCancellationIsNotReportedAsAConnectionFailure() = runBlocking {
        val owner = Thread.currentThread()
        var cancel = false
        var actualTag: String? = null
        val transport = WindowsSecureTransport({ _, path, body, _, etag ->
            assertNotSame(owner, Thread.currentThread())
            if (cancel) throw CancellationException("fixture cancelled")
            when {
                path == "/api/status" -> 200 to JSONObject().put("v", 2)
                body != null -> 200 to body
                else -> { actualTag = etag; 304 to null }
            }
        }) { device }
        val profile = ClipboardProfile.fromText("测试内容")
        assertTrue(transport.push(profile))
        assertNull(transport.testConnection())
        assertNull(transport.pull())
        assertEquals(profile.hash, actualTag)
        cancel = true
        val job = async { transport.testConnection() }
        try { job.await(); fail("cancellation was swallowed") } catch (_: CancellationException) { }
        assertTrue(job.isCancelled)
    }
}
