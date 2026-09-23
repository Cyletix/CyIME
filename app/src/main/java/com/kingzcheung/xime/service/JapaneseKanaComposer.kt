package com.kingzcheung.xime.service

import com.kingzcheung.xime.ui.keyboard.kanaModifierCycles

/** 只记录由假名键生成、仍原样留在引擎中的尾部；从不操作宿主文本。 */
internal class JapaneseKanaComposer {
    data class Replacement(val input: String, val romaji: String)
    private var expectedInput = ""
    private var lastRomaji = ""

    fun reset() { expectedInput = ""; lastRomaji = "" }

    fun recordInput(before: String, romaji: String, after: String, committed: Boolean) {
        if (committed || after != before + romaji) reset()
        else { expectedInput = after; lastRomaji = romaji }
    }

    fun replacement(currentInput: String): Replacement? {
        if (lastRomaji.isEmpty() || currentInput.isEmpty() || currentInput != expectedInput || !currentInput.endsWith(lastRomaji)) {
            reset()
            return null
        }
        val cycle = kanaModifierCycles.firstOrNull { lastRomaji in it } ?: return null
        val next = cycle[(cycle.indexOf(lastRomaji) + 1) % cycle.size]
        return Replacement(currentInput.dropLast(lastRomaji.length) + next, next)
    }

    fun recordReplacement(replacement: Replacement, actualInput: String) {
        if (actualInput != replacement.input) reset()
        else { expectedInput = actualInput; lastRomaji = replacement.romaji }
    }
}
