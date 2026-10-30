package com.kingzcheung.xime.ui.theme

import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.kingzcheung.xime.settings.BackgroundConfig
import com.kingzcheung.xime.settings.FrostedGlassConfig
import com.kingzcheung.xime.ui.keyboard.rememberKeyboardInputPreferences
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 角度制 → 渐变起始终止点（归一化坐标 0..1）。 */
private fun angleToFractionalPoints(angleDeg: Int): Pair<Offset, Offset> {
    val rad = Math.toRadians(angleDeg.toDouble())
    val cosA = cos(rad)
    val sinA = sin(rad)
    val divisor = (abs(cosA) + abs(sinA)).coerceAtLeast(1e-10)
    return Offset(
        (0.5f - (cosA / divisor * 0.5f)).toFloat().coerceIn(0f, 1f),
        (0.5f - (sinA / divisor * 0.5f)).toFloat().coerceIn(0f, 1f),
    ) to Offset(
        (0.5f + (cosA / divisor * 0.5f)).toFloat().coerceIn(0f, 1f),
        (0.5f + (sinA / divisor * 0.5f)).toFloat().coerceIn(0f, 1f),
    )
}

/** 从 BackgroundConfig 解析出像素色值，供不支持渐变/图片的场景使用。 */
fun resolveSolidColorHex(config: BackgroundConfig, isDark: Boolean): Long? {
    return when (config.type) {
        "solid" -> if (isDark) config.colorDark ?: config.color else config.color
        "gradient" -> {
            val hexes = if (isDark) config.colorsDark ?: config.colors else config.colors
            hexes?.firstOrNull()
        }
        else -> null
    }
}

/** 从 BackgroundConfig 解析出 Compose Color。 */
fun resolveSolidColor(config: BackgroundConfig, isDark: Boolean): Color? {
    return resolveSolidColorHex(config, isDark)?.let { Color(0xFF000000 or it) }
}

/**
 * 应用 BackgroundConfig 背景到 Composable。
 *
 * - solid / gradient：直接用 [Modifier.background]
 * - image：用 [Modifier.drawWithContent] 在内容下方绘制图片
 */
@Composable
fun Modifier.keyboardBackground(
    background: BackgroundConfig?,
    isDark: Boolean,
    fallbackColor: Color = Color(0xFFE3E4E8),
    frostedGlass: FrostedGlassConfig = rememberKeyboardInputPreferences(isDark).frostedGlass,
    translucentSurface: Boolean = false,
): Modifier {
    if (frostedGlass.enabled) {
        return frostedGlassBackground(background, isDark, fallbackColor, frostedGlass, translucentSurface)
    }
    if (background == null) return this.then(Modifier.background(fallbackColor))

    when (background.type) {
        "solid" -> {
            val hex = if (isDark) background.colorDark ?: background.color else background.color
            val color = hex?.let { Color(0xFF000000 or it) }
            return if (color != null) this.then(Modifier.background(color))
            else this.then(Modifier.background(fallbackColor))
        }
        "gradient" -> {
            val hexes = if (isDark) background.colorsDark ?: background.colors else background.colors
            if (hexes != null && hexes.size >= 2) {
                val colors = hexes.map { Color(0xFF000000 or it) }
                val (startRatio, endRatio) = angleToFractionalPoints(background.angle ?: 0)
                return this.then(
                    Modifier.drawWithContent {
                        val start = Offset(size.width * startRatio.x, size.height * startRatio.y)
                        val end = Offset(size.width * endRatio.x, size.height * endRatio.y)
                        drawRect(
                            brush = Brush.linearGradient(colors, start, end),
                            size = size,
                        )
                        drawContent()
                    }
                )
            }
            return this.then(Modifier.background(fallbackColor))
        }
        "image" -> {
            val src = themeImageSource(background, isDark)
            if (src.isNullOrBlank()) return this.then(Modifier.background(fallbackColor))

            val bitmap = rememberThemeImage(src)
            if (bitmap != null) {
                val fit = background.fit ?: "cover"
                return this.then(
                    Modifier.drawWithContent {
                        drawImageBackground(bitmap, fit)
                        drawImageOverlay(background, isDark)
                        drawContent()
                    }
                )
            }
            return this.then(Modifier.background(fallbackColor))
        }
        else -> return this.then(Modifier.background(fallbackColor))
    }
}

private fun themeImageSource(background: BackgroundConfig, isDark: Boolean): String? =
    if (isDark) background.srcDark ?: background.src else background.src

