package com.kingzcheung.xime.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 「获取模型」：解析 OpenAI 兼容接口 `GET /models` 的返回。
 *
 * 常见三种形状：`{"data":[{"id":"deepseek-chat"}]}`（OpenAI/DeepSeek/硅基流动）、
 * `{"models":[{"name":"qwen"}]}`（Ollama 等本地服务）、`["a","b"]`（个别网关直接给数组）。
 * 解析失败返回空列表，由调用方提示「这个接口没给模型列表，手动填模型名」。
 */
object AiModelList {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(body: String): List<String> {
        val root = runCatching { json.parseToJsonElement(body.trim()) }.getOrNull() ?: return emptyList()
        return parseElement(root)
    }

    private fun parseElement(element: JsonElement): List<String> {
        val out = LinkedHashSet<String>()
        when (element) {
            is JsonArray -> element.forEach { item -> collect(item, out) }
            is JsonObject -> {
                for (key in listOf("data", "models")) {
                    val list = element[key] as? JsonArray ?: continue
                    list.forEach { item -> collect(item, out) }
                }
            }
            else -> Unit
        }
        return out.toList()
    }

    private fun collect(item: JsonElement, out: MutableSet<String>) {
        when (item) {
            is JsonPrimitive -> item.contentOrNull()?.let { out.add(it) }
            is JsonObject -> {
                for (key in listOf("id", "name", "model")) {
                    val value = (item[key] as? JsonPrimitive)?.contentOrNull() ?: continue
                    out.add(value)
                    break
                }
            }
            else -> Unit
        }
    }

    private fun JsonPrimitive.contentOrNull(): String? =
        runCatching { jsonPrimitive.content }.getOrNull()?.trim()?.takeIf { it.isNotEmpty() }
}
