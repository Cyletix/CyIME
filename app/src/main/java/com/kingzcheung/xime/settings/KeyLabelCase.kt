package com.kingzcheung.xime.settings

/**
 * 仅纯字母键帽（英文字母，西班牙、德、法、俄语键盘上的 ñ ä é й…）随 Shift 改变大小写；
 * 韩语字母按 Shift 换成双辅音和 ㅒ ㅖ（ㄱ → ㄲ）。自定义中文、符号及混合标签保持原样。
 */
internal fun keyLabelWithCase(label: String, isShifted: Boolean): String {
    if (isShifted) com.kingzcheung.xime.service.HangulComposer.SHIFTED[label.singleOrNull()]?.let { return it.toString() }
    return if (label.isNotEmpty() && label.all { it in 'a'..'z' || it in 'A'..'Z' || it in LetterLanguage.allExtraLetters }) {
        // ß 没有单个大写字母：Shift 时键帽照旧。
        if (isShifted) label.uppercase().takeIf { it.length == label.length } ?: label else label.lowercase()
    } else {
        label
    }
}
