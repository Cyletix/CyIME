package com.kingzcheung.xime.service

/** Selected text survives editor rejection; only an explicit retry may deliver it again. */
internal data class PendingCandidateCommit(
    val owner: InputCommandOwner,
    val text: String,
    val t9: Boolean,
)
