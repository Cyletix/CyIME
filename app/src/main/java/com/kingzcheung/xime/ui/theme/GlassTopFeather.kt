package com.kingzcheung.xime.ui.theme

import android.view.View
import androidx.compose.runtime.State
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * 玻璃「柔化顶部边缘」（B站用户 10-31：磨玻璃在别的应用上割裂感很重）：固定键盘上沿这一段里，
 * 模糊和玻璃底色都从 0 渐变到满，不再一刀切。只在键盘自己的上沿里面渐变，键盘外面的应用内容
 * （通常是贴着键盘的输入栏）不被模糊、不被染色。按键和文字不跟着淡。
 *
 * 底色在 Compose 里按这段高度渐隐（[drawUnderTopFeather]）；模糊是系统窗口模糊，只能整块一个强度，
 * 所以这段切成几条细窗口，透明度一级级升上去（[glassTopFeatherBands]）。
 */
internal val GLASS_TOP_FEATHER_HEIGHT = 16.dp
internal const val GLASS_TOP_FEATHER_STEPS = 6

/** [top] 是固定键盘卡片上沿在窗口里的 y（像素），null = 没开或当前不适用（悬浮、手写全屏、实体键盘条）。 */
internal class GlassTopFeatherSource(val windowRoot: View, val top: State<Float?>, val heightPx: Float)

internal val LocalGlassTopFeather = staticCompositionLocalOf<GlassTopFeatherSource?> { null }

/**
 * 画玻璃底色：[featherTop]（本层坐标）往下 [featherHeight] 这段由透明渐变到满，下面照旧，
 * [featherTop] 以上不画（固定键盘上沿以上本来就没有键盘）。[featherTop] 为 null 时原样画。
 */
internal fun DrawScope.drawUnderTopFeather(featherTop: Float?, featherHeight: Float, draw: DrawScope.() -> Unit) {
    if (featherTop == null || featherHeight <= 0f) { draw(); return }
    val bandBottom = featherTop + featherHeight
    // 整层在这段下面（导航条、下面的面板）或整层在卡片上沿以上：原样画。
    if (bandBottom <= 0f || featherTop >= size.height) { draw(); return }
    if (bandBottom < size.height) clipRect(top = bandBottom) { draw() }
    val bandTop = featherTop.coerceAtLeast(0f)
    val bandEnd = bandBottom.coerceAtMost(size.height)
    drawIntoCanvas { canvas ->
        // 只给这一条开离屏层：先画底色，再按竖向渐变留下它的一部分。
        canvas.saveLayer(Rect(0f, bandTop, size.width, bandEnd), Paint())
        clipRect(top = bandTop, bottom = bandEnd) { draw() }
        drawRect(
            Brush.verticalGradient(listOf(Color.Transparent, Color.Black), startY = featherTop, endY = bandBottom),
            topLeft = Offset(0f, bandTop), size = Size(size.width, bandEnd - bandTop), blendMode = BlendMode.DstIn,
        )
        canvas.restore()
    }
}

/** 一条模糊窗口：窗口坐标（像素）和它的透明度系数（乘在整块键盘透明度上）。 */
internal data class GlassFeatherBand(val left: Int, val top: Int, val right: Int, val bottom: Int, val alpha: Float)

/**
 * 把卡片顶上 [featherPx] 切成 [steps] 条，从上到下透明度 1/(steps+1) … steps/(steps+1)，下面整块是 1，
 * 上沿以上是 0：每级只差一点，看不出台阶。卡片太矮（不到两段高）时不切，返回空。
 */
internal fun glassTopFeatherBands(left: Int, top: Int, right: Int, bottom: Int, featherPx: Int,
    steps: Int = GLASS_TOP_FEATHER_STEPS): List<GlassFeatherBand> {
    if (featherPx <= 0 || steps <= 0 || right <= left || bottom - top < featherPx * 2) return emptyList()
    return (0 until steps).mapNotNull { i ->
        val from = top + (featherPx * i / steps.toFloat()).roundToInt()
        val to = top + (featherPx * (i + 1) / steps.toFloat()).roundToInt()
        if (to <= from) null else GlassFeatherBand(left, from, right, to, (i + 1f) / (steps + 1f))
    }
}
