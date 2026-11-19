package com.kingzcheung.xime.ai

import android.content.Context
import com.kingzcheung.xime.plugin.SecureValueCipher
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 内置 AI 助手的接口配置：地址、密钥、模型。
 *
 * 做法和在线语音（[com.kingzcheung.xime.speech.OnlineSpeechApi]）一致：单独一个设置文件，
 * 不进备份、不上传；密钥用 Android Keystore 加密后落盘（明文回退只在 Keystore 不可用时发生）。
 * 模型列表是「获取模型」拿到的结果，跟配置一起存，下次打开面板直接可选。
 */
data class AiConfig(
    val baseUrl: String = AiEndpoints.DEFAULT_BASE_URL,
    val apiKey: String = "",
    val model: String = AiEndpoints.DEFAULT_MODEL,
    val models: List<String> = emptyList(),
    /** 面板顶部显示的服务商名字（预设名或「自定义」）。 */
    val providerLabel: String = DEFAULT_PROVIDER,
) {
    val isConfigured: Boolean get() = apiKey.isNotBlank()

    companion object {
        const val DEFAULT_PROVIDER = "DeepSeek"
    }
}

/** 常用服务商预设：地址、默认模型。 */
data class AiPreset(val label: String, val baseUrl: String, val model: String)

object AiPresets {
    val all = listOf(
        AiPreset("DeepSeek", "https://api.deepseek.com/v1", "deepseek-chat"),
        AiPreset("OpenAI", "https://api.openai.com/v1", "gpt-4o-mini"),
        AiPreset("硅基流动", "https://api.siliconflow.cn/v1", "deepseek-ai/DeepSeek-V3"),
        AiPreset("智谱 GLM", "https://open.bigmodel.cn/api/paas/v4", "glm-4-flash"),
        AiPreset("月之暗面", "https://api.moonshot.cn/v1", "moonshot-v1-8k"),
        AiPreset("自定义", "", ""),
    )

    fun byLabel(label: String): AiPreset? = all.find { it.label == label }

    /** 按地址反查预设名，认不出来就是「自定义」。 */
    fun labelOf(baseUrl: String): String {
        val url = AiEndpoints.cleanUrl(baseUrl)
        return all.firstOrNull { it.baseUrl.isNotBlank() && AiEndpoints.cleanUrl(it.baseUrl) == url }?.label
            ?: "自定义"
    }
}

/**
 * AI 配置的读写。密钥单独加密存放（[SecureValueCipher]，`enc:` 前缀的密文），
 * 其余字段明文；跨设备同步用 [exportCode]，不走备份（备份会换设备，密钥解不开）。
 */
object AiConfigStore {
    private const val PREFS = "ai_assistant"
    private const val KEY_BASE_URL = "base_url"
    private const val KEY_API_KEY = "api_key"
    private const val KEY_MODEL = "model"
    private const val KEY_MODELS = "models"
    private const val KEY_PROVIDER = "provider"

    private fun prefs(context: Context): android.content.SharedPreferences? =
        runCatching {
            (context.applicationContext ?: context).getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        }.getOrNull()

    fun load(context: Context): AiConfig {
        val p = prefs(context) ?: return AiConfig()
        val baseUrl = p.getString(KEY_BASE_URL, null)?.let(AiEndpoints::cleanUrl)
            ?.takeIf { it.isNotBlank() } ?: AiEndpoints.DEFAULT_BASE_URL
        val model = p.getString(KEY_MODEL, null)?.let(AiEndpoints::cleanToken)
            ?.takeIf { it.isNotBlank() } ?: AiEndpoints.DEFAULT_MODEL
        return AiConfig(
            baseUrl = baseUrl,
            apiKey = decrypt(p.getString(KEY_API_KEY, "").orEmpty()),
            model = model,
            models = p.getString(KEY_MODELS, null)?.let(::splitModels).orEmpty(),
            providerLabel = p.getString(KEY_PROVIDER, null)?.takeIf { it.isNotBlank() }
                ?: AiPresets.labelOf(baseUrl),
        )
    }

    fun save(context: Context, config: AiConfig) {
        prefs(context)?.edit()
            ?.putString(KEY_BASE_URL, AiEndpoints.cleanUrl(config.baseUrl))
            ?.putString(KEY_API_KEY, encrypt(AiEndpoints.cleanToken(config.apiKey)))
            ?.putString(KEY_MODEL, AiEndpoints.cleanToken(config.model))
            ?.putString(KEY_MODELS, config.models.joinToString("\n"))
            ?.putString(KEY_PROVIDER, config.providerLabel)
            ?.apply()
    }

    /** 只更新模型列表（「获取模型」成功后调用，不动地址和密钥）。 */
    fun saveModels(context: Context, models: List<String>, model: String) {
        prefs(context)?.edit()
            ?.putString(KEY_MODELS, models.joinToString("\n"))
            ?.putString(KEY_MODEL, AiEndpoints.cleanToken(model))
            ?.apply()
    }

    /** 面板顶部显示的服务商名字。 */
    fun providerLabel(config: AiConfig): String =
        config.providerLabel.ifBlank { AiPresets.labelOf(config.baseUrl) }

    internal fun splitModels(raw: String): List<String> =
        raw.split('\n').map { it.trim() }.filter { it.isNotEmpty() }.distinct()

    /**
     * 同步码：把配置压成一行 JSON，换设备时粘贴回来就能用。
     * 含密钥，只由用户自己复制，不写进备份、不上传。
     */
    fun exportCode(config: AiConfig): String = buildJsonObject {
        put("base_url", config.baseUrl)
        put("api_key", config.apiKey)
        put("model", config.model)
        put("models", buildJsonArray { config.models.forEach { add(JsonPrimitive(it)) } })
    }.toString()

    /** 解析同步码；格式不对返回 null，由调用方提示「同步码无效」。 */
    fun parseCode(code: String): AiConfig? {
        val root = runCatching { Json.parseToJsonElement(code.trim()) }.getOrNull() as? JsonObject ?: return null
        fun text(name: String): String? = (root[name] as? JsonPrimitive)?.contentOrNull()
        val baseUrl = text("base_url")?.takeIf { it.isNotBlank() } ?: return null
        val apiKey = text("api_key").orEmpty()
        val model = text("model")?.takeIf { it.isNotBlank() } ?: AiEndpoints.DEFAULT_MODEL
        val models = (root["models"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull() }.orEmpty()
        return AiConfig(
            baseUrl = AiEndpoints.cleanUrl(baseUrl),
            apiKey = AiEndpoints.cleanToken(apiKey),
            model = AiEndpoints.cleanToken(model),
            models = models.map { AiEndpoints.cleanToken(it) }.filter { it.isNotEmpty() }.distinct(),
            providerLabel = AiPresets.labelOf(baseUrl),
        )
    }

    private fun JsonPrimitive.contentOrNull(): String? =
        runCatching { content }.getOrNull()?.takeIf { it.isNotEmpty() }

    private fun encrypt(plain: String): String {
        if (plain.isEmpty()) return ""
        return runCatching { SecureValueCipher.encrypt(plain) }.getOrElse { plain }
    }

    private fun decrypt(stored: String): String {
        if (stored.isEmpty()) return ""
        return runCatching { SecureValueCipher.decrypt(stored) ?: stored }.getOrElse { stored }
    }
}
