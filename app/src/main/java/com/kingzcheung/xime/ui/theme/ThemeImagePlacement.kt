package com.kingzcheung.xime.ui.theme

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** 图片在键盘里画在哪（像素：左上角与宽高，可以超出键盘）。 */
internal data class ThemeImageFrame(val left: Float, val top: Float, val width: Float, val height: Float) {
    val right: Float get() = left + width
    val bottom: Float get() = top + height

    /** 是否盖满 [dstW]×[dstH]，没盖满的地方要先铺主题底色。 */
    fun covers(dstW: Float, dstH: Float): Boolean =
        left <= EDGE_EPSILON && top <= EDGE_EPSILON && right >= dstW - EDGE_EPSILON && bottom >= dstH - EDGE_EPSILON
}

/**
 * 「我的图片」调整页存下的摆放。与键盘尺寸无关，横竖屏、换布局时按同一意思重新摆：
 * - cover：盖满键盘，[zoom] ≥ 1 在盖满的基础上放大；
 * - fit_height：上下贴齐键盘，两边可以留白；
 * - fit_width：左右贴齐键盘，上下可以留白；
 * - contain：自由大小，[zoom] 相对整张放进键盘的大小。
 * [alignX]/[alignY] 是图片在可移动范围里的位置：0 左/上边对齐，1 右/下边对齐，0.5 居中。
 */
internal data class ThemeImagePlacement(
    val fit: String = "cover",
    val zoom: Float = 1f,
    val alignX: Float = 0.5f,
    val alignY: Float = 0.5f,
) {
    fun encode(): String = "$fit,$zoom,$alignX,$alignY"

    companion object {
        val DEFAULT = ThemeImagePlacement()
        val FITS = setOf("cover", "fit_height", "fit_width", "contain")

        fun decode(value: String?): ThemeImagePlacement {
            val parts = value?.split(',') ?: return DEFAULT
            if (parts.size != 4 || parts[0] !in FITS) return DEFAULT
            val numbers = parts.drop(1).map { it.toFloatOrNull()?.takeIf { n -> n.isFinite() } ?: return DEFAULT }
            return ThemeImagePlacement(parts[0], numbers[0].coerceIn(MIN_ZOOM, MAX_ZOOM),
                numbers[1].coerceIn(0f, 1f), numbers[2].coerceIn(0f, 1f))
        }
    }
}

private const val EDGE_EPSILON = 0.5f
private const val MIN_ZOOM = 0.2f
private const val MAX_ZOOM = 5f

/**
 * 按 [fit]（cover / contain / fit_width / fit_height）、[zoom] 与对齐位置摆放 [srcW]×[srcH] 的图片。
 * 都未配置时与原来的居中 cover/contain 一样。
 */
internal fun placeThemeImage(
    srcW: Float, srcH: Float, dstW: Float, dstH: Float,
    fit: String, zoom: Float? = null, alignX: Float? = null, alignY: Float? = null,
): ThemeImageFrame {
    val scale = when (fit) {
        "contain" -> min(dstW / srcW, dstH / srcH) * (zoom ?: 1f)
        "fit_width" -> dstW / srcW
        "fit_height" -> dstH / srcH
        else -> max(dstW / srcW, dstH / srcH) * (zoom ?: 1f).coerceAtLeast(1f)
    }
    val width = srcW * scale
    val height = srcH * scale
    return ThemeImageFrame(
        (dstW - width) * (alignX ?: 0.5f).coerceIn(0f, 1f),
        (dstH - height) * (alignY ?: 0.5f).coerceIn(0f, 1f),
        width, height,
    )
}

internal fun placeThemeImage(srcW: Float, srcH: Float, dstW: Float, dstH: Float, placement: ThemeImagePlacement) =
    placeThemeImage(srcW, srcH, dstW, dstH, placement.fit, placement.zoom, placement.alignX, placement.alignY)

