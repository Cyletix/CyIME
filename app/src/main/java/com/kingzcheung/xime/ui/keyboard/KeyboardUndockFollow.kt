package com.kingzcheung.xime.ui.keyboard

import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow

/**
 * 固定键盘往上拉变悬浮后，悬浮键盘接着跟手指走，松手停在手指那里（用户 2026-10-30：上拉只是变成悬浮，不会跟着手指继续移动）。
 *
 * 上拉的把手（平板固定键盘顶边、调节界面里的键盘空白和「拖动移动」胶囊）在固定键盘里，一变悬浮就被换掉，
 * 它们自己的手势跟着结束；所以由键盘最外层一直在的节点 [undockFollowTracker] 接着读同一根手指，
 * 悬浮卡片（普通悬浮或调节界面）按 [finger] 摆到手指下面，松手时由它登记的 [onFinish] 保存位置。
 */
@Stable
internal class KeyboardUndockFollow {
    /** 正在跟的那根手指；没在跟时为 null。 */
    var pointerId: PointerId? = null
        private set

    /** 手指的窗口坐标；没在跟时为 null。悬浮卡片只在摆放阶段读它，跟手时不重组键盘。 */
    var finger by mutableStateOf<Offset?>(null)
        private set

    /** 最外层节点的窗口位置，把它收到的本地坐标换成窗口坐标。 */
    internal var trackerOrigin = Offset.Zero

    /** 悬浮卡片登记的松手动作：按最后的手指位置保存。 */
    internal var onFinish: (() -> Unit)? = null

    /** [fingerInWindow] 为空时由 [undockFollowTracker] 在这个事件的最后一轮补上。 */
    fun start(id: PointerId, fingerInWindow: Offset?) {
        pointerId = id
        finger = fingerInWindow
    }

    internal fun move(fingerInWindow: Offset) {
        finger = fingerInWindow
    }

    internal fun finish() {
        pointerId = null
        onFinish?.invoke()
        finger = null
    }
}

internal val LocalKeyboardUndockFollow = compositionLocalOf<KeyboardUndockFollow?> { null }

/** 跟手时手指在悬浮卡片顶边正中往下这么多：和按着把手、胶囊时的手感一样。 */
internal const val UNDOCK_FOLLOW_GRAB_DP = 20f

/** 跟手时悬浮卡片的位置：尺寸不变，顶边正中在手指上方 [grabPx]，整张收在 [bounds] 里。 */
internal fun undockFollowRect(card: ResizeRect, fingerLocal: Offset, grabPx: Float, bounds: ResizeRect): ResizeRect {
    val left = fingerLocal.x - card.width / 2f
    val top = fingerLocal.y - grabPx
    return ResizeRect(left, top, left + card.width, top + card.height).coerceInside(bounds)
}

/**
 * 放在键盘最外层、换模式也不会被换掉的节点上：跟手开始后，在最后一轮（Final）读这根手指的位置，抬起或取消时结束。
 * 别的触摸一概不管，也不消费。
 */
internal fun Modifier.undockFollowTracker(follow: KeyboardUndockFollow): Modifier = this
    .onGloballyPositioned { follow.trackerOrigin = it.positionInWindow() }
    .pointerInput(follow) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Final)
                val id = follow.pointerId ?: continue
                val change = event.changes.firstOrNull { it.id == id }
                if (change == null || !change.pressed) follow.finish()
                else follow.move(change.position + follow.trackerOrigin)
            }
        }
    }
