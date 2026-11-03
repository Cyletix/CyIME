package com.kingzcheung.xime.handwriting

import android.util.Log
import com.kingzcheung.xime.util.FileLogger
import org.json.JSONObject
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 手写完整推理管线，运行于 :inference 进程（仅由 [com.kingzcheung.xime.service.InferenceService] 调用）。
 *
 * 主进程客户端（[HandwritingEngine]）只传原始笔画、收最终候选字；
 * 笔画预处理、ONNX 推理与 idx→汉字映射全部在本对象内完成，
 * 保证输入法主进程不加载 ONNX 运行库、不常驻词表。
 */
object HandwritingInference {
    private const val TAG = "HandwritingInference"
    private const val FIXED_LEN = 200
    private const val MAX_POINTS_PER_STROKE = 8

    private var chars: List<String> = emptyList()

    /** 加载 idx→汉字词表（char_index.json，路径由 loadModel 的 extraPath 传入）。 */
    fun loadCharIndex(charIndexPath: String): Boolean {
        return try {
            val text = File(charIndexPath).readText().trimStart('\uFEFF')
            val json = JSONObject(text)

            val extracted = mutableListOf<String>()

            if (json.has("chars")) {
                val arr = json.getJSONArray("chars")
                for (i in 0 until arr.length()) {
                    extracted.add(arr.getString(i))
                }
            } else if (json.has("char_index")) {
                val obj = json.getJSONObject("char_index")
                val keys = obj.keys()
                val indexed = mutableMapOf<Int, String>()
                while (keys.hasNext()) {
                    val ch = keys.next()
                    indexed[obj.getInt(ch)] = ch
                }
                val maxIdx = indexed.keys.maxOrNull() ?: 0
                extracted.addAll(Array(maxIdx + 1) { "" }.toList())
                for ((idx, ch) in indexed) {
                    extracted[idx] = ch
                }
            } else if (json.has("labels")) {
                val arr = json.getJSONArray("labels")
                for (i in 0 until arr.length()) {
                    extracted.add(arr.optString(i, ""))
                }
            } else {
                val keys = json.keys()
                val indexed = mutableMapOf<Int, String>()
                while (keys.hasNext()) {
                    val key = keys.next()
                    indexed[json.getInt(key)] = key
                }
                val maxIdx = indexed.keys.maxOrNull() ?: 0
                extracted.addAll(Array(maxIdx + 1) { "" }.toList())
                for ((idx, ch) in indexed) {
                    extracted[idx] = ch
                }
            }

            chars = extracted.toList()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load char_index: ${e.message}", e)
            FileLogger.e(TAG, "Failed to load char_index: ${e.message}", e)
            false
        }
    }

    fun clearChars() {
        chars = emptyList()
    }

    /** AIDL 原始笔画解码：points = [x0,y0,x1,y1,...]，strokePointCounts = 每笔画点数。 */
    internal fun decodeStrokes(
        points: FloatArray,
        strokePointCounts: IntArray
    ): List<List<Pair<Float, Float>>> {
        val strokes = mutableListOf<List<Pair<Float, Float>>>()
        var offset = 0
        for (count in strokePointCounts) {
            val stroke = mutableListOf<Pair<Float, Float>>()
            repeat(count) {
                if (offset + 1 < points.size) {
                    stroke.add(points[offset] to points[offset + 1])
                }
                offset += 2
            }
            strokes.add(stroke)
        }
        return strokes
    }

    internal fun simplifyStrokes(
        strokes: List<List<Pair<Float, Float>>>
    ): List<List<Pair<Float, Float>>> {
        val simplified = mutableListOf<List<Pair<Float, Float>>>()
        for (stroke in strokes) {
            if (stroke.size <= MAX_POINTS_PER_STROKE) {
                simplified.add(stroke)
            } else {
                val step = (stroke.size - 1).toFloat() / (MAX_POINTS_PER_STROKE - 1)
                val indices = (0 until MAX_POINTS_PER_STROKE).map { i ->
                    (i * step).roundToInt().coerceIn(0, stroke.size - 1)
                }
                simplified.add(indices.map { stroke[it] })
            }
        }
        return simplified
    }

    /** 连笔时一笔最多取这么多点。 */
    private const val MAX_POINTS_CURSIVE = 40
    /** 拐点数（Douglas-Peucker，容差为字大小的 [CORNER_TOLERANCE]）超过这个数的笔算连笔。 */
    private const val CURSIVE_CORNERS = 5
    /** 连笔每多这么多个拐点多给 8 个点。 */
    private const val CORNERS_PER_8_POINTS = 4
    private const val CORNER_TOLERANCE = 0.05f

