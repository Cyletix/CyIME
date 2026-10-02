package com.kingzcheung.xime.clipboard.sync

import android.content.Context
import android.content.ClipboardManager as AndroidClipboardManager
import android.os.SystemClock
import android.util.Log
import com.kingzcheung.xime.clipboard.VerificationCodeSmsReceiver
import com.kingzcheung.xime.clipboard.ClipboardManager
import com.kingzcheung.xime.plugin.core.api.ClipboardSyncPlugin
import com.kingzcheung.xime.plugin.core.sync.ClipboardSyncSession
import com.kingzcheung.xime.plugin.core.sync.SyncClipboardPort
import com.kingzcheung.xime.plugin.core.sync.SyncClipboardSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Main owns clipboard/state; only plugin HTTP runs on IO. No input-command path is involved. */
class ClipboardSyncBridge(
    context: Context,
    private val clipboardManager: ClipboardManager,
    private val plugin: ClipboardSyncPlugin,
    val pluginId: String = "",
    foregroundPollIntervalMs: Long = 0,
    private val maxTextBytes: Int = 0,
) {
    private val systemClipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as AndroidClipboardManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private val pollInterval = if (foregroundPollIntervalMs > 0) foregroundPollIntervalMs.coerceIn(2_000, 60_000) else 0
    private var visible = false
    private var requested = false
    private var worker: Job? = null
    private var session: ClipboardSyncSession? = null
    private var lastPullAt = Long.MIN_VALUE
    private var visibilityGeneration = 0L

    private val port = object : SyncClipboardPort {
        override fun read(): SyncClipboardSnapshot {
            val clip = try { systemClipboard.primaryClip } catch (_: SecurityException) {
                // Switching away from the default IME can revoke clipboard access.
                return SyncClipboardSnapshot(null, 0, sensitive = true)
            }
            val description = clip?.description
            val text = if (clip != null && clip.itemCount > 0) clip.getItemAt(0).text?.toString() else null
            return SyncClipboardSnapshot(text, description?.timestamp ?: 0,
                description?.extras?.getBoolean("android.content.extra.IS_SENSITIVE", false) == true ||
                    description?.label == VerificationCodeSmsReceiver.CLIP_LABEL)
        }
        override fun writeIfUnchanged(expected: SyncClipboardSnapshot, text: String): Boolean {
            if (read() != expected) return false
            clipboardManager.copyToSystemClipboard(text)
            return read().text == text
        }
    }
    private val listener = AndroidClipboardManager.OnPrimaryClipChangedListener {
        // Use the system event, not delayed database history inserts or edits.
        session?.localChanged(port.read())
        wake.trySend(Unit)
    }

    fun start() {
        if (worker != null) return
        session = ClipboardSyncSession(plugin, port, SystemClock::elapsedRealtime, reportFailure = { error ->
            // Do not log clipboard text, URL, credentials or exception payloads.
            Log.w("ClipboardSync", "[$pluginId] sync attempt failed (${error.javaClass.simpleName})")
        }, maxTextBytes = maxTextBytes.takeIf { it > 0 } ?: Int.MAX_VALUE)
        systemClipboard.addPrimaryClipChangedListener(listener)
        worker = scope.launch {
            while (isActive) {
                val now = SystemClock.elapsedRealtime()
                val periodic = visible && pollInterval > 0 && (lastPullAt == Long.MIN_VALUE || now - lastPullAt >= pollInterval)
                val pull = visible && (requested || periodic)
                val requestVisibility = visibilityGeneration
                requested = false
                if (pull) lastPullAt = now
                session?.pump { pull && visible && visibilityGeneration == requestVisibility }
                val wait = when {
                    visible && pollInterval > 0 -> pollInterval
                    session?.hasPendingPush == true -> 5_000L
                    else -> null
                }
                if (wait == null) wake.receive() else withTimeoutOrNull(wait) { wake.receive() }
            }
        }
    }

    fun setKeyboardVisible(shown: Boolean) {
        if (visible == shown) return
        visible = shown
        visibilityGeneration++
        if (shown) {
            val now = SystemClock.elapsedRealtime()
            // Legacy WebDAV services retain their 30s visibility-triggered throttle.
            if (pollInterval > 0 || lastPullAt == Long.MIN_VALUE || now - lastPullAt >= 30_000) requested = true
        }
        wake.trySend(Unit)
    }

    /** Explicit refresh bypasses the automatic visibility throttle. */
    fun pullOnce() { requested = true; wake.trySend(Unit) }

    fun usesPlugin(instance: ClipboardSyncPlugin): Boolean = plugin === instance

    fun stop() {
        session?.close()
        session = null
        worker?.cancel()
        worker = null
        systemClipboard.removePrimaryClipChangedListener(listener)
    }

    fun release() { stop(); scope.cancel() }
}
