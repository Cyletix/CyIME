package com.kingzcheung.xime.service

import com.kingzcheung.xime.speech.cleanSpeechText
import com.kingzcheung.xime.settings.InputLanguage

private val dictatedPunctuation = Regex(" *(逗号|句号|问号|感叹号|叹号) *")
/** With model marks kept, a dictated mark replaces the marks the model put right next to it. */
private val dictatedWithMarks = Regex("[，。？！、；：]*\\s*(逗号|句号|问号|感叹号|叹号)\\s*[，。？！、；：]*")

/**
 * Call once on incoming ASR text, never on already rendered partial text.
 * [keepMarks]: 整句定稿 keeps the model's sentence/clause marks instead of turning them into spaces.
 */
internal fun normalizeVoiceText(text: String, language: InputLanguage = InputLanguage.CHINESE, keepMarks: Boolean = false): String {
    val clean = cleanSpeechText(text, keepMarks)
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
