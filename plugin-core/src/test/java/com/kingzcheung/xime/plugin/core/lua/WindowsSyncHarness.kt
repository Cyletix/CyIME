package com.kingzcheung.xime.plugin.core.lua

import android.app.Application
import com.kingzcheung.xime.plugin.core.config.PluginConfigStore
import com.kingzcheung.xime.plugin.core.lua.crypto.CryptoHostApi
import com.kingzcheung.xime.plugin.core.lua.http.HttpHostApi
import com.kingzcheung.xime.plugin.core.lua.http.HttpResponse
import com.kingzcheung.xime.plugin.core.lua.sdk.LuaHostApi
import com.kingzcheung.xime.plugin.core.lua.sdk.SimpleJson
import com.kingzcheung.xime.plugin.core.model.PluginContext
import com.kingzcheung.xime.plugin.core.model.PluginInfo
import java.io.File
import java.security.MessageDigest

internal class WindowsSyncHarness(http: HttpHostApi) : AutoCloseable {
    class Store : PluginConfigStore {
        val values = mutableMapOf("serverUrl" to "http://127.0.0.1:18740", "pairingCode" to "test-only-pairing-123456")
        override fun get(key: String) = values[key]
        override fun set(key: String, value: String) { values[key] = value }
        override fun remove(key: String) { values.remove(key) }
        override fun keys() = values.keys.toSet()
    }
    class Http : HttpHostApi {
        data class Request(val method: String, val url: String, val headers: Map<String, String>, val body: ByteArray?)
        val requests = mutableListOf<Request>()
        val responses = ArrayDeque<HttpResponse>()
        var onRequest: () -> Unit = {}
        override fun request(method: String, url: String, headers: Map<String, String>, body: ByteArray?, timeoutMillis: Int?): HttpResponse? {
            requests += Request(method, url, headers, body)
            onRequest()
            return responses.removeFirstOrNull()
        }
        override fun lastError() = "test offline"
    }
    val store = Store()
    private val host = object : LuaHostApi {
        override val sdkVersion = "0.1.0"
        override fun log(message: String) {}
        override fun logError(message: String) {}
        override fun configGet(key: String) = store.get(key)
        override fun configSet(key: String, value: String) = store.set(key, value)
        override fun configRemove(key: String) = store.remove(key)
        override fun configKeys() = store.keys()
        override fun resourcePath(name: String): String? = null
        override fun resourceList(dir: String) = emptyList<String>()
        override fun jsonEncode(obj: Any?) = SimpleJson.encode(obj)
        override fun jsonDecode(json: String) = SimpleJson.decode(json)
        override fun uuid() = "test"
    }
    private val crypto = object : CryptoHostApi {
        override fun sha256(data: ByteArray) = MessageDigest.getInstance("SHA-256").digest(data)
        override fun hex(data: ByteArray) = data.joinToString("") { "%02x".format(it) }
        override fun base64(data: ByteArray) = java.util.Base64.getEncoder().encodeToString(data)
        override fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray = error("unused")
        override fun hmacSha1(key: ByteArray, data: ByteArray): ByteArray = error("unused")
        override fun utcTime(format: String): String = error("unused")
        override fun epochSeconds() = 0L
    }
    val runtime = LuaScriptRuntime("com.cyime.plugin.windows_clipboard", File("../plugins/cyime-windows-sync"),
        "main.lua", store, hostApi = host, httpHostApi = http, cryptoHostApi = crypto)
    val adapter: LuaClipboardSyncPluginAdapter
    init {
        check(runtime.load())
        val info = PluginInfo("com.cyime.plugin.windows_clipboard", "Windows", 0, 1, "0.1.0", "", "", type = "clipboard_sync")
        adapter = LuaClipboardSyncPluginAdapter(runtime, PluginContext(Application(), info, configStore = store))
    }
    override fun close() = runtime.close()
}