    /**
     * 中文单字识别用：普通的笔仍均匀取 8 点；几笔连成一笔、拐点多的笔多取点（每 4 个拐点 8 点，最多 40 点），
     * 不然拐角全被丢掉，连笔字认不出。总点数超过模型输入长度时退回每笔 8 点。
     * 只用于 [recognize]（中文候选）；[recognizeRaw] 给假名、字母模板当特征，模板按每笔 8 点做的，不能改。
     */
    internal fun simplifyStrokesCursive(
        strokes: List<List<Pair<Float, Float>>>
    ): List<List<Pair<Float, Float>>> {
        val all = strokes.flatten()
        if (all.isEmpty()) return simplifyStrokes(strokes)
        val size = max(max(all.maxOf { it.first } - all.minOf { it.first }, all.maxOf { it.second } - all.minOf { it.second }), 1f)
        val out = strokes.map { stroke ->
            val corners = if (stroke.size > 2) cornerCount(stroke, CORNER_TOLERANCE * size) else stroke.size
            if (corners <= CURSIVE_CORNERS) sample(stroke, MAX_POINTS_PER_STROKE)
            else sample(stroke, min(MAX_POINTS_CURSIVE, 8 * ((corners - 1 + CORNERS_PER_8_POINTS - 1) / CORNERS_PER_8_POINTS)))
        }
        return if (out.sumOf { it.size } > FIXED_LEN) simplifyStrokes(strokes) else out
    }

    /** 按下标均匀取 [k] 个点（与 [simplifyStrokes] 同一取法）。 */
    private fun sample(stroke: List<Pair<Float, Float>>, k: Int): List<Pair<Float, Float>> {
        if (stroke.size <= k) return stroke
        val step = (stroke.size - 1).toFloat() / (k - 1)
        return (0 until k).map { i -> stroke[(i * step).roundToInt().coerceIn(0, stroke.size - 1)] }
    }

    /** Douglas-Peucker 保留的点数（含两端），偏离连线超过 [eps] 的点才算拐点。 */
    internal fun cornerCount(stroke: List<Pair<Float, Float>>, eps: Float): Int {
        val keep = BooleanArray(stroke.size)
        keep[0] = true; keep[stroke.size - 1] = true
        val stack = ArrayDeque<Pair<Int, Int>>()
        stack.addLast(0 to stroke.size - 1)
        while (stack.isNotEmpty()) {
            val (a, b) = stack.removeLast()
            if (b - a < 2) continue
            val (ax, ay) = stroke[a]; val (bx, by) = stroke[b]
            val vx = bx - ax; val vy = by - ay
            val len = kotlin.math.hypot(vx, vy).takeIf { it > 0f } ?: 1f
            var best = -1f; var at = -1
            for (i in a + 1 until b) {
                val d = kotlin.math.abs(vx * (stroke[i].second - ay) - vy * (stroke[i].first - ax)) / len
                if (d > best) { best = d; at = i }
            }
            if (best > eps) { keep[at] = true; stack.addLast(a to at); stack.addLast(at to b) }
        }
        return keep.count { it }
    }

    internal data class SequenceData(
        val data: FloatArray,
        val originalLen: Int,
    )

    internal fun strokesToSequence(
        strokes: List<List<Pair<Float, Float>>>
    ): SequenceData {
        val allPoints = mutableListOf<Pair<Float, Float>>()
        val penDownFlags = mutableListOf<Int>()

        for (stroke in strokes) {
            for (pt in stroke) {
                allPoints.add(pt)
                penDownFlags.add(1)
            }
            if (penDownFlags.isNotEmpty()) {
                penDownFlags[penDownFlags.size - 1] = 0
            }
        }

        val T = allPoints.size
        if (T == 0) {
            return SequenceData(FloatArray(FIXED_LEN * 5) { 0f }, 0)
        }

        val xs = allPoints.map { it.first }
        val ys = allPoints.map { it.second }

        val minX = xs.min()
        val maxX = xs.max()
        val minY = ys.min()
        val maxY = ys.max()

        val rangeX = max(maxX - minX, 1.0f)
        val rangeY = max(maxY - minY, 1.0f)

        val seq = FloatArray(T * 5)
        for (i in 0 until T) {
            val xNorm = (allPoints[i].first - minX) / rangeX
            val yNorm = (allPoints[i].second - minY) / rangeY
            val dx = if (i == 0) 0f else (allPoints[i].first - allPoints[i - 1].first) / rangeX
            val dy = if (i == 0) 0f else (allPoints[i].second - allPoints[i - 1].second) / rangeY
            val base = i * 5
            seq[base] = xNorm
            seq[base + 1] = yNorm
            seq[base + 2] = dx
            seq[base + 3] = dy
            seq[base + 4] = penDownFlags[i].toFloat()
        }

        val paddedLen = min(T, FIXED_LEN)
        val padded = FloatArray(FIXED_LEN * 5) { 0f }
        System.arraycopy(seq, 0, padded, 0, paddedLen * 5)
        return SequenceData(padded, min(T, FIXED_LEN))
    }