@Composable
private fun rememberThemeImage(src: String): ImageBitmap? {
    val context = LocalContext.current
    var imageBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(src) {
        imageBitmap = try {
            withContext(Dispatchers.Default) {
                // 按屏幕宽度降采样：4000 px 的主题图整张解码要占几十 MB，键盘只铺满一屏宽。
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                openThemeImageStream(context, src)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                val target = context.resources.displayMetrics.let { maxOf(it.widthPixels, it.heightPixels) }
                var sample = 1
                while (bounds.outWidth / (sample * 2) >= target && bounds.outHeight / (sample * 2) >= target / 2) sample *= 2
                openThemeImageStream(context, src)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
                }
            }
        } catch (e: Exception) { null }
    }
    return imageBitmap
}

/** 半透明遮罩（默认黑色）压住图片，提升按键与候选字对比度。 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawImageOverlay(background: BackgroundConfig, isDark: Boolean) {
    val overlayAlpha = if (isDark) background.overlayAlphaDark ?: background.overlayAlpha else background.overlayAlpha
    if (overlayAlpha == null || overlayAlpha <= 0f) return
    val overlayColor = (if (isDark) background.overlayColorDark ?: background.overlayColor
        else background.overlayColor)?.let { Color(0xFF000000 or it) } ?: Color.Black
    drawRect(color = overlayColor.copy(alpha = overlayAlpha.coerceIn(0f, 1f)))
}

/**
 * 键盘卡片下面那条导航区的背景：接着卡片底边往下铺，而不是按这条自己的尺寸重新铺一遍。
 *
 * 各铺各的时，渐变在卡片底下从头色重新开始，图片另截中间一小块，底下那 1 厘米左右
 * 跟键盘断开，看起来像键盘悬空了（2026-10-30 用户反馈：换了渐变/图片主题后底下悬空）。
 * 渐变按卡片的同一条线性渐变延伸过去；图片没有卡片以外的部分，沿卡片底边镜像过来。
 * 两种接缝处都和卡片最后一行同色。纯色和毛玻璃（已共用一张窗口坐标背景）照旧。
 *
 * [card] 是卡片在窗口里的位置；还没量到时退回按这条自身尺寸铺。
 */
@Composable
fun Modifier.keyboardBackgroundBelowCard(
    background: BackgroundConfig?,
    isDark: Boolean,
    fallbackColor: Color,
    card: () -> Rect?,
    frostedGlass: FrostedGlassConfig = rememberKeyboardInputPreferences(isDark).frostedGlass,
): Modifier {
    val continued = background != null && !frostedGlass.enabled && when (background.type) {
        "gradient" -> gradientColors(background, isDark) != null
        "image" -> !themeImageSource(background, isDark).isNullOrBlank()
        else -> false
    }
    if (!continued || background == null) return keyboardBackground(background, isDark, fallbackColor, frostedGlass)
    val bitmap = if (background.type == "image") rememberThemeImage(themeImageSource(background, isDark)!!) else null
    var origin by remember { mutableStateOf<Offset?>(null) }
    return onGloballyPositioned { origin = it.positionInWindow() }.drawWithContent {
        val frame = origin?.let { o -> card()?.takeIf { it.width > 0f && it.height > 0f }?.translate(-o.x, -o.y) }
        if (background.type == "gradient") {
            val colors = gradientColors(background, isDark)!!
            val (start, end) = gradientLine(background, frame ?: Rect(Offset.Zero, size))
            drawRect(Brush.linearGradient(colors, start, end), size = size)
        } else if (frame != null && bitmap != null) {
            // 窄于整宽的固定卡片两侧没有图，先铺主题底色。
            drawRect(fallbackColor)
            clipRect {
                // 卡片底边为轴上下翻转：卡片最后一行正好落在这条第一行。
                translate(frame.left, frame.bottom + frame.height) {
                    scale(1f, -1f, pivot = Offset.Zero) {
                        drawImageBackground(bitmap, background.fit ?: "cover", frame.width, frame.height)
                    }
                }
            }
            drawImageOverlay(background, isDark)
        } else {
            drawRect(fallbackColor)
        }
        drawContent()
    }
}

/**
 * 固定卡片窄于整宽时两侧的留空（设置「填满留空处」打开时）：只在卡片那一段高度里铺满整宽。
 * 渐变沿卡片同一条线延伸；图片没有卡片以外的部分，两侧铺主题底色（与导航条两侧相同）；
 * 纯色和毛玻璃照卡片的画法，毛玻璃的模糊由服务另开两侧的模糊窗口。
 * [card] 是卡片在窗口里的位置；还没量到时什么也不画。
 */
