package com.kingzcheung.xime.service

/** Selected text survives editor rejection; only an explicit retry may deliver it again. */
internal data class PendingCandidateCommit(
    val owner: InputCommandOwner,
    val text: String,
    val t9: Boolean,
    val learnSelection: Boolean = true,
    val caret: CommitCaret = CommitCaret.NONE,
    /** 通讯录的人名、号码：上屏不学习（见 [CandidateAction.isPrivate]）。 */
    val isPrivate: Boolean = false,
)
