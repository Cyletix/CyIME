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

/** Trust is scoped to one exact leaf certificate, checked during the TLS handshake. */
class WindowsSecureTransport(private val peer: () -> WindowsPeer?) : ClipboardSyncPlugin {
    private var acceptedHash: String? = null
    private var identity: String? = null
    override fun onLoad(context: PluginContext) {}
    override fun onUnload() { acceptedHash = null }
    companion object {
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
            val tls = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trust), null) }
            val client = OkHttpClient.Builder().sslSocketFactory(tls.socketFactory, trust)
                .hostnameVerifier { _, session ->
                    runCatching { ClipboardProfile.sha256Hex(session.peerCertificates[0].encoded) == pin }.getOrDefault(false)
                }.followRedirects(false).followSslRedirects(false)
                .connectTimeout(4, TimeUnit.SECONDS).readTimeout(4, TimeUnit.SECONDS).callTimeout(6, TimeUnit.SECONDS).build()
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
            } finally { client.connectionPool.evictAll(); client.dispatcher.executorService.shutdown() }
        }
    }
    private fun current(): WindowsPeer = requireNotNull(peer()) { "请先连接电脑" }.also {
        val key = it.id + it.fingerprint + it.token
        if (identity != key) { identity = key; acceptedHash = null }
    }
    override suspend fun pull(): ClipboardProfile? {
        val p = current()
        val (status, json) = request(p, "/api/clipboard", token = true, etag = acceptedHash)
        check(peer() == p) { "连接已更改" }
        if (status == 204) { acceptedHash = null; return null }
        if (status == 304) return null
        check(status == 200) { "电脑同步失败（$status），401 表示配对已撤销" }
        return WindowsPairingProtocol.profile(requireNotNull(json))
    }
    override suspend fun acknowledgePull(hash: String) { acceptedHash = hash }
    override suspend fun push(profile: ClipboardProfile): Boolean {
        val p = current()
        val json = JSONObject().put("type", "text").put("text", profile.text).put("hash", profile.hash)
            .put("size", profile.size).put("has_data", false).put("data_name", JSONObject.NULL).put("source", "CyIME-Android")
        WindowsPairingProtocol.profile(json)
        val (status, response) = request(p, "/api/clipboard", json, token = true)
        check(peer() == p && status == 200 && WindowsPairingProtocol.profile(requireNotNull(response)).hash == profile.hash)
        acceptedHash = profile.hash
        return true
    }
    override suspend fun testConnection(): String? = try { pull(); null } catch (e: Exception) { e.message ?: "无法连接电脑" }
}
