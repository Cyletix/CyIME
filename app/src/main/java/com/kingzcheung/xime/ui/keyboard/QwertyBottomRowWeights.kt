package com.kingzcheung.xime.ui.keyboard

internal data class QwertyBottomRowWeights(
    val mode: Float, val punctuation: Float, val space: Float, val language: Float, val enter: Float,
    val modeCount: Int = 2,
) {
    val total: Float get() = mode * modeCount + punctuation + space + language + enter
    companion object {
        // 总权重10：逗号、地球各占1个字母键；符号/数字入口各加宽20%。
        val Standard = QwertyBottomRowWeights(1.2f, 1f, 3.9f, 1f, 1.7f)
        // 合并 !@# 与 123 为单个 123 按钮并隐藏语言切换键：123 与回车等宽（1.8），空格延长并保持两侧对称居中（总重10）。
        val Compact = QwertyBottomRowWeights(1.8f, 1f, 5.4f, 0f, 1.8f, modeCount = 1)
        val Legacy = QwertyBottomRowWeights(0.7f, 0.6f, 3f, 0.8f, 1.2f)
        // Legacy 合并后与上方总权重（~7.0）等比对齐：123 与回车等宽（1.25），空格延长并对称居中。
        val LegacyCompact = QwertyBottomRowWeights(1.25f, 0.6f, 3.9f, 0f, 1.25f, modeCount = 1)
    }
}
