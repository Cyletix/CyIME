package com.kingzcheung.xime.clipboard

internal data class CopiedImageSource(val uri: String, val resolvedMime: String?)

/** ClipDescription describes the entire clip, not each item in a mixed clip. */
internal fun copiedImageSource(items: List<CopiedImageSource>, advertisedMime: String?): CopiedImageSource? {
    val readable = items.filter { it.uri.startsWith("content://") }
    return readable.firstOrNull { it.resolvedMime?.startsWith("image/") == true }
        ?: readable.firstOrNull {
            advertisedMime?.startsWith("image/") == true &&
                (it.resolvedMime.isNullOrBlank() || it.resolvedMime == "application/octet-stream")
        }
}
