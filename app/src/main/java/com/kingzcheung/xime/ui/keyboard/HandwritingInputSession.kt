package com.kingzcheung.xime.ui.keyboard

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.kingzcheung.xime.handwriting.OverlappedHandwritingRecognizer.Segment
import com.kingzcheung.xime.handwriting.PenEraser
import com.kingzcheung.xime.handwriting.StrokePoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal enum class HandwritingPhase(val description: String) {
    IDLE("手写区域空白"), WRITING("正在书写"), WAITING("等待识别"),
    RECOGNIZING("正在识别"), CANDIDATES("请选择候选"), NO_RESULT("未识别，请补写或删除")
}

/** 一轮书写拥有自己的笔画、等待和结果；功能键从独立入口执行，不参与笔画命中。 */
internal class HandwritingInputSession(
    private val scope: CoroutineScope,
    private val pauseMillis: () -> Long,
    private val recognize: suspend (List<List<StrokePoint>>) -> List<Segment>,
    private val onNewCharacter: () -> Unit,
    private val onRecognition: (List<Segment>) -> Unit,
    private val onKey: (String) -> Unit,
    /** 公式模式：识别后笔迹留在板上，接着写会连同前面的笔迹整段重新识别；删除键撤掉最后一笔。 */
    private val keepInk: () -> Boolean = { false },
    /**
     * 边写边识别（默认关）：每写完一笔就在后台先认当前全部笔迹，停顿一到直接用这次的结果，不用停顿后才开始认；
     * 接着落笔就作废重来。认的是同一份笔迹，结果和停顿后再认完全一样，只是等待藏进了停顿里。
     * 只给识别没有副作用的书写方式（中文、日语）；英文分词和公式不提前认。
     */
    private val recognizeEarly: () -> Boolean = { false },
) {
    var strokes: List<List<StrokePoint>> by mutableStateOf(emptyList())
        private set
    var currentStroke: List<StrokePoint> by mutableStateOf(emptyList())
        private set
    var phase by mutableStateOf(HandwritingPhase.IDLE)
        private set
    private var revision = 0
    private var job: Job? = null
    private var early: Deferred<List<Segment>>? = null
    private val pendingKeys = mutableListOf<String>()
    val hasInk get() = strokes.isNotEmpty() || currentStroke.isNotEmpty()

    private fun invalidate(): Int {
        job?.cancel()
        job = null
        early?.cancel()
        early = null
        pendingKeys.clear()
        return ++revision
    }

    fun begin(point: StrokePoint): Int {
        val token = invalidate()
        if (strokes.isEmpty()) onNewCharacter()
        currentStroke = listOf(point)
        phase = HandwritingPhase.WRITING
        return token
    }

    fun move(token: Int, point: StrokePoint) = moveAll(token, listOf(point))

    /** 一帧内的全部采样点（含笔的高频中间点）一次追加，避免逐点复制整条笔画。 */
    fun moveAll(token: Int, points: List<StrokePoint>) {
        if (token != revision || phase != HandwritingPhase.WRITING || points.isEmpty()) return
        var last = currentStroke.lastOrNull()
        val added = points.filter { p -> (p.x != last?.x || p.y != last?.y).also { if (it) last = p } }
        if (added.isNotEmpty()) currentStroke = currentStroke + added
    }

    /**
     * 笔尾橡皮或按住笔侧键划过：擦掉碰到的整条笔画，剩余笔迹重新等待识别。返回是否擦到。
     * [from] 为上一帧最后一个橡皮点，橡皮轨迹按线段连起来判断（见 PenEraser），快速划过不漏擦。
     */
    fun erase(points: List<StrokePoint>, radius: Float, from: StrokePoint? = null): Boolean {
        if (strokes.isEmpty() || points.isEmpty()) return false
        val path = if (from != null) listOf(from) + points else points
        val kept = strokes.filterNot { stroke -> stroke.touchedBy(path, radius) }
        if (kept.size == strokes.size) return false
        invalidate()
        strokes = kept
        if (kept.isNotEmpty()) schedule(pauseMillis()) else phase = HandwritingPhase.IDLE
        return true
    }

    fun end(token: Int, cancelled: Boolean = false) {
        if (token != revision) return
        if (!cancelled && currentStroke.isNotEmpty()) strokes = strokes + listOf(currentStroke)
        currentStroke = emptyList()
        if (strokes.isNotEmpty()) schedule(pauseMillis()) else phase = HandwritingPhase.IDLE
    }

    fun clear() {
        invalidate()
        strokes = emptyList()
        currentStroke = emptyList()
        phase = HandwritingPhase.IDLE
    }

    fun press(action: String) {
        when {
            action == "delete" && keepInk() && strokes.isNotEmpty() -> {
                invalidate()
                strokes = strokes.dropLast(1)
                currentStroke = emptyList()
                if (strokes.isNotEmpty()) schedule(pauseMillis()) else {
                    phase = HandwritingPhase.IDLE
                    onKey("clear_composition")
                }
            }
            action in setOf("delete", "clear_all", "clear_composition") && hasInk -> {
                val wasKept = keepInk()
                clear()
                if (wasKept) onKey("clear_composition")
            }
            action in setOf("symbol", "number", "ime_switch") -> {
                clear()
                onKey(action)
            }
            hasInk -> {
                if (pendingKeys.isNotEmpty()) {
                    pendingKeys += action
                    return
                }
                // 空格、回车和标点先处理当前完整笔迹，再执行本次按键。
                if (currentStroke.isNotEmpty()) strokes = strokes + listOf(currentStroke)
                currentStroke = emptyList()
                schedule(0, action)
            }
            else -> {
                invalidate()
                phase = HandwritingPhase.IDLE
                onKey(action)
            }
        }
    }

    /** 笔画与橡皮轨迹都按线段判断，稀疏的快速笔画、快速划过的橡皮中间也能擦到。 */
    private fun List<StrokePoint>.touchedBy(path: List<StrokePoint>, radius: Float): Boolean {
        val stroke = this
        if (path.size == 1) {
            val q = path[0]
            return PenEraser.hits(stroke.size, { stroke[it].x }, { stroke[it].y }, q.x, q.y, q.x, q.y, radius)
        }
        return path.zipWithNext().any { (a, b) ->
            PenEraser.hits(stroke.size, { stroke[it].x }, { stroke[it].y }, a.x, a.y, b.x, b.y, radius)
        }
    }

    private fun schedule(waitMs: Long, after: String? = null) {
        val token = invalidate()
        if (after != null) pendingKeys += after
        val snapshot = strokes.map { it.toList() }
        phase = HandwritingPhase.WAITING
        // 提前认的异常在这里吞掉：async 里的异常会连带取消整个键盘协程作用域
        val ahead = if (after == null && waitMs > 0 && recognizeEarly()) scope.async {
            try { recognize(snapshot) } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { emptyList() }
        }.also { early = it } else null
        job = scope.launch {
            delay(waitMs)
            if (token != revision) return@launch
            phase = HandwritingPhase.RECOGNIZING
            val segments = try {
                ahead?.await() ?: recognize(snapshot)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                emptyList()
            }
            // 删除、切换面板、继续落笔及退出均会使旧推理结果失效。
            if (token != revision) return@launch
            if (segments.isNotEmpty()) {
                if (!keepInk()) strokes = emptyList()
                phase = HandwritingPhase.CANDIDATES
                onRecognition(segments)
            } else phase = HandwritingPhase.NO_RESULT
            if (pendingKeys.isNotEmpty() && token == revision) {
                val keys = pendingKeys.toList()
                pendingKeys.clear()
                strokes = emptyList()
                phase = HandwritingPhase.IDLE
                for (key in keys) {
                    if (token != revision) break
                    onKey(key)
                }
            }
        }
    }
}
