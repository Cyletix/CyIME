package com.kingzcheung.xime.handwriting

import com.kingzcheung.xime.settings.InputLanguage

/** The shipped classifier uses a Chinese character table. Other languages need their own recognizer. */
object HandwritingLanguages {
    fun supports(language: InputLanguage): Boolean = language == InputLanguage.CHINESE

    /** English typing may explicitly open Chinese handwriting; this is not English recognition. */
    fun recognitionLanguageForKeyboard(language: InputLanguage): InputLanguage? = when (language) {
        InputLanguage.CHINESE, InputLanguage.ENGLISH -> InputLanguage.CHINESE
        InputLanguage.JAPANESE, InputLanguage.UNSPECIFIED -> null
    }

    fun unavailableMessage(language: InputLanguage): String = if (language == InputLanguage.UNSPECIFIED)
        "当前方案未标注语言，暂不能使用手写" else "暂不支持${language.displayName}手写，请先切换到中文"
}
