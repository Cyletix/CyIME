package com.kingzcheung.xime.service

import com.kingzcheung.xime.settings.JapaneseSchemas
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 所有方法在按键队列串行调用；会话或原编码改变就作废旧转换预览。 */
internal class JapaneseInputController(private val service: XimeInputMethodService) {
    private var conversion: JapaneseConversion? = null
    private var session = -1L
    private var schema = ""
    private val engine get() = service.rimeEngine
    private fun available(): Boolean = !engine.isAsciiMode() && (engine.getCurrentSchema() in JapaneseSchemas.ids || engine.getCurrentSchema() == "jaroomaji")
    fun displayCandidates(input: String) = conversion?.takeIf { it.input == input && session == service.uiState.value.inputSessionId }?.candidates?.toList()
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
        val result = engine.getProcessResult(true).let { if (active == null) it else it.copy(preeditText = active.preview, candidates = active.candidates, hasNextPage = false, hasPrevPage = false) }
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
        val end = engine.previousJapaneseBoundary(input)
        engine.setInput(input.take(end))
        show()
        return true
    }
}
