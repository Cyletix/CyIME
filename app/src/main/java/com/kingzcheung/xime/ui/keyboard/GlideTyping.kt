package com.kingzcheung.xime.ui.keyboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.kingzcheung.xime.glide.GlideTracker

/** 滑行时手指走过的轨迹（键盘本地坐标），抬手即清空。 */
internal class GlideTrail {
    var points by mutableStateOf<List<Offset>>(emptyList())
}

/**
 * 在字母区上方观察触摸（Initial 阶段，先于按键）。确认是滑行后才消费事件，
 * 按键随即取消自己的点按、上下滑和长按；确认之前一律不干预。
 * [rootOrigin] 把本地坐标换到按键记录位置所用的根坐标。
 */
@Composable
internal fun Modifier.glideTyping(
    enabled: Boolean,
    letterGeometry: LetterGeometry?,
    rootOrigin: () -> Offset,
    trail: GlideTrail,
    onTrace: (xs: FloatArray, ys: FloatArray) -> Unit,
): Modifier {
    // 回调每次重组都会换新，不能作为 pointerInput 的 key，否则正在进行的滑行会被打断。
    val currentOnTrace by rememberUpdatedState(onTrace)
    val currentOrigin by rememberUpdatedState(rootOrigin)
    if (!enabled || letterGeometry == null) return this
    return pointerInput(letterGeometry, trail) { awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val keys = letterGeometry.snapshot()
        if (keys.size < 26) return@awaitEachGesture
        val origin = currentOrigin()
        val tracker = GlideTracker(keys, viewConfiguration.touchSlop,
            down.position.x + origin.x, down.position.y + origin.y, down.uptimeMillis)
        if (tracker.abandoned) return@awaitEachGesture
        val local = arrayListOf(down.position)
        try {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                // 另一根手指按下（快速连打）时放弃，不影响任何按键。
                if (!tracker.claimed && event.changes.any { it.id != down.id && it.pressed }) break
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!tracker.claimed && change.isConsumed) break
                if (!change.pressed) {
                    if (tracker.claimed) {
                        change.consume()
                        currentOnTrace(tracker.xs(), tracker.ys())
                    }
                    break
                }
                (change.historical.map { it.position } + change.position).forEach { position ->
                    local += position
                    tracker.move(position.x + origin.x, position.y + origin.y, change.uptimeMillis)
                }
                if (tracker.abandoned) break
                if (tracker.claimed) {
                    change.consume()
                    trail.points = local.toList()
                }
            }
        } finally {
            trail.points = emptyList()
        }
    } }
}

@Composable
internal fun GlideTrailOverlay(trail: GlideTrail, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val points = trail.points
        if (points.size < 2) return@Canvas
        val path = Path().apply {
            moveTo(points[0].x, points[0].y)
            for (index in 1 until points.size) lineTo(points[index].x, points[index].y)
        }
        drawPath(path, color.copy(alpha = 0.55f),
            style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
