package com.kingzcheung.xime.clipboard.sync

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import com.kingzcheung.xime.plugin.SecureValueCipher
import com.kingzcheung.xime.settings.SettingsPreferences
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.util.UUID
import kotlin.coroutines.resume

/** Native discovery and credentials; transport uses the existing clipboard session scheduler. */
object WindowsDevices {
    const val ID = "builtin.windows.secure"
    private lateinit var context: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _nearby = MutableStateFlow<List<WindowsPeer>>(emptyList())
    val nearby = _nearby.asStateFlow()
    private val _status = MutableStateFlow("正在寻找附近电脑")
    val status = _status.asStateFlow()
    private val _paired = MutableStateFlow<WindowsPeer?>(null)
    val paired = _paired.asStateFlow()
    private val _confirm = MutableStateFlow(false)
    val confirm = _confirm.asStateFlow()
    val transport = WindowsSecureTransport { _paired.value }
    private var listener: NsdManager.DiscoveryListener? = null
    private var resolver: Job? = null
    private var pairing: Job? = null
    private var candidate: WindowsPeer? = null
    private val attempted = mutableSetOf<String>()
    private val prefs get() = context.getSharedPreferences("windows_device_pairing", Context.MODE_PRIVATE)

    fun initialize(value: Context) {
        if (::context.isInitialized) return
        context = value.applicationContext
        try {
            prefs.getString("peer", null)?.let { saved ->
                val json = JSONObject(requireNotNull(SecureValueCipher.decrypt(saved)))
                _paired.value = WindowsPeer(json.getString("id"), json.getString("name"),
                    WindowsPairingProtocol.localOrigin(json.getString("origin")),
                    WindowsPairingProtocol.fingerprint(json.getString("fingerprint")), json.getString("token"))
                _status.value = "已配对 ${_paired.value!!.name}，同网自动重连"
            }
        } catch (_: Exception) { _status.value = "配对凭据无法读取，请重新配对；原记录已保留" }
    }

