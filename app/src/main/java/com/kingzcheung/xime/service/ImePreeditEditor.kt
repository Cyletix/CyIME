package com.kingzcheung.xime.service

import com.kingzcheung.xime.rime.PinyinEditBuffer
import com.kingzcheung.xime.rime.PinyinEditSession
import com.kingzcheung.xime.ui.keyboard.isT9Schema
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** No InputConnection writes: only the unconfirmed Rime code can be edited. */
internal class ImePreeditEditor(private val service: XimeInputMethodService) {
    private fun enqueue(schema: String, block: suspend () -> Unit) {
        val t9Queue = if (isT9Schema(schema)) service.keyboardCallbacks?.onT9RunLiteralInput else null
        if (t9Queue != null) t9Queue(block) else service.keyRouter.postRimeJob { block() }
    }

    fun open(reply: (PinyinEditSession?) -> Unit) {
        val state = service.uiState.value
        if (state.isAsciiMode || !PinyinEditBuffer.supports(state.currentSchemaId)) { reply(null); return }
        enqueue(state.currentSchemaId) {
            // Drain a preceding nine-key UI snapshot before reading its protected partial segments.
            withContext(Dispatchers.Main) { Unit }
            if (service.uiState.value.inputSessionId != state.inputSessionId ||
                service.uiState.value.currentSchemaId != state.currentSchemaId) return@enqueue
            val engine = service.rimeEngine
            val snapshot = engine.readPinyinEditSnapshot()
            val input = snapshot.getOrNull(0).orEmpty()
            val confirmed = snapshot.getOrNull(2)?.toIntOrNull()?.coerceIn(0, input.length) ?: 0
            val t9 = isT9Schema(state.currentSchemaId)
            val rawText = if (t9) snapshot.getOrNull(4).orEmpty() else input.drop(confirmed)
            val text = PinyinEditBuffer.normalized(rawText)
            val unsupportedCode = rawText.any { it !in 'a'..'z' && it !in 'A'..'Z' && it != '\'' && !it.isWhitespace() && it != 'ü' }
            val prefix = if (t9) service.t9PartialSegments.joinToString("") { it.text }
                else snapshot.getOrNull(3).orEmpty()
            // Never invent letters for a numeric-only failed T9 translation.
            val session = if (input.isEmpty() || text.isEmpty() || unsupportedCode) null
            else PinyinEditSession(state.inputSessionId, state.currentSchemaId, input,
                if (t9) "" else input.take(confirmed), prefix, text,
                if (t9) text.length else ((snapshot.getOrNull(1)?.toIntOrNull() ?: input.length) - confirmed).coerceIn(0, text.length), t9, if (t9) engine.t9GetRemainingDigits() else "", service.uiState.value.t9RightCandidateSelectedCount)
            withContext(Dispatchers.Main) {
                if (service.uiState.value.inputSessionId == state.inputSessionId &&
                    service.uiState.value.currentSchemaId == state.currentSchemaId) {
                    if (unsupportedCode) android.widget.Toast.makeText(service, "请先选择左侧拼音，再编辑编码", android.widget.Toast.LENGTH_SHORT).show()
                    reply(session)
                }
            }
        }
    }

    private fun isCurrent(session: PinyinEditSession): Boolean = session.isActive() && isOwnerCurrent(session)

    private fun isOwnerCurrent(session: PinyinEditSession): Boolean = service.uiState.value.let {
        it.inputSessionId == session.inputSessionId && it.currentSchemaId == session.schemaId && !it.isAsciiMode &&
            (!session.isT9 || it.t9RightCandidateSelectedCount == session.t9SelectionCount)
    }

    fun apply(session: PinyinEditSession, text: String, caret: Int, reply: (Boolean) -> Unit) {
        enqueue(session.schemaId) {
            val state = service.uiState.value
            if (!session.isActive() || state.inputSessionId != session.inputSessionId || state.currentSchemaId != session.schemaId || state.isAsciiMode) {
                withContext(Dispatchers.Main) { reply(false) }; return@enqueue
            }
            if (session.isT9 && service.t9PartialSegments.joinToString("") { it.text } != session.protectedText) {
                withContext(Dispatchers.Main) { reply(false) }; return@enqueue
            }
            val normalized = PinyinEditBuffer.normalized(text)
            val engine = service.rimeEngine
            val result = engine.editPinyinAndReadState {
                if (session.isT9) engine.applyT9PinyinEdit(session.expectedInput, normalized,
                    session.t9RemainingDigits, session.schemaId) { isCurrent(session) }
                else engine.applyPinyinEdit(session.expectedInput, session.protectedInput + normalized,
                    session.protectedInput.length + caret.coerceIn(0, normalized.length), session.protectedInput.length,
                    session.protectedText, session.schemaId) { isCurrent(session) }
            }
            if (result != null) {
                withContext(Dispatchers.Main) {
                    if (!isOwnerCurrent(session)) { reply(false); return@withContext }
                    if (session.isT9) service.keyboardCallbacks?.onT9RefreshAfterPreeditEdit?.invoke()
                    service.sessionController.updateUIWithResult(result)
                    reply(true)
                }
            } else withContext(Dispatchers.Main) { reply(false) }
        }
    }
}
