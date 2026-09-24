package com.kingzcheung.xime.ui.keyboard

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties

/** 按住显示五个候选方向，滑动后用浮动气泡显示最终待输入字符。浮层不获取输入焦点。 */
@Composable
internal fun KanaFlickPreview(key: KanaFlickKey, direction: KanaFlickDirection, background: Color, foreground: Color, contentScale: Float = 1f) {
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
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val appear by animateFloatAsState(if (shown) 1f else 0f, tween(100), label = "kana-preview-appear")
    val x by animateDpAsState(when (direction) {
        KanaFlickDirection.LEFT -> (-20f * scale).dp; KanaFlickDirection.RIGHT -> (20f * scale).dp; else -> 0.dp
    }, tween(90), label = "kana-preview-x")
    val y by animateDpAsState(when (direction) {
        KanaFlickDirection.UP -> (-20f * scale).dp; KanaFlickDirection.DOWN -> (20f * scale).dp; else -> 0.dp
    }, tween(90), label = "kana-preview-y")
    val diameter by animateDpAsState(if (direction == KanaFlickDirection.TAP) (100f * scale).dp else (56f * scale).dp,
        tween(90), label = "kana-preview-size")
    Popup(popupPositionProvider = position,
        properties = PopupProperties(focusable = false, dismissOnBackPress = false,
            dismissOnClickOutside = false, clippingEnabled = false)) {
        Box(Modifier.size((112f * scale).dp).testTag("kana-flick-preview")
            .semantics { stateDescription = key.choice(direction)?.label ?: "无字符" }, contentAlignment = Alignment.Center) {
            Box(Modifier.offset(x, y).size(diameter).testTag("kana-preview-bubble").graphicsLayer {
                alpha = appear; scaleX = 0.9f + appear * 0.1f; scaleY = scaleX
            }.shadow(4.dp, CircleShape).background(background, CircleShape), contentAlignment = Alignment.Center) {
                Crossfade(direction, animationSpec = tween(90), label = "kana-preview-direction") { selected ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        if (selected == KanaFlickDirection.TAP) {
                            KanaDirectionLabels(key, foreground, 28f * scale, 17f * scale)
                        } else {
                            Text(punctuationKeyLabel(key.choice(selected)?.label ?: "—"), color = foreground, fontSize = (30f * scale).sp,
                                lineHeight = (34f * scale).sp, maxLines = 1, fontFamily = AppFonts.keyFontFamily)
                        }
                    }
                }
            }
        }
    }
}

/** 标点键常显方向提示，字母键仅在浮层里显示，避免小键帽文字相叠。 */
@Composable
internal fun KanaDirectionLabels(key: KanaFlickKey, color: Color, centerSize: Float, hintSize: Float) {
    Box(Modifier.fillMaxSize().padding(2.dp), contentAlignment = Alignment.Center) {
        for (direction in KanaFlickDirection.entries) {
            val choice = key.choice(direction) ?: continue
            val alignment = when (direction) {
                KanaFlickDirection.TAP -> Alignment.Center
                KanaFlickDirection.LEFT -> Alignment.CenterStart
                KanaFlickDirection.UP -> Alignment.TopCenter
                KanaFlickDirection.RIGHT -> Alignment.CenterEnd
                KanaFlickDirection.DOWN -> Alignment.BottomCenter
            }
            val fontSize = if (direction == KanaFlickDirection.TAP) centerSize else hintSize
            Text(punctuationKeyLabel(choice.label), color = color, fontSize = fontSize.sp, lineHeight = (fontSize * 1.15f).sp,
                maxLines = 1, softWrap = false, fontFamily = AppFonts.keyFontFamily, modifier = Modifier.align(alignment))
        }
    }
}

/** 主题色箭头从当前方向的键边缘指向中心，方向改变时淡入淡出。 */
@Composable
internal fun KanaDirectionIndicator(direction: KanaFlickDirection, color: Color) {
    Crossfade(direction, animationSpec = tween(90), label = "kana-arrow") { selected ->
        if (selected != KanaFlickDirection.TAP) Canvas(Modifier.fillMaxSize().testTag("kana-direction:${selected.name}")) {
            val w = size.width; val h = size.height
            val halfBase = minOf(w, h) * 0.32f
            val depth = minOf(w, h) * 0.34f
            val path = Path().apply {
                when (selected) {
                    KanaFlickDirection.LEFT -> { moveTo(0f, h / 2 - halfBase); lineTo(depth, h / 2); lineTo(0f, h / 2 + halfBase) }
                    KanaFlickDirection.RIGHT -> { moveTo(w, h / 2 - halfBase); lineTo(w - depth, h / 2); lineTo(w, h / 2 + halfBase) }
                    KanaFlickDirection.UP -> { moveTo(w / 2 - halfBase, 0f); lineTo(w / 2, depth); lineTo(w / 2 + halfBase, 0f) }
                    KanaFlickDirection.DOWN -> { moveTo(w / 2 - halfBase, h); lineTo(w / 2, h - depth); lineTo(w / 2 + halfBase, h) }
                    else -> Unit
                }
                close()
            }
            drawPath(path, color)
        }
    }
}
