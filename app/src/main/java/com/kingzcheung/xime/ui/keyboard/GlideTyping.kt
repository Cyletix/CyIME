package com.kingzcheung.xime.ui.keyboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import com.kingzcheung.xime.glide.GlideTracker

internal val LocalLetterGlide = androidx.compose.runtime.staticCompositionLocalOf { false }

/**
 * 滑行时手指走过的轨迹（键盘本地坐标）和实时预览的词，抬手即清空。
 * [gesture] 在每次确认为滑行时加一：上一笔还没算完的预览对不上就丢掉。
 */
internal class GlideTrail {
    var points by mutableStateOf<List<Offset>>(emptyList())
    var preview by mutableStateOf("")
    var gesture = 0
        private set

    fun startGesture() { gesture++; preview = "" }
}

/**
 * 滑行实时预览的后台识别：每笔一份（[gesture]），[english] 是这一笔开头取的英文上下文规则。
 * 同一时间只有一个 [job]；它在算或歇着时手指又动了就记 [moved]，算完歇够再按最新轨迹算一次。
 */
internal class GlidePreviewJob {
    var gesture = -1
        private set
    var job: kotlinx.coroutines.Job? = null
    var moved = false
    var points: (() -> Pair<FloatArray, FloatArray>)? = null
    var english: ((List<com.kingzcheung.xime.glide.GlideCandidate>, (String) -> String) -> String)? = null

    fun start(gesture: Int) {
        job?.cancel(); job = null
        this.gesture = gesture
        moved = false
        points = null
        english = null
    }

    fun stop() { job?.cancel(); job = null; points = null }
}

/** 实时预览两次识别之间至少隔这么久；手机上一次中文识别约十几到三十毫秒。 */
internal const val GLIDE_PREVIEW_INTERVAL_MS = 60L

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
    /** 确认为滑行后手指每移动一批调用一次（实时预览用），参数取当前整条轨迹（根坐标），只能在主线程调用。 */
    onProgress: ((points: () -> Pair<FloatArray, FloatArray>) -> Unit)? = null,
): Modifier {
    // 回调每次重组都会换新，不能作为 pointerInput 的 key，否则正在进行的滑行会被打断。
    val currentOnTrace by rememberUpdatedState(onTrace)
    val currentOnProgress by rememberUpdatedState(onProgress)
    val currentOrigin by rememberUpdatedState(rootOrigin)
    val holdMs by rememberUpdatedState(LocalKeyboardInputPreferences.current.glideHoldMs)
    if (!enabled || letterGeometry == null) return this
    return pointerInput(letterGeometry, trail) { awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val keys = letterGeometry.snapshot()
        if (keys.size < 26) return@awaitEachGesture
        val origin = currentOrigin()
        val tracker = GlideTracker(keys, viewConfiguration.touchSlop,
            down.position.x + origin.x, down.position.y + origin.y, down.uptimeMillis, holdMs)
        if (tracker.abandoned) return@awaitEachGesture
        val local = arrayListOf(down.position)
        try {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                // 另一根手指按下（快速连打）时放弃，不影响任何按键。
                if (!tracker.claimed && event.changes.any { it.id != down.id && it.pressed }) break
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!tracker.claimed && change.isConsumed) break
                // 抬手也是最后一个轨迹点：短滑行可能在这里才到达相邻字母。
                // 历史采样必须用各自的时刻，不能把整批都算成最后一刻。
                change.historical.forEach { sample ->
                    local += sample.position
                    tracker.move(sample.position.x + origin.x, sample.position.y + origin.y, sample.uptimeMillis, change.uptimeMillis)
                }
                local += change.position
                tracker.move(change.position.x + origin.x, change.position.y + origin.y, change.uptimeMillis)
                if (tracker.abandoned) break
                if (!change.pressed) {
                    if (tracker.claimed) {
                        change.consume()
                        val xs = tracker.xs(); val ys = tracker.ys()
                        // 「贡献滑行轨迹」页开着时才有人收，平时直接返回。
                        com.kingzcheung.xime.glide.GlideSampleRecorder.record("qwerty", keys, xs, ys, tracker.ts())
                        currentOnTrace(xs, ys)
                    }
                    break
                }
                if (tracker.claimed) {
                    if (trail.points.isEmpty()) trail.startGesture()
                    change.consume()
                    trail.points = local.toList()
                    currentOnProgress?.invoke { tracker.xs() to tracker.ys() }
                }
            }
        } finally {
            trail.points = emptyList()
            trail.preview = ""
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

/**
 * 滑行实时预览（「滑行时实时显示词」）：写着现在抬手会出的词（中文是拼音），颜色与按键上滑气泡相同。
 * 用户 11-09：原来跟着手指放在键盘中间，被手指和轨迹挡住看不见；现在固定在键盘上方正中（候选栏那一带），
 * 和 Gboard 一样，手指在哪都挡不住。单独的不接收触摸的小窗，键盘卡片的裁切挡不住它。
 */
@Composable
internal fun GlidePreviewBubble(trail: GlideTrail, background: Color, textColor: Color, modifier: Modifier = Modifier) {
    val text = trail.preview
    if (text.isEmpty() || trail.points.isEmpty()) return
    val gap = with(androidx.compose.ui.platform.LocalDensity.current) { GLIDE_PREVIEW_GAP.roundToPx() }
    val provider = remember(gap) {
        object : androidx.compose.ui.window.PopupPositionProvider {
            override fun calculatePosition(anchorBounds: androidx.compose.ui.unit.IntRect, windowSize: IntSize,
                layoutDirection: androidx.compose.ui.unit.LayoutDirection, popupContentSize: IntSize): IntOffset =
                glidePreviewPosition(anchorBounds, windowSize, popupContentSize, gap)
        }
    }
    Box(modifier) {
        androidx.compose.ui.window.Popup(popupPositionProvider = provider, properties = DisplayOnlyPopupProperties) {
            // 外面留一圈给阴影，小窗只有内容那么大。
            Box(Modifier.padding(4.dp)) {
                Text(text, color = textColor, fontSize = 18.sp, maxLines = 1,
                    modifier = Modifier
                        .testTag("glide-preview")
                        .shadow(3.dp, RoundedCornerShape(50))
                        .background(background, RoundedCornerShape(50))
                        .padding(horizontal = 14.dp, vertical = 6.dp))
            }
        }
    }
}

/** 预览底边离键盘顶边多远。 */
internal val GLIDE_PREVIEW_GAP = 6.dp

/** 预览小窗放在键盘（[anchor]）上方正中；上面没地方时贴窗口顶，左右不出窗口。 */
internal fun glidePreviewPosition(anchor: androidx.compose.ui.unit.IntRect, window: IntSize, popup: IntSize, gap: Int): IntOffset = IntOffset(
    (anchor.left + (anchor.width - popup.width) / 2).coerceIn(0, (window.width - popup.width).coerceAtLeast(0)),
    (anchor.top - gap - popup.height).coerceIn(0, (window.height - popup.height).coerceAtLeast(0)),
)
