package com.kingzcheung.xime.service

import com.kingzcheung.xime.rime.PinyinSyllableChoices
import com.kingzcheung.xime.settings.InputLanguage
import com.kingzcheung.xime.settings.InputScheme
import com.kingzcheung.xime.settings.LayoutKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Shares the input FIFO; choosing a reading never commits text or moves the host caret. */
internal class ImePinyinSyllableSelector(private val service: XimeInputMethodService) {
    private fun eligible(state: InputUIState): Boolean = !state.isAsciiMode &&
        state.inputProfile.language == InputLanguage.CHINESE &&
        state.inputProfile.scheme in setOf(InputScheme.PINYIN, InputScheme.DOUBLE_PINYIN) &&
        state.inputProfile.layout.kind != LayoutKind.T9

    fun request(revision: Long, reply: (PinyinSyllableChoices?) -> Unit) {
        val owner = service.uiState.value
        if (!eligible(owner)) { reply(null); return }
        service.keyRouter.postRimeJob {
            if (!current(owner.inputSessionId, owner.currentSchemaId)) return@postRimeJob
            val snapshot = service.rimeEngine.readPinyinSyllableChoices(revision) {
                current(owner.inputSessionId, owner.currentSchemaId)
            }
                ?.copy(inputSessionId = owner.inputSessionId)
            withContext(Dispatchers.Main) {
                if (current(owner.inputSessionId, owner.currentSchemaId) &&
                    service.candidateState.value.engineRevision == revision) reply(snapshot)
            }
        }
    }

    fun select(snapshot: PinyinSyllableChoices, index: Int, reply: (PinyinSyllableChoices?) -> Unit) {
        if (!current(snapshot.inputSessionId, snapshot.schemaId)) { reply(null); return }
        service.keyRouter.postRimeJob {
            if (!current(snapshot.inputSessionId, snapshot.schemaId)) return@postRimeJob
            val result = service.rimeEngine.selectPinyinSyllable(snapshot, index) {
                current(snapshot.inputSessionId, snapshot.schemaId)
            }
            val next = result?.let { service.rimeEngine.readPinyinSyllableChoices(it.engineRevision) {
                current(snapshot.inputSessionId, snapshot.schemaId)
            } }
                ?.copy(inputSessionId = snapshot.inputSessionId)
            withContext(Dispatchers.Main) {
                if (!current(snapshot.inputSessionId, snapshot.schemaId)) return@withContext
                if (result != null) service.sessionController.updateUIWithResult(result, updateInputBoxComposition = false)
                reply(next)
            }
        }
    }

    private fun current(session: Long, schema: String): Boolean = service.uiState.value.let {
        eligible(it) && it.inputSessionId == session && it.currentSchemaId == schema
    }
}
