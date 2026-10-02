package com.kingzcheung.xime.plugin.core.sync

import com.kingzcheung.xime.plugin.core.api.ClipboardProfile
import com.kingzcheung.xime.plugin.core.api.ClipboardSyncPlugin
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Clipboard I/O and session state belong to one host dispatcher; transport may suspend on IO. */
data class SyncClipboardSnapshot(val text: String?, val revision: Long, val sensitive: Boolean = false)

interface SyncClipboardPort {
    fun read(): SyncClipboardSnapshot
    /** Recheck immediately before writing. Android cannot provide an OS-wide atomic compare/set. */
    fun writeIfUnchanged(expected: SyncClipboardSnapshot, text: String): Boolean
}

/** One serial pump; at most the latest local copy is retained across network failures. */
class ClipboardSyncSession(
    private val plugin: ClipboardSyncPlugin,
    private val clipboard: SyncClipboardPort,
    private val now: () -> Long,
    private val reportFailure: (Exception) -> Unit = {},
    private val maxTextBytes: Int = Int.MAX_VALUE,
) {
    private var pending: SyncClipboardSnapshot? = null
    private var lastObserved: SyncClipboardSnapshot? = null
    private var selfWritten: SyncClipboardSnapshot? = null
    private var generation = 0L
    private var active = true
    private var retryDelay = 5_000L
    private var retryAt = 0L
    private var pumping = false
    val hasPendingPush: Boolean get() = pending != null

    fun localChanged(snapshot: SyncClipboardSnapshot) {
        if (!active) return
        if (snapshot == lastObserved) return
        lastObserved = snapshot
        generation++
        if (snapshot.sensitive || snapshot.text.isNullOrBlank()) { pending = null; return }
        if (snapshot == selfWritten) { pending = null; return }
        selfWritten = null
        pending = snapshot
        // A new copy replaces stale retries but does not bypass the active backoff.
    }

    fun close() { active = false; pending = null; generation++ }

    suspend fun pump(shouldPull: () -> Boolean) {
        check(!pumping) { "Clipboard sync must have one serial pump" }
        pumping = true
        try {
            if (!active) return
            val copy = pending
            if (copy != null) {
                if (clipboard.read() != copy) { pending = null }
                else {
                    if (now() < retryAt) return
                    val profile = ClipboardProfile.fromText(copy.text!!, "CyIME-Android")
                    if (profile.size > maxTextBytes || profile.text.contains('\u0000')) {
                        pending = null
                        reportFailure(IllegalArgumentException("文本超出同步服务支持的范围"))
                        return
                    }
                    try {
                        check(plugin.push(profile)) { "剪贴板推送未被接收" }
                        currentCoroutineContext().ensureActive()
                        if (!active) return
                        if (pending == copy) pending = null
                        retryAt = 0; retryDelay = 5_000L
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (error: Exception) {
                        retryAt = now() + retryDelay
                        retryDelay = (retryDelay * 2).coerceAtMost(30_000L)
                        reportFailure(error)
                        return
                    }
                }
            }
            if (pending != null || !shouldPull() || !active) return
            val before = clipboard.read()
            if (before.sensitive) return
            val requestGeneration = generation
            try {
                val remote = plugin.pull() ?: return
                currentCoroutineContext().ensureActive()
                if (!active || !shouldPull() || generation != requestGeneration || clipboard.read() != before) return
                val bytes = remote.text.toByteArray(Charsets.UTF_8)
                require(remote.type == "text" && !remote.hasData && remote.dataName == null &&
                    remote.text.isNotBlank() && !remote.text.contains('\u0000') && bytes.size <= maxTextBytes &&
                    remote.hash == ClipboardProfile.sha256Hex(bytes)) { "远端剪贴板校验失败" }
                if (before.text != remote.text) {
                    if (!clipboard.writeIfUnchanged(before, remote.text)) {
                        return
                    }
                    selfWritten = clipboard.read()
                    lastObserved = selfWritten
                    if (pending == selfWritten) pending = null // Also tolerate synchronous host notifications.
                }
                plugin.acknowledgePull(remote.hash)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { reportFailure(error) }
        } finally { pumping = false }
    }
}
