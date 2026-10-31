package com.kingzcheung.xime.service

import com.kingzcheung.xime.rime.RimeCandidate

data class CandidateState(
    val candidates: List<String> = emptyList(),
    val candidateComments: List<String> = emptyList(),
    val inputText: String = "",
    val preeditText: String = "",
    val isComposing: Boolean = false,
    val hasNextPage: Boolean = false,
    val hasPrevPage: Boolean = false,
    val associationCandidates: List<String> = emptyList(),
    val pendingEnglishText: String = "",
    /** 刚由滑行整词上屏的词；光标仍紧跟它时，下一个点按字母先补空格。 */
    val glidedWord: String = "",
    val isShowingRecentClipboard: Boolean = false,
    /** 当前宿主是否支持英文候选的"回删替换"（终端等受限宿主为 false，不展示英文候选）。 */
    val englishReplaceSupported: Boolean = true,
    /** 候选词变换映射（插件 candidate_transform 能力）：与 [candidates] 平行；
     *  空列表 = 纯引擎语义（显示 index 即引擎 index）。见 CandidateAction。 */
    val candidateActions: List<CandidateAction> = emptyList(),
    /** 当前引擎候选中用户以前打过的词（按文字），候选右上角画学习标记。 */
    val learnedCandidates: Set<String> = emptySet(),
    /** 当前引擎候选中纠错来的词（按文字）→ 引擎给的逐音节纠正，候选旁标出怎么改的。 */
    val correctedCandidates: Map<String, String> = emptyMap(),
    /** 跨页全量候选（仅候选展开态时由服务层填充，供本地分页与单字筛选）。
     *  空列表 = 未填充或引擎无候选。全局索引用于 selectCandidateByGlobalIndex。 */
    val expandedCandidates: List<RimeCandidate> = emptyList(),
    val expandedCandidatesLoaded: Boolean = false,
    /** Identity of the native candidate list, independent of displayed preedit text. */
    val engineRevision: Long = 0L,
    val traceEventId: Int = 0,
    val smsVerificationCode: String? = null,
    val clipboardImage: com.kingzcheung.xime.clipboard.ClipboardImage? = null,
    val candidateFocus: CandidateFocus? = null,
    /** 「删字后给同音字」放上去的那一组（第一项是删掉的那一段）；联想位置换成别的词后自然不再算。见 DeletionRecovery。 */
    val deletionRecovery: List<String> = emptyList(),
)

/** 候选栏上现在是删字后放回的那一组：不挡退格、不被空格上屏、不进九键选词。 */
internal val CandidateState.isDeletionRecovery: Boolean
    get() = deletionRecovery.isNotEmpty() && associationCandidates == deletionRecovery

/** Appending expanded pages or updating suggestions does not change existing candidate identities. */
internal fun CandidateState.hasSameSelectionSource(current: CandidateState): Boolean =
    engineRevision == current.engineRevision && inputText == current.inputText &&
        isShowingRecentClipboard == current.isShowingRecentClipboard &&
        candidates == current.candidates && candidateComments == current.candidateComments &&
        candidateActions == current.candidateActions &&
        expandedCandidates.size <= current.expandedCandidates.size &&
        expandedCandidates.indices.all { expandedCandidates[it] == current.expandedCandidates[it] }
