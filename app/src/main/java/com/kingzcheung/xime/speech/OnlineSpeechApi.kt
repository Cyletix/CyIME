package com.kingzcheung.xime.speech

import android.app.Application
import android.content.Context
import com.kingzcheung.xime.plugin.PluginConfigStoreImpl
import com.kingzcheung.xime.settings.InputLanguage
import com.kingzcheung.xime.settings.SettingsPreferences
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * 内置在线语音：设置 › 语音转文本 › 在线语音里直接填地址、密钥、模型，不用装插件。
 * 两种接口按地址区分：OpenAI 兼容 `/audio/transcriptions`（OpenAI、Groq、硅基流动等，multipart 上传），
 * 腾讯 TokenHub 同步语音识别（地址含 /asrproxy/ 或以 sync_transcribe 结尾，JSON 上传 Base64 WAV，文字在 output.text）。
 * 配置单独存一个设置文件：不进备份，密钥只留在本机。
 */
object OnlineSpeechApi {
    /** 存进「用哪个在线语音」的值：选了它且打开「优先使用在线语音」时先走这里。 */
    const val BUILTIN_ID = "builtin:online_speech"
    const val DEFAULT_BASE_URL = "https://api.openai.com/v1"
    const val DEFAULT_MODEL = "whisper-1"
    /** 1.15.40～1.15.50 的「在线语音」插件；装过的人第一次打开语音页或说话时，把它的设置搬回来。 */
    private const val OLD_PLUGIN_ID = "com.kingzcheung.xime.plugin.online_asr"
    private const val PREFS = "online_speech_api"
    private const val KEY_BASE_URL = "base_url"
    private const val KEY_API_KEY = "api_key"
    private const val KEY_MODEL = "model"
    private const val KEY_STATUS = "status"
    private const val KEY_PLUGIN_MIGRATED = "plugin_migrated"
    private const val SAMPLE_RATE = 16000

    data class Config(val baseUrl: String, val apiKey: String, val model: String)

