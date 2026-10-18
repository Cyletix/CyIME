package com.kingzcheung.xime.ui.keyboard

/**
 * 26 键三行字母的错位方式。
 * - 实体键盘：A 行比 Q 行右移 1/4 键，Z 行右移 3/4 键（ANSI 键盘）。
 * - 手机常见：A 行右移半键，Z 与 S 上下对齐。
 * - 按尺寸自动：手机宽度用手机排布，键宽接近实体键距时逐渐过渡到实体排布（原有行为）。
 */
enum class QwertyStaggerMode(val label: String) {
    AUTO("按尺寸自动"), PHYSICAL("实体键盘"), PHONE("手机常见"), CUSTOM("自定义")
}

/** 0 = 手机常见（s 与 z 对齐），1 = 实体键盘。 */
internal fun resolvedQwertyStagger(
    mode: QwertyStaggerMode, custom: Float, cellWidthDp: Float, targetCellDp: Float?,
): Float = when (mode) {
    QwertyStaggerMode.AUTO -> wideQwertyStagger(cellWidthDp, targetCellDp)
    QwertyStaggerMode.PHYSICAL -> 1f
    QwertyStaggerMode.PHONE -> 0f
    QwertyStaggerMode.CUSTOM -> custom.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0.5f
}

/** 空格两侧的一个标点键：点按输入 [primary]，滑动输入 [alternate]。 */
internal data class SpacePunctuationKey(val primary: String, val alternate: String)

/**
 * 空格左侧逗号、右侧句号，各自独立开关；整个键盘只有这两个键跟随全角/半角：
 * 点按输入当前宽度，滑动输入另一种宽度（日文的全角逗号是 、）。
 * 英文输入不分全角半角，只输入 , 和 .，没有副字符（不显示角标，也不能滑动输入）。
 */
internal fun spacePunctuationKeys(comma: Boolean, period: Boolean, fullWidth: Boolean, english: Boolean = false,
    japanese: Boolean = false): Pair<SpacePunctuationKey?, SpacePunctuationKey?> {
    val fullComma = if (japanese) "、" else "，"
    val commaKey = when {
        english -> SpacePunctuationKey(",", "")
        fullWidth -> SpacePunctuationKey(fullComma, ",")
        else -> SpacePunctuationKey(",", fullComma)
    }
    val periodKey = when {
        english -> SpacePunctuationKey(".", "")
        fullWidth -> SpacePunctuationKey("。", ".")
        else -> SpacePunctuationKey(".", "。")
    }
    return Pair(commaKey.takeIf { comma }, periodKey.takeIf { period })
}
