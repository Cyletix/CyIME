package com.kingzcheung.xime.ui.keyboard

import android.os.SystemClock
import android.view.MotionEvent
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerInput
import com.kingzcheung.xime.handwriting.StrokePoint

/** 笔最近一次出现（接触或悬停）的时间；用于在用笔时忽略手掌误触。 */
internal class StylusPresence {
    private var lastSeenAt = Long.MIN_VALUE / 2

    fun mark(now: Long) { lastSeenAt = now }

    /** 笔在悬停或刚离开时，手指/手掌落下不当作书写。笔收起一会儿后手指仍可书写。 */
    fun rejectsTouch(type: PointerType, now: Long): Boolean =
        type == PointerType.Touch && now - lastSeenAt < PALM_REJECT_MS

    companion object { const val PALM_REJECT_MS = 1000L }
}

internal fun PointerType.isPen() = this == PointerType.Stylus || this == PointerType.Eraser

/** 笔尾橡皮，或按住笔侧键落笔，都是擦除；三星部分机型把侧键报告为副键。 */
internal fun isErasing(type: PointerType, buttonState: Int): Boolean =
    type == PointerType.Eraser || type == PointerType.Stylus &&
        buttonState and (MotionEvent.BUTTON_STYLUS_PRIMARY or MotionEvent.BUTTON_SECONDARY) != 0

/**
 * 手写板的笔画输入：取一帧内全部历史采样点，区分笔、橡皮端和手指；
 * 笔落下时接管已按下的手掌，手掌不画线。离开画布的点不记录，手势仍由画布持有。
 */
internal fun Modifier.handwritingInk(
    session: HandwritingInputSession,
    presence: StylusPresence,
    eraserRadiusPx: Float,
    onErased: () -> Unit = {},
): Modifier = this
    .pointerInput(presence) {
        // 只观察不消费：悬停事件也要记下笔的存在。
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.changes.any { it.type.isPen() }) presence.mark(SystemClock.uptimeMillis())
            }
        }
    }
    .pointerInput(session, presence) {
        awaitEachGesture {
            val first = awaitFirstDown()
            var active: PointerId? = null
            var token = -1
            var erasing = false
            var lastErase: StrokePoint? = null

            fun erase(points: List<StrokePoint>) {
                if (points.isEmpty()) return
                if (session.erase(points, eraserRadiusPx, lastErase)) onErased()
                lastErase = points.last()
            }

            fun inside(p: StrokePoint) = p.x >= 0 && p.y >= 0 && p.x < size.width && p.y < size.height

            fun start(change: PointerInputChange, event: PointerEvent) {
                if (presence.rejectsTouch(change.type, change.uptimeMillis)) return
                active = change.id
                erasing = isErasing(change.type, event.motionEvent?.buttonState ?: 0)
                val point = change.strokePoints().last()
                lastErase = null
                if (erasing) erase(listOf(point)) else token = session.begin(point)
            }

            fun stopWriting(cancelled: Boolean) {
                if (active != null && !erasing) session.end(token, cancelled)
                active = null
            }

            start(first, currentEvent)
            first.consume()
            try {
                while (true) {
                    val event = awaitPointerEvent()
                    // 手掌先按住时，笔落下立刻接管；手掌那一笔作废。
                    val pen = event.changes.firstOrNull { it.changedToDown() && it.type.isPen() && it.id != active }
                    val current = event.changes.firstOrNull { it.id == active }
                    if (pen != null && (current == null || !current.type.isPen())) {
                        stopWriting(cancelled = true)
                        start(pen, event)
                    } else if (active == null) {
                        event.changes.firstOrNull { it.changedToDown() }?.let { start(it, event) }
                    }
                    event.changes.firstOrNull { it.id == active }?.let { change ->
                        val points = change.strokePoints().filter(::inside)
                        if (erasing) erase(points) else session.moveAll(token, points)
                        if (!change.pressed) stopWriting(cancelled = false)
                    }
                    event.changes.forEach { it.consume() }
                    if (event.changes.none { it.pressed }) break
                }
            } finally {
                stopWriting(cancelled = true)
            }
        }
    }

/** 当前点之前还有一帧内的高频中间点；时间换算到与 [StrokePoint] 相同的墙钟。 */
private fun PointerInputChange.strokePoints(): List<StrokePoint> {
    val offset = System.currentTimeMillis() - uptimeMillis
    val pen = type.isPen()
    return historical.map { StrokePoint(it.position.x, it.position.y, it.uptimeMillis + offset, pen) } +
        StrokePoint(position.x, position.y, uptimeMillis + offset, pen)
}
