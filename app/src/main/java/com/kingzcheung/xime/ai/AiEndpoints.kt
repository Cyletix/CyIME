package com.kingzcheung.xime.ai

/**
 * 内置 AI 助手的接口地址拼接。
 *
 * 用户填的是 OpenAI 兼容接口的「基址」（如 `https://api.deepseek.com/v1`），
 * 这里负责把基址拼成 `/chat/completions`（对话）和 `/models`（获取模型列表）。
 * 地址栏常被连说明文字一起粘进来（见在线语音 OnlineSpeechApi.cleanUrl 的同类问题），
 * 所以先只取第一个网址，再去掉多余斜杠；已经填到具体接口的也照常识别。
 */
object AiEndpoints {
    /** DeepSeek 官方接口（默认值）。 */
    const val DEFAULT_BASE_URL = "https://api.deepseek.com/v1"
    const val DEFAULT_MODEL = "deepseek-chat"

    private val URL_IN_TEXT = Regex("(?i)https?://[^\\s•·，。、；;,\"'<>()（）]+")

    /** 地址栏只取第一个网址；没有网址时去掉首尾空白。 */
    fun cleanUrl(raw: String): String {
        val found = URL_IN_TEXT.find(raw)?.value ?: raw.trim()
        return found.trim().trimEnd('/')
    }

    /** 密钥、模型名里不会有空白：粘贴带进来的换行、空格、零宽字符去掉。 */
    fun cleanToken(raw: String): String =
        raw.filterNot { it.isWhitespace() || it in "\u200B\u200C\u200D\uFEFF" }

    /** 对话接口地址。 */
    fun chatUrl(baseUrl: String): String = join(baseUrl, "chat/completions")

    /** 模型列表地址。 */
    fun modelsUrl(baseUrl: String): String = join(baseUrl, "models")

    /**
     * 基址 + 接口路径。基址里已经写到接口名时先剥掉，避免拼出 `/v1/chat/completions/chat/completions`；
     * 基址为空时用默认地址；没有 http(s) 前缀时补 `https://`。
     */
    fun join(baseUrl: String, path: String): String {
        var base = cleanUrl(baseUrl).ifBlank { DEFAULT_BASE_URL }
        for (suffix in listOf("/chat/completions", "/models")) {
            if (base.endsWith(suffix, ignoreCase = true)) {
                base = base.dropLast(suffix.length)
            }
        }
        if (!base.startsWith("http://", ignoreCase = true) && !base.startsWith("https://", ignoreCase = true)) {
            base = "https://$base"
        }
        return base.trimEnd('/') + "/" + path.trimStart('/')
    }
}
