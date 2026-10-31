package com.kingzcheung.xime.ui.keyboard

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/**
 * 没有键帽托着的字和图标（候选栏、工具栏、实体键盘浮条）背后垫的反色光晕。
 *
 * 透明键盘后面是什么颜色，输入法看不到：系统模糊由合成器完成，Android 不让输入法读别的应用的画面。
 * 所以不靠检测，而是按最坏情况算：后面可能是纯白也可能是纯黑，两种都要达到与键面文字相同的 4.5:1。
 * 光晕与文字反色（深字白晕、浅字黑晕）：后面本来就亮（深字）或本来就暗（浅字）时光晕与背景同色、看不出来；
 * 后面换成反差色时它才显出来托住字。普通情况的观感和整体透明度都不变。
 */
internal data class GlassTextHalo(val color: Color, val alpha: Float)

/** 光晕的模糊半径；中文字的笔画内空约 1-2dp，2dp 能填满并在字外留一圈窄柔边。 */
internal val GLASS_HALO_RADIUS = 2.dp

/**
 * 光晕颜色与文字阴影同一规则：取与文字反差更大的黑或白。
 * 不按亮度 0.5 切：#C4B2B4 这类偏亮的灰字亮度不到 0.5，按 0.5 会配白晕，白宿主上等于没有。
 */
internal fun glassHaloColor(text: Color): Color {
    val opaque = text.copy(alpha = 1f)
    return if (textContrast(opaque, Color.Black) >= textContrast(opaque, Color.White)) Color.Black else Color.White
}

/**
 * 光晕最少要多浓，才能在白、黑两种宿主上都让 [texts] 达到 [target]。
 * [panel] 是字下面那层玻璃（主题底色按 [panelOpacity] 盖在宿主上）；本来就达标返回 null。
 */
internal fun glassTextHalo(
    texts: List<Color>,
    panel: Color,
    panelOpacity: Float,
    target: Float = GLASS_LABEL_CONTRAST,
): GlassTextHalo? {
    if (texts.isEmpty()) return null
    val opaqueTexts = texts.map { it.copy(alpha = 1f) }
    val halo = glassHaloColor(opaqueTexts.first())
    val opacity = panelOpacity.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 1f
    fun worst(alpha: Float): Float = listOf(Color.White, Color.Black).minOf { host ->
        val behind = halo.copy(alpha = alpha).compositeOver(panel.copy(alpha = opacity).compositeOver(host))
        opaqueTexts.minOf { textContrast(it, behind) }
    }
    if (worst(0f) >= target) return null
    for (step in 1..20) {
        val alpha = step / 20f
        if (worst(alpha) >= target) return GlassTextHalo(halo, alpha)
    }
    return GlassTextHalo(halo, 1f)
}

/**
 * 只给内容里的字和图标垫光晕：比光晕色更接近文字色的不透明像素才算字，
 * 键帽色的按钮底、选中高亮这类与光晕同侧或很淡的填充不生晕。
 * 放在修饰链里背景、按钮底之后。Android 12 以下没有 RenderEffect，保留原来的 1dp 文字阴影。
 */
@Composable
internal fun Modifier.glassTextHalo(halo: GlassTextHalo?): Modifier {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return this
    if (halo == null || halo.alpha <= 0f) return this
    val radiusPx = with(LocalDensity.current) { GLASS_HALO_RADIUS.toPx() }
    val layer = rememberGraphicsLayer()
    val effect = remember(halo.color, radiusPx) { glassHaloEffect(halo.color, radiusPx).asComposeRenderEffect() }
    return drawWithContent {
        layer.record { this@drawWithContent.drawContent() }
        layer.renderEffect = effect
        layer.alpha = halo.alpha
        drawLayer(layer)
        drawContent()
    }
}

/** 取字形 → 模糊 → 染成光晕色并加厚，相当于把字形向外扩一圈柔边。 */
@RequiresApi(Build.VERSION_CODES.S)
private fun glassHaloEffect(color: Color, radiusPx: Float): android.graphics.RenderEffect {
    val glyphs = android.graphics.RenderEffect.createColorFilterEffect(
        ColorMatrixColorFilter(ColorMatrix(glyphMaskMatrix(darkGlyphs = color.luminance() > 0.5f))))
    val blurred = android.graphics.RenderEffect.createBlurEffect(radiusPx, radiusPx, glyphs, Shader.TileMode.DECAL)
    return android.graphics.RenderEffect.createColorFilterEffect(
        ColorMatrixColorFilter(ColorMatrix(haloTintMatrix(color))), blurred)
}

/** 选字矩阵的放大倍数：深浅分明的字形取满。 */
internal const val GLASS_HALO_MASK_GAIN = 2f
/** 选字门槛：选中高亮（主题色 20%）这类淡填充落在门槛下，不生晕。 */
internal const val GLASS_HALO_MASK_THRESHOLD = 0.5f
/** 模糊后的加厚倍数：笔画周围一圈取满光晕浓度，再往外柔和过渡。 */
internal const val GLASS_HALO_SPREAD_GAIN = 4f

/**
 * Android ColorMatrix（未预乘，0-255）。白晕时字比背景深：A' = g·(A − L) − t；
 * 黑晕时字比背景浅：A' = g·(A + L − 255) − t。L 是 sRGB 亮度近似，RGB 输出无关紧要。
 */
internal fun glyphMaskMatrix(darkGlyphs: Boolean): FloatArray {
    val g = GLASS_HALO_MASK_GAIN
    val sign = if (darkGlyphs) -1f else 1f
    val offset = (if (darkGlyphs) 0f else -g * 255f) - GLASS_HALO_MASK_THRESHOLD * 255f
    return floatArrayOf(
        0f, 0f, 0f, 0f, 0f,
        0f, 0f, 0f, 0f, 0f,
        0f, 0f, 0f, 0f, 0f,
        sign * g * 0.2126f, sign * g * 0.7152f, sign * g * 0.0722f, g, offset,
    )
}

internal fun haloTintMatrix(color: Color): FloatArray = floatArrayOf(
    0f, 0f, 0f, 0f, color.red * 255f,
    0f, 0f, 0f, 0f, color.green * 255f,
    0f, 0f, 0f, 0f, color.blue * 255f,
    0f, 0f, 0f, GLASS_HALO_SPREAD_GAIN, 0f,
)
