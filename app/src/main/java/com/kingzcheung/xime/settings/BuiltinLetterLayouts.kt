package com.kingzcheung.xime.settings

/** Colemak on a phone: the 10/10/7 letter rows; ,./ stay on the control row like Cyletix10. */
internal object ColemakLayout {
    const val ID = "pinyin_colemak"
    val rows = listOf("qwfpgjluy;", "arstdhneio", "zxcvbkm").map { it.map(Char::toString) }
    val preset = CustomKeyboardLayout(ID, "Colemak", rows)
}

/** Typewise's honeycomb: four staggered rows; Shift and delete flank the third row. */
internal object TypewiseLayout {
    const val ID = "pinyin_typewise"
    val rows = listOf("wetyio", "qsrgukp", "adfhjl", "zxcvbnm").map { it.map(Char::toString) }
    val preset = CustomKeyboardLayout(ID, "Typewise", rows, redMoved = false)
}

/**
 * Built-in letter arrangements. Each one is a pinyin schema generated from rime_ice at build time
 * (build-logic/tasks-chinese.gradle.kts) and, through [LetterLayouts], also an arrangement for
 * English and Japanese romaji. Adding one here adds it to all three languages.
 */
internal object BuiltinLetterLayouts {
    val all: List<CustomKeyboardLayout> = listOf(QwjrtkLayout.preset, Cyletix10Layout.preset, ColemakLayout.preset,
        TypewiseLayout.preset)
    val ids: Set<String> = all.mapTo(linkedSetOf()) { it.id }
    fun find(id: String): CustomKeyboardLayout? = all.firstOrNull { it.id == id }
}