    private val client by lazy {
        OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS).writeTimeout(30, TimeUnit.SECONDS).build()
    }

    /** 可空：测试里的假 Context 可能没有这个设置文件，此时按未填写处理。 */
    private fun prefs(context: Context): android.content.SharedPreferences? =
        (context.applicationContext ?: context).getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun config(context: Context): Config = prefs(context).let {
        Config(it?.getString(KEY_BASE_URL, null)?.takeIf { url -> url.isNotBlank() } ?: DEFAULT_BASE_URL,
            it?.getString(KEY_API_KEY, "").orEmpty(),
            it?.getString(KEY_MODEL, null)?.takeIf { model -> model.isNotBlank() } ?: DEFAULT_MODEL)
    }

    /** 改了配置就清掉旧的测试结果，状态不会停在“可用”误导人。 */
    fun save(context: Context, config: Config) {
        prefs(context)?.edit()?.putString(KEY_BASE_URL, config.baseUrl.trim())?.putString(KEY_API_KEY, config.apiKey.trim())
            ?.putString(KEY_MODEL, config.model.trim())?.remove(KEY_STATUS)?.apply()
    }

    fun isConfigured(context: Context): Boolean = config(context).apiKey.isNotBlank()

    /** 选了内置在线语音（「优先使用在线语音」打开时存的就是它）。 */
    fun isSelected(context: Context): Boolean = SettingsPreferences.getSttOnlinePluginId(context) == BUILTIN_ID

    /** 现在说话会先走内置在线语音。 */
    fun isActive(context: Context): Boolean =
        isSelected(context) && !SettingsPreferences.isSttUseLocal(context) && isConfigured(context)

    fun status(context: Context): String = prefs(context)?.getString(KEY_STATUS, null)
        ?: if (isConfigured(context)) "未测试" else "未填写密钥"

    /** 用一秒静音真正调用一次转写接口：地址、密钥、模型都对才算可用。在后台线程调用。 */
    fun test(context: Context): String {
        val time = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date())
        val status = if (!isConfigured(context)) "未填写密钥"
            else transcribe(context, ByteArray(SAMPLE_RATE * 2), InputLanguage.CHINESE)
                .fold({ "可用（$time 测试）" }, { "不可用：${it.message}（$time 测试）" })
        prefs(context)?.edit()?.putString(KEY_STATUS, status)?.apply()
        return status
    }

    /**
     * 「在线语音」插件 → 内置：插件里填过密钥、内置还没填时，把地址、密钥、模型搬过来；
     * 原来选的是这个插件的，改选内置（「优先使用在线语音」照旧）。只搬一次，插件本身不动。
     */
    fun migrateFromPlugin(context: Context) {
        val prefs = prefs(context) ?: return
        if (prefs.getBoolean(KEY_PLUGIN_MIGRATED, false)) return
        val app = context.applicationContext as? Application ?: return
        runCatching {
            val store = PluginConfigStoreImpl(app, OLD_PLUGIN_ID)
            val apiKey = store.get("apiKey")?.trim().orEmpty()
            if (apiKey.isNotEmpty() && !isConfigured(context)) {
                save(context, Config(store.get("baseUrl")?.trim().orEmpty().ifEmpty { DEFAULT_BASE_URL }, apiKey,
                    store.get("model")?.trim().orEmpty().ifEmpty { DEFAULT_MODEL }))
            }
            val selected = SettingsPreferences.getSttOnlinePluginId(context)
            // 选的是这个插件，或 1.15.33～1.15.39 用内置接口时打开过「优先使用在线接口」：改选内置。
            val preferredOnline = !SettingsPreferences.getPrefsPublic(context).getBoolean(SettingsPreferences.KEY_STT_USE_LOCAL, true)
            if (isConfigured(context) && (selected == OLD_PLUGIN_ID || selected.isBlank() && preferredOnline)) {
                SettingsPreferences.setSttOnlinePluginId(context, BUILTIN_ID)
            }
        }
        prefs.edit().putBoolean(KEY_PLUGIN_MIGRATED, true).apply()
    }

    /** 腾讯 TokenHub 的同步识别地址。 */
    internal fun isTokenHub(url: String): Boolean = url.contains("/asrproxy/") || url.endsWith("sync_transcribe")

    /** 用户填的地址 → 实际请求地址：OpenAI 兼容填到 /v1 为止也行，填到 /audio/transcriptions 也行。 */
    internal fun endpoint(baseUrl: String): String {
        val url = baseUrl.trim().trimEnd('/')
        return if (isTokenHub(url) || url.endsWith("/audio/transcriptions")) url else "$url/audio/transcriptions"
    }

    /** [pcm] 为 16 kHz 单声道 16 位小端 PCM。在后台线程调用。 */
    fun transcribe(context: Context, pcm: ByteArray, language: InputLanguage): Result<String> = runCatching {
        val config = config(context)
        require(config.apiKey.isNotBlank()) { "未填写密钥" }
        val url = endpoint(config.baseUrl)
        val body = if (isTokenHub(url)) {
            // 语种不传：中英混说、方言都交给接口自己认。
            JSONObject().put("model", config.model).put("data", Base64.getEncoder().encodeToString(wav(pcm)))
                .put("voice_encode_format", "wav").toString().toRequestBody("application/json".toMediaType())
        } else {
            MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("model", config.model)
                .addFormDataPart("response_format", "json")
                .apply { if (language != InputLanguage.UNSPECIFIED) addFormDataPart("language", language.id) }
                .addFormDataPart("file", "speech.wav", wav(pcm).toRequestBody("audio/wav".toMediaType()))
                .build()
        }
        val request = Request.Builder().url(url).header("Authorization", "Bearer ${config.apiKey}").post(body).build()
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            val json = runCatching { JSONObject(text) }.getOrNull()
            check(response.isSuccessful) { httpError(response.code, serverMessage(json, text)) }
            checkNotNull(json) { "接口返回的格式看不懂" }
            businessError(json)?.let { error(it) }
            (extractText(json, 0) ?: if (json.optString("status") == "completed") "" else error("接口返回的格式看不懂")).trim()
        }
    }.recoverCatching { error ->
        throw if (error is java.io.IOException) IllegalStateException("连不上服务器", error) else error
    }

    private fun httpError(code: Int, detail: String?): String {
        val base = when (code) {
            401, 403 -> "密钥不对或没有权限（$code）"
            404 -> "接口地址或模型名不对（404）"
            413 -> "这段录音太长（413）"
            429 -> "请求太多或余额不足（429）"
            400 -> "接口不认这个请求（400）"
            else -> "接口返回 $code"
        }
        return if (detail != null) "$base：$detail" else base
    }

    /** 接口自己给的出错原因（OpenAI：error.message；腾讯：message / msg / Response.Error.Message）。 */
    internal fun serverMessage(json: JSONObject?, raw: String): String? {
        if (json == null) return raw.trim().takeIf { it.isNotEmpty() && '<' !in it }?.let(::shorten)
        val error = json.opt("error").let { if (it is JSONObject) it.optString("message").ifEmpty { it.optString("msg") } else it as? String }
        val tencent = json.optJSONObject("Response")?.optJSONObject("Error")?.optString("Message")
        return listOf(error, json.optString("message"), json.optString("msg"), json.optString("Message"),
            json.optString("error_msg"), json.optString("detail"), tencent)
            .firstOrNull { !it.isNullOrBlank() }?.let(::shorten)
    }

    private fun shorten(text: String): String = text.replace(Regex("\\s+"), " ").trim().let { if (it.length > 60) it.take(60) + "…" else it }

    /** HTTP 200 但 code 不为 0、带 error、或 status 为 failed 也算失败。 */
    private fun businessError(json: JSONObject): String? {
        val code = json.opt("code")?.takeIf { it != JSONObject.NULL }?.toString()
        val failed = code != null && code !in setOf("0", "200") || json.has("error") && !json.isNull("error") ||
            json.optString("status") == "failed" || json.optJSONObject("Response")?.has("Error") == true
        if (!failed) return null
        val head = "接口报错" + (code?.let { "（$it）" } ?: "")
        return serverMessage(json, "")?.let { "$head：$it" } ?: head
    }

    private val TEXT_KEYS = listOf("text", "transcript", "Text", "Transcript")
    private val WRAPPER_KEYS = listOf("data", "output", "result", "Result", "Data", "Response", "results", "sentences", "segments")

    /** 从返回里找识别出的文字：先找 text / transcript，再进 data / output / result 之类的外层；句子列表按顺序拼起来。 */
    internal fun extractText(value: Any?, depth: Int): String? {
        if (depth > 6) return null
        return when (value) {
            is JSONArray -> (0 until value.length()).mapNotNull { i ->
                value.opt(i).let { it as? String ?: extractText(it, depth + 1) }?.trim()
            }.takeIf { it.isNotEmpty() }?.reduce { acc, piece ->
                if (acc.isNotEmpty() && piece.isNotEmpty() && acc.last().isLetterOrDigit() && acc.last().code < 128 &&
                    piece.first().isLetterOrDigit() && piece.first().code < 128) "$acc $piece" else acc + piece
            }
            is JSONObject -> TEXT_KEYS.firstNotNullOfOrNull { value.opt(it) as? String }
                ?: WRAPPER_KEYS.firstNotNullOfOrNull { extractText(value.opt(it), depth + 1) }
                ?: (value.opt("result") as? String) ?: (value.opt("Result") as? String)
            else -> null
        }
    }

    private fun wav(pcm: ByteArray): ByteArray {
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + pcm.size); put("WAVE".toByteArray())
            put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1)
            putInt(SAMPLE_RATE); putInt(SAMPLE_RATE * 2); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(pcm.size)
        }
        return ByteArrayOutputStream(44 + pcm.size).apply { write(header.array()); write(pcm) }.toByteArray()
    }
}

