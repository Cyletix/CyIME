package com.kingzcheung.xime.ui.keyboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalDensity
import com.kingzcheung.xime.ui.theme.cyPopupEnter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlin.math.ceil
import kotlin.math.roundToInt

internal fun swipeBubbleWindowPosition(anchor: IntRect, window: IntSize, popup: IntSize,
    left: Float, top: Float, shadow: Float): IntOffset = IntOffset(
    (anchor.left + left - shadow).roundToInt().coerceIn(0, (window.width - popup.width).coerceAtLeast(0)),
    (anchor.top + top - shadow).roundToInt().coerceIn(0, (window.height - popup.height).coerceAtLeast(0)),
)

/** Separate non-focusable window: keyboard-card clipping/opacity must not cut off the choices. */
@Composable
internal fun SwipeBubbleOverlay(data: BubbleDrawData?) {
    if (data == null) return
    val density = LocalDensity.current
    val shadow = ceil(data.shadowRadiusPx)
    val width = ceil(maxOf(data.pathBodyLeft + data.pathBodyWidth,
        data.pointerLeftInBox + data.keyWidthPx) + shadow * 2).toInt()
    val height = ceil(data.bodyHeightPx + data.pointerHeightPx + shadow * 2).toInt()
    var displacedY by remember { mutableFloatStateOf(0f) }
    val currentData by rememberUpdatedState(data)
    val provider = remember(shadow) {
        object : PopupPositionProvider {
            override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize,
                layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
                val bubble = currentData
                val position = swipeBubbleWindowPosition(anchorBounds, windowSize, popupContentSize,
                    bubble.boxLeft, bubble.boxTop, shadow)
                displacedY = position.y + shadow - (anchorBounds.top + bubble.boxTop)
                return position
            }
        }
    }
    Popup(popupPositionProvider = provider, properties = PopupProperties(
        focusable = false, dismissOnBackPress = false, dismissOnClickOutside = false,
        clippingEnabled = false,
    )) {
        // 从按键上的尖角处淡入放大；选择只看手指坐标，缩放不影响命中。
        val origin = TransformOrigin(
            ((data.pointerLeftInBox + data.keyWidthPx / 2f + shadow) / width).coerceIn(0f, 1f), 1f)
        Canvas(Modifier.size(with(density) { width.toDp() }, with(density) { height.toDp() })
            .cyPopupEnter(origin)
            .testTag("key-choice-popup")) {
            drawSwipeBubble(data.copy(boxLeft = shadow, boxTop = shadow,
                // At the physical screen edge, shorten the stem instead of moving its tip off the key.
                pointerHeightPx = (data.pointerHeightPx - displacedY).coerceAtLeast(0f)))
        }
    }
}
