package com.kingzcheung.xime.ui.keyboard

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

/** Visual progress only. The gesture recognizer still owns release/cancellation. */
enum class KeyFlickOrigin { TOP_END, BOTTOM_START, TOP, BOTTOM, LEFT, RIGHT }

data class KeyFlickPreview(val text: String, val progress: Float, val origin: KeyFlickOrigin) {
    constructor(text: String, progress: Float, fromTop: Boolean) : this(text, progress,
        if (fromTop) KeyFlickOrigin.TOP_END else KeyFlickOrigin.BOTTOM_START)

    val fromTop get() = origin == KeyFlickOrigin.TOP_END || origin == KeyFlickOrigin.TOP
}

internal fun keyFlickPreview(
    dx: Float, dy: Float, touchSlop: Float, threshold: Float,
    upText: String?, downText: String?,
    upFromTop: Boolean = true, downFromTop: Boolean = false,
): KeyFlickPreview? {
    val distance = abs(dy)
    if (distance <= touchSlop || distance <= abs(dx) * 1.1f) return null
    val text = (if (dy > 0f) downText else upText)?.takeIf { it.isNotEmpty() } ?: return null
    val progress = ((distance - touchSlop) / (threshold - touchSlop).coerceAtLeast(1f)).coerceIn(0f, 1f)
    return KeyFlickPreview(text, progress, if (dy > 0f) downFromTop else upFromTop)
}

internal fun horizontalKeyFlickPreview(
    dx: Float, dy: Float, touchSlop: Float, threshold: Float, leftText: String?, rightText: String?,
): KeyFlickPreview? {
    val distance = abs(dx)
    if (distance <= touchSlop || distance <= abs(dy) * 1.1f) return null
    val text = (if (dx < 0f) leftText else rightText)?.takeIf { it.isNotEmpty() } ?: return null
    val progress = ((distance - touchSlop) / (threshold - touchSlop).coerceAtLeast(1f)).coerceIn(0f, 1f)
    return KeyFlickPreview(text, progress, if (dx < 0f) KeyFlickOrigin.LEFT else KeyFlickOrigin.RIGHT)
}

/** Move the existing corner hint into the key's centre, without changing its bounds. */
@Composable
internal fun BoxScope.KeyFlickLabel(
    preview: KeyFlickPreview,
    color: Color,
    widthDp: Float,
    heightDp: Float,
    hintSizeSp: Float,
    labelSizeSp: Float,
) {
    val density = LocalDensity.current
    val label = punctuationKeyLabel(preview.text)
    // labelSizeSp already includes the keyboard/user scaling; fit long action labels once.
    val targetSize = KeyboardKeyMetrics.labelSizeSp(label, labelSizeSp, widthDp, heightDp,
        density.fontScale, scaleOverride = 1f)
    val progress = preview.progress
    val fontSize = hintSizeSp + (targetSize - hintSizeSp) * progress
    var textSize by remember(label) { mutableStateOf(IntSize.Zero) }
    Text(
        text = label,
        color = color.copy(alpha = color.alpha * (0.6f + 0.4f * progress)),
        fontSize = fontSize.sp,
        lineHeight = (fontSize * 1.2f).sp,
        fontWeight = FontWeight.Normal,
        fontFamily = AppFonts.keyFontFamily,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
        onTextLayout = { textSize = it.size },
        modifier = Modifier.align(Alignment.Center).testTag("key-flick-preview").graphicsLayer {
            val x = (with(density) { widthDp.dp.toPx() } / 2f - textSize.width / 2f -
                with(density) { 5.dp.toPx() }).coerceAtLeast(0f) * (1f - progress)
            val y = (with(density) { heightDp.dp.toPx() } / 2f - textSize.height / 2f -
                with(density) { 2.dp.toPx() }).coerceAtLeast(0f) * (1f - progress)
            translationX = when (preview.origin) {
                KeyFlickOrigin.TOP_END, KeyFlickOrigin.RIGHT -> x
                KeyFlickOrigin.BOTTOM_START, KeyFlickOrigin.LEFT -> -x
                else -> 0f
            }
            translationY = when (preview.origin) {
                KeyFlickOrigin.TOP_END, KeyFlickOrigin.TOP -> -y
                KeyFlickOrigin.BOTTOM_START, KeyFlickOrigin.BOTTOM -> y
                else -> 0f
            }
        },
    )
}
