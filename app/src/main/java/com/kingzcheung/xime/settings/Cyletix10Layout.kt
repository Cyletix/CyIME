package com.kingzcheung.xime.settings

/**
 * Mobile adaptation of the historical 10/10/10 research layout: the user's own Cyletix10
 * (genome qazwsxdecrtvfgb;hnjlmki,yo.pu/ in Cyletix-Keyboard README). It is his design; a
 * different arrangement never replaces it under this name unless he has seen and accepted
 * those exact rows (2026-10-23 a search result did, and he did not recognise his layout).
 */
internal object Cyletix10Layout {
    const val ID = "pinyin_cyletix10"
    // The research rows end with "zxcvbnm,./". CyIME keeps ,./ on its control row
    // and punctuation gestures, so the seven letters retain usable key widths.
    val rows = listOf("qwdrf;jkyp", "asetghliou", "zxcvbnm").map { it.map(Char::toString) }
    val referenceRows = listOf("qwertyuiop", "asdfghjkl;", "zxcvbnm")
    val preset = CustomKeyboardLayout(ID, "Cyletix10（实验）", rows)
}
