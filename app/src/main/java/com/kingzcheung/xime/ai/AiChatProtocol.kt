package com.kingzcheung.xime.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** 对话里的一条消息。role 用 OpenAI 的 user / assistant / system。 */
data class AiMessage(val role: String, val content: String)

/**
 * OpenAI 兼容 `/chat/completions` 的请求体与返回值解析。
 *
 * 请求体固定 `stream: true`（面板边生成边显示）；返回值既可能是 SSE 的若干条 `data: {...}`，
 * 也可能是普通 JSON 的 `choices[0].message.content`，两种都认。
 */
object AiChatProtocol {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** SSE 结束标记。 */
    const val DONE = "[DONE]"

    fun requestBody(model: String, messages: List<AiMessage>, stream: Boolean = true): String =
        buildJsonObject {
            put("model", model)
            put("stream", stream)
            put("messages", buildJsonArray {
                messages.forEach { message ->
                    add(buildJsonObject {
                        put("role", message.role)
                        put("content", message.content)
                    })
                }
            })
        }.toString()

    /**
     * 从一条 SSE 的 data 里取增量文字。`choices[0].delta.content` 是主流形状；
     * 有的服务在流的最后一条给 `choices[0].message.content`，也一并取。
     * 没有文字（心跳、role 行、结束行）返回 null。
     */
    fun deltaOf(payload: String): String? {
        val text = payload.trim()
        if (text.isEmpty() || text == DONE) return null
        val root = runCatching { json.parseToJsonElement(text) }.getOrNull() as? JsonObject ?: return null
        val choices = root["choices"] as? JsonArray ?: return null
        val first = choices.firstOrNull() as? JsonObject ?: return null
        for (key in listOf("delta", "message")) {
            val holder = first[key] as? JsonObject ?: continue
            val content = (holder["content"] as? JsonPrimitive)?.contentOrNull() ?: continue
            if (content.isNotEmpty()) return content
        }
        return null
    }

    /** 非流式返回里整段文字；SSE 拼完也可以用。 */
    fun wholeText(body: String): String {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) return ""
        // 普通 JSON：choices[0].message.content
        (runCatching { json.parseToJsonElement(trimmed) }.getOrNull() as? JsonObject)?.let { root ->
            val choices = root["choices"] as? JsonArray
            val first = choices?.firstOrNull() as? JsonObject
            val content = ((first?.get("message") as? JsonObject)?.get("content") as? JsonPrimitive)?.contentOrNull()
            if (!content.isNullOrEmpty()) return content
        }
        // 已经是多行 SSE：逐条取增量拼起来
        return trimmed.lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("data:") }
            .mapNotNull { deltaOf(it.removePrefix("data:").trim()) }
            .joinToString("")
    }

    /** 接口自己给的原因（`error.message`）；取不到返回 null。 */
    fun errorMessage(body: String): String? {
        val root = runCatching { json.parseToJsonElement(body.trim()) }.getOrNull() as? JsonObject ?: return null
        val error = root["error"] as? JsonObject
        return (error?.get("message") as? JsonPrimitive)?.contentOrNull()
            ?: (root["message"] as? JsonPrimitive)?.contentOrNull()
    }

    /** 是否已经收到结束标记。 */
    fun isDone(payload: String): Boolean = payload.trim() == DONE

    private fun JsonPrimitive.contentOrNull(): String? =
        runCatching { content }.getOrNull()?.takeIf { it.isNotEmpty() }
}
