package com.kingzcheung.xime.ui.keyboard

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.kingzcheung.xime.handwriting.OverlappedHandwritingRecognizer.Segment
import com.kingzcheung.xime.handwriting.StrokePoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
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
) {
    var strokes: List<List<StrokePoint>> by mutableStateOf(emptyList())
        private set
    var currentStroke: List<StrokePoint> by mutableStateOf(emptyList())
        private set
    var phase by mutableStateOf(HandwritingPhase.IDLE)
        private set
    private var revision = 0
    private var job: Job? = null
    private val pendingKeys = mutableListOf<String>()
    val hasInk get() = strokes.isNotEmpty() || currentStroke.isNotEmpty()

    private fun invalidate(): Int {
        job?.cancel()
        job = null
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

    fun move(token: Int, point: StrokePoint) {
        if (token == revision && phase == HandwritingPhase.WRITING && currentStroke.lastOrNull() != point) {
            currentStroke = currentStroke + point
        }
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
            action == "delete" && hasInk -> clear()
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

    private fun schedule(waitMs: Long, after: String? = null) {
        val token = invalidate()
        if (after != null) pendingKeys += after
        val snapshot = strokes.map { it.toList() }
        phase = HandwritingPhase.WAITING
        job = scope.launch {
            delay(waitMs)
            if (token != revision) return@launch
            phase = HandwritingPhase.RECOGNIZING
            val segments = try {
                recognize(snapshot)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                emptyList()
            }
            // 删除、切换面板、继续落笔及退出均会使旧推理结果失效。
            if (token != revision) return@launch
            if (segments.isNotEmpty()) {
                strokes = emptyList()
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
