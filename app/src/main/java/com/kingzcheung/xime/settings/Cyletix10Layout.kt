package com.kingzcheung.xime.settings

/** Mobile adaptation of the historical 10/10/10 research layout. */
internal object Cyletix10Layout {
    const val ID = "pinyin_cyletix10"
    // The research rows end with "zxcvbnm,./". CyIME keeps ,./ on its control row
    // and punctuation gestures, so the seven letters retain usable key widths.
    val rows = listOf("qwdrf;jkyp", "asetghliou", "zxcvbnm").map { it.map(Char::toString) }
    val referenceRows = listOf("qwertyuiop", "asdfghjkl;", "zxcvbnm")
    val preset = CustomKeyboardLayout(ID, "Cyletix10（实验）", rows)
}
