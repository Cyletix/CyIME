package com.kingzcheung.xime.ui.keyboard

import android.view.WindowManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalDensity
import com.kingzcheung.xime.ui.theme.cyPopupEnter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 按键上的浮窗（点按气泡、滑动/长按选择气泡、拂动方向盘）只显示、不接触摸。
 * 浮窗是单独的窗口，盖住按下的键、上一排的键和四周一圈阴影；可接触摸时，落在里面的点按全被它吃掉：
 * 抬手后气泡还留几十毫秒，这时快速再点同一个键或旁边的键就丢字（用户反馈 10-31「同一个键快速连按两下漏字」）。
 * 不接触摸后这些点按落回键盘；正在按着的那根手指本来就归键盘窗口，滑动和长按选择照旧。
 */
internal val DisplayOnlyPopupProperties = PopupProperties(
    flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
    dismissOnBackPress = false, dismissOnClickOutside = false,
)

/** 悬浮键盘当前的旋转角；独立窗口里的气泡要跟着转，尖角才对得上按键。 */
internal val LocalFloatingKeyboardRotation = compositionLocalOf { 0f }

/**
 * 键盘转过时：Popup 的锚点是转过后的布局原点，气泡在布局里的位置也按同一角度转到窗口坐标；
 * 窗口取转后气泡的外接框，画布里再把气泡转回去。
 */
@Composable
private fun RotatedSwipeBubble(data: BubbleDrawData, shadow: Float, width: Int, height: Int, rotation: Float) {
    val density = LocalDensity.current
    val radians = Math.toRadians(rotation.toDouble())
    val c = cos(radians).toFloat()
    val s = sin(radians).toFloat()
    val xs = floatArrayOf(0f, width * c, width * c - height * s, -height * s)
    val ys = floatArrayOf(0f, width * s, width * s + height * c, height * c)
    val minX = xs.min()
    val minY = ys.min()
    val boxWidth = ceil(xs.max() - minX).toInt().coerceAtLeast(1)
    val boxHeight = ceil(ys.max() - minY).toInt().coerceAtLeast(1)
    val localX = data.boxLeft - shadow
    val localY = data.boxTop - shadow
    val offsetX = localX * c - localY * s + minX
    val offsetY = localX * s + localY * c + minY
    val provider = remember(offsetX, offsetY) {
        object : PopupPositionProvider {
            override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize,
                layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset =
                IntOffset((anchorBounds.left + offsetX).roundToInt(), (anchorBounds.top + offsetY).roundToInt())
        }
    }
    Popup(popupPositionProvider = provider, properties = DisplayOnlyPopupProperties) {
        Canvas(Modifier.size(with(density) { boxWidth.toDp() }, with(density) { boxHeight.toDp() })
            .testTag("key-choice-popup")) {
            withTransform({
                translate(-minX, -minY)
                rotate(rotation, androidx.compose.ui.geometry.Offset.Zero)
            }) {
                drawSwipeBubble(data.copy(boxLeft = shadow, boxTop = shadow))
            }
        }
    }
}

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
    val rotation = LocalFloatingKeyboardRotation.current
    if (isFloatingRotated(rotation)) {
        RotatedSwipeBubble(data, shadow, width, height, rotation)
        return
    }
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
    Popup(popupPositionProvider = provider, properties = DisplayOnlyPopupProperties) {
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
