package com.kingzcheung.xime.service

/** Orders editor delivery before any learning/cleanup. Rejection has no accepted side effects. */
internal suspend fun PendingCandidateCommit.deliver(
    submit: suspend (String) -> TextCommitResult,
    rejected: suspend (PendingCandidateCommit) -> Unit,
    accepted: suspend (PendingCandidateCommit) -> Unit,
): TextCommitResult {
    check(InputCommandOwner.requireOwner() === owner) { "Commit must retain its original command owner" }
    owner.validate()
    val result = submit(text)
    if (result.accepted) accepted(this) else rejected(this)
    return result
}
