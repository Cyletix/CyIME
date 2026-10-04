package com.kingzcheung.xime.service

import com.kingzcheung.xime.settings.InputProfile

internal enum class HardwareCandidateKey { LEFT, RIGHT, UP, DOWN, SPACE, ENTER, DIGIT, CANCEL }

/** A hardware selection belongs to one editor, language and displayed candidate source. */
internal data class HardwareCandidateSnapshot(
    val editor: Long,
    val profile: InputProfile,
    val state: CandidateState,
    val english: Boolean,
    val predictionPending: Boolean = false,
    val associationContext: String = "",
    val associationLimit: Int = 10,
    val expanded: Boolean = false,
    val singleCharOnly: Boolean = false,
) {
    private val basePresentation = hardwareCandidatePresentation(state, english, predictionPending, associationLimit)
    private val waitingForPrediction = predictionPending && !english && basePresentation.association
    /** Expanded UI indices refer to a filtered view; commits need the original global index. */
    val expandedGlobalIndices: List<Int>? = if (expanded && state.expandedCandidatesLoaded && !basePresentation.association) {
        state.expandedCandidates.indices.filter { index ->
            !singleCharOnly || state.expandedCandidates[index].text.let { it.codePointCount(0, it.length) == 1 }
        }
    } else null
    val words: List<String> = when {
        expandedGlobalIndices != null -> expandedGlobalIndices.map { state.expandedCandidates[it].text }
        basePresentation.association -> basePresentation.words
        else -> basePresentation.words.take(10)
    }
    val comments: List<String> = expandedGlobalIndices?.map { state.expandedCandidates[it].comment }
        ?: basePresentation.comments.take(words.size)
    val presentation = basePresentation.copy(words = words, comments = comments)
    private val canonicalActions = state.candidates.indices.map { index ->
        state.candidateActions.getOrElse(index) { CandidateAction.engine(index) }
    }

    fun sameSource(other: HardwareCandidateSnapshot): Boolean =
        editor == other.editor && profile == other.profile && english == other.english &&
            waitingForPrediction == other.waitingForPrediction &&
            presentation.association == other.presentation.association &&
            if (presentation.association) {
                words == other.words && comments == other.comments &&
                    state.pendingEnglishText == other.state.pendingEnglishText &&
                    state.englishReplaceSupported == other.state.englishReplaceSupported &&
                    associationContext == other.associationContext
            } else {
                state.engineRevision == other.state.engineRevision &&
                    state.inputText == other.state.inputText &&
                    state.isShowingRecentClipboard == other.state.isShowingRecentClipboard &&
                    state.candidateComments == other.state.candidateComments &&
                    canonicalActions == other.canonicalActions &&
                    expanded == other.expanded && singleCharOnly == other.singleCharOnly &&
                    sameDisplayedCandidates(other)
            }

    private fun sameDisplayedCandidates(other: HardwareCandidateSnapshot): Boolean {
        val oldIndices = expandedGlobalIndices
        val newIndices = other.expandedGlobalIndices
        if (oldIndices == null || newIndices == null) {
            return oldIndices == newIndices && words == other.words && comments == other.comments
        }
        // Expanded candidates load in batches. Appending preserves every existing
        // display/global mapping; removal, insertion or reordering does not.
        return state.expandedCandidates.isPrefixOf(other.state.expandedCandidates) &&
            oldIndices.isPrefixOf(newIndices) && words.isPrefixOf(other.words) && comments.isPrefixOf(other.comments)
    }

    private fun <T> List<T>.isPrefixOf(other: List<T>): Boolean =
        size <= other.size && indices.all { this[it] == other[it] }
}

