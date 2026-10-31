package com.kingzcheung.xime.ui.keyboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.material3.LocalTextStyle
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider

/** Five choices stay at fixed positions above the key; only the selected choice changes. */
@Composable
internal fun KanaFlickPreview(key: KanaFlickKey, direction: KanaFlickDirection,
    background: Color, foreground: Color, contentScale: Float = 1f) {
    val labels = KanaFlickDirection.entries.mapNotNull { item -> key.choice(item)?.let { item to it.label } }.toMap()
    DirectionDiscPreview(labels, direction, background, foreground, contentScale, tagPrefix = "kana")
}

/**
 * The kana direction disc, shared by every key whose flick directions are fixed
 * (kana keys, nine-key flick letters, fourteen-key letter pairs). Display only.
 */
@Composable
internal fun DirectionDiscPreview(labels: Map<KanaFlickDirection, String>, direction: KanaFlickDirection,
    background: Color, foreground: Color, contentScale: Float = 1f, tagPrefix: String = "letter") {
    val scale = contentScale.coerceIn(1f, 1.5f)
    val density = LocalDensity.current
    val margin = with(density) { 6.dp.roundToPx() }
    val position = remember(margin) {
        object : PopupPositionProvider {
            override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize,
                layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset = IntOffset(
                (anchorBounds.center.x - popupContentSize.width / 2).coerceIn(margin,
                    (windowSize.width - popupContentSize.width - margin).coerceAtLeast(margin)),
                (anchorBounds.top - popupContentSize.height - margin).coerceIn(margin,
                    (windowSize.height - popupContentSize.height - margin).coerceAtLeast(margin)),
            )
        }
    }
    // 方向盘悬在上一排键上：只显示不接触摸，另一只手快速点上一排的键不被它吃掉。
    Popup(popupPositionProvider = position, properties = DisplayOnlyPopupProperties) {
        Box(Modifier.size((112f * scale).dp).testTag("$tagPrefix-flick-preview")
            .semantics { stateDescription = labels[direction] ?: "无字符" }, contentAlignment = Alignment.Center) {
            Box(Modifier.size((100f * scale).dp).testTag("$tagPrefix-preview-bubble")
                .shadow(4.dp, CircleShape).background(background, CircleShape).padding((5f * scale).dp)) {
                for (item in KanaFlickDirection.entries) {
                    val selected = direction == item
                    // Re-aiming fades the highlight across instead of jumping (stable call count per direction).
                    val emphasis by animateFloatAsState(if (selected) 1f else 0f, tween(120), label = "disc-${item.name}")
                    val label = labels[item]?.takeIf { it.isNotEmpty() } ?: continue
                    val requestedSp = when {
                        item == KanaFlickDirection.TAP -> 24f
                        selected -> 21f
                        else -> 17f
                    } * scale / density.fontScale.coerceAtLeast(1f)
                    val sizeSp = KeyboardKeyMetrics.labelSizeSp(label, requestedSp,
                        30f * scale, 30f * scale, density.fontScale, scaleOverride = 1f)
                    Box(Modifier.align(kanaDirectionAlignment(item)).size((30f * scale).dp)
                        .testTag("$tagPrefix-preview-choice:${item.name}").semantics { this.selected = selected }
                        .background(foreground.copy(alpha = .2f * emphasis), CircleShape),
                        contentAlignment = Alignment.Center) {
                        Text(punctuationKeyLabel(label), color = foreground.copy(alpha = .75f + .25f * emphasis),
                            fontSize = sizeSp.sp, lineHeight = (sizeSp * 1.15f).sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1, softWrap = false, fontFamily = AppFonts.keyFontFamily)
                    }
                }
            }
        }
    }
}

private fun kanaDirectionAlignment(direction: KanaFlickDirection): Alignment = when (direction) {
    KanaFlickDirection.TAP -> Alignment.Center
    KanaFlickDirection.LEFT -> Alignment.CenterStart
    KanaFlickDirection.UP -> Alignment.TopCenter
    KanaFlickDirection.RIGHT -> Alignment.CenterEnd
    KanaFlickDirection.DOWN -> Alignment.BottomCenter
}

