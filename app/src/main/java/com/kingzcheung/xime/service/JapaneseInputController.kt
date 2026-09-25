package com.kingzcheung.xime.service

import com.kingzcheung.xime.rime.RimeProcessResult
import com.kingzcheung.xime.rime.romajiDeleteStart
import com.kingzcheung.xime.settings.JapaneseSchemas
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 所有方法在按键队列串行调用；会话或原编码改变就作废旧转换预览。 */
internal class JapaneseInputController(private val service: XimeInputMethodService) {
    private var conversion: JapaneseConversion? = null
    private var session = -1L
    private var schema = ""
    private var displaySession = -1L
    private var displaySchema = ""
    private var displayInput = ""
    private var displayPreeditText: String? = null
    private val engine get() = service.rimeEngine
    private fun available(): Boolean = !engine.isAsciiMode() && (engine.getCurrentSchema() in JapaneseSchemas.ids || engine.getCurrentSchema() == "jaroomaji")
    fun displayCandidates(input: String) = conversion?.takeIf { it.input == input && session == service.uiState.value.inputSessionId }?.candidates?.toList()

    /**
     * 显示层修正（key-processing 线程）：末尾尚未拼完的罗马音显示为按下的字母。
     *
     * 引擎回显会把只按下的声母显示成 っ / ん（原方案给促音/拨音准备的单字母规则）。
     * 按 (会话, 方案, 编码) 缓存结果：同一编码的后续 UI 刷新（翻页、高亮）不再探测引擎。
     */
    fun withDisplayPreedit(result: RimeProcessResult): RimeProcessResult {
        if (!available()) return result
        val input = result.inputText
        val session = service.uiState.value.inputSessionId
        val schema = service.uiState.value.currentSchemaId
        if (session != displaySession || schema != displaySchema || input != displayInput) {
            displaySession = session
            displaySchema = schema
            displayInput = input
            displayPreeditText = engine.japaneseDisplayText(input)
        }
        val display = displayPreeditText ?: return result
        return result.copy(preeditText = display)
    }

    private fun current(): JapaneseConversion? {
        if (!available() || session != service.uiState.value.inputSessionId || schema != engine.getCurrentSchema() || conversion?.input != engine.getInput()) conversion = null
        return conversion
    }
    private fun start(): JapaneseConversion? {
        current()?.let { return it }
        val input = engine.getInput()
        if (input.isEmpty()) return null
        session = service.uiState.value.inputSessionId
        schema = engine.getCurrentSchema()
        return JapaneseConversion(engine, input).also { conversion = it; it.refresh() }
    }
    private suspend fun show() {
        val active = current()
        val result = engine.getProcessResult(true).let {
            // 转换预览显示假名本身；无转换时修正未拼完的罗马音尾部（显示按下的字母）
            if (active == null) withDisplayPreedit(it)
            else it.copy(preeditText = active.preview, candidates = active.candidates, hasNextPage = false, hasPrevPage = false)
        }
        val owner = service.uiState.value.inputSessionId
        withContext(Dispatchers.Main) { if (owner == service.uiState.value.inputSessionId) service.sessionController.updateUIWithResult(result) }
    }
    suspend fun cancel() { conversion = null; show() }
    suspend fun commit(index: Int? = null): Boolean {
        val active = current() ?: return false
        if (index != null) active.choose(index)
        val text = active.preview
        val owner = session
        conversion = null
        engine.clearComposition()
        withContext(Dispatchers.Main) {
            if (owner == service.uiState.value.inputSessionId) {
                service.commitText(text)
                service.sessionController.updateUIWithResult(engine.getProcessResult(true))
            }
        }
        return true
    }
    suspend fun prepareKana(modify: Boolean) { if (current() != null) { if (modify) cancel() else commit() } }
    suspend fun handleKey(key: String): Boolean {
        if (!available()) { conversion = null; return false }
        when (key) {
            "japanese_convert" -> { val old = current(); val active = start(); if (old != null) active?.cycle(); show(); return true }
            "japanese_undo" -> { cancel(); return true }
            "japanese_left", "japanese_right" -> {
                val steps = if (key.endsWith("left")) -1 else 1
                val active = start()
                if (active == null) service.schemaController.moveJapaneseCursorFromKeyQueue(steps)
                else { active.moveRange(steps); show() }
                return true
            }
            "delete", "clear_composition", "clear_all" -> conversion = null
            "space", "enter" -> if (commit()) return true
            else -> if (current() != null) commit()
        }
        return false
    }
    suspend fun deleteKana(): Boolean {
        if (!available()) return false
        val input = engine.getInput()
        if (input.isEmpty()) { conversion = null; return false }
        conversion = null
        // 删除单位与显示同源（romajiDeleteStart）：未拼完的罗马音只删一个字母，
        // 已拼完的假名整体删。不能再用引擎读音长度探测边界——ん 与 っ 等长时
        // 判不出假名边界，会把整串一次删光（2026-09-25 复现）。
        val end = romajiDeleteStart(input)
        engine.setInput(input.take(end))
        show()
        return true
    }
}
