package com.kingzcheung.xime.ui.caption

/** The language the captions listen for; chosen on the caption page and remembered. */
enum class CaptionLanguage(val id: String, val label: String) {
    CHINESE("zh", "中文"),
    JAPANESE("ja", "日本語"),
    ENGLISH("en", "English");

    companion object {
        fun of(id: String?): CaptionLanguage = entries.firstOrNull { it.id == id } ?: CHINESE
    }
}
