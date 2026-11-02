package com.kingzcheung.xime.service

import com.kingzcheung.xime.rime.RimeEngine
import com.kingzcheung.xime.rime.RimeProcessResult
import com.kingzcheung.xime.settings.LayoutKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Owned by one delete-key touch. The shared key FIFO orders it with ordinary typing. */
internal class ImeDistanceBackspace(private val service: XimeInputMethodService, private val editor: Long) {
    private val initial = service.uiState.value
    private val ticket = service.inputReadiness.ticket()
    private val ic = service.currentInputConnection
    private var document: DistanceBackspace? = null
    private var code: DragCode? = null
    private var english = ""
    private var invalid = false
    /** Fixed by the first non-zero count: negative counts are a right swipe (forward delete). */
    private var forward: Boolean? = null
    @Volatile private var desired = -1

    private fun current() = ticket != null && service.inputReadiness.accepts(ticket) &&
        service.uiState.value.let { it.inputSessionId == editor && it.currentSchemaId == initial.currentSchemaId &&
            it.isAsciiMode == initial.isAsciiMode && !it.inputTarget.isInternal && !it.showQuickSendForm } &&
        !service.keyRouter.hasPendingCandidateCommit

    fun move(signed: Int) {
        if (signed != 0 && forward == null) forward = signed < 0
        // The gesture never changes direction mid-touch; a stray opposite count is ignored.
        if (signed != 0 && (signed < 0) != forward) return
        val count = kotlin.math.abs(signed)
        val forward = this.forward == true
        desired = count
        if (!current() || ic == null || service.isSecretEditor() || service.currentInputConnection !== ic ||
            service.keyboardCallbacks?.onPreeditKeyInput != null) return
        service.keyRouter.postRimeJob("distance-backspace") {
            if (invalid || !current()) return@postRimeJob
            val engine = service.rimeEngine
            val composing = engine.compositionActiveForDeletion()
            if (document == null && code == null) {
                if (count == 0) return@postRimeJob
                val candidates = service.candidateState.value
                if (composing == true && !initial.isAsciiMode && !initial.inputProfile.capabilities.japaneseConversion) {
                    if (service.t9PartialSegments.isEmpty()) code = DragCode.open(engine, initial, forward)
                } else if (composing == false && !candidates.isComposing && candidates.inputText.isEmpty() &&
                    candidates.preeditText.isEmpty() && service.t9PartialSegments.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        if (current() && service.currentInputConnection === ic) {
                            english = service.candidateState.value.pendingEnglishText
                            document = DistanceBackspace.open(ic, forward)
                        }
                    }
                }
                if (document == null && code == null) { invalid = true; return@postRimeJob }
            }
            if (count != desired) return@postRimeJob
            val preedit = code
            if (preedit != null) {
                val result = preedit.move(engine, count, ::current) ?: run { invalid = true; return@postRimeJob }
                withContext(Dispatchers.Main) {
                    if (current() && service.currentInputConnection === ic) {
                        if (preedit.t9) service.keyboardCallbacks?.onT9RefreshAfterPreeditEdit?.invoke()
                        service.sessionController.updateUIWithResult(result)
                    }
                }
            } else {
                val text = document
                if (text == null || composing != false || service.t9PartialSegments.isNotEmpty()) {
                    invalid = true; return@postRimeJob
                }
                withContext(Dispatchers.Main) {
                    val cs = service.candidateState.value
                    if (!current() || cs.isComposing || cs.inputText.isNotEmpty() || cs.preeditText.isNotEmpty() ||
                        service.currentInputConnection !== ic || !text.move(count)) { invalid = true; return@withContext }
                    // Text before the caret is untouched: keep prediction, association and English state.
                    if (text.forward) return@withContext
                    service.predictionManager.invalidatePendingPredictions()
                    service.predictionManager.replaceCommittedText(text.remainingContext)
                    val pending = english.dropLast(text.removedUnits.coerceAtMost(english.length))
                    service.candidateState.value = cs.copy(pendingEnglishText = pending.takeIf {
                        text.remainingContext.endsWith(it) }.orEmpty(), associationCandidates = emptyList(),
                        deletionRecovery = emptyList(), isShowingRecentClipboard = false, glidedWord = "")
                }
            }
        }
    }
}

/** Keep ambiguous 14-key letters and raw 9-key digits; never replay the displayed pinyin as typed keys. */
internal class DragCode private constructor(
    private val original: String, private val caret: Int, private val schema: String, val t9: Boolean,
    private val forward: Boolean,
) {
    private var expected = original
    private var revision = 0L

    fun move(engine: RimeEngine, count: Int, current: () -> Boolean): RimeProcessResult? {
        val nextCaret = if (forward) caret else (caret - count).coerceAtLeast(0)
        val next = if (forward) original.removeRange(caret, (caret + count).coerceAtMost(original.length))
            else original.removeRange(nextCaret, caret)
        val result = engine.editPinyinAndReadState {
            if (revision != 0L && !engine.isCandidateRevisionCurrent(revision)) return@editPinyinAndReadState false
            if (t9) engine.moveDeleteDragDigits(expected, next, schema, current)
            else engine.applyPinyinEdit(expected, next, nextCaret, expectedSchema = schema, isStillCurrent = current)
        } ?: return null
        expected = result.inputText
        revision = result.engineRevision
        return result
    }

    companion object {
        fun open(engine: RimeEngine, state: InputUIState, forward: Boolean = false): DragCode? {
            val (snap, digits) = engine.readDeleteDragInput(state.currentSchemaId) ?: return null
            val raw = snap.getOrNull(0)?.takeIf { it.isNotEmpty() && it.all { ch -> ch.code in 32..126 } } ?: return null
            val caret = snap.getOrNull(1)?.toIntOrNull()?.takeIf { it in 0..raw.length } ?: return null
            // Rebuilding a selected/locked reading would lose information on undo.
            if (snap.getOrNull(2) != "0" || !snap.getOrNull(3).isNullOrEmpty()) return null
            val t9 = digits.isNotEmpty()
            if (!t9 && state.inputProfile.layout.kind == LayoutKind.T9) return null
            if (t9 && (caret != raw.length || !raw.all { it in '2'..'9' } || digits != raw)) return null
            // Nine-key digits always end at the caret: nothing after it to delete.
            if (forward && (t9 || caret == raw.length)) return null
            return DragCode(raw, caret, state.currentSchemaId, t9, forward)
        }
    }
}