    fun start() {
        if (listener != null) return
        val nsd = context.getSystemService(NsdManager::class.java)
        val queue = Channel<NsdServiceInfo>(32)
        resolver = scope.launch {
            for (service in queue) {
                val result = withTimeoutOrNull(5000) {
                    suspendCancellableCoroutine<NsdServiceInfo?> { continuation ->
                        nsd.resolveService(service, object : NsdManager.ResolveListener {
                            override fun onResolveFailed(info: NsdServiceInfo, code: Int) { if (continuation.isActive) continuation.resume(null) }
                            override fun onServiceResolved(info: NsdServiceInfo) { if (continuation.isActive) continuation.resume(info) }
                        })
                    }
                } ?: continue
                runCatching {
                    fun attr(key: String) = result.attributes[key]?.toString(Charsets.UTF_8).orEmpty()
                    if (attr("v") != "2" || attr("kind") != "windows") return@runCatching
                    val id = UUID.fromString(attr("id")).toString()
                    val address = result.host?.hostAddress ?: return@runCatching
                    val host = if (address.contains(':')) "[$address]" else address
                    val origin = WindowsPairingProtocol.localOrigin("https://$host:${result.port}")
                    val saved = _paired.value
                    if (saved?.id == id) {
                        // Discovery only supplies an address. Keep the previously trusted pin/token.
                        _paired.value = saved.copy(origin = origin)
                        _status.value = "已找到 ${saved.name}，自动重连"
                    }
                    val pin = attr("fp").ifEmpty { saved?.takeIf { it.id == id }?.fingerprint.orEmpty() }
                    val peer = WindowsPeer(id, attr("name").filterNot { it.isISOControl() }.take(64), origin, WindowsPairingProtocol.fingerprint(pin))
                    _nearby.value = (_nearby.value.filterNot { it.id == id } + peer).take(16)
                    if (saved == null && attr("nearby") == "1" && _nearby.value.size == 1 && pairing?.isActive != true && attempted.add(id)) {
                        requestNearby(peer)
                    }
                }
            }
        }
        val discovery = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(type: String) {}
            override fun onDiscoveryStopped(type: String) {}
            override fun onStartDiscoveryFailed(type: String, code: Int) {
                scope.launch { _status.value = "附近设备发现失败（$code），可以扫码连接"; stop() }
            }
            override fun onStopDiscoveryFailed(type: String, code: Int) {}
            override fun onServiceFound(info: NsdServiceInfo) { queue.trySend(info) }
            override fun onServiceLost(info: NsdServiceInfo) {
                val id = info.serviceName.removePrefix("CyIME-").replace("-", "")
                scope.launch { _nearby.value = _nearby.value.filterNot { it.id.replace("-", "") == id } }
            }
        }
        listener = discovery
        try { nsd.discoverServices("_cyime-clip._tcp.", NsdManager.PROTOCOL_DNS_SD, discovery) }
        catch (_: Exception) { _status.value = "无法开始发现，请检查局域网连接"; stop() }
    }

    fun stop() {
        listener?.let { runCatching { context.getSystemService(NsdManager::class.java).stopServiceDiscovery(it) } }
        listener = null; resolver?.cancel(); resolver = null
        pairing?.cancel(); pairing = null; attempted.clear(); _nearby.value = emptyList()
        candidate = null; _confirm.value = false
    }
    private fun clientId(): String = prefs.getString("client", null) ?: UUID.randomUUID().toString().also {
        check(prefs.edit().putString("client", it).commit())
    }
    private fun request(id: String, secret: String) = JSONObject().put("v", 2).put("pairing_id", id)
        .put("secret", secret).put("client_id", clientId()).put("client_name", Build.MODEL.take(64)).put("client_kind", "android")
    private fun received(peer: WindowsPeer, json: JSONObject): WindowsPeer {
        require(json.getInt("v") == 2 && json.getString("server_id") == peer.id)
        UUID.fromString(json.getString("device_id"))
        val token = json.getString("access_token")
        require(token.matches(Regex("[A-Za-z0-9_-]{43}")))
        return peer.copy(token = token)
    }
    fun requestNearby(peer: WindowsPeer) {
        pairing?.cancel(); candidate = null; _confirm.value = false
        pairing = scope.launch {
            val proof = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also { java.security.SecureRandom().nextBytes(it) })
            val code = WindowsPairingProtocol.code(peer.fingerprint, proof)
            val body = request(UUID.randomUUID().toString(), proof)
            _status.value = "${peer.name} · 连接码 $code\n请在电脑确认同一连接码"
            try {
                withTimeout(120000) {
                    while (true) {
                        val (status, json) = withContext(Dispatchers.IO) { WindowsSecureTransport.request(peer, "/api/pair/nearby", body) }
                        if (status == 201) {
                            candidate = received(peer, requireNotNull(json)); _confirm.value = true
                            _status.value = "电脑已允许 · 连接码 $code\n两边代码一致后连接并同步"
                            break
                        }
                        check(status == 202) { "电脑未允许连接（$status），请开启加密连接和附近发现" }
                        delay(8000)
                    }
                }
            } catch (e: TimeoutCancellationException) { _status.value = "连接确认已超时，请重试" }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { _status.value = e.message ?: "无法连接电脑" }
        }
    }
    fun pairQr(text: String) {
        pairing?.cancel(); candidate = null; _confirm.value = false
        pairing = scope.launch {
            try {
                val data = WindowsPairingProtocol.offer(text)
                val endpoints = data.getJSONArray("endpoints")
                var failure: Exception? = null
                for (i in 0 until endpoints.length()) {
                    val peer = WindowsPeer(data.getString("server_id"), data.getString("name").take(64), endpoints.getString(i), data.getString("certificate_sha256"))
                    try {
                        val response = withContext(Dispatchers.IO) { WindowsSecureTransport.request(peer, "/api/pair", request(data.getString("pairing_id"), data.getString("secret"))) }
                        check(response.first == 201) { "二维码过期或配对失败（${response.first}），请刷新电脑二维码" }
                        candidate = received(peer, requireNotNull(response.second))
                        confirmConnection(); return@launch
                    } catch (e: CancellationException) { throw e } catch (e: Exception) { failure = e }
                }
                throw requireNotNull(failure)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _status.value = e.message ?: "二维码无效" }
        }
    }
    fun confirmConnection() {
        val peer = candidate ?: return
        val json = JSONObject().put("id", peer.id).put("name", peer.name).put("origin", peer.origin)
            .put("fingerprint", peer.fingerprint).put("token", peer.token)
        check(prefs.edit().putString("peer", SecureValueCipher.encrypt(json.toString())).commit()) { "配对保存失败" }
        _paired.value = peer; candidate = null; _confirm.value = false
        SettingsPreferences.setClipboardSyncPluginId(context, ID)
        SettingsPreferences.setClipboardSyncEnabled(context, true)
        _status.value = "已连接 ${peer.name}，同网自动重连"
    }
    fun forget() {
        SettingsPreferences.setClipboardSyncEnabled(context, false)
        stop(); _paired.value = null
        prefs.edit().remove("peer").apply()
        _status.value = "已断开，请在电脑设备列表移除此手机以撤销授权"
    }
}
