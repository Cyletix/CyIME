package com.kingzcheung.xime.settings

/** 26 keys with six relocated letters; engine and non-letter keys remain Chinese26. */
internal object QwjrtkLayout {
    const val ID = "pinyin_qwjrtk"
    private val replacements = mapOf("e" to "j", "y" to "k", "j" to "e", "k" to "n", "v" to "y", "n" to "v")
    fun rows(base: List<List<String>>) = base.map { row -> row.map { replacements[it] ?: it } }

    // Keep digit/symbol swipes in their familiar physical positions. Letter long-press
    // variants and radical hints still belong to the letter printed on the key.
    fun gestures(base: Map<String, KeyGestureConfig>): Map<String, KeyGestureConfig> =
        base + replacements.mapNotNull { (old, new) ->
            base[new]?.let { new to it.copy(swipeUp = base[old]?.swipeUp) }
        }.toMap()

}
