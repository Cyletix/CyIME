package com.kingzcheung.xime.clipboard

/** One recent-image budget shared by copied images and the optional screenshot feed. */
internal const val RECENT_CLIPBOARD_IMAGE_LIMIT = 16

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
