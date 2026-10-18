package com.kingzcheung.xime.clipboard

enum class ClipboardFilter(val label: String) {
    ALL("全部"), TEXT("文本"), LINK("链接"), ADDRESS("地址"), PHONE("电话"), EMAIL("邮箱"), IMAGE("图片")
}

sealed interface ClipboardCard {
    val key: String
    val timestamp: Long
    data class Text(val item: ClipboardItem) : ClipboardCard {
        override val key = "text:${item.id}"
        override val timestamp = item.timestamp
    }
    data class Image(val item: ClipboardImage) : ClipboardCard {
        override val key = "image:${item.uri}"
        override val timestamp = item.timestamp
    }
}

private val emailPattern = Regex("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", RegexOption.IGNORE_CASE)
private val linkPattern = Regex("(?:https?://|www\\.)\\S+", RegexOption.IGNORE_CASE)
private val phonePattern = Regex("(?:\\+?[0-9][0-9 ()-]{7,}[0-9])")
private val addressPattern = Regex("〒\\s*[0-9]{3}|[都道府県].*[市区町村]|[省市自治区].*[路街巷弄号]|[0-9]+丁目|[0-9]+番地")

internal fun clipboardMatches(card: ClipboardCard, filter: ClipboardFilter): Boolean {
    if (filter == ClipboardFilter.ALL) return true
    if (card is ClipboardCard.Image) return filter == ClipboardFilter.IMAGE
    val text = (card as ClipboardCard.Text).item.text
    return when (filter) {
        ClipboardFilter.TEXT -> true
        ClipboardFilter.LINK -> linkPattern.containsMatchIn(text)
        ClipboardFilter.EMAIL -> emailPattern.containsMatchIn(text)
        ClipboardFilter.ADDRESS -> addressPattern.containsMatchIn(text)
        ClipboardFilter.PHONE -> phonePattern.findAll(text).any { match -> match.value.count(Char::isDigit) in 10..15 }
        else -> false
    }
}

internal fun clipboardCards(text: List<ClipboardItem>, images: List<ClipboardImage>, filter: ClipboardFilter,
    pinned: Set<String>): List<ClipboardCard> =
    (text.map { ClipboardCard.Text(it) } + images.map { ClipboardCard.Image(it) })
        .distinctBy { it.key }.filter { clipboardMatches(it, filter) }
        .sortedWith(compareByDescending<ClipboardCard> { it.key in pinned }.thenByDescending { it.timestamp })

/** Text pins live in Room; the board preferences retain only image identities. */
internal fun clipboardPinnedKeys(text: List<ClipboardItem>, storedPins: Set<String>,
    pendingTextPins: Map<Long, Boolean> = emptyMap()): Set<String> = buildSet {
    addAll(storedPins.filter { it.startsWith("image:") })
    text.forEach { item ->
        if (pendingTextPins[item.id] ?: item.isPinned) add("text:${item.id}")
    }
}

/** Keep immediate feedback until the observed database state acknowledges the change. */
internal fun pendingClipboardTextPins(text: List<ClipboardItem>, pending: Map<Long, Boolean>): Map<Long, Boolean> {
    val stored = text.associate { it.id to it.isPinned }
    return pending.filter { (id, pinned) -> id in stored && stored[id] != pinned }
}

internal fun legacyClipboardTextPinIds(storedPins: Set<String>): Set<Long> = storedPins
    .filter { it.startsWith("text:") }.mapNotNull { it.removePrefix("text:").toLongOrNull() }
    .filter { it > 0 }.toSet()

internal fun clipboardColumnCount(widthDp: Float): Int = (widthDp / 160f).toInt().coerceIn(1, 5)

// Keep the existing placeholder shape for the whole thumbnail lifetime. Decoding
// must not change masonry geometry as more images enter the viewport.
internal const val ClipboardThumbnailAspectRatio = .8f

internal data class ClipboardBoardContent(
    /** The newest copies, shown above the pins. */
    val recent: List<ClipboardCard>,
    val pinned: List<ClipboardCard>,
    /** Older copies, shown below the pins only after the toolbar toggle opens them. */
    val older: List<ClipboardCard> = emptyList(),
    /** All unpinned records in the category, including those folded away. */
    val recentCount: Int,
    /** Folding recent copies only makes room for pins; without pins below there is nothing to fold. */
    val recentCollapsible: Boolean = false,
) {
    /** Newest copies, then pins, then any opened older copies: opening never pushes the pins down. */
    val visible: List<ClipboardCard> get() = recent + pinned + older
    val hiddenRecent: Int get() = recentCount - recent.size - older.size
    /** Older copies after the newest row, whether folded or open; this is what the toggle acts on. */
    val olderCount: Int get() = recentCount - recent.size
}

/** Rows of newest copies kept above the pins, so the pins stay within reach. */
internal const val ClipboardRecentRows = 1

/**
 * The newest [ClipboardRecentRows] rows of copies come first and pins always follow them.
 * Older copies are folded away, or listed after the pins when opened. Folding is
 * presentation only: neither persistence nor history is truncated.
 */
internal fun clipboardBoardContent(cards: List<ClipboardCard>, pins: Set<String>, columns: Int,
    recentExpanded: Boolean): ClipboardBoardContent {
    val (fixed, recent) = cards.partition { it.key in pins }
    val limit = columns.coerceAtLeast(1) * ClipboardRecentRows
    val collapsible = fixed.isNotEmpty() && recent.size > limit
    return ClipboardBoardContent(
        recent = if (collapsible) recent.take(limit) else recent,
        pinned = fixed,
        older = if (collapsible && recentExpanded) recent.drop(limit) else emptyList(),
        recentCount = recent.size,
        recentCollapsible = collapsible,
    )
}

/** A tall picture or long text must not turn the collapsed row into the whole panel. */
internal fun clipboardPinnedPreviewHeight(recordsHeightDp: Float): Float =
    (recordsHeightDp.coerceAtLeast(0f) * .35f).coerceAtMost(112f)
