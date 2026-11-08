package com.kingzcheung.xime.ui.keyboard

sealed interface CandidateBarState {

    data object Idle : CandidateBarState

    data class ChineseCandidates(
        val candidates: List<String> = emptyList(),
        val comments: List<String> = emptyList(),
        val inputText: String = "",
        val preeditText: String = "",
        val hasMore: Boolean = false,
        val associationCandidates: List<String> = emptyList(),
        val highlightIndex: Int = 0,
    ) : CandidateBarState

    data class AssociationOnly(
        val candidates: List<String> = emptyList(),
        val comments: List<String> = emptyList(),
        val hasMore: Boolean = false,
        val highlightIndex: Int = -1,
    ) : CandidateBarState

    data class EnglishCandidates(
        val candidates: List<String> = emptyList(),
        val comments: List<String> = emptyList(),
        val pendingText: String = "",
    ) : CandidateBarState

    data class ClipboardDisplay(
        val candidates: List<String> = emptyList(),
        val smsVerificationCode: String? = null,
        val image: com.kingzcheung.xime.clipboard.ClipboardImage? = null,
        /** [candidates] 开头这几项是提取出来的链接、长数字，放在前面的短胶囊；0 时只有原文一个胶囊。 */
        val extractedCount: Int = 0,
    ) : CandidateBarState

    data class Calculator(
        val candidates: List<String> = emptyList(),
        val comments: List<String> = emptyList(),
        val expression: String = "",
        val result: String = "",
    ) : CandidateBarState

    companion object {
        fun from(
            candidates: List<String>,
            candidateComments: List<String>,
            inputText: String,
            preeditText: String = "",
            isComposing: Boolean,
            associationCandidates: List<String>,
            isShowingRecentClipboard: Boolean,
            hasNextPage: Boolean,
            isCalculatorActive: Boolean = false,
            smsVerificationCode: String? = null,
            clipboardImage: com.kingzcheung.xime.clipboard.ClipboardImage? = null,
            clipboardExtractedCount: Int = 0,
            highlightIndex: Int = 0,
            hardwareHighlightIndex: Int? = null,
            associationHighlightIndex: Int? = null,
            expandAssociations: Boolean = true,
        ): CandidateBarState {
            val hasCandidates = candidates.isNotEmpty()
            val hasAssociations = associationCandidates.isNotEmpty()
            val hasInput = inputText.isNotEmpty()
            return when {
                isShowingRecentClipboard && (hasCandidates || clipboardImage != null) ->
                    ClipboardDisplay(candidates = candidates, smsVerificationCode = smsVerificationCode, image = clipboardImage,
                        extractedCount = clipboardExtractedCount.coerceIn(0, candidates.size))
                isCalculatorActive && hasCandidates ->
                    Calculator(candidates = candidates, comments = candidateComments)
                isComposing && (hasCandidates || hasInput) ->
                    ChineseCandidates(
                        candidates = candidates,
                        comments = candidateComments,
                        inputText = inputText,
                        preeditText = preeditText,
                        hasMore = hasCandidates && hasNextPage,
                        associationCandidates = associationCandidates,
                        highlightIndex = hardwareHighlightIndex ?: highlightIndex,
                    )
                !isComposing && !hasInput && hasAssociations && !hasCandidates ->
                    AssociationOnly(
                        candidates = associationCandidates,
                        hasMore = expandAssociations && associationCandidates.size > 1,
                        highlightIndex = hardwareHighlightIndex ?: associationHighlightIndex ?: -1,
                    )
                hasCandidates || hasInput ->
                    ChineseCandidates(
                        candidates = candidates,
                        comments = candidateComments,
                        inputText = inputText,
                        preeditText = preeditText,
                        hasMore = hasCandidates && hasNextPage,
                        highlightIndex = hardwareHighlightIndex ?: highlightIndex,
                    )
                else -> Idle
            }
        }
    }
}
