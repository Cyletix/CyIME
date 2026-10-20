package com.kingzcheung.xime.clipboard

/**
 * How a clipboard record reaches the app being typed in. Fixed records hold accounts, passwords
 * and addresses: they are committed as sensitive text, so no plugin commit event, no prediction
 * context and no input learning ever sees them. Everything else is an ordinary paste.
 */
internal enum class ClipboardPasteMode { PRIVATE, PASTE }

internal fun clipboardPasteMode(text: String, pinned: List<ClipboardItem>): ClipboardPasteMode =
    if (pinned.any { it.text == text }) ClipboardPasteMode.PRIVATE else ClipboardPasteMode.PASTE
