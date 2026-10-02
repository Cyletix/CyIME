package com.kingzcheung.xime.plugin.core.sync

import com.kingzcheung.xime.plugin.core.api.ClipboardProfile
import com.kingzcheung.xime.plugin.core.api.ClipboardSyncPlugin
import com.kingzcheung.xime.plugin.core.model.PluginContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ClipboardSyncSessionTest {
    private class Transport : ClipboardSyncPlugin {
        val sent = mutableListOf<String>()
        val acknowledged = mutableListOf<String>()
        var remote: ClipboardProfile? = ClipboardProfile.fromText("电脑内容")
        var pushAction: suspend () -> Boolean = { true }
        var pullAction: suspend () -> Unit = {}
        var pulls = 0
        override fun onLoad(context: PluginContext) {}
        override fun onUnload() {}
        override suspend fun push(profile: ClipboardProfile): Boolean { sent += profile.text; return pushAction() }
        override suspend fun pull(): ClipboardProfile? { pulls++; pullAction(); return remote }
        override suspend fun acknowledgePull(hash: String) { acknowledged += hash }
        override suspend fun testConnection(): String? = null
    }
    private class Port : SyncClipboardPort {
        var value = SyncClipboardSnapshot("手机原内容", 1)
        var writes = 0
        var reject = false
        var onWrite: () -> Unit = {}
        override fun read() = value
        override fun writeIfUnchanged(expected: SyncClipboardSnapshot, text: String): Boolean {
            if (reject || expected != value) return false
            copy(text); writes++; onWrite(); return true
        }
        fun copy(text: String?, sensitive: Boolean = false) { value = SyncClipboardSnapshot(text, value.revision + 1, sensitive) }
    }
    private class Fixture {
        var clock = 0L
        val transport = Transport()
        val port = Port()
        val errors = mutableListOf<Exception>()
        val session = ClipboardSyncSession(transport, port, { clock }, errors::add, maxTextBytes = 65_536)
        fun copy(text: String?, sensitive: Boolean = false) { port.copy(text, sensitive); session.localChanged(port.value) }
    }

    @Test fun `remote applies once acknowledges and does not echo`() = runBlocking {
        val f = Fixture()
        f.session.pump { true }
        f.session.localChanged(f.port.value)
        f.session.pump { true }
        assertEquals(1, f.port.writes)
        assertTrue(f.transport.sent.isEmpty())
        assertEquals(2, f.transport.acknowledged.size)
    }

    @Test fun `synchronous system clipboard notification also suppresses remote echo`() = runBlocking {
        val f = Fixture()
        f.port.onWrite = { f.session.localChanged(f.port.value) }
        f.session.pump { true }; f.session.pump { false }
        assertEquals(1, f.port.writes)
        assertFalse(f.session.hasPendingPush)
        assertTrue(f.transport.sent.isEmpty())
    }

    @Test fun `offline retries only latest copy after backoff without requiring another user copy`() = runBlocking {
        val f = Fixture()
        f.transport.pushAction = { false }
        f.copy("第一条"); f.session.pump { true }
        f.copy("最新条"); f.session.pump { true }
        assertEquals(listOf("第一条"), f.transport.sent)
        f.clock = 5_000; f.transport.pushAction = { true }
        f.session.pump { false }
        assertEquals(listOf("第一条", "最新条"), f.transport.sent)
        assertFalse(f.session.hasPendingPush)
        assertEquals(0, f.transport.pulls)
    }

    @Test fun `new copy while GET is in flight wins and is subsequently sent`() = runBlocking {
        val f = Fixture(); val started = CompletableDeferred<Unit>(); val finish = CompletableDeferred<Unit>()
        f.transport.pullAction = { started.complete(Unit); finish.await() }
        val request = async { f.session.pump { true } }
        started.await(); f.copy("刚复制的新文字"); finish.complete(Unit); request.await()
        assertEquals("刚复制的新文字", f.port.value.text)
        assertTrue(f.transport.acknowledged.isEmpty())
        f.session.pump { false }
        assertEquals(listOf("刚复制的新文字"), f.transport.sent)
    }

    @Test fun `copy A B A while PUT is in flight retains final A`() = runBlocking {
        val f = Fixture(); f.copy("A"); f.session.pump { false }
        val started = CompletableDeferred<Unit>(); val finish = CompletableDeferred<Unit>()
        f.transport.pushAction = { started.complete(Unit); finish.await(); true }
        f.copy("B"); val request = async { f.session.pump { false } }
        started.await(); f.copy("A"); finish.complete(Unit); request.await()
        f.transport.pushAction = { true }; f.session.pump { false }
        assertEquals(listOf("A", "B", "A"), f.transport.sent)
    }

    @Test fun `changed system clipboard wins even before listener arrives`() = runBlocking {
        val f = Fixture()
        f.transport.pullAction = { f.port.copy(null) }
        f.session.pump { true }
        assertNull(f.port.value.text)
        assertEquals(0, f.port.writes)
        assertTrue(f.transport.acknowledged.isEmpty())
    }

    @Test fun `failed local write is not acknowledged and can be retried`() = runBlocking {
        val f = Fixture(); f.port.reject = true
        f.session.pump { true }; assertTrue(f.transport.acknowledged.isEmpty())
        f.port.reject = false; f.session.pump { true }
        assertEquals(1, f.port.writes); assertEquals(1, f.transport.acknowledged.size)
    }

    @Test fun `hidden keyboard discards in flight pull`() = runBlocking {
        val f = Fixture(); var shown = true
        f.transport.pullAction = { shown = false }
        f.session.pump { shown }
        assertEquals(0, f.port.writes); assertTrue(f.transport.acknowledged.isEmpty())
    }

    @Test fun `disabled session discards in flight result`() = runBlocking {
        val f = Fixture(); f.transport.pullAction = { f.session.close() }
        f.session.pump { true }
        assertEquals(0, f.port.writes); assertTrue(f.transport.acknowledged.isEmpty())
    }

    @Test fun `nontext copy cancels retry and sensitive content is never transmitted or overwritten`() = runBlocking {
        val f = Fixture(); f.transport.pushAction = { false }
        f.copy("old"); f.session.pump { false }
        f.copy(null); f.clock = 5_000; f.session.pump { false }
        f.copy("secret", sensitive = true); f.session.pump { true }
        assertEquals(listOf("old"), f.transport.sent)
        assertEquals(0, f.transport.pulls)
        assertEquals("secret", f.port.value.text)
    }

    @Test fun `invalid hash and unsupported remote cannot touch clipboard`() = runBlocking {
        val f = Fixture()
        listOf(ClipboardProfile.fromText("hi").copy(hash = "bad"),
            ClipboardProfile.fromText("hi").copy(hasData = true), ClipboardProfile.fromText("")).forEach {
            f.transport.remote = it; f.session.pump { true }
        }
        assertEquals(0, f.port.writes); assertEquals(3, f.errors.size)
    }

    @Test fun `cancelled request propagates and does not enter retry logic`() = runBlocking {
        val f = Fixture()
        f.transport.pushAction = { throw kotlinx.coroutines.CancellationException("cancel") }
        f.copy("pending")
        try { f.session.pump { false }; fail("must cancel") } catch (_: kotlinx.coroutines.CancellationException) {}
        assertTrue(f.errors.isEmpty())
    }

    @Test fun `oversized text does not create a network retry loop`() = runBlocking {
        val f = Fixture(); f.copy("中".repeat(30_000)); f.session.pump { false }
        assertTrue(f.transport.sent.isEmpty()); assertFalse(f.session.hasPendingPush)
    }
}
