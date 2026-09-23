package com.kingzcheung.xime.service

private val voiceSeparators = Regex("""[\p{P}\p{Z}\s]+""")

/** 各语音引擎、实时/最终结果共用：保留词间空格，标点只形成间隔，不补句读。 */
internal fun normalizeVoiceText(text: String): String = voiceSeparators.replace(text, " ").trim()
