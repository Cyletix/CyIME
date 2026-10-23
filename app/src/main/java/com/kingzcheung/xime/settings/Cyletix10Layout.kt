package com.kingzcheung.xime.settings

/**
 * Cyletix10 with every symbol on its QWERTY key: the letters only move among QWERTY's 26 letter
 * keys, so the phone keeps 10/9/7 letter rows. Chosen 2026-10-23 by a constrained search on the
 * user's own typing (Cyletix-Keyboard research/nosymbol-20261023); it replaces the research
 * version that put ";" on the top row.
 */
internal object Cyletix10Layout {
    const val ID = "pinyin_cyletix10"
    val rows = listOf("qwbrtydfpu", "aseoghjin", "zxckvlm").map { it.map(Char::toString) }
    val preset = CustomKeyboardLayout(ID, "Cyletix10（实验）", rows)
}
