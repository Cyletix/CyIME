package com.kingzcheung.xime.ui.keyboard

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.State
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.painter.Painter
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

/**
 * In-key flick labels per direction. The direction under the finger follows it exactly; when the
 * finger re-aims, the previous direction's label shrinks back to its side instead of vanishing.
 * Ending the gesture clears everything at once, as before.
 */
internal class KeyFlickFade(private val texts: Map<KeyFlickOrigin, String>, private val progress: List<State<Float>>) {
    val strongest: Float get() = progress.maxOf { it.value }
    fun labels(): List<KeyFlickPreview> = KeyFlickOrigin.entries.mapIndexedNotNull { index, origin ->
        val value = progress[index].value
        texts[origin]?.takeIf { value > 0.001f }?.let { KeyFlickPreview(it, value, origin) }
    }
}

@Composable
internal fun rememberKeyFlickFade(preview: KeyFlickPreview?): KeyFlickFade {
    // The preview itself drives recomposition; this only remembers each side's last text.
    val texts = remember { HashMap<KeyFlickOrigin, String>() }
    if (preview == null) texts.clear() else texts[preview.origin] = preview.text
    val progress = KeyFlickOrigin.entries.map { origin ->
        val active = preview?.origin == origin
        animateFloatAsState(if (active) preview!!.progress else 0f,
            if (active || preview == null) snap() else tween(120), label = "flick-${origin.name}")
    }
    return KeyFlickFade(texts.toMap(), progress)
}

@Composable
internal fun BoxScope.KeyFlickLabels(
    fade: KeyFlickFade, color: Color, widthDp: Float, heightDp: Float, hintSizeSp: Float, labelSizeSp: Float,
    /** 用图标代替文字的方向（如 0 键左滑「上一项」用左箭头），图标最终大小为 [iconSizeDp]。 */
    icons: Map<KeyFlickOrigin, Painter> = emptyMap(),
    iconSizeDp: Float = KeyboardKeyMetrics.FunctionIconSize.value,
) {
    for (label in fade.labels()) {
        val icon = icons[label.origin]
        if (icon != null) KeyFlickIcon(label, icon, color, widthDp, heightDp, iconSizeDp)
        else KeyFlickLabel(label, color, widthDp, heightDp, hintSizeSp, labelSizeSp)
    }
}

/** 和 [KeyFlickLabel] 一样从原来那一侧移到键中央、由小变大，只是画的是图标。 */
@Composable
internal fun BoxScope.KeyFlickIcon(
    preview: KeyFlickPreview,
    icon: Painter,
    color: Color,
    widthDp: Float,
    heightDp: Float,
    iconSizeDp: Float,
) {
    val progress = preview.progress
    val size = iconSizeDp * (0.6f + 0.4f * progress)
    Icon(
        painter = icon,
        contentDescription = preview.text,
        tint = keyHintColor(color, LocalKeyboardInputPreferences.current.frostedGlass, color.alpha * (0.6f + 0.4f * progress)),
        modifier = Modifier.align(Alignment.Center).size(size.dp).testTag("key-flick-preview").graphicsLayer {
            val x = ((widthDp - size) / 2f - 2f).coerceAtLeast(0f).dp.toPx() * (1f - progress)
            val y = ((heightDp - size) / 2f - 2f).coerceAtLeast(0f).dp.toPx() * (1f - progress)
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
    val targetSize = KeyboardKeyMetrics.labelSizeSp(label, labelSizeSp * 1.15f, widthDp, heightDp,
        density.fontScale, scaleOverride = 1f)
    val progress = preview.progress
    val fontSize = hintSizeSp + (targetSize - hintSizeSp) * progress
    var textSize by remember(label) { mutableStateOf(IntSize.Zero) }
    Text(
        text = label,
        color = keyHintColor(color, LocalKeyboardInputPreferences.current.frostedGlass, color.alpha * (0.6f + 0.4f * progress)),
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
