package com.kingzcheung.xime.ui.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.kingzcheung.xime.settings.BackgroundConfig
import com.kingzcheung.xime.settings.FrostedGlassConfig
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Only the keyboard's own background is blurred. The cached bitmap works on API 28+;
 * text/icons are drawn afterwards and never enter the blur. Opacity changes only
 * redraw the material tint, so dragging either opacity slider does no bitmap work.
 */
@Composable
internal fun Modifier.frostedGlassBackground(
    background: BackgroundConfig?,
    isDark: Boolean,
    fallbackColor: Color,
    config: FrostedGlassConfig,
    translucentSurface: Boolean = false,
): Modifier {
    val context = LocalContext.current.applicationContext
    val density = LocalDensity.current.density
    val normalized = config.normalized()
    val source = if (background?.type == "image") {
        if (isDark) background.srcDark ?: background.src else background.src
    } else null
    var sourceImage by remember(source) { mutableStateOf<FrostedSourceImage?>(null) }
    LaunchedEffect(context, source) {
        sourceImage = source?.takeIf { it.isNotBlank() }?.let {
            withContext(Dispatchers.IO) { decodeFrostedImage(context, it) }
        }
    }

    var targetSize by remember { mutableStateOf(IntSize.Zero) }
    var blurredImage by remember(background, isDark, fallbackColor) { mutableStateOf<ImageBitmap?>(null) }
    // Floating capsules resize during docking; keep their backdrop cache stable throughout.
    val renderSize = if (translucentSurface) IntSize(512, 256) else targetSize
    LaunchedEffect(background, isDark, fallbackColor, sourceImage, renderSize, density, normalized.blurRadiusDp) {
        if (renderSize.width <= 0 || renderSize.height <= 0) return@LaunchedEffect
        blurredImage = withContext(Dispatchers.Default) {
            val coroutineContext = currentCoroutineContext()
            renderFrostedBackground(
                background, isDark, fallbackColor.toArgb(), sourceImage, renderSize,
                normalized.blurRadiusDp * density,
                checkCancelled = { coroutineContext.ensureActive() },
            ).asImageBitmap()
        }
    }

    // This replaces a theme image's overlayAlpha; applying both would double-darken it.
    val tint = (if (isDark) Color.Black else Color.White).copy(alpha = normalized.backgroundOpacity)
    return onSizeChanged { targetSize = it }.drawWithContent {
        // Only the backdrop is translucent; never fade candidate text or buttons.
        val opacity = if (translucentSurface) normalized.backgroundOpacity else 1f
        if (!translucentSurface || blurredImage == null) drawRect(fallbackColor.copy(alpha = opacity))
        blurredImage?.let { image ->
            drawImage(
                image,
                dstOffset = IntOffset.Zero,
                dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                filterQuality = FilterQuality.Medium,
                alpha = opacity,
            )
        }
        if (!translucentSurface) drawRect(tint)
        drawContent()
    }
}

private data class FrostedSourceImage(val bitmap: Bitmap, val originalWidth: Int, val originalHeight: Int)

private fun decodeFrostedImage(context: Context, source: String): FrostedSourceImage? = try {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    openThemeImageStream(context, source)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) null else {
        val options = BitmapFactory.Options().apply { inSampleSize = 1 }
        while (max(bounds.outWidth, bounds.outHeight) / options.inSampleSize > 2048) {
            options.inSampleSize *= 2
        }
        openThemeImageStream(context, source)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?.let { FrostedSourceImage(it, bounds.outWidth, bounds.outHeight) }
    }
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (_: Exception) {
    null
}

private fun renderFrostedBackground(
    background: BackgroundConfig?,
    isDark: Boolean,
    fallback: Int,
    image: FrostedSourceImage?,
    targetSize: IntSize,
    blurRadiusPx: Float,
    checkCancelled: () -> Unit,
): Bitmap {
    // Fine details disappear under blur, so keep that cache small. Radius zero retains
    // full keyboard resolution (bounded for unusually large displays).
    val maxDimension = if (blurRadiusPx < 1f) 2048 else 512
    val scale = min(1f, maxDimension.toFloat() / max(targetSize.width, targetSize.height))
    val width = (targetSize.width * scale).roundToInt().coerceAtLeast(1)
    val height = (targetSize.height * scale).roundToInt().coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    try {
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val base = background?.let { resolveSolidColorHex(it, isDark) }
            ?.let { (0xff000000L or it).toInt() } ?: fallback
        canvas.drawColor(base or 0xff000000.toInt())
        val colors = background?.takeIf { it.type == "gradient" }?.let {
            if (isDark) it.colorsDark ?: it.colors else it.colors
        }
        if (colors != null && colors.size >= 2) {
            val radians = Math.toRadians((background?.angle ?: 0).toDouble())
            val x = cos(radians).toFloat()
            val y = sin(radians).toFloat()
            val divisor = (abs(x) + abs(y)).coerceAtLeast(0.00001f)
            paint.shader = LinearGradient(
                width * (0.5f - x / divisor * 0.5f), height * (0.5f - y / divisor * 0.5f),
                width * (0.5f + x / divisor * 0.5f), height * (0.5f + y / divisor * 0.5f),
                colors.map { (0xff000000L or it).toInt() }.toIntArray(), null, Shader.TileMode.CLAMP,
            )
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            paint.shader = null
        } else if (background?.type != "image" || image == null) {
            // A pure solid has nothing to blur. Add a restrained, neutral light wash
            // using the theme's base hue, without imposing a wallpaper or accent hue.
            paint.shader = RadialGradient(
                width * 0.22f, height * 0.2f, max(width, height) * 0.85f,
                if (isDark) 0x38ffffff else 0x88ffffff.toInt(), 0x00ffffff,
                Shader.TileMode.CLAMP,
            )
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            paint.shader = null
        }
        if (background?.type == "image" && image != null) {
            canvas.save()
            canvas.scale(width.toFloat() / targetSize.width, height.toFloat() / targetSize.height)
            canvas.drawBitmap(
                image.bitmap, null,
                frostedImageBounds(image, targetSize, background.fit ?: "cover"), paint,
            )
            canvas.restore()
        }
        checkCancelled()
        if (blurRadiusPx >= 1f) {
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
            blurFrostedPixels(pixels, width, height, blurRadiusPx * scale, checkCancelled)
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        }
        return bitmap
    } catch (error: Throwable) {
        bitmap.recycle()
        throw error
    }
}

private fun frostedImageBounds(image: FrostedSourceImage, size: IntSize, fit: String): RectF {
    val width = size.width.toFloat()
    val height = size.height.toFloat()
    val sourceWidth = image.originalWidth.toFloat()
    val sourceHeight = image.originalHeight.toFloat()
    if (fit == "fill") return RectF(0f, 0f, width, height)
    if (fit == "none") return RectF(0f, 0f, sourceWidth, sourceHeight)
    val scale = when (fit) {
        "contain" -> min(width / sourceWidth, height / sourceHeight)
        "fit_width" -> width / sourceWidth
        "fit_height" -> height / sourceHeight
        else -> max(width / sourceWidth, height / sourceHeight)
    }
    val left = (width - sourceWidth * scale) / 2f
    val top = (height - sourceHeight * scale) / 2f
    return RectF(left, top, left + sourceWidth * scale, top + sourceHeight * scale)
}
