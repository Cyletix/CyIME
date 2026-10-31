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
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
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
 * The theme is a translucent tint over the real window behind the keyboard.
 * ImeGlassBlurWindow blurs that window; this modifier never fades text/icons.
 * Theme images keep their cached material rendering and do not capture the screen.
 */
/**
 * 「我的图片」开着玻璃时，背景图不模糊、不变淡：用户自己选的图要看得见（用户 10-31：被模糊掉了，什么都看不到）。
 * 按键、候选栏照旧按玻璃透明；内置图片主题不变。
 */
internal fun FrostedGlassConfig.forBackground(background: BackgroundConfig?): FrostedGlassConfig =
    if (CustomImageTheme.isCustomImage(background)) copy(blurRadiusDp = 0f, backgroundOpacity = 1f) else this

@Composable
internal fun Modifier.frostedGlassBackground(
    background: BackgroundConfig?,
    isDark: Boolean,
    fallbackColor: Color,
    config: FrostedGlassConfig,
    translucentSurface: Boolean = false,
): Modifier {
    // Embedded keyboard surfaces sample the same window-space image. Popup windows
    // have their own coordinate origin and must keep their independent backdrop.
    val shared = LocalSharedKeyboardBackdrop.current
    val root = LocalView.current.rootView
    // 「柔化顶部边缘」：只对输入法窗口里的固定键盘；弹窗另有坐标原点，不跟。
    val feather = LocalGlassTopFeather.current?.takeIf { !translucentSurface && it.windowRoot === root }
    if (!translucentSurface && shared != null && root === shared.windowRoot) {
        var origin by remember { mutableStateOf(Offset.Zero) }
        return onGloballyPositioned { origin = it.positionInWindow() }.drawWithContent {
            if (shared.opacity > 0f) drawUnderTopFeather(feather?.top?.value?.minus(origin.y), feather?.heightPx ?: 0f) {
                val image = shared.image?.takeIf { shared.frame.size.width > 0 && shared.frame.size.height > 0 }
                if (image == null) drawRect(shared.fallback, alpha = shared.opacity)
                else {
                    val offset = shared.frame.offsetIn(origin)
                    clipRect {
                        translate(offset.x, offset.y) {
                            drawImage(image, dstSize = shared.frame.size, alpha = shared.opacity,
                                filterQuality = FilterQuality.Medium)
                        }
                    }
                }
            }
            drawContent()
        }
    }
    val normalized = config.normalized()
    var targetSize by remember { mutableStateOf(IntSize.Zero) }
    // Floating capsules resize during docking; keep their backdrop cache stable throughout.
    val renderSize = if (translucentSurface) IntSize(512, 256) else targetSize
    val blurredImage = rememberFrostedBackgroundImage(background, isDark, fallbackColor, normalized, renderSize)

    // 底部圆角时没有共用背景，各层自己画；柔化上沿要知道自己在窗口里的位置。
    var featherOrigin by remember { mutableStateOf(0f) }
    val positioned = if (feather == null) this else onGloballyPositioned { featherOrigin = it.positionInWindow().y }
    return positioned.onSizeChanged { targetSize = it }.drawWithContent {
        // The cached theme bitmap is opaque. Draw exactly one source with material alpha,
        // or its fallback while loading; labels and keycaps remain outside this operation.
        val opacity = normalized.backgroundOpacity
        if (opacity > 0f) drawUnderTopFeather(feather?.top?.value?.minus(featherOrigin), feather?.heightPx ?: 0f) {
            val image = blurredImage
            if (image == null) drawRect(fallbackColor, alpha = opacity)
            else drawImage(
                image,
                dstOffset = IntOffset.Zero,
                dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                alpha = opacity,
                filterQuality = FilterQuality.Medium,
            )
        }
        drawContent()
    }
}

@Composable
internal fun rememberFrostedBackgroundImage(
    background: BackgroundConfig?,
    isDark: Boolean,
    fallbackColor: Color,
    config: FrostedGlassConfig,
    renderSize: IntSize,
    clipboardInMotion: Boolean = LocalClipboardBackdropMotion.current,
): ImageBitmap? {
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

    val resizeCache = remember { ClipboardBackdropSizeCache() }
    val cachedSize = resizeCache.resolve(renderSize, clipboardInMotion)
    var blurredImage by remember(background, isDark, fallbackColor) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(background, isDark, fallbackColor, sourceImage, cachedSize, density, normalized.blurRadiusDp) {
        if (cachedSize.width <= 0 || cachedSize.height <= 0) return@LaunchedEffect
        blurredImage = withContext(Dispatchers.Default) {
            val coroutineContext = currentCoroutineContext()
            renderFrostedBackground(
                background, isDark, fallbackColor.toArgb(), sourceImage, cachedSize,
                normalized.blurRadiusDp * density,
                checkCancelled = { coroutineContext.ensureActive() },
            ).asImageBitmap()
        }
    }

    return blurredImage
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
        }
        if (background?.type == "image" && image != null) {
            canvas.save()
            canvas.scale(width.toFloat() / targetSize.width, height.toFloat() / targetSize.height)
            canvas.drawBitmap(
                image.bitmap, null,
                frostedImageBounds(image, targetSize, background), paint,
            )
            canvas.restore()
            // 与非玻璃时相同的图片遮罩，否则玻璃下图片原样露出，键面文字压在花图上。
            val overlayAlpha = (if (isDark) background.overlayAlphaDark ?: background.overlayAlpha
                else background.overlayAlpha)?.coerceIn(0f, 1f)
            if (overlayAlpha != null && overlayAlpha > 0f) {
                val overlay = (if (isDark) background.overlayColorDark ?: background.overlayColor
                    else background.overlayColor) ?: 0L
                canvas.drawColor(((overlayAlpha * 255).roundToInt() shl 24) or (overlay and 0xFFFFFF).toInt())
            }
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

private fun frostedImageBounds(image: FrostedSourceImage, size: IntSize, background: BackgroundConfig): RectF {
    val width = size.width.toFloat()
    val height = size.height.toFloat()
    val sourceWidth = image.originalWidth.toFloat()
    val sourceHeight = image.originalHeight.toFloat()
    val fit = background.fit ?: "cover"
    if (fit == "fill") return RectF(0f, 0f, width, height)
    if (fit == "none") return RectF(0f, 0f, sourceWidth, sourceHeight)
    // 与不开玻璃时同一套摆放（含「我的图片」的缩放与对齐）；没盖满处是上面先铺的底色。
    val frame = placeThemeImage(sourceWidth, sourceHeight, width, height, fit,
        background.zoom, background.alignX, background.alignY)
    return RectF(frame.left, frame.top, frame.right, frame.bottom)
}
