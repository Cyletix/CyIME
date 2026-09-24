package com.kingzcheung.xime.service

import com.kingzcheung.xime.speech.cleanSpeechText

private val dictatedPunctuation = Regex(" *(逗号|句号|问号|感叹号|叹号) *")

/** Call once on incoming ASR text, never on already rendered partial text. */
internal fun normalizeVoiceText(text: String): String =
    dictatedPunctuation.replace(cleanSpeechText(text)) {
        when (it.groupValues[1]) {
            "逗号" -> "，"
            "句号" -> "。"
            "问号" -> "？"
            else -> "！"
        }
    }
