package com.kingzcheung.xime.ui.keyboard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/** 所有键盘共用键帽内容尺度：跟随实际键帽，不能用屏幕尺寸放大悬浮键盘。 */
internal object KeyboardKeyMetrics {
    val FunctionIconSize = 20.dp
    val LabelSize = 18.sp

    fun longPressMenuWidthPx(keyWidth: Float, cells: Int, keyboardWidth: Float, margin: Float): Float =
        (cells.coerceAtLeast(1) * keyWidth.coerceAtLeast(1f))
            .coerceAtMost((keyboardWidth - 2f * margin).coerceAtLeast(1f))

    fun contentScale(widthDp: Float, heightDp: Float): Float {
        // Compose 按像素分配等权行列，兄弟键可能相差不足 1dp；字号不能跟着逐键抖动。
        val scale = adaptiveKeyContentScale(minOf(widthDp, heightDp))
        return (scale * 8f).roundToInt() / 8f
    }

    /** 文本和图标使用相同放大倍率，额外限制长标签、大系统字号和矮键帽。 */
    fun labelSizeSp(text: String, baseSp: Float, widthDp: Float, heightDp: Float,
        fontScale: Float, textScale: Float = 1f, scaleOverride: Float? = null): Float {
        val scale = fontScale.coerceAtLeast(0.1f)
        val widthUnits = text.sumOf { if (it.code < 0x2E80) 0.6 else 1.0 }.toFloat().coerceAtLeast(1f)
        return minOf(baseSp * (scaleOverride ?: contentScale(widthDp, heightDp)) * textScale,
            (widthDp - 4f).coerceAtLeast(1f) / (widthUnits * scale * 1.08f),
            (heightDp - 2f).coerceAtLeast(1f) / (scale * 1.4f)).coerceAtLeast(1f)
    }

    fun iconSizeDp(widthDp: Float, heightDp: Float, baseDp: Float = FunctionIconSize.value, scaleOverride: Float? = null): Float =
        minOf(baseDp * (scaleOverride ?: contentScale(widthDp, heightDp)),
            (minOf(widthDp, heightDp) - 4f).coerceAtLeast(1f))

    fun hintOffsetDp(heightDp: Float, hintSizeSp: Float, fontScale: Float, contentScale: Float): Float =
        adaptiveHintOffsetDp(contentScale)
            // 矮键上提示贴顶只留 1dp（行高本身上方还有约 1dp 留白），给下面的图标多让 1dp。
            .coerceAtMost(((heightDp - hintSizeSp * fontScale * 1.2f) / 2f - 1f).coerceAtLeast(0f))

    /**
     * 图标键上方的提示文字（清空、重输）会不会压到图标：文字中心在键中心上方 [hintCenterAboveDp]。
     * Material 图标的字形约占图标框的 75%，汉字字形约等于字号；两者之间至少留 1dp，
     * 放不下（矮键、大字号）时只画图标。
     */
    fun hintClearsIcon(hintCenterAboveDp: Float, hintSizeSp: Float, fontScale: Float, iconDp: Float): Boolean =
        hintCenterAboveDp - hintSizeSp * fontScale / 2f >= iconDp * 0.375f + 1f
}

/** Grid siblings share one scale; sub-pixel cell allocation must not change their font size. */
internal val LocalKeyboardKeyContentScale = staticCompositionLocalOf<Float?> { null }

@Composable
internal fun keyContentScale(widthDp: Float, heightDp: Float): Float =
    LocalKeyboardKeyContentScale.current ?: KeyboardKeyMetrics.contentScale(widthDp, heightDp)

@Composable
internal fun keyLabelSizeSp(text: String, baseSp: Float, widthDp: Float, heightDp: Float,
    fontScale: Float, textScale: Float = 1f): Float =
    KeyboardKeyMetrics.labelSizeSp(text, baseSp, widthDp, heightDp, fontScale, textScale,
        keyContentScale(widthDp, heightDp))

@Composable
internal fun keyIconSizeDp(widthDp: Float, heightDp: Float, baseDp: Float = KeyboardKeyMetrics.FunctionIconSize.value): Float =
    KeyboardKeyMetrics.iconSizeDp(widthDp, heightDp, baseDp, keyContentScale(widthDp, heightDp))