internal sealed interface HardwareCandidateDecision {
    data class Highlight(val index: Int) : HardwareCandidateDecision
    data class Page(val direction: Int) : HardwareCandidateDecision
    data class Confirm(val index: Int, val association: Boolean) : HardwareCandidateDecision
    data object Cancel : HardwareCandidateDecision
    data object Consume : HardwareCandidateDecision
    data object Host : HardwareCandidateDecision
    data object DefaultInput : HardwareCandidateDecision
    data object MoveCursor : HardwareCandidateDecision
    data object LiteralSpace : HardwareCandidateDecision
}

/** Physical arrows belong to the editor, independent of candidate-window navigation. */
internal fun physicalCandidateDecision(
    key: HardwareCandidateKey,
    snapshot: HardwareCandidateSnapshot,
    engineHasInput: Boolean?,
    digitIndex: Int? = null,
): HardwareCandidateDecision = when (key) {
    HardwareCandidateKey.CANCEL -> HardwareCandidateDecision.Cancel
    HardwareCandidateKey.LEFT, HardwareCandidateKey.RIGHT,
    HardwareCandidateKey.UP, HardwareCandidateKey.DOWN -> HardwareCandidateDecision.MoveCursor
    HardwareCandidateKey.SPACE -> when {
        snapshot.english || snapshot.state.pendingEnglishText.isNotEmpty() -> HardwareCandidateDecision.LiteralSpace
        engineHasInput == true || snapshot.state.isComposing || snapshot.state.inputText.isNotEmpty() -> {
            // Only post-commit suggestions are number/click-only. Space still
            // confirms active composition, using the current FIFO snapshot.
            if (engineHasInput == true && !snapshot.presentation.association &&
                !snapshot.state.isShowingRecentClipboard && snapshot.words.isNotEmpty()) {
                val index = if (snapshot.expandedGlobalIndices != null) 0
                    else snapshot.state.spaceCandidateIndex(snapshot.profile)
                HardwareCandidateDecision.Confirm(index, false)
            } else {
                // Missing/stale candidates must not turn Space into raw-letter Enter.
                HardwareCandidateDecision.Consume
            }
        }
        else -> HardwareCandidateDecision.LiteralSpace
    }
    HardwareCandidateKey.ENTER -> HardwareCandidateDecision.DefaultInput
    HardwareCandidateKey.DIGIT -> when {
        // An English completion must not steal the digits of hello123. Idle/pending
        // predictions and unlabelled/out-of-range digits also remain literal input.
        snapshot.english || snapshot.state.pendingEnglishText.isNotEmpty() -> HardwareCandidateDecision.DefaultInput
        digitIndex !in snapshot.words.indices || snapshot.state.isShowingRecentClipboard -> HardwareCandidateDecision.DefaultInput
        !snapshot.presentation.association && engineHasInput != true -> HardwareCandidateDecision.DefaultInput
        else -> HardwareCandidateDecision.Confirm(digitIndex!!, snapshot.presentation.association)
    }
}

/** Keeps display navigation separate from engine selection and ordinary English spacing. */
internal class HardwareCandidateNavigation {
    private data class Focus(val source: HardwareCandidateSnapshot, val index: Int)
    @Volatile private var focus: Focus? = null
    private val ownedArrows = mutableSetOf<HardwareCandidateKey>()

    fun clear() { focus = null }

    // FIFO-only hold ownership outlives the candidate list until the physical UP.
    fun startPress(key: HardwareCandidateKey, repeat: Boolean) { if (!repeat) ownedArrows.remove(key) }
    fun retainPress(key: HardwareCandidateKey) {
        if (key == HardwareCandidateKey.LEFT || key == HardwareCandidateKey.RIGHT ||
            key == HardwareCandidateKey.UP || key == HardwareCandidateKey.DOWN) ownedArrows += key
    }
    fun releasePress(key: HardwareCandidateKey) { ownedArrows.remove(key) }
    fun ownsPress(key: HardwareCandidateKey): Boolean = key in ownedArrows

    fun selectedIndex(snapshot: HardwareCandidateSnapshot): Int? = focus
        ?.takeIf { it.source.sameSource(snapshot) && it.index in snapshot.words.indices }?.index

