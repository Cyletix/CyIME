package com.kingzcheung.xime.clipboard.sync

import android.content.Context
import android.util.Log
import com.kingzcheung.xime.clipboard.VerificationCodeExtractor
import com.kingzcheung.xime.plugin.core.api.ClipboardProfile
import com.kingzcheung.xime.settings.IncognitoState
import com.kingzcheung.xime.settings.SettingsPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * 验证码发到其他设备（用户 11-05：收验证码、复制同步各自能用，两个一起用没做；默认关）。
 *
 * 本机「新短信验证码自动复制」拿到的验证码，只经剪贴板同步已配对的设备通道（钉住证书的 HTTPS、配对时发的令牌）
 * 发给已保存的电脑、手机、平板，走单独的 `POST /api/code`，不走 `/api/clipboard`：
 * 旧版对端没有这个接口，回 404 就不再发，验证码不会被旧版当普通复制记进历史或再转出去。
 * 对端把它写进剪贴板并标成验证码：不进历史，不再转发，[LIFETIME_MS] 内（从短信到达算）自动清掉。
 * 验证码和对端地址都不写日志。
 */
object VerificationCodeRelay {
    /** 短信到达后多久内还发，对端最多留多久（和候选栏提示验证码的 3 分钟一样）。 */
    const val LIFETIME_MS = 3 * 60_000L
    /** 剩不到这么久就不发了：到了对端也来不及用。 */
    internal const val MIN_LEFT_MS = 5_000L
    const val PATH = "/api/code"

    /** 该不该发：开关开着，同步开着且用的是内置的设备互联（不发给 WebDAV 等云端服务），没开无痕（开着无痕时一条也不发）。 */
    fun enabled(context: Context): Boolean =
        SettingsPreferences.isSmsCodeSyncEnabled(context) &&
            SettingsPreferences.isSmsCodeEnabled(context) &&
            SettingsPreferences.isClipboardSyncEnabled(context) &&
            SettingsPreferences.getClipboardSyncPluginId(context) == WindowsDevices.ID &&
            !SettingsPreferences.isIncognito(context) && !IncognitoState.manual

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private val sender = VerificationCodeSender(WindowsSecureTransport.Companion::request)

    /** 短信刚复制出验证码时调用；[arrivedAt] 是到达时间（System.currentTimeMillis）。 */
    fun send(context: Context, code: String, arrivedAt: Long) {
        if (!enabled(context) || !VerificationCodeExtractor.isCode(code)) return
        WindowsDevices.initialize(context)
        val targets = WindowsDevices.saved.value
        if (targets.isEmpty()) return
        synchronized(this) {
            // 新验证码顶掉还在重试的旧验证码。
            job?.cancel()
            job = scope.launch { for (peer in targets) launch { sender.deliver(peer, code, arrivedAt) } }
        }
    }

    /** 发出去的内容：只有验证码、校验和剩余时间。 */
    fun body(code: String, ttlMs: Long): JSONObject {
        val bytes = code.toByteArray(Charsets.UTF_8)
        return JSONObject().put("v", 2).put("type", "code").put("text", code)
            .put("hash", ClipboardProfile.sha256Hex(bytes)).put("size", bytes.size).put("ttl_ms", ttlMs)
    }

    /** 收到的一条：校验通过返回 验证码 to 留多久（毫秒，最多 [LIFETIME_MS]），否则 null。 */
    fun parse(json: JSONObject): Pair<String, Long>? {
        if (json.optInt("v") != 2 || json.optString("type") != "code") return null
        val code = json.optString("text")
        if (!VerificationCodeExtractor.isCode(code)) return null
        val bytes = code.toByteArray(Charsets.UTF_8)
        if (json.optString("hash") != ClipboardProfile.sha256Hex(bytes) || json.optLong("size", -1) != bytes.size.toLong()) return null
        val ttl = json.optLong("ttl_ms", 0L)
        if (ttl < MIN_LEFT_MS) return null
        return code to ttl.coerceAtMost(LIFETIME_MS)
    }

}

/** 给一台设备发一条验证码；过期就停，对端拒收（旧版 404、撤销 401、暂停 403 等）不再重试。 */
class VerificationCodeSender internal constructor(
    private val exchange: (WindowsPeer, String, JSONObject?, Boolean, String?) -> Pair<Int, JSONObject?>,
    private val now: () -> Long = System::currentTimeMillis,
    private val retryWaits: LongArray = longArrayOf(0, 5_000, 20_000, 60_000),
) {
    /** True when [peer] took it. */
    suspend fun deliver(peer: WindowsPeer, code: String, arrivedAt: Long): Boolean {
        for (wait in retryWaits) {
            if (wait > 0) delay(wait)
            val left = arrivedAt + VerificationCodeRelay.LIFETIME_MS - now()
            if (left < VerificationCodeRelay.MIN_LEFT_MS) return false
            val status = try {
                exchange(peer, VerificationCodeRelay.PATH, VerificationCodeRelay.body(code, left), true, null).first
            } catch (e: CancellationException) { throw e } catch (_: Exception) { -1 }
            if (status == 200) return true
            // 只记状态码，不记验证码和对端。
            Log.w("CodeRelay", "code not delivered ($status)")
            if (status in 400..499) return false
        }
        return false
    }
}
