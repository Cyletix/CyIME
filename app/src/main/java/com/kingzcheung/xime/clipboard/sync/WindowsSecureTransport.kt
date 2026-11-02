package com.kingzcheung.xime.clipboard.sync

import com.kingzcheung.xime.plugin.core.api.ClipboardProfile
import com.kingzcheung.xime.plugin.core.api.ClipboardSyncPlugin
import com.kingzcheung.xime.plugin.core.model.PluginContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.cert.X509Certificate
import java.security.cert.CertificateException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Trust is scoped to one exact leaf certificate, checked during the TLS handshake.
 *
 * [peer] is the device the session talks to (its errors, pauses and retries are the ones shown).
 * [others] are the rest of the saved devices: a copy is pushed to them too, and catching up reads
 * them too, keeping whichever item is newest ([ClipboardClock]).
 */
class WindowsSecureTransport internal constructor(
    private val exchange: (WindowsPeer, String, JSONObject?, Boolean, String?) -> Pair<Int, JSONObject?>,
    private val peer: () -> WindowsPeer?,
    private val others: () -> List<WindowsPeer> = { emptyList() },
    private val onUnreachable: (WindowsPeer) -> Unit = {},
) : ClipboardSyncPlugin {
    constructor(peer: () -> WindowsPeer?) : this(Companion::request, peer)
    constructor(peer: () -> WindowsPeer?, others: () -> List<WindowsPeer>, onUnreachable: (WindowsPeer) -> Unit) :
        this(Companion::request, peer, others, onUnreachable)
    private class Remote(val profile: ClipboardProfile, val stamp: Long, val seen: () -> Unit)
    /** ETags of the other devices, by pairing. */
    private val etags = ConcurrentHashMap<String, String>()
    /** Other devices whose last push failed every retry; they catch up on their own when back. */
    private val pushFailed = ConcurrentHashMap.newKeySet<String>()
    private val background = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var fanOut: Job? = null
    private fun key(p: WindowsPeer) = p.id + p.fingerprint + p.token
    /** Last status reply of each device, by pairing: whether it pushes, and what it holds for this one. */
    private val statuses = ConcurrentHashMap<String, JSONObject>()
    fun lastStatus(p: WindowsPeer): JSONObject? = statuses[key(p)]
    /** Other saved devices exist, so a copy has somewhere to go even when [peer] does not take it. */
    val hasOthers: Boolean get() = peer()?.id.let { active -> others().any { it.id != active } }
    /** True when the latest copy did not reach [deviceId] (an older CyIME then keeps polling this one). */
    fun pushFailed(deviceId: String): Boolean = deviceId in pushFailed
    private var acceptedHash: String? = null
    private var identity: String? = null
    var sendAllowed: Boolean = true
        private set
    var receiveAllowed: Boolean = true
        private set
    private var permissionIdentity: String? = null
    private fun permissions(p: WindowsPeer, body: JSONObject?) {
        if (body?.optInt("v") != 2) return
        permissionIdentity = p.id + p.fingerprint + p.token
        // Server directions are relative to the computer.
        sendAllowed = body.optBoolean("receive_enabled", true)
        receiveAllowed = body.optBoolean("send_enabled", true)
    }
    class SyncPaused : IllegalStateException("电脑已暂停此方向的剪贴板同步")
    class TransferFailure(val status: Int) : IllegalStateException(when (status) {
        401 -> "电脑已撤销配对，请重新连接"
        403 -> "电脑已暂停此方向的剪贴板同步"
        409 -> "电脑刚复制了新内容，稍后重试"
        413 -> "文字过长，最多同步 64 KiB"
        400, 415 -> "两端剪贴板格式不匹配，请更新 CyIME"
        503 -> "电脑剪贴板正被占用，稍后重试"
        else -> "电脑暂时无法同步（$status）"
    })
    override fun onLoad(context: PluginContext) {}
    override fun onUnload() { acceptedHash = null }
    companion object {
        /** First try, then 30 s, 2 min and 10 min (the CySync waits). */
        private val PUSH_RETRIES = longArrayOf(0, 30_000, 120_000, 600_000)

        fun request(peer: WindowsPeer, path: String, body: JSONObject? = null, token: Boolean = false, etag: String? = null): Pair<Int, JSONObject?> {
            val origin = WindowsPairingProtocol.localOrigin(peer.origin)
            val pin = WindowsPairingProtocol.fingerprint(peer.fingerprint)
            val trust = object : X509TrustManager {
                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
                override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = throw CertificateException("不接受客户端证书")
                override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                    if (chain.isEmpty() || ClipboardProfile.sha256Hex(chain[0].encoded) != pin) throw CertificateException("电脑证书已变化，请重新配对")
                    chain[0].checkValidity()
                }
            }
            // 电脑发放新凭据前要等手头那次剪贴板读写做完，发放即作废旧凭据；配对请求多等一会儿，
            // 免得电脑已经换了凭据、这边却因为超时没收到，旧凭据从此 401。
            val pairing = path.startsWith("/api/pair")
            // 键盘显示时每 2 秒取一次电脑剪贴板：同一台电脑共用一条连接，不再每次重新握手（省电、省流量）。
            // 配对仍用一次性连接，用完即关。
            val client = if (pairing) buildClient(pin, trust, true) else sharedClient(pin, trust)
            val request = Request.Builder().url(origin + path).header("Accept", "application/json")
            if (token) { require(peer.token.matches(Regex("[A-Za-z0-9_-]{43}"))); request.header("Authorization", "Bearer ${peer.token}") }
            if (etag != null) request.header("If-None-Match", "\"$etag\"")
            if (body != null) {
                val payload = body.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
                if (path == "/api/clipboard") request.put(payload) else request.post(payload)
            }
            try {
                return client.newCall(request.build()).execute().use { response ->
                    val source = response.body?.source()
                    val buffer = okio.Buffer()
                    if (source != null) {
                        while (buffer.size <= 262144 && source.read(buffer, minOf(8192, 262145 - buffer.size)) != -1L) { }
                        require(buffer.size <= 262144) { "电脑响应过大" }
                    }
                    val text = buffer.readUtf8()
                    response.code to if (text.isBlank()) null else JSONObject(text)
                }
            } finally { if (pairing) close(client) }
        }

        private val sharedLock = Any()
        private val shared = LinkedHashMap<String, OkHttpClient>()

        /** 同一证书（同一台设备）复用，最多留 8 台的；闲置 30 秒自动断开，键盘收起后不留连接。 */
        private fun sharedClient(pin: String, trust: X509TrustManager): OkHttpClient = synchronized(sharedLock) {
            shared[pin] ?: buildClient(pin, trust, false).also { client ->
                if (shared.size >= 8) shared.keys.first().let { oldest -> shared.remove(oldest)?.let(::close) }
                shared[pin] = client
            }
        }

        private fun buildClient(pin: String, trust: X509TrustManager, pairing: Boolean): OkHttpClient {
            val tls = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trust), null) }
            return OkHttpClient.Builder().proxy(java.net.Proxy.NO_PROXY).sslSocketFactory(tls.socketFactory, trust)
                .hostnameVerifier { _, session ->
                    runCatching { ClipboardProfile.sha256Hex(session.peerCertificates[0].encoded) == pin }.getOrDefault(false)
                }.followRedirects(false).followSslRedirects(false)
                .connectionPool(okhttp3.ConnectionPool(1, 30, TimeUnit.SECONDS))
                .connectTimeout(4, TimeUnit.SECONDS).readTimeout(if (pairing) 20L else 4L, TimeUnit.SECONDS)
                .callTimeout(if (pairing) 25L else 6L, TimeUnit.SECONDS).build()
        }

        private fun close(client: OkHttpClient) { client.connectionPool.evictAll(); client.dispatcher.executorService.shutdown() }
    }
    private fun current(): WindowsPeer = requireNotNull(peer()) { "请先连接电脑" }.also {
        val key = it.id + it.fingerprint + it.token
        if (identity != key) { identity = key; acceptedHash = null }
        if (permissionIdentity != key) { permissionIdentity = key; sendAllowed = true; receiveAllowed = true }
    }
    override suspend fun pull(): ClipboardProfile? = coroutineScope {
        val p = current()
        // 上线报到：其他设备也一起读，谁的最新用谁的；读不到的不算失败。
        val extra = others().filter { it.id != p.id }.map { o ->
            async(Dispatchers.IO) { runCatching { pullOther(o) }.getOrElse { if (it is CancellationException) throw it; null } }
        }
        val primary = pullPrimary(p)
        val remotes = listOfNotNull(primary) + extra.mapNotNull { it.await() }
        remotes.forEach { it.seen() }
        val newest = remotes.maxByOrNull { it.stamp } ?: return@coroutineScope null
        // Older than what this device holds, or the same text: leave the clipboard alone.
        if (!ClipboardClock.accept(newest.profile.hash, newest.stamp)) return@coroutineScope null
        newest.profile
    }

    private suspend fun pullPrimary(p: WindowsPeer): Remote? {
        val etag = acceptedHash
        val (status, json) = withContext(Dispatchers.IO) {
            exchange(p, "/api/clipboard", null, true, etag)
        }
        check(peer() == p) { "连接已更改" }
        if (status == 403 && json?.optBoolean("sync_paused") == true) { permissions(p, json); return null }
        if (status == 204) { acceptedHash = null; return null }
        if (status == 304) return null
        if (status != 200) throw TransferFailure(status)
        val body = requireNotNull(json)
        val profile = WindowsPairingProtocol.profile(body)
        // An older computer sends no number: what it hands out is its newest copy, as before.
        return Remote(profile, WindowsPairingProtocol.stamp(body).takeIf { it > 0 } ?: ClipboardClock.now()) { acceptedHash = profile.hash }
    }
    override suspend fun acknowledgePull(hash: String) { acceptedHash = hash }
    override suspend fun push(profile: ClipboardProfile): Boolean {
        val p = current()
        // 收到的不再转发：每台只推自己复制的，几台之间不会来回推。
        if (ClipboardClock.isReceived(profile.hash)) { acceptedHash = profile.hash; return true }
        val stamp = ClipboardClock.stampOf(profile.hash) ?: ClipboardClock.noteLocal(profile.hash, 0)
        val json = JSONObject().put("type", "text").put("text", profile.text).put("hash", profile.hash)
            .put("size", profile.size).put("has_data", false).put("data_name", JSONObject.NULL).put("source", "CyIME-Android")
            .put("stamp", stamp)
        WindowsPairingProtocol.profile(json)
        pushToOthers(p, json, profile.hash)
        // 电脑关了“接收手机内容”：仍推给其他设备，只是不发给电脑。
        if (!sendAllowed && hasOthers) return true
        val (status, response) = withContext(Dispatchers.IO) {
            exchange(p, "/api/clipboard", json, true, null)
        }
        if (peer() == p && status == 403 && response?.optBoolean("sync_paused") == true) {
            permissions(p, response); throw SyncPaused()
        }
        check(peer() == p) { "连接已更改" }
        if (status != 200) throw TransferFailure(status)
        check(WindowsPairingProtocol.profile(requireNotNull(response)).hash == profile.hash) { "电脑未确认收到相同文字" }
        acceptedHash = profile.hash
        return true
    }
    override suspend fun testConnection(): String? = try {
        val p = requireNotNull(peer()) { "请先连接电脑" }
        val (status, body) = withContext(Dispatchers.IO) { exchange(p, "/api/status", null, true, null) }
        check(peer() == p) { "连接已更改" }
        check(status == 200 && body?.optInt("v") == 2) {
            if (status == 404) "电脑端需要更新，当前版本没有连接状态接口"
            else if (status == 401) "电脑已撤销配对，请重新连接"
            else "电脑暂时无法连接（$status）"
        }
        permissions(p, body)
        body?.let { statuses[key(p)] = it }
        null
    } catch (e: CancellationException) { throw e }
    catch (e: Exception) { e.message ?: "无法连接电脑" }

    /** Status of one of the other devices; null when it cannot be reached. */
    suspend fun status(p: WindowsPeer): JSONObject? = runCatching {
        val (status, body) = withContext(Dispatchers.IO) { exchange(p, "/api/status", null, true, null) }
        body?.takeIf { status == 200 && it.optInt("v") == 2 }?.also { statuses[key(p)] = it }
    }.getOrElse { if (it is CancellationException) throw it; null }

    /** Asks [p] to push to this device from now on (`POST /api/peer`). */
    suspend fun register(p: WindowsPeer, body: JSONObject): Boolean = runCatching {
        withContext(Dispatchers.IO) { exchange(p, "/api/peer", body, true, null) }.first == 200
    }.getOrElse { if (it is CancellationException) throw it; false }

    /**
     * Pushes one copy to the other devices at once. A device that does not answer gets three more
     * tries (30 s, 2 min, 10 min), then nothing until it comes back and catches up by itself.
     */
    private fun pushToOthers(primary: WindowsPeer, json: JSONObject, hash: String) {
        fanOut?.cancel()
        val targets = others().filter { it.id != primary.id }
        if (targets.isEmpty()) return
        fanOut = background.launch {
            for (target in targets) launch {
                for (wait in PUSH_RETRIES) {
                    delay(wait)
                    if (!ClipboardClock.isCurrent(hash)) return@launch // A newer copy replaced it.
                    val status = runCatching { exchange(target, "/api/clipboard", json, true, null).first }
                        .getOrElse { if (it is CancellationException) throw it; -1 }
                    when (status) {
                        200 -> { pushFailed.remove(target.id); etags[key(target)] = hash; return@launch }
                        // Revoked, paused or refused: retrying would not help.
                        400, 401, 403, 413, 415 -> return@launch
                    }
                    if (wait == 0L) onUnreachable(target)
                }
                pushFailed.add(target.id)
            }
        }
    }

    private fun pullOther(o: WindowsPeer): Remote? {
        val k = key(o)
        val (status, json) = exchange(o, "/api/clipboard", null, true, etags[k])
        if (status != 200 || json == null) return null
        val profile = WindowsPairingProtocol.profile(json)
        return Remote(profile, WindowsPairingProtocol.stamp(json).takeIf { it > 0 } ?: ClipboardClock.now()) { etags[k] = profile.hash }
    }

}
