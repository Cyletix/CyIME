package com.kingzcheung.xime.ui.keyboard

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

internal const val POSITION_HIGHLIGHT_FADE_IN_MILLIS = 150
internal const val POSITION_HIGHLIGHT_HOLD_MILLIS = 1200L
internal const val POSITION_HIGHLIGHT_FADE_OUT_MILLIS = 450

/** 角标离卡片外沿的距离。 */
internal val PositionHighlightGap = 8.dp

/**
 * Gboard 式位置提示：切换成悬浮、拖完悬浮键盘、点一下底部拖动条、退出调节界面后，在悬浮卡片外四角亮出弧形角标，
 * 停一会儿再淡出，让人看清键盘现在落在哪里。亮着时角标就是调大小的把手：拖角改尺寸，点角打开调节界面。
 * 只在绘制阶段读透明度，动画不重组键盘。
 */
@Stable
internal class KeyboardPositionHighlight {
    val alpha = Animatable(0f)
    var epoch by mutableIntStateOf(0)
        private set

    /** 手指按在角标上（正在拖角调大小）时一直亮着；松手再 [trigger] 停一会儿淡出。 */
    var held by mutableStateOf(false)

    /**
     * 角标是否在显示（从亮起到淡出完）：亮起时立刻置上，不等第一帧动画；把手的有无只看它，
     * 不在组合里读逐帧变化的透明度。
     */
    var lit by mutableStateOf(false)
        private set

    fun trigger() {
        epoch++
        lit = true
    }

    internal fun faded() {
        if (!held) lit = false
    }
}

@Composable
internal fun rememberKeyboardPositionHighlight(): KeyboardPositionHighlight {
    val highlight = remember { KeyboardPositionHighlight() }
    LaunchedEffect(highlight.epoch, highlight.held) {
        if (highlight.held) {
            highlight.alpha.snapTo(1f)
            return@LaunchedEffect
        }
        if (highlight.epoch == 0) return@LaunchedEffect
        highlight.alpha.animateTo(1f, tween(POSITION_HIGHLIGHT_FADE_IN_MILLIS))
        delay(POSITION_HIGHLIGHT_HOLD_MILLIS)
        highlight.alpha.animateTo(0f, tween(POSITION_HIGHLIGHT_FADE_OUT_MILLIS))
        highlight.faded()
    }
    return highlight
}

/**
 * 在本层（卡片四周各外扩 [PositionHighlightGap]）的四角画弧形角标：圆心与卡片圆角同心，
 * 弧两端各接一小段直边。颜色用主题强调色；玻璃下背后透着内容，角标下再垫一圈淡的同色光晕。
 */
internal fun Modifier.keyboardPositionCorners(
    highlight: KeyboardPositionHighlight,
    color: Color,
    glass: Boolean,
): Modifier = drawBehind {
    val alpha = highlight.alpha.value
    if (alpha <= 0f) return@drawBehind
    drawPositionCorners(Offset.Zero, size, color, alpha, glass)
}

/**
 * 外扩后的区域（[topLeft]、[size]，卡片四周各多 [PositionHighlightGap]）四角的弧形角标。
 * 悬浮键盘亮角和调节界面里的悬浮键盘共用这一种画法，两处看起来一样。
 */
internal fun DrawScope.drawPositionCorners(topLeft: Offset, size: Size, color: Color, alpha: Float, glass: Boolean) {
    val gap = PositionHighlightGap.toPx()
    val radius = (FloatingKeyboardCardCorner.toPx() + gap).coerceAtMost(minOf(size.width, size.height) / 2f)
    val leg = 10.dp.toPx().coerceAtMost(minOf(size.width, size.height) / 4f)
    val inset = 2.dp.toPx()
    val path = Path()
    // 左上、右上、右下、左下：每个角从一条边上的直段走过圆弧到另一条边。
    fun corner(cx: Float, cy: Float, startAngle: Float, dirX: Float, dirY: Float) {
        val arc = Rect(cx - radius, cy - radius, cx + radius, cy + radius)
        val startX = cx + radius * kotlin.math.cos(Math.toRadians(startAngle.toDouble())).toFloat()
        val startY = cy + radius * kotlin.math.sin(Math.toRadians(startAngle.toDouble())).toFloat()
        // 弧起点在竖边上，先从竖边往里伸一段。
        path.moveTo(startX, startY + dirY * leg)
        path.lineTo(startX, startY)
        path.arcTo(arc, startAngle, 90f * dirX * dirY, forceMoveTo = false)
        val endX = cx + radius * kotlin.math.cos(Math.toRadians((startAngle + 90f * dirX * dirY).toDouble())).toFloat()
        val endY = cy + radius * kotlin.math.sin(Math.toRadians((startAngle + 90f * dirX * dirY).toDouble())).toFloat()
        path.lineTo(endX + dirX * leg, endY)
    }
    val l = topLeft.x + inset + radius
    val t = topLeft.y + inset + radius
    val r = topLeft.x + size.width - inset - radius
    val b = topLeft.y + size.height - inset - radius
    corner(l, t, 180f, 1f, 1f)
    corner(r, t, 0f, -1f, 1f)
    corner(r, b, 0f, -1f, -1f)
    corner(l, b, 180f, 1f, -1f)
    if (glass) drawPath(path, color.copy(alpha = 0.3f * alpha), style = Stroke(9.dp.toPx(), cap = StrokeCap.Round))
    drawPath(path, color.copy(alpha = alpha), style = Stroke(4.dp.toPx(), cap = StrokeCap.Round))
}