/**
 * 调整页手势后的吸附：宽或高离键盘差 [threshold] 以内就贴齐（取差得少的那边），
 * 再把四条边和中线吸到键盘的边和中线。图片小于键盘时整张留在键盘里，大于键盘时不露出空白，
 * 所以每个方向要么留白、要么盖满，不会一边露空一边被截。
 */
internal fun snapThemeImage(raw: ThemeImageFrame, dstW: Float, dstH: Float, threshold: Float): ThemeImageFrame {
    var width = raw.width
    var height = raw.height
    val aspect = width / height
    val dw = abs(width - dstW)
    val dh = abs(height - dstH)
    if (dw <= threshold && (dw <= dh || dh > threshold)) {
        width = dstW; height = width / aspect
    } else if (dh <= threshold) {
        height = dstH; width = height * aspect
    }
    val centerX = raw.left + raw.width / 2f
    val centerY = raw.top + raw.height / 2f
    return ThemeImageFrame(
        snapAxis(centerX - width / 2f, width, dstW, threshold),
        snapAxis(centerY - height / 2f, height, dstH, threshold),
        width, height,
    )
}

/** 不吸附，只限制在可移动范围里（手势过程中原始位置用它，不会越拖越远）。 */
internal fun clampThemeImage(raw: ThemeImageFrame, dstW: Float, dstH: Float): ThemeImageFrame =
    raw.copy(left = snapAxis(raw.left, raw.width, dstW, 0f), top = snapAxis(raw.top, raw.height, dstH, 0f))

private fun snapAxis(start: Float, length: Float, span: Float, threshold: Float): Float {
    val low = min(0f, span - length)
    val high = max(0f, span - length)
    val clamped = start.coerceIn(low, high)
    val centered = (span - length) / 2f
    return when {
        threshold <= 0f -> clamped
        abs(clamped - low) <= threshold -> low
        abs(clamped - high) <= threshold -> high
        abs(clamped - centered) <= threshold -> centered
        else -> clamped
    }
}

/** 调整页上的图片框换成与键盘尺寸无关的摆放，存下后由 [placeThemeImage] 在各种键盘尺寸里还原。 */
internal fun placementOf(frame: ThemeImageFrame, srcW: Float, srcH: Float, dstW: Float, dstH: Float): ThemeImagePlacement {
    fun align(start: Float, length: Float, span: Float) =
        if (abs(span - length) < EDGE_EPSILON) 0.5f else (start / (span - length)).coerceIn(0f, 1f)
    val alignX = align(frame.left, frame.width, dstW)
    val alignY = align(frame.top, frame.height, dstH)
    val scale = frame.width / srcW
    val fullWidth = frame.width >= dstW - EDGE_EPSILON
    val fullHeight = frame.height >= dstH - EDGE_EPSILON
    return when {
        fullWidth && fullHeight -> ThemeImagePlacement("cover",
            (scale / max(dstW / srcW, dstH / srcH)).coerceIn(1f, MAX_ZOOM), alignX, alignY)
        abs(frame.height - dstH) < EDGE_EPSILON -> ThemeImagePlacement("fit_height", 1f, alignX, alignY)
        abs(frame.width - dstW) < EDGE_EPSILON -> ThemeImagePlacement("fit_width", 1f, alignX, alignY)
        else -> ThemeImagePlacement("contain",
            (scale / min(dstW / srcW, dstH / srcH)).coerceIn(MIN_ZOOM, MAX_ZOOM), alignX, alignY)
    }
}

/** 调整页手势缩放的范围：最小到长边约为键盘的五分之一，最大到盖满的 [MAX_ZOOM] 倍。 */
internal fun themeImageWidthRange(srcW: Float, srcH: Float, dstW: Float, dstH: Float): ClosedFloatingPointRange<Float> {
    val containWidth = srcW * min(dstW / srcW, dstH / srcH)
    val coverWidth = srcW * max(dstW / srcW, dstH / srcH)
    return containWidth * MIN_ZOOM..coverWidth * MAX_ZOOM
}
