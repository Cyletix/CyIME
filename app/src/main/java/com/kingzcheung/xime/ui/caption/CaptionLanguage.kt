package com.kingzcheung.xime.ui.caption

/** The language the captions listen for; chosen on the caption page and remembered. */
enum class CaptionLanguage(val id: String, val label: String) {
    CHINESE("zh", "中文"),
    JAPANESE("ja", "日本語"),
    ENGLISH("en", "English");

    companion object {
        /** The elder package's captions are Chinese only (docs/development/elder-pack-spec.md). */
        val supported: List<CaptionLanguage> = forPackage(com.kingzcheung.xime.BuildConfig.ELDER)
        internal fun forPackage(elder: Boolean) = if (elder) listOf(CHINESE) else entries.toList()
        /** A stored language this package does not offer (e.g. "ja" from a restored backup) reads as Chinese. */
        fun of(id: String?, offered: List<CaptionLanguage> = supported): CaptionLanguage =
            offered.firstOrNull { it.id == id } ?: CHINESE
    }
}
