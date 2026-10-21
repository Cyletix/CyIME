package com.kingzcheung.xime.handwriting

import com.kingzcheung.xime.settings.InputLanguage

/**
 * 内置汉字手写模型（ochwpro）服务的语言：中文取汉字；英文取同一模型里的英文字母和数字；
 * 日语假名用它的输出当特征和假名模板比对（HandwritingTemplates）。都不需要公式模型。
 */
object HandwritingLanguages {
    fun supports(language: InputLanguage): Boolean =
        language == InputLanguage.CHINESE || language == InputLanguage.ENGLISH || language == InputLanguage.JAPANESE

    fun recognitionLanguageForKeyboard(language: InputLanguage): InputLanguage? =
        language.takeIf { supports(it) }

    /** 英文候选：只留单个英文字母或数字，按原概率排序去重。 */
    fun latinCandidates(raw: List<HandwritingCandidate>, limit: Int): List<HandwritingCandidate> =
        raw.asSequence().filter { c -> c.char.length == 1 && c.char[0].let { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' } }
            .filter { it.score.isFinite() && it.score >= 0f }
            .distinctBy { it.char }.sortedByDescending { it.score }.take(limit.coerceAtLeast(0)).toList()

    fun unavailableMessage(language: InputLanguage): String = if (language == InputLanguage.UNSPECIFIED)
        "当前方案未标注语言，暂不能使用手写" else "暂不支持${language.displayName}手写，请先切换到中文"
}
