package com.kingzcheung.xime.service

import com.kingzcheung.xime.speech.cleanSpeechText
import com.kingzcheung.xime.settings.InputLanguage

private val dictatedPunctuation = Regex(" *(逗号|句号|问号|感叹号|叹号) *")
/** With model marks kept, a dictated mark replaces the marks the model put right next to it. */
private val dictatedWithMarks = Regex("[，。？！、；：]*\\s*(逗号|句号|问号|感叹号|叹号)\\s*[，。？！、；：]*")

/** Which of the model's marks 自动加标点 keeps; a dropped kind becomes spacing. Dictated marks always stay. */
internal data class VoiceMarks(val comma: Boolean = true, val period: Boolean = true, val question: Boolean = true) {
    val all get() = comma && period && question
}

private val spacing = Regex(" {2,}")

/** Drops the model marks of the kinds turned off; numeric '.', ',' and ':' between digits stay. */
private fun dropMarks(text: String, marks: VoiceMarks): String {
    if (marks.all) return text
    val out = buildString {
        text.forEachIndexed { i, c ->
            val keep = when (c) {
                '，', '、', '；', '：', ',', ';', ':' -> marks.comma
                '。', '.' -> marks.period
                '？', '！', '?', '!' -> marks.question
                else -> true
            } || c in ".,:" && text.getOrNull(i - 1)?.isDigit() == true && text.getOrNull(i + 1)?.isDigit() == true
            append(if (keep) c else ' ')
        }
    }
    return spacing.replace(out, " ").trim()
}

/**
 * Call once on incoming ASR text, never on already rendered partial text.
 * [keepMarks]: 整句定稿 keeps the model's sentence/clause marks instead of turning them into spaces;
 * [marks] picks which kinds stay (自动加标点 → 逐项).
 */
internal fun normalizeVoiceText(text: String, language: InputLanguage = InputLanguage.CHINESE, keepMarks: Boolean = false,
    marks: VoiceMarks = VoiceMarks()): String {
    val clean = cleanSpeechText(text, keepMarks).let { if (keepMarks) dropMarks(it, marks) else it }
    if (language != InputLanguage.CHINESE) return clean
    return (if (keepMarks) dictatedWithMarks else dictatedPunctuation).replace(clean) {
        when (it.groupValues[1]) {
            "逗号" -> "，"
            "句号" -> "。"
            "问号" -> "？"
            else -> "！"
        }
    }
}
