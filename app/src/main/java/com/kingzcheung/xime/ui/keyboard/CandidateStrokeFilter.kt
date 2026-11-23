package com.kingzcheung.xime.ui.keyboard

import android.content.Context
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.composed
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

/** The packaged Rime stroke table may give a character more than one valid spelling. */
internal class CandidateStrokeTable(private val codes: Map<String, List<String>>) {
    fun matches(text: String, prefix: String): Boolean {
        if (prefix.isEmpty()) return true
        if (text.isEmpty()) return false
        val first = text.substring(0, Character.charCount(text.codePointAt(0)))
        return codes[first]?.any { code ->
            code.length >= prefix.length && prefix.indices.all { i ->
                code[i] == prefix[i] || prefix[i] == 'N' && (code[i] == 'n' || code[i] == 'p')
            }
        } == true
    }
    companion object {
        fun load(context: Context): CandidateStrokeTable {
            val rows = HashMap<String, MutableList<String>>()
            context.assets.open("rime_chinese/stroke.dict.yaml").bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    val fields = line.split('\t', limit = 3)
                    if (fields.size >= 2 && fields[0].isNotEmpty() && fields[1].isNotEmpty() &&
                        fields[1].all { it in "hspnz" }) rows.getOrPut(fields[0]) { mutableListOf() }.add(fields[1])
                }
            }
            return CandidateStrokeTable(rows)
        }
    }
}

@Composable
internal fun rememberCandidateStrokeTable(): CandidateStrokeTable? {
    val context = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val table by produceState<CandidateStrokeTable?>(null, context) {
        value = withContext(Dispatchers.IO) { runCatching { CandidateStrokeTable.load(context) }.getOrNull() }
    }
    return table
}

/** N is an ambiguous left-falling dot/撇: show 点, but match either code. */
internal fun classifyCandidateStroke(points: List<Offset>, density: Float = 1f): Char? {
    if (points.size < 2) return null
    val start = points.first(); val end = points.last()
    val dx = end.x - start.x; val dy = end.y - start.y
    var path = 0f
    for (i in 1 until points.size) path += (points[i] - points[i - 1]).getDistance()
    if (path < 4f * density) return null
    val straight = (end - start).getDistance()
    if (path > straight * 1.35f && path - straight > 6f * density) return 'z'
    // A dot can be quite long or steep; a nearly straight vertical drag remains 竖.
    // Left-falling dots may also be short 撇, so preserve both readings in the filter.
    val minimumDotLean = if (path <= 32f * density) 0.15f else 0.25f
    if (path <= 64f * density && dy > 0f && abs(dy) >= abs(dx) * 0.4f && abs(dx) >= abs(dy) * minimumDotLean)
        return if (dx < 0f) 'N' else 'n'
    if (abs(dx) > abs(dy) * 1.6f) return 'h'
    if (abs(dy) > abs(dx) * 2.2f) return 's'
    return if (dx < 0f) 'p' else 'n'
}

internal fun Modifier.candidateStrokeGesture(
    enabled: Boolean,
    onStroke: (Char) -> Unit,
    onDrawing: (Boolean) -> Unit = {},
    onInk: (Offset?) -> Unit = {},
): Modifier = if (!enabled) this else composed {
    val current = rememberUpdatedState(onStroke)
    val drawing = rememberUpdatedState(onDrawing)
    val ink = rememberUpdatedState(onInk)
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    Modifier.onGloballyPositioned { coordinates = it }.pointerInput(Unit) {
        val points = ArrayList<Offset>()
        fun append(point: Offset) {
            points.add(point)
            coordinates?.takeIf { it.isAttached }?.localToRoot(point)?.let { ink.value(it) }
        }
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val start = awaitTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
                ?: return@awaitEachGesture
            drawing.value(true)
            points.clear(); ink.value(null)
            append(down.position)
            append(start.position)
            try {
                val finished = drag(start.id) { change ->
                    change.historical.forEach { append(it.position) }
                    append(change.position)
                    change.consume()
                }
                if (finished) classifyCandidateStroke(points, density)?.let { current.value(it) }
            } finally {
                drawing.value(false); points.clear(); ink.value(null)
            }
        }
    }
}

internal fun candidateStrokeLabel(code: String): String = code.map {
    when (it) { 'h' -> '一'; 's' -> '丨'; 'p' -> '丿'; 'n', 'N' -> '丶'; else -> '乛' }
}.joinToString("")

/**
 * 候选栏未展开时从空格起笔触发笔画筛选的控制器。
 * [canTrigger] 在滑动瞬间按需读取候选态，避免把候选更新扩散到整块键盘重组。
 */
internal class SpaceStrokeController(
    val canTrigger: () -> Boolean,
    val onDrawingChanged: (Boolean) -> Unit,
    val onInkPoint: (Offset?) -> Unit,
    val onStrokeTriggered: (Char) -> Unit,
)

internal val LocalSpaceStrokeController = staticCompositionLocalOf<SpaceStrokeController?> { null }

internal val LocalStrokeFilterBackspace = staticCompositionLocalOf<(() -> Boolean)?> { null }

internal val LocalStrokeDeleteRelease = staticCompositionLocalOf<(() -> Unit)?> { null }
