package com.kingzcheung.xime.clipboard

/** One recent-image budget shared by copied images and the optional screenshot feed. */
internal const val RECENT_CLIPBOARD_IMAGE_LIMIT = 50

/** Copied images kept in the app cache may use at most this much space in total. */
internal const val CLIPBOARD_IMAGE_CACHE_BYTES = 500L * 1024 * 1024

internal data class CachedImageFile(val name: String, val bytes: Long, val lastModified: Long)

/**
 * Oldest cached copies to delete so the cache fits [maxBytes]. Fixed (pinned) images are never
 * evicted, even when they alone exceed the budget.
 */
internal fun clipboardImageEvictions(files: List<CachedImageFile>, protectedNames: Set<String>,
    maxBytes: Long = CLIPBOARD_IMAGE_CACHE_BYTES): List<String> {
    var total = files.sumOf { it.bytes }
    if (total <= maxBytes) return emptyList()
    val evicted = mutableListOf<String>()
    for (file in files.filterNot { it.name in protectedNames }.sortedBy { it.lastModified }) {
        if (total <= maxBytes) break
        evicted += file.name
        total -= file.bytes
    }
    return evicted
}

/** A transient provider failure may keep cached metadata, but cannot undo an opt-out/removal. */
internal fun clipboardMediaAfterRefreshFailure(
    previous: List<ClipboardImage>, includeRecent: Boolean,
    pinnedKeys: Set<String>, hiddenUris: Set<String>,
): List<ClipboardImage> = previous.filter {
    it.uri !in hiddenUris && (includeRecent || "image:${it.uri}" in pinnedKeys)
}

internal fun retainedClipboardImages(
    copied: List<ClipboardImage>, screenshots: List<ClipboardImage>,
    pinnedKeys: Set<String>, hiddenUris: Set<String>,
): List<ClipboardImage> {
    var recent = 0
    return (copied + screenshots).distinctBy { it.uri }
        .filterNot { it.uri in hiddenUris }
        .sortedByDescending { it.timestamp }
        .filter { "image:${it.uri}" in pinnedKeys || recent++ < RECENT_CLIPBOARD_IMAGE_LIMIT }
}
