package com.kingzcheung.xime.ui.keyboard

internal data class QwertyBottomRowWeights(
    val mode: Float, val punctuation: Float, val space: Float, val language: Float, val enter: Float,
) {
    val total: Float get() = mode * 2 + punctuation + space + language + enter
    companion object {
        // 总权重10：逗号、地球各占1个字母键；符号/数字入口各加宽20%。
        val Standard = QwertyBottomRowWeights(1.2f, 1f, 3.9f, 1f, 1.7f)
        val Legacy = QwertyBottomRowWeights(0.7f, 0.6f, 3f, 0.8f, 1.2f)
    }
}
