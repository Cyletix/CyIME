package com.kingzcheung.xime.service

/** Accepted means the editor accepted the request, not that it persisted or rendered it. */
internal enum class TextCommitResult {
    ACCEPTED_HOST, ACCEPTED_INTERNAL, REJECTED, NO_CONNECTION;
    val accepted: Boolean get() = this == ACCEPTED_HOST || this == ACCEPTED_INTERNAL
}
