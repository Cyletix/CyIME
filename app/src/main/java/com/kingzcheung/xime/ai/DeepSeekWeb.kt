package com.kingzcheung.xime.ai

import android.content.Context
import android.content.Intent
import com.kingzcheung.xime.ui.ai.DeepSeekWebActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import org.json.JSONTokener

/**
 * DeepSeek 网页版的「取最新回复」通道。
 *
 * 和 [AiAssistant] 不同，这里不碰接口和密钥：用户直接在 [DeepSeekWebActivity] 里登录
 * chat.deepseek.com 提问，页面只读 DOM，把最后一条 AI 回复的文本交回来。
 * 输入法面板观察 [latestReply] 显示/上屏；[acceptResult] 只在拿到非空文本时覆盖旧值，
 * 所以点「取最新回复」失败不会把上一次的结果冲掉。
 */
object DeepSeekWeb {
    /** 最新一条取到的回复；null 表示还没有（或已 [clearReply]）。 */
    private val _latestReply = MutableStateFlow<String?>(null)
    val latestReply: StateFlow<String?> = _latestReply.asStateFlow()

    /** 打开网页版问答页。输入法里没有 Activity 栈，必须带 NEW_TASK。 */
    fun start(context: Context) {
        context.startActivity(
            Intent(context, DeepSeekWebActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    /** 丢弃上一条回复（面板清空时用）。 */
    fun clearReply() {
        _latestReply.value = null
    }

    /**
     * 接收 WebView `evaluateJavascript` 的回调值。
     *
     * 回调拿到的是 JSON 编码的字符串（形如 `" 你好\n世界 "`、`"null"` 或 `null`），
     * 这里还原成普通文本；空串、null、`"null"` 和只有空白的文本都不覆盖旧回复。
     */
    internal fun acceptResult(rawJsResult: String?) {
        val text = decode(rawJsResult) ?: return
        val trimmed = text.trim()
        if (trimmed.isEmpty() || trimmed == "null") return
        _latestReply.value = trimmed
    }

    /**
     * 把 JS 回调值还原成文本，解析不了就原样去掉首尾引号。
     *
     * evaluateJavascript 会把它拿到的 JS 返回值整体再做一次 JSON 编码，而 [SCRIPT]
     * 自己返回的是 `JSON.stringify(text)`，所以字符串实际被编码了两层。这里先解一层；
     * 如果解出来的仍是「整体带引号的 JSON 字符串」，再解一层，最多两层。
     */
    private fun decode(rawJsResult: String?): String? {
        val raw = rawJsResult?.trim().orEmpty()
        if (raw.isEmpty() || raw == "null") return null
        val parsed = parseJson(raw) ?: return unquote(raw)
        if (parsed.length >= 2 && parsed.first() == '"' && parsed.last() == '"') {
            parseJson(parsed)?.let { return it }
        }
        return parsed
    }

    /** 按 JSON 解析回调值；字符串返回原文，其他值转成文本，解析失败返回 null。 */
    private fun parseJson(raw: String): String? {
        val parsed = runCatching { JSONTokener(raw).nextValue() }.getOrNull()
        return when (parsed) {
            null, JSONObject.NULL -> null
            is String -> parsed
            else -> parsed.toString()
        }
    }

    /** JSON 解析失败时的兜底：字符串被直接拼出来时，只去掉最外层成对的引号。 */
    private fun unquote(raw: String): String? {
        if (raw.length >= 2 && raw.startsWith('"') && raw.endsWith('"')) {
            return raw.substring(1, raw.length - 1)
        }
        return raw.ifBlank { null }
    }

    /**
     * 取最新一条 AI 回复的 JS。只读 DOM，不发任何网络请求。
     *
     * deepseek 网页的 DOM 常改，所以按优先级依次试几种选择器，取每个选择器命中的
     * 最后一个节点；都对不上时退回整页 innerText 的最后一段。结果用 JSON.stringify
     * 包成 JSON 字符串，避免换行和引号在回传时丢失。
     */
    internal const val SCRIPT: String = """
        (function () {
            function lastText(selector) {
                try {
                    var nodes = document.querySelectorAll(selector);
                    if (!nodes || nodes.length === 0) return '';
                    var node = nodes[nodes.length - 1];
                    var text = node.innerText || node.textContent || '';
                    return String(text).trim();
                } catch (e) {
                    return '';
                }
            }
            var text = lastText('[data-message-author-role="assistant"]');
            if (!text) text = lastText('.ds-markdown');
            if (!text) text = lastText('div[class*="markdown"]');
            if (!text) {
                var body = document.body ? (document.body.innerText || '') : '';
                var parts = String(body).split(/\n{2,}/).filter(function (part) {
                    return part.trim().length > 0;
                });
                text = parts.length > 0 ? parts[parts.length - 1].trim() : String(body).trim();
            }
            return JSON.stringify(text);
        })()
    """
}