@Composable
fun Modifier.keyboardBackgroundBesideCard(
    background: BackgroundConfig?,
    isDark: Boolean,
    fallbackColor: Color,
    card: () -> Rect?,
    frostedGlass: FrostedGlassConfig = rememberKeyboardInputPreferences(isDark).frostedGlass,
): Modifier {
    var origin by remember { mutableStateOf<Offset?>(null) }
    val gradient = background?.takeIf { !frostedGlass.enabled && it.type == "gradient" }?.let { gradientColors(it, isDark) }
    val image = !frostedGlass.enabled && background?.type == "image"
    fun frame(): Rect? = origin?.let { o -> card()?.takeIf { it.width > 0f && it.height > 0f }?.translate(-o.x, -o.y) }
    val band = onGloballyPositioned { origin = it.positionInWindow() }.drawWithContent {
        val f = frame() ?: return@drawWithContent
        clipRect(0f, f.top, size.width, f.bottom) {
            when {
                gradient != null -> {
                    val (start, end) = gradientLine(background!!, f)
                    drawRect(Brush.linearGradient(gradient, start, end), size = size)
                }
                image -> drawRect(fallbackColor)
                else -> this@drawWithContent.drawContent()
            }
        }
    }
    return if (gradient != null || image) band
        else band.keyboardBackground(background, isDark, fallbackColor, frostedGlass)
}

private fun gradientColors(background: BackgroundConfig, isDark: Boolean): List<Color>? =
    (if (isDark) background.colorsDark ?: background.colors else background.colors)
        ?.takeIf { it.size >= 2 }?.map { Color(0xFF000000 or it) }

/** 渐变在 [frame] 里的起止点（[Modifier.keyboardBackground] 按自身尺寸算的同一条线）。 */
internal fun gradientLine(background: BackgroundConfig, frame: Rect): Pair<Offset, Offset> {
    val (startRatio, endRatio) = angleToFractionalPoints(background.angle ?: 0)
    return Offset(frame.left + frame.width * startRatio.x, frame.top + frame.height * startRatio.y) to
        Offset(frame.left + frame.width * endRatio.x, frame.top + frame.height * endRatio.y)
}

/** 在 DrawScope 内绘制图片背景。 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawImageBackground(
    bitmap: ImageBitmap,
    fit: String,
    dstW: Float = size.width,
    dstH: Float = size.height,
) {
    val srcW = bitmap.width.toFloat()
    val srcH = bitmap.height.toFloat()
    if (srcW <= 0f || srcH <= 0f || dstW <= 0f || dstH <= 0f) return

    val scale: Float
    val offsetX: Float
    val offsetY: Float

    when (fit) {
        "contain" -> {
            scale = min(dstW / srcW, dstH / srcH)
            offsetX = (dstW - srcW * scale) / 2f
            offsetY = (dstH - srcH * scale) / 2f
            clipRect(right = dstW, bottom = dstH) {
                drawImage(
                    bitmap,
                    dstOffset = IntOffset(offsetX.roundToInt(), offsetY.roundToInt()),
                    dstSize = IntSize((srcW * scale).roundToInt(), (srcH * scale).roundToInt()),
                )
            }
        }
        "fill" -> {
            drawImage(
                bitmap,
                dstOffset = IntOffset.Zero,
                dstSize = IntSize(dstW.roundToInt(), dstH.roundToInt()),
            )
        }
        "fit_width" -> {
            scale = dstW / srcW
            offsetY = (dstH - srcH * scale) / 2f
            clipRect(right = dstW, bottom = dstH) {
                drawImage(
                    bitmap,
                    dstOffset = IntOffset(0, offsetY.roundToInt()),
                    dstSize = IntSize((srcW * scale).roundToInt(), (srcH * scale).roundToInt()),
                )
            }
        }
        "fit_height" -> {
            scale = dstH / srcH
            offsetX = (dstW - srcW * scale) / 2f
            clipRect(right = dstW, bottom = dstH) {
                drawImage(
                    bitmap,
                    dstOffset = IntOffset(offsetX.roundToInt(), 0),
                    dstSize = IntSize((srcW * scale).roundToInt(), (srcH * scale).roundToInt()),
                )
            }
        }
        "none" -> {
            drawImage(
                bitmap,
                dstOffset = IntOffset.Zero,
                dstSize = IntSize(srcW.roundToInt(), srcH.roundToInt()),
            )
        }
        else -> { // cover (default)
            scale = max(dstW / srcW, dstH / srcH)
            offsetX = (dstW - srcW * scale) / 2f
            offsetY = (dstH - srcH * scale) / 2f
            clipRect(right = dstW, bottom = dstH) {
                drawImage(
                    bitmap,
                    dstOffset = IntOffset(offsetX.roundToInt(), offsetY.roundToInt()),
                    dstSize = IntSize((srcW * scale).roundToInt(), (srcH * scale).roundToInt()),
                )
            }
        }
    }
}
