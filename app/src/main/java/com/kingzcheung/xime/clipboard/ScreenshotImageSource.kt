package com.kingzcheung.xime.clipboard

import java.util.Locale

/** Only the optional MediaStore feed uses this policy. Explicitly copied images are unrestricted. */
internal object ScreenshotImageSource {
    // Keep the previous 60-image feed's metadata budget, before applying the shared
    // 16-item recent limit. The provider and the local cursor both enforce this bound.
    const val MAX_QUERY_ROWS = 60
    data class PinnedMediaImage(val id: Long, val uri: String)
    private val pinnedMediaUri = Regex("^content://media/(?:external|external_primary)/images/media/([1-9][0-9]*)$")

    /** Only previously pinned MediaStore image identities qualify for a direct lookup. */
    fun pinnedMediaImages(pinnedKeys: Set<String>): List<PinnedMediaImage> = pinnedKeys.mapNotNull { key ->
        if (!key.startsWith("image:")) return@mapNotNull null
        val uri = key.removePrefix("image:")
        val id = pinnedMediaUri.matchEntire(uri)?.groupValues?.get(1)?.toLongOrNull()
            ?: return@mapNotNull null
        PinnedMediaImage(id, uri)
    }

    private val folders = listOf(
        "screenshots", "screenshot", "screen captures", "screen capture",
        "截图", "截屏", "屏幕截图", "螢幕截圖", "螢幕擷取畫面", "スクリーンショット",
    )
    private val namePrefixes = listOf(
        "screenshot", "screen shot", "screen_shot", "screen-shot",
        "截图", "截屏", "屏幕截图", "螢幕截圖", "螢幕擷取畫面", "スクリーンショット",
    )
    // A dated screenshot filename also works when the user changes the screenshot save folder.
    // Merely mentioning "screenshot" in an ordinary photo/download filename is insufficient.
    private val datedName = Regex(
        "^(?:${namePrefixes.joinToString("|") { Regex.escape(it) }})[ _-]*" +
            "[0-9]{4}[-_.]?[0-9]{2}[-_.]?[0-9]{2}(?=[ _t.-]|[0-9]|$)",
    )

    fun matches(displayName: String?, bucketName: String?, relativePath: String?): Boolean {
        if (bucketName?.trim()?.lowercase(Locale.ROOT) in folders) return true
        if (relativePath?.replace('\\', '/')?.split('/')?.any { it.lowercase(Locale.ROOT) in folders } == true) return true
        return displayName?.lowercase(Locale.ROOT)?.let(datedName::containsMatchIn) == true
    }

    data class QueryFilter(val selection: String, val arguments: List<String>)

    /** Filter before reading rows, so a burst of camera photos cannot displace screenshots. */
    fun queryFilter(nameColumn: String, bucketColumn: String, pathColumn: String?): QueryFilter {
        val terms = mutableListOf<String>()
        val arguments = mutableListOf<String>()
        terms += "LOWER($bucketColumn) IN (${folders.joinToString(",") { "?" }})"
        arguments += folders
        for (prefix in namePrefixes) {
            terms += "LOWER($nameColumn) LIKE ? ESCAPE '\\'"
            arguments += prefix.replace("_", "\\_") + "%"
        }
        if (pathColumn != null) for (folder in folders) {
            terms += "LOWER($pathColumn) LIKE ?"
            arguments += "$folder/%"
            terms += "LOWER($pathColumn) LIKE ?"
            arguments += "%/$folder/%"
        }
        return QueryFilter("(${terms.joinToString(" OR ")})", arguments)
    }
}
