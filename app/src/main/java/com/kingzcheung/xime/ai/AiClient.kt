package com.kingzcheung.xime.ai

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.util.concurrent.TimeUnit

/**
 * 内置 AI 助手的网络层：OpenAI 兼容的 `/models` 和 `/chat/completions`。
 *
 * 只有两件事——列模型、流式对话；重试、线路、面板状态都由调用方决定。
 * 地址与密钥来自 [AiConfig]，每次调用现取，改了配置不用重建。
 */
object AiClient {
    private val JSON = "application/json; charset=utf-8".toMediaType()

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            // 流式对话可能停很久才有下一个字，读超时放到 120 秒；生成中用户可以点关闭取消。
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /** 列模型：`GET {base}/models`。失败时 [Result.failure] 里带可读原因。 */
    fun fetchModels(config: AiConfig): Result<List<String>> = runCatching {
        val request = Request.Builder()
            .url(AiEndpoints.modelsUrl(config.baseUrl))
            .header("Authorization", "Bearer ${config.apiKey}")
            .header("Accept", "application/json")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException(
                    AiChatProtocol.errorMessage(body) ?: "获取模型失败(HTTP ${response.code})"
                )
            }
            AiModelList.parse(body).ifEmpty {
                throw IllegalStateException("这个接口没给模型列表，请手动填模型名")
            }
        }
    }

    /**
     * 流式对话：每个增量字回调 [onDelta]，正常结束回调 [onDone]，出错回调 [onError]。
     * 返回 [EventSource]，调用方关闭面板时 `cancel()`。
     */
    fun streamChat(
        config: AiConfig,
        messages: List<AiMessage>,
        onDelta: (String) -> Unit,
        onDone: () -> Unit,
        onError: (String) -> Unit,
    ): EventSource {
        val request = Request.Builder()
            .url(AiEndpoints.chatUrl(config.baseUrl))
            .header("Authorization", "Bearer ${config.apiKey}")
            .header("Accept", "text/event-stream")
            .post(AiChatProtocol.requestBody(config.model, messages, stream = true).toRequestBody(JSON))
            .build()
        val listener = object : EventSourceListener() {
            private var failed = false

            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                if (AiChatProtocol.isDone(data)) {
                    onDone()
                    return
                }
                AiChatProtocol.deltaOf(data)?.let(onDelta)
            }

            override fun onClosed(eventSource: EventSource) {
                if (!failed) onDone()
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                failed = true
                val body = runCatching { response?.body?.string() }.getOrNull().orEmpty()
                val reason = AiChatProtocol.errorMessage(body)
                    ?: t?.message?.takeIf { it.isNotBlank() }
                    ?: body.takeIf { it.isNotBlank() }?.take(200)
                    ?: "连不上接口"
                onError(reason)
            }
        }
        return EventSources.createFactory(client).newEventSource(request, listener)
    }

    /** 非流式的一次问答（用于「网页版登录不可用时的兜底」或设置页的「测试连接」）。 */
    fun chatOnce(config: AiConfig, messages: List<AiMessage>): Result<String> = runCatching {
        val request = Request.Builder()
            .url(AiEndpoints.chatUrl(config.baseUrl))
            .header("Authorization", "Bearer ${config.apiKey}")
            .post(AiChatProtocol.requestBody(config.model, messages, stream = false).toRequestBody(JSON))
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException(AiChatProtocol.errorMessage(body) ?: "请求失败(HTTP ${response.code})")
            }
            AiChatProtocol.wholeText(body).ifEmpty { throw IllegalStateException("接口没有返回内容") }
        }
    }
}
