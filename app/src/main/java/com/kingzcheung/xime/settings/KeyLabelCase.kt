package com.kingzcheung.xime.settings

/** 仅纯英文字母键帽随 Shift 改变；自定义中文、符号及混合标签保持原样。 */
internal fun keyLabelWithCase(label: String, isShifted: Boolean): String =
    if (label.isNotEmpty() && label.all { it in 'a'..'z' || it in 'A'..'Z' }) {
        if (isShifted) label.uppercase() else label.lowercase()
    } else {
        label
    }
