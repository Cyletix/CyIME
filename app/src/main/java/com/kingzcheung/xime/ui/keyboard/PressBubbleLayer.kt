package com.kingzcheung.xime.ui.keyboard

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Matrix
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.view.View
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.core.graphics.withSave
import kotlin.math.ceil
import kotlin.math.floor

/** The decor overlay draws above the clipped keyboard card and has no touch or focus target. */
@Composable
internal fun PressBubbleLayer(controller: SwipeBubbleController, background: Color, text: Color,
    accent: Color, keyboardWidth: Float) {
    val root = LocalView.current.rootView
    val layer = remember(root) { PressBubbleDrawable(root) }
    val rotation = LocalFloatingKeyboardRotation.current
    val density = LocalDensity.current
    val referenceHeight = bubbleReferenceHeightDp()
    val typeface = AppFonts.keyLabelTypeface
    DisposableEffect(root, layer) {
        root.overlay.add(layer)
        onDispose { root.overlay.remove(layer) }
    }
    DisposableEffect(controller, layer, rotation, density, referenceHeight, keyboardWidth, background, text, accent, typeface) {
        val draw: (SwipeState, Rect) -> Unit = { state, bounds ->
            val data = if (state.isPlainPress()) measureSwipeBubble(state, bounds, background, text,
                accent, bounds.width, keyboardWidth, density, referenceHeight, keyLabelTypeface = typeface) else null
            layer.show(data, rotation)
        }
        controller.onPreviewChanged = draw
        draw(controller.state, controller.keyBounds)
        onDispose { controller.onPreviewChanged = null }
    }
    Spacer(Modifier.onGloballyPositioned { coordinates ->
        val parent = coordinates.parentLayoutCoordinates ?: coordinates
        layer.anchorAt(parent.localToWindow(Offset.Zero))
    }.semantics { if (controller.state.isPlainPress()) testTag = "key-choice-popup" })
}

private fun SwipeState.isPlainPress() =
    isPressed && !isSwiping && !isLongPress && keyFlick == null && !pressedText.isNullOrEmpty()

private class PressBubbleDrawable(private val host: View) : Drawable() {
    private val location = IntArray(2)
    private val transform = Matrix()
    private val area = RectF()
    private var anchor: Offset? = null
    private var rotation = 0f
    private var preview: BubbleDrawData? = null
    private var drawing: BubbleDrawData? = null

    fun anchorAt(windowOrigin: Offset) {
        host.getLocationInWindow(location)
        val origin = Offset(windowOrigin.x - location[0], windowOrigin.y - location[1])
        anchor = origin
        place()
    }

    fun show(data: BubbleDrawData?, angle: Float) {
        if (preview == data && rotation == angle) return
        preview = data
        rotation = angle
        place()
    }

    private fun place() {
        val data = preview
        val origin = anchor
        if (data == null || origin == null) {
            drawing = null
            setBounds(0, 0, 0, 0)
            return
        }
        val shadow = ceil(data.shadowRadiusPx)
        area.set(data.boxLeft - shadow, data.boxTop - shadow,
            data.boxLeft + maxOf(data.pathBodyLeft + data.pathBodyWidth, data.pointerLeftInBox + data.keyWidthPx) + shadow,
            data.boxTop + data.bodyHeightPx + data.pointerHeightPx + shadow)
        transform.setRotate(rotation)
        transform.postTranslate(origin.x, origin.y)
        transform.mapRect(area)
        val dx = area.left.coerceIn(0f, (host.width - area.width()).coerceAtLeast(0f)) - area.left
        val dy = area.top.coerceIn(0f, (host.height - area.height()).coerceAtLeast(0f)) - area.top
        transform.postTranslate(dx, dy)
        area.offset(dx, dy)
        drawing = if (rotation == 0f && dy != 0f) data.copy(pointerHeightPx = (data.pointerHeightPx - dy).coerceAtLeast(0f)) else data
        setBounds(floor(area.left).toInt(), floor(area.top).toInt(), ceil(area.right).toInt(), ceil(area.bottom).toInt())
        invalidateSelf()
    }

    override fun draw(canvas: Canvas) {
        val data = drawing ?: return
        canvas.withSave {
            canvas.concat(transform)
            paintSwipeBubble(canvas, data)
        }
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Deprecated("Deprecated in Android")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