/** Optional quiet labels on the key itself; preview visibility remains independent. */
@Composable
internal fun KanaDirectionLabels(key: KanaFlickKey, color: Color, centerSize: Float, hintSize: Float) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val baseStyle = LocalTextStyle.current.copy(fontFamily = AppFonts.keyFontFamily)
    BoxWithConstraints(Modifier.fillMaxSize().padding(2.dp), contentAlignment = Alignment.Center) {
        val choices = KanaFlickDirection.entries.mapNotNull { direction -> key.choice(direction)?.let { direction to it } }
        val sizes = choices.associate { (direction, choice) ->
            val fontSp = if (direction == KanaFlickDirection.TAP) centerSize else hintSize
            direction to measurer.measure(punctuationKeyLabel(choice.label),
                style = baseStyle.copy(fontSize = fontSp.sp, lineHeight = (fontSp * 1.15f).sp),
                maxLines = 1, softWrap = false).size
        }
        val center = sizes.getValue(KanaFlickDirection.TAP)
        // Opposite hints align to the outer edges and the main label stays centred.
        // Fit the actual Android line boxes, not only fontSize, with one pixel per seam.
        val verticalHints = maxOf(sizes[KanaFlickDirection.UP]?.height ?: 0, sizes[KanaFlickDirection.DOWN]?.height ?: 0)
        val horizontalHints = maxOf(sizes[KanaFlickDirection.LEFT]?.width ?: 0, sizes[KanaFlickDirection.RIGHT]?.width ?: 0)
        val fit = minOf(1f,
            (with(density) { maxHeight.toPx() } - 2f).coerceAtLeast(1f) / (center.height + 2 * verticalHints).coerceAtLeast(1),
            (with(density) { maxWidth.toPx() } - 2f).coerceAtLeast(1f) / (center.width + 2 * horizontalHints).coerceAtLeast(1))
        for ((direction, choice) in choices) {
            val fontSize = (if (direction == KanaFlickDirection.TAP) centerSize else hintSize) * fit
            Text(punctuationKeyLabel(choice.label), color = if (direction == KanaFlickDirection.TAP) color else Color.Gray,
                style = baseStyle.copy(fontSize = fontSize.sp, lineHeight = (fontSize * 1.15f).sp),
                maxLines = 1, softWrap = false, modifier = Modifier.align(kanaDirectionAlignment(direction))
                    .testTag("kana-key-hint:${key.center.romaji}:${direction.name}"))
        }
    }
}

/**
 * Highlight the pressed key's sector for the chosen direction: the key is split by its diagonals
 * (45° lines through the centre, the same rule the gesture uses), and the chosen triangle lights up.
 */
@Composable
internal fun KanaDirectionIndicator(direction: KanaFlickDirection, color: Color) {
    if (direction != KanaFlickDirection.TAP) Canvas(Modifier.fillMaxSize().testTag("kana-direction:${direction.name}")) {
        val w = size.width; val h = size.height
        val cx = w / 2; val cy = h / 2
        val s = minOf(w, h) / 2
        val path = Path().apply {
            moveTo(cx, cy)
            when (direction) {
                KanaFlickDirection.LEFT -> { lineTo(cx - s, cy - s); lineTo(0f, cy - s); lineTo(0f, cy + s); lineTo(cx - s, cy + s) }
                KanaFlickDirection.RIGHT -> { lineTo(cx + s, cy - s); lineTo(w, cy - s); lineTo(w, cy + s); lineTo(cx + s, cy + s) }
                KanaFlickDirection.UP -> { lineTo(cx - s, cy - s); lineTo(cx - s, 0f); lineTo(cx + s, 0f); lineTo(cx + s, cy - s) }
                KanaFlickDirection.DOWN -> { lineTo(cx - s, cy + s); lineTo(cx - s, h); lineTo(cx + s, h); lineTo(cx + s, cy + s) }
                else -> Unit
            }
            close()
        }
        drawPath(path, color.copy(alpha = color.alpha * .75f))
    }
}
