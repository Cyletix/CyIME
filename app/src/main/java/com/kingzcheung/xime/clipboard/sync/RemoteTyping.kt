package com.kingzcheung.xime.clipboard.sync

import android.os.Handler
import android.os.Looper
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * 隔空输入（用户 2026-11-02 要的，参照微信输入法）：手机说完的一句经剪贴板同步的加密连接发给电脑，
 * 电脑插到当前 CyIME 输入框的光标处（`POST /api/type`，电脑 `/api/status` 带 `type:true` 才发）。
 *
 * 只发给一台电脑；不写剪贴板历史，不走 [ClipboardClock] 和推送，不转给其他手机平板。
 * 按说话顺序一句一句发；同一句重发带同一个 id，电脑不会输两遍。
 */
class RemoteTypingSender internal constructor(
    /** Saved devices to try, the active one first. */
    private val candidates: () -> List<WindowsPeer>,
    /** The last status reply of a device, or null when none yet. */
    private val lastStatus: (WindowsPeer) -> JSONObject?,
    /** Asks a device for its status now (null when unreachable). */
    private val refreshStatus: suspend (WindowsPeer) -> JSONObject?,
    private val exchange: (WindowsPeer, String, JSONObject?, Boolean, String?) -> Pair<Int, JSONObject?>,
    private val retryWaits: LongArray = longArrayOf(0, 1_000, 3_000),
) {
    /** What became of one piece: typed at the cursor, left on the computer's clipboard, or [error]. */
    data class Result(val typed: Boolean, val copied: Boolean, val error: String?, val computer: String?)

    private fun supports(p: WindowsPeer) = lastStatus(p)?.optBoolean("type") == true

    /** The computer that takes 隔空输入, from what is already known (no network). */
    fun knownTarget(): WindowsPeer? = candidates().firstOrNull(::supports)

    private suspend fun target(): WindowsPeer? = knownTarget() ?: run {
        // Not asked since the keyboard process started: ask the saved devices once.
        for (p in candidates()) { if (refreshStatus(p)?.optBoolean("type") == true) return p }
        null
    }

    suspend fun deliver(id: String, text: String): Result {
        var failure: String? = null
        var name: String? = null
        for (wait in retryWaits) {
            if (wait > 0) delay(wait)
            val peer = target() ?: return Result(false, false, failure ?: "没有连上支持隔空输入的电脑（电脑端 CyIME 需开着手机互联并更新到新版）", null)
            name = peer.name
            val body = JSONObject().put("v", 1).put("id", id).put("text", text)
            try {
                val (code, reply) = exchange(peer, "/api/type", body, true, null)
                when (code) {
                    200 -> return Result(reply?.optBoolean("typed") == true, reply?.optBoolean("copied") == true, null, peer.name)
                    401 -> return Result(false, false, "${peer.name} 已撤销配对，请重新连接", peer.name)
                    403 -> return Result(false, false, "${peer.name} 关了「接收手机内容」或暂停了这台手机", peer.name)
                    404 -> return Result(false, false, "${peer.name} 上的 CyIME 版本太旧，不支持隔空输入", peer.name)
                    413 -> return Result(false, false, "这段太长，最多 64 KiB", peer.name)
                    400, 415 -> return Result(false, false, "两端版本不匹配，请更新 CyIME", peer.name)
                    else -> failure = "${peer.name} 暂时无法输入（$code）"
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { failure = "连不上 ${peer.name}" }
        }
        return Result(false, false, failure, name)
    }
}

object RemoteTyping {
    private val sender = RemoteTypingSender(
        candidates = { (listOfNotNull(WindowsDevices.paired.value) + WindowsDevices.saved.value).distinctBy { it.id } },
        lastStatus = { WindowsDevices.transport.lastStatus(it) },
        refreshStatus = { p ->
            if (p.id == WindowsDevices.paired.value?.id) {
                WindowsDevices.transport.testConnection(); WindowsDevices.transport.lastStatus(p)
            } else WindowsDevices.transport.status(p)
        },
        exchange = WindowsSecureTransport.Companion::request,
    )
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val main = Handler(Looper.getMainLooper())
    private class Piece(val id: String, val text: String, val done: (String, RemoteTypingSender.Result) -> Unit)
    private val queue = Channel<Piece>(Channel.UNLIMITED)

    init {
        // One at a time, in the order spoken.
        scope.launch {
            for (piece in queue) {
                val result = runCatching { sender.deliver(piece.id, piece.text) }
                    .getOrElse { if (it is CancellationException) throw it; RemoteTypingSender.Result(false, false, "连不上电脑", null) }
                main.post { piece.done(piece.text, result) }
            }
        }
    }

    /** Any device saved at all; whether one takes 隔空输入 is found out on the first send. */
    fun available(): Boolean = WindowsDevices.saved.value.isNotEmpty()

    /** Name of the computer known to take 隔空输入, for the notice when speaking starts. */
    fun knownComputer(): String? = sender.knownTarget()?.name

    /** Queues [text] for the computer; [done] runs on the main thread. */
    fun send(text: String, done: (String, RemoteTypingSender.Result) -> Unit) {
        if (text.isEmpty()) return
        queue.trySend(Piece(UUID.randomUUID().toString().replace("-", ""), text, done))
    }
}