    /** engineHasInput is read inside the input FIFO; null means the engine cannot safely be inspected. */
    fun decide(
        key: HardwareCandidateKey,
        snapshot: HardwareCandidateSnapshot,
        engineHasInput: Boolean?,
        pageAtBoundary: Boolean = false,
        digitIndex: Int? = null,
    ): HardwareCandidateDecision {
        val index = selectedIndex(snapshot)
        if (index == null) clear()
        val state = snapshot.state
        val uiHasComposition = !snapshot.english && (state.isComposing || state.inputText.isNotEmpty())
        val hasComposition = engineHasInput == true || uiHasComposition
        val hasInput = hasComposition || state.pendingEnglishText.isNotEmpty() ||
            state.candidates.isNotEmpty() || state.associationCandidates.isNotEmpty() || snapshot.predictionPending
        if (key == HardwareCandidateKey.CANCEL) {
            clear()
            return if (hasInput) HardwareCandidateDecision.Cancel else HardwareCandidateDecision.Host
        }
        if (engineHasInput == null && uiHasComposition) return HardwareCandidateDecision.Consume
        // Enter during composition keeps the established raw-input/reading commit.
        if (key == HardwareCandidateKey.ENTER) return HardwareCandidateDecision.DefaultInput
        if (key == HardwareCandidateKey.DIGIT) {
            return when {
                // English is already in the editor. Digits extend that text (hello123),
                // rather than replacing it with one of the completion suggestions.
                (snapshot.english || state.pendingEnglishText.isNotEmpty()) && index == null -> HardwareCandidateDecision.DefaultInput
                hasComposition && snapshot.words.isEmpty() -> HardwareCandidateDecision.Consume
                snapshot.words.isEmpty() -> HardwareCandidateDecision.DefaultInput
                digitIndex != null && digitIndex in snapshot.words.indices ->
                    HardwareCandidateDecision.Confirm(digitIndex, snapshot.presentation.association)
                snapshot.presentation.association -> HardwareCandidateDecision.DefaultInput
                else -> HardwareCandidateDecision.Consume
            }
        }
        val vertical = key == HardwareCandidateKey.UP || key == HardwareCandidateKey.DOWN
        val direction = if (key == HardwareCandidateKey.LEFT || key == HardwareCandidateKey.UP) -1 else 1
        if (vertical && !snapshot.presentation.association) {
            val canPage = snapshot.expanded || if (direction < 0) snapshot.presentation.hasPreviousPage else snapshot.presentation.hasNextPage
            if (canPage) {
                clear()
                return HardwareCandidateDecision.Page(direction)
            }
            return HardwareCandidateDecision.Consume
        }
        if (snapshot.words.isNotEmpty()) {
            if (key == HardwareCandidateKey.SPACE) {
                // Suggestions are number/click-only. A separator must never insert predicted text.
                return if (snapshot.presentation.association) HardwareCandidateDecision.DefaultInput
                    else HardwareCandidateDecision.Confirm(index ?: 0, false)
            }
            if (pageAtBoundary && !snapshot.presentation.association &&
                ((direction < 0 && (index ?: 0) == 0 && snapshot.presentation.hasPreviousPage) ||
                    (direction > 0 && (index ?: 0) == snapshot.words.lastIndex && snapshot.presentation.hasNextPage))) {
                clear()
                return HardwareCandidateDecision.Page(direction)
            }
            val next = ((index ?: 0) + direction).coerceIn(snapshot.words.indices)
            focus = Focus(snapshot, next)
            return HardwareCandidateDecision.Highlight(next)
        }
        if (hasComposition) {
            return HardwareCandidateDecision.Consume
        }
        return if (key == HardwareCandidateKey.SPACE || key == HardwareCandidateKey.ENTER) HardwareCandidateDecision.DefaultInput
            else HardwareCandidateDecision.Host
    }
}
