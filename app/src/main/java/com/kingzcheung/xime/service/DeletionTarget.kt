package com.kingzcheung.xime.service

internal enum class DeletionTarget { WAIT, COMPOSITION, ENGLISH, CANDIDATES, DOCUMENT }

/** UI can lag the engine. An unavailable engine is never evidence that the editor is idle. */
internal fun deletionTarget(engineComposing: Boolean?, uiComposing: Boolean, partialSegments: Boolean,
    pendingEnglish: Boolean, candidatesVisible: Boolean, predictionPending: Boolean): DeletionTarget = when {
    engineComposing == null -> DeletionTarget.WAIT
    engineComposing || uiComposing || partialSegments -> DeletionTarget.COMPOSITION
    pendingEnglish -> DeletionTarget.ENGLISH
    candidatesVisible || predictionPending -> DeletionTarget.CANDIDATES
    else -> DeletionTarget.DOCUMENT
}

/**
 * 设置「退格直接删字」打开时，只有联想词（含在途的联想）挡着的那一下退格改成删字；
 * 剪贴板胶囊照旧先收起，不删正文。
 */
internal fun DeletionTarget.skippingAssociation(enabled: Boolean, clipboardShown: Boolean): DeletionTarget =
    if (enabled && this == DeletionTarget.CANDIDATES && !clipboardShown) DeletionTarget.DOCUMENT else this