/**
 * 录完一段再整段上传；说话中不出预览。上传失败时这段录音改用本地模型识别（[localFallback]），
 * 并用 [onNotice] 在键盘上说明，不会无声无息地结束。中文、英文、日语都交给接口。
 */
class OnlineApiAsrBackend(
    private val context: Context,
    private val localFallback: () -> AsrBackend? = { null },
    private val onNotice: (String) -> Unit = {},
) : AsrBackend {
    override val name: String = "在线语音"
    override val finishTimeoutMs: Long = 30_000L

    /** 接口给的文字自带标点；本地补识别的那段没有。 */
    @Volatile private var keepsPunctuation = false
    override val resultKeepsPunctuation: Boolean get() = keepsPunctuation

    private var onResult: ((String) -> Unit)? = null
    private var onState: ((RecognitionState) -> Unit)? = null
    private var onError: ((String) -> Unit)? = null
    private val audio = ByteArrayOutputStream()
    private var language = InputLanguage.CHINESE

    override fun setCallbacks(
        onResult: (String) -> Unit,
        onPartialResult: ((String) -> Unit)?,
        onStateChange: (RecognitionState) -> Unit,
        onError: (String) -> Unit
    ) {
        this.onResult = onResult; onState = onStateChange; this.onError = onError
    }

    override fun initialize(): Boolean = OnlineSpeechApi.isConfigured(context)
    override fun start(): Boolean = start(InputLanguage.CHINESE)

    override fun start(language: InputLanguage): Boolean {
        this.language = language
        synchronized(audio) { audio.reset() }
        onState?.invoke(RecognitionState.LISTENING)
        return true
    }

    override fun canContinueIn(language: InputLanguage): Boolean = language != InputLanguage.UNSPECIFIED

    override fun processAudioChunk(buffer: ByteArray) {
        synchronized(audio) { audio.write(buffer) }
    }

    override fun stop() {
        val pcm = synchronized(audio) { audio.toByteArray().also { audio.reset() } }
        if (pcm.isEmpty()) { onState?.invoke(RecognitionState.IDLE); return }
        onState?.invoke(RecognitionState.PROCESSING)
        OnlineSpeechApi.transcribe(context, pcm, language)
            .onSuccess { keepsPunctuation = true; onResult?.invoke(it); onState?.invoke(RecognitionState.IDLE) }
            .onFailure { error ->
                if (recognizeLocally(pcm)) onNotice("在线语音没识别出来（${error.message}），这次已改用本地模型")
                else { onError?.invoke("在线语音：${error.message}；本地模型也没能识别"); onState?.invoke(RecognitionState.ERROR) }
            }
    }

    /** 把这段录音交给本地模型识别；本地也不可用时返回 false。 */
    private fun recognizeLocally(pcm: ByteArray): Boolean {
        val local = runCatching { localFallback() }.getOrNull() ?: return false
        return try {
            var text: String? = null
            local.setCallbacks(onResult = { text = (text.orEmpty() + it) }, onPartialResult = null,
                onStateChange = {}, onError = {})
            if (!local.initialize(language) || !local.start(language)) return false
            pcm.asList().chunked(3200).forEach { local.processAudioChunk(it.toByteArray()) }
            local.stop()
            keepsPunctuation = false
            onResult?.invoke(text.orEmpty()); onState?.invoke(RecognitionState.IDLE)
            true
        } catch (_: Exception) {
            false
        } finally {
            runCatching { local.release() }
        }
    }

    override fun cancel() {
        synchronized(audio) { audio.reset() }
        onState?.invoke(RecognitionState.IDLE)
    }

    override fun release() = cancel()
    override fun isAvailable(): Boolean = OnlineSpeechApi.isConfigured(context)
}
