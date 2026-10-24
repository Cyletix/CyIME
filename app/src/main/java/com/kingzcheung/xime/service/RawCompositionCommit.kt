package com.kingzcheung.xime.service

/** Raw alphabetic encoding is not the formatted preedit. T9 has numeric engine input,
 * so use its decoded reading, keeping partial Chinese selections but removing display separators. */
internal fun rawCompositionCommitText(engineInput: String, preedit: String, t9: Boolean): String =
    if (t9 && preedit.isNotEmpty()) preedit.filterNot { it.isWhitespace() || it == '\'' }
    else engineInput

/**
 * Text that finishes an unfinished composition before a literal (symbol, digit) or a language switch.
 * Gboard rule (user 2026-10-24): symbols and the language key end it as typed — the letters, never the
 * first candidate, and no space; [asTyped] false keeps the first-candidate commit for voice/handwriting.
 * T9 takes the same decoded reading as Enter (partial selections are already in [preedit]).
 */
internal fun compositionCommitPrefix(asTyped: Boolean, engineInput: String, preedit: String, t9: Boolean,
    merged14: Boolean, partial: String, englishDelivered: Boolean, japanesePreview: String?,
    firstCandidate: () -> String): String = when {
    // English has already been delivered character by character.
    englishDelivered && engineInput.isEmpty() -> ""
    engineInput.isEmpty() -> partial
    japanesePreview != null -> partial + japanesePreview
    asTyped -> rawCompositionCommitText(if (merged14) engineInput.lowercase() else engineInput, preedit, t9)
    else -> partial + firstCandidate()
}
