package com.kingzcheung.xime.service

import com.kingzcheung.xime.settings.InputLanguage
import com.kingzcheung.xime.settings.InputMode
import com.kingzcheung.xime.settings.InputProfile
import com.kingzcheung.xime.settings.InputScheme
import com.kingzcheung.xime.settings.LayoutKind

/** Display focus is tied to candidate identities, never a persistent engine selection. */
data class CandidateFocus(
    val index: Int,
    private val revision: Long,
    private val input: String,
    private val words: List<String>,
    private val comments: List<String>,
    private val actions: List<CandidateAction>,
) {
    internal fun matches(state: CandidateState): Boolean = state.isComposing &&
        !state.isShowingRecentClipboard && revision == state.engineRevision && input == state.inputText &&
        words == state.candidates && comments == state.candidateComments && actions == state.candidateActions

    companion object {
        internal fun at(index: Int, state: CandidateState) = CandidateFocus(index, state.engineRevision,
            state.inputText, state.candidates, state.candidateComments, state.candidateActions)
    }
}

fun CandidateState.usesT9CandidateNavigation(profile: InputProfile): Boolean =
    profile.language == InputLanguage.CHINESE && profile.scheme == InputScheme.PINYIN &&
        profile.layout.kind == LayoutKind.T9 && profile.mode == InputMode.KEYBOARD &&
        isComposing && inputText.isNotEmpty() && pendingEnglishText.isEmpty() && !isShowingRecentClipboard

/** Missing/changed candidates reset to the first item, including after partial commit or editing. */
val CandidateState.highlightedCandidateIndex: Int get() = candidateFocus
    ?.takeIf { it.index in candidates.indices && it.matches(this) }?.index ?: 0

internal fun CandidateState.advanceT9Candidate(profile: InputProfile): CandidateState =
    moveT9Candidate(profile, 1)

internal fun CandidateState.retreatT9Candidate(profile: InputProfile): CandidateState =
    moveT9Candidate(profile, -1)

private fun CandidateState.moveT9Candidate(profile: InputProfile, direction: Int): CandidateState {
    if (!usesT9CandidateNavigation(profile) || candidates.isEmpty()) return this
    val index = (highlightedCandidateIndex + direction + candidates.size) % candidates.size
    return copy(candidateFocus = CandidateFocus.at(index, this))
}

internal fun CandidateState.spaceCandidateIndex(profile: InputProfile): Int =
    if (usesT9CandidateNavigation(profile)) highlightedCandidateIndex else 0
