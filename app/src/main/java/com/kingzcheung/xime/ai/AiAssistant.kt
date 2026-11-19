package com.kingzcheung.xime.ai

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.sse.EventSource

/**
 * 内置 AI 助手：面板本体（左边输入、右边输出）的状态与请求。
 *
 * 和「AI 回复 / AI 改写」插件不同，这一份跑在宿主里：接口地址、密钥、模型都在
 * 设置 › AI 助手里填，不用装插件；面板只负责把问题发出去、把流式回复显示在右边。
 * 会话历史留在内存里（关掉面板不清），换模型、清空都从这里走。
 */
object AiAssistant {
    enum class Status { IDLE, STREAMING, ERROR, NEEDS_CONFIG }

    data class UiState(
        val model: String = "",
        val models: List<String> = emptyList(),
        val provider: String = "",
        val status: Status = Status.IDLE,
        /** 正在生成的回复（流式追加）。 */
        val output: String = "",
        val error: String = "",
        /** 已完成的问答，作为下一轮的上下文。 */
        val history: List<AiMessage> = emptyList(),
    ) {
        val configured: Boolean get() = status != Status.NEEDS_CONFIG
    }

    private const val SYSTEM_PROMPT =
        "你是一个简洁的中文助手。回答直接给结论，必要时才分点；不要客套话，不要重复问题。"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var stream: EventSource? = null
    private var sendJob: Job? = null

    /** 打开面板：读当前配置。没填密钥时提示去配置。 */
    fun open(context: Context) {
        val config = AiConfigStore.load(context)
        _state.value = _state.value.copy(
            model = config.model,
            models = config.models,
            provider = AiConfigStore.providerLabel(config),
            status = if (config.isConfigured) Status.IDLE else Status.NEEDS_CONFIG,
            error = "",
        )
    }

    /** 重新读配置（设置页保存后回来）。 */
    fun reload(context: Context) {
        val config = AiConfigStore.load(context)
        _state.value = _state.value.copy(
            model = config.model,
            models = config.models,
            provider = AiConfigStore.providerLabel(config),
            status = when {
                !config.isConfigured -> Status.NEEDS_CONFIG
                _state.value.status == Status.NEEDS_CONFIG -> Status.IDLE
                else -> _state.value.status
            },
        )
    }

    /** 发送一条问题；[prompt] 为空时用面板输入框里的字（由调用方读取）。 */
    fun send(context: Context, prompt: String) {
        val text = prompt.trim()
        if (text.isEmpty() || _state.value.status == Status.STREAMING) return
        val config = AiConfigStore.load(context)
        if (!config.isConfigured) {
            _state.value = _state.value.copy(status = Status.NEEDS_CONFIG, error = "先在「AI 配置」里填接口密钥")
            return
        }
        val history = _state.value.history + AiMessage("user", text)
        val messages = listOf(AiMessage("system", SYSTEM_PROMPT)) + history
        _state.value = _state.value.copy(
            status = Status.STREAMING,
            output = "",
            error = "",
            model = config.model,
        )
        sendJob?.cancel()
        stream?.cancel()
        stream = AiClient.streamChat(
            config = config,
            messages = messages,
            onDelta = { delta ->
                _state.value = _state.value.copy(output = _state.value.output + delta)
            },
            onDone = {
                _state.value = _state.value.let { current ->
                    val reply = current.output
                    current.copy(
                        status = Status.IDLE,
                        output = "",
                        history = if (reply.isBlank()) current.history
                        else current.history + AiMessage("assistant", reply),
                    )
                }
                stream = null
            },
            onError = { reason ->
                _state.value = _state.value.copy(status = Status.ERROR, error = reason)
                stream = null
            },
        )
    }

    /** 停止生成（关面板或再点一次发送）。 */
    fun cancel() {
        stream?.cancel()
        stream = null
        sendJob?.cancel()
        if (_state.value.status == Status.STREAMING) {
            _state.value = _state.value.copy(status = Status.IDLE)
        }
    }

    /** 清空会话（输出区和上下文一起清）。 */
    fun clear() {
        cancel()
        _state.value = _state.value.copy(output = "", error = "", history = emptyList(), status = Status.IDLE)
    }

    fun setModel(context: Context, model: String) {
        val name = AiEndpoints.cleanToken(model)
        if (name.isEmpty()) return
        AiConfigStore.saveModels(context, _state.value.models, name)
        _state.value = _state.value.copy(model = name)
    }

    /** 「获取模型」：拉列表并记住（面板的模型按钮和设置页共用）。 */
    fun refreshModels(context: Context, onResult: (Result<List<String>>) -> Unit = {}) {
        val config = AiConfigStore.load(context)
        if (!config.isConfigured) {
            onResult(Result.failure(IllegalStateException("先填接口密钥")))
            return
        }
        scope.launch {
            val result = AiClient.fetchModels(config)
            result.onSuccess { models ->
                if (models.isNotEmpty()) {
                    val current = _state.value.model.takeIf { it in models } ?: models.first()
                    AiConfigStore.saveModels(context, models, current)
                    _state.value = _state.value.copy(models = models, model = current)
                }
            }
            onResult(result)
        }
    }

    /**
     * 外部来源的回复（DeepSeek 网页版里取回的），直接放进输出区并记进上下文，
     * 这样接着在面板里追问也能带上它。
     */
    fun showExternalReply(text: String) {
        val reply = text.trim()
        if (reply.isEmpty()) return
        _state.value = _state.value.copy(
            status = Status.IDLE,
            output = "",
            error = "",
            history = _state.value.history + AiMessage("assistant", reply),
        )
    }

    /** 面板关掉时保留历史，但要停掉在途请求，避免后台继续烧额度。 */
    fun onPanelClosed() {
        cancel()
        _state.value = _state.value.copy(output = "", error = "")
    }
}
