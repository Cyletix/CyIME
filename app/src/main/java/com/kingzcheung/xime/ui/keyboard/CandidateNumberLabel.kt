package com.kingzcheung.xime.ui.keyboard

/** Matches the 1…9,0 selection keys; never repeat shortcut numbers for later items.
 * 多行短语的换行显示成 ↵，候选还是一行（见 PhraseLineBreaks）。 */
internal fun candidateNumberedText(text: String, index: Int, enabled: Boolean): String {
    val shown = com.kingzcheung.xime.settings.PhraseLineBreaks.shown(text)
    return if (enabled && index in 0..9) "${(index + 1) % 10} $shown" else shown
}