    internal fun buildMask(seqLen: Int): ByteArray {
        val mask = ByteArray(FIXED_LEN) { 0 }
        for (i in 0 until min(seqLen, FIXED_LEN)) {
            mask[i] = 1
        }
        return mask
    }

    /** 原始输出：概率最高的 [n] 类（字、类序号、概率），不按汉字过滤。 */
    fun recognizeRaw(strokes: List<List<Pair<Float, Float>>>, n: Int): List<Triple<String, Int, Float>> {
        if (n <= 0 || chars.isEmpty()) return emptyList()
        val simplified = simplifyStrokes(strokes)
        if (simplified.isEmpty()) return emptyList()
        val seqData = strokesToSequence(simplified)
        val raw = HandwritingNativeEngine.predict(seqData.data, buildMask(seqData.originalLen), min(chars.size, n))
        return raw.filter { (idx, _) -> idx in chars.indices && chars[idx].isNotEmpty() }
            .map { (idx, score) -> Triple(chars[idx], idx, score) }
    }

    /** 完整推理：预处理 → native ONNX → idx→汉字映射（去重、截断）。 */
    fun recognize(
        strokes: List<List<Pair<Float, Float>>>,
        topK: Int
    ): List<HandwritingCandidate> {
        if (topK <= 0 || chars.isEmpty()) return emptyList()
        val simplified = simplifyStrokesCursive(strokes)
        if (simplified.isEmpty()) return emptyList()

        val seqData = strokesToSequence(simplified)
        val mask = buildMask(seqData.originalLen)

        val modelTop = min(chars.size, topK.coerceAtMost(64) * 5)
        val rareTemplates = rare
        // 生僻字模板要模型前 320 类当特征；模型候选仍只看前 modelTop 个（与原来一致）
        val queryTop = if (rareTemplates != null) max(modelTop, min(chars.size, HanziRareTemplates.QUERY_TOP)) else modelTop
        val allRaw = HandwritingNativeEngine.predict(seqData.data, mask, queryTop)
        if (allRaw.isEmpty()) return emptyList()
        val rawResults = allRaw.take(modelTop)

        val results = mutableListOf<HandwritingCandidate>()
        val seen = mutableSetOf<String>()

        for ((idx, score) in rawResults) {
            if (idx < 0 || idx >= chars.size) continue
            val ch = chars[idx]
            if (ch.isEmpty()) continue
            if (ch in seen) continue
            seen.add(ch)
            results.add(HandwritingCandidate(ch, score))
        }

        // The upstream demo prioritizes Han candidates too. Never refill with symbols here:
        // those would participate in DP as high-confidence fragments of an unfinished character.
        val han = HandwritingCandidatePolicy.select(results, results.size)
        if (rareTemplates == null) return HandwritingCandidatePolicy.preferCommon(han, frequency).take(topK)
        val raw = allRaw.asList()
        val rareMatches = rareTemplates.match(raw, HandwritingCandidatePolicy.RARE_MAX * 2, HandwritingCandidatePolicy.RARE_MIN_SIMILARITY)
        val mass = raw.sumOf { it.second.toDouble() }.toFloat().coerceIn(1e-6f, 1f)
        return HandwritingCandidatePolicy.mergeRare(han, rareMatches, frequency, mass).take(topK)
    }

    /** 字表外生僻字的笔顺模板（assets/handwriting/hanzi_rare_och.*）；没加载时不出生僻字。 */
    @Volatile
    private var rare: HanziRareTemplates? = null

    internal fun loadRareTemplates(templates: HanziRareTemplates?) {
        rare = templates
    }

    /** 汉字常用度（assets/handwriting/hanzi_frequency.tsv），候选排序用；没加载时不调整。 */
    @Volatile
    private var frequency: Map<String, Int> = emptyMap()

    fun loadFrequency(lines: Sequence<String>) {
        val map = HashMap<String, Int>(16384)
        for (line in lines) {
            val tab = line.indexOf('\t')
            if (tab <= 0) continue
            line.substring(tab + 1).trim().toIntOrNull()?.let { map[line.substring(0, tab)] = it }
        }
        frequency = map
    }
}
