package com.kingzcheung.xime.clipboard

/**
 * How a clipboard record reaches the app being typed in. Fixed records hold accounts, passwords
 * and addresses, and passwords kept by 「记录密码等敏感内容」 are passwords: both are committed as
 * sensitive text, so no plugin commit event, no prediction context and no input learning ever sees
 * them. Everything else is an ordinary paste. [pinned] is every such private record.
 */
internal enum class ClipboardPasteMode { PRIVATE, PASTE }

internal fun clipboardPasteMode(text: String, pinned: List<ClipboardItem>): ClipboardPasteMode =
    if (pinned.any { it.text == text }) ClipboardPasteMode.PRIVATE else ClipboardPasteMode.PASTE
