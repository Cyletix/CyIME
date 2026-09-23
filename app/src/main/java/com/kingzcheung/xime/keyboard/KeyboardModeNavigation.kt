package com.kingzcheung.xime.keyboard

/** Two fixed slots: symbols at the left edge, numbers immediately beside them. */
enum class KeyboardInputPage { TEXT, SYMBOLS, NUMBERS }

data class KeyboardModeTarget(val label: String, val action: String)

fun modeSlotTarget(page: KeyboardInputPage, slot: Int, textLabel: String): KeyboardModeTarget {
    require(slot in 1..2)
    return when {
        slot == 1 && page == KeyboardInputPage.SYMBOLS -> KeyboardModeTarget(textLabel, "abc")
        slot == 1 -> KeyboardModeTarget("!@#", "symbol")
        page == KeyboardInputPage.NUMBERS -> KeyboardModeTarget(textLabel, "abc")
        else -> KeyboardModeTarget("123", "number")
    }
}

/** Secondary pages never become the text destination, including nested tool overlays. */
fun KeyboardPage.textMainType(): MainType = when (this) {
    is KeyboardPage.Main -> type
    is KeyboardPage.Panel -> returnTo
    is KeyboardPage.Overlay -> behind.textMainType()
}

/** First page contains everyday punctuation; specialist categories remain available. */
fun commonSymbolsFor(textLabel: String): List<String> = when (textLabel) {
    "ABC" -> listOf(".", ",", "?", "!", "'", "\"", ":", ";", "-", "_", "/", "\\",
        "@", "#", "$", "%", "&", "*", "+", "=", "(", ")", "[", "]", "{", "}", "<", ">", "~", "`", "…", "°")
    "あいう" -> listOf("、", "。", "？", "！", "ー", "〜", "・", "…", "「", "」", "『", "』",
        "（", "）", "［", "］", "：", "；", "／", "＼", "@", "#", "%", "&", "+", "-", "×", "÷", "=", "♡", "♪", "☆")
    else -> listOf("，", "。", "？", "！", "、", "：", "；", "…", "“", "”", "‘", "’", "（", "）", "《", "》",
        "【", "】", "—", "～", "@", "#", "￥", "%", "&", "*", "+", "-", "=", "/", "\\", "·")
}
