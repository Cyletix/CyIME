package com.kingzcheung.xime.clipboard

enum class ClipboardFilter(val label: String) {
    ALL("全部"), TEXT("文本"), IMAGE("图片"), LINK("链接"), ADDRESS("地址"), PHONE("电话"), EMAIL("邮箱")
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
