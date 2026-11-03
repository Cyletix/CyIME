package com.kingzcheung.xime.ui.theme

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 拉高、拉低剪贴板时，毛玻璃背景不在每一帧重画（[ClipboardBackdropSizeCache]），旧图按新高度画，
 * 图片主题就被拉长、压扁，松手停稳重画后才恢复（用户 11-03：松手前也要保持正确比例）。
 * 现在拖动开始时另画一张整图，每一帧按当前高度摆放（和不开玻璃时同一套「调整图片」摆放），
 * 松手后一直用它，直到按新高度画好的图出来再换回。渐变本来就跟着高度铺开，照旧。
 */
internal class ResizingBackdropImage(
    val image: ImageBitmap,
    /** 图片没盖满的地方（「完整显示」等摆放）：底色加遮罩，和停稳后的图一样。 */
    val uncovered: Color,
    private val place: (IntSize) -> Rect,
) {
    fun frame(viewport: IntSize): Rect = place(viewport)
}

/** One picture placed in a surface; [uncovered] fills the rest of the surface. */
internal data class BackdropLayer(val image: ImageBitmap, val frame: Rect, val uncovered: Color?)

internal enum class BackdropResizeDraw {
    /** The cached render fills the surface (normal case, and gradients while resizing). */
    Cached,
    /** The whole picture at its true place for this size. */
    Picture,
    /** The whole picture is not ready yet: the cached render keeps its aspect ratio (no stretching). */
    CoverCached,
}

internal fun backdropResizeDraw(
    inMotion: Boolean,
    holdingPicture: Boolean,
    isImage: Boolean,
    renderedSize: IntSize,
    viewport: IntSize,
): BackdropResizeDraw = when {
    viewport.width <= 0 || viewport.height <= 0 || !isImage -> BackdropResizeDraw.Cached
    // After release the picture stays until the render for the final height has arrived.
    holdingPicture && (inMotion || renderedSize != viewport) -> BackdropResizeDraw.Picture
    inMotion && renderedSize.width > 0 && renderedSize.height > 0 && renderedSize != viewport -> BackdropResizeDraw.CoverCached
    else -> BackdropResizeDraw.Cached
}

/** [source] scaled to cover [viewport] at its own aspect ratio, centered. */
internal fun coverRect(source: IntSize, viewport: IntSize): Rect {
    val scale = max(viewport.width.toFloat() / source.width, viewport.height.toFloat() / source.height)
    val width = source.width * scale
    val height = source.height * scale
    val left = (viewport.width - width) / 2f
    val top = (viewport.height - height) / 2f
    return Rect(left, top, left + width, top + height)
}

/** Glass pixels for one surface; [layer] says how to draw them at the current size. */
internal class FrostedBackdropPixels(
    val image: ImageBitmap?,
    private val renderedSize: IntSize,
    private val inMotion: Boolean,
    private val isImage: Boolean,
    private val picture: ResizingBackdropImage?,
) {
    /** Null: draw [image] over the whole surface, as before. */
    fun layer(viewport: IntSize): BackdropLayer? =
        when (backdropResizeDraw(inMotion, picture != null, isImage, renderedSize, viewport)) {
            BackdropResizeDraw.Cached -> null
            BackdropResizeDraw.Picture -> picture?.let { BackdropLayer(it.image, it.frame(viewport), it.uncovered) }
            BackdropResizeDraw.CoverCached -> image?.let { BackdropLayer(it, coverRect(renderedSize, viewport), null) }
        }
}

/** Draws [layer] in this surface: the picture at its frame, [BackdropLayer.uncovered] around it. */
internal fun DrawScope.drawBackdropLayer(layer: BackdropLayer, alpha: Float) {
    val frame = layer.frame
    layer.uncovered?.let { color ->
        clipRect(frame.left, frame.top, frame.right, frame.bottom, androidx.compose.ui.graphics.ClipOp.Difference) {
            drawRect(color, alpha = alpha)
        }
    }
    clipRect {
        drawImage(
            layer.image,
            dstOffset = IntOffset(frame.left.roundToInt(), frame.top.roundToInt()),
            dstSize = IntSize(frame.width.roundToInt(), frame.height.roundToInt()),
            alpha = alpha,
            filterQuality = FilterQuality.Medium,
        )
    }
}
