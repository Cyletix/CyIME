package com.kingzcheung.xime.ui.keyboard

/** Matches the 1…9,0 selection keys; never repeat shortcut numbers for later items. */
internal fun candidateNumberedText(text: String, index: Int, enabled: Boolean): String =
    if (enabled && index in 0..9) "${(index + 1) % 10} $text" else text
