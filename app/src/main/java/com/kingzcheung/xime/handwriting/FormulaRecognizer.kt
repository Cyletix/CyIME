package com.kingzcheung.xime.handwriting

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.util.Log
import com.kingzcheung.xime.util.FileLogger
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.FloatBuffer
import java.nio.LongBuffer
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max

/**
 * 手写公式识别：整段笔迹 → LaTeX。模型是电脑上训练的个人模型包（tools/handwriting_training/
 * formula_train.py + export_formula_onnx.py），推到外部私有目录 `handwriting_models/formula/`，不随安装包分发。
 *
 * 预处理必须和训练一致（mathwriting_baseline.featurize + formula_arch_ablation.collate_segments）：
 * 按公式高度统一缩放 → 沿笔迹每走 1.5% 高度留一个点 → 每点 (x, y, dx, dy, 笔画结束, 笔画开始) →
 * 长笔画均分成不超过 16 点的段，段内坐标相对段中心，段特征 (中心 x、y，宽、高，段是否一笔开头/结尾，
 * 所在笔画先后 0~1，有时间信息=1)。解码用小束宽的束搜索，给出几个候选。
 */
object FormulaRecognizer {
    private const val TAG = "FormulaRecognizer"
    private const val DIR = "handwriting_models/formula"
    private const val BEAM = 3

    data class Meta(val segLen: Int, val segFeat: Int, val maxPoints: Int, val maxTokens: Int,
                    val minStep: Float, val wideRatio: Float, val bos: Int, val eos: Int)

    private var env: OrtEnvironment? = null
    private var encoder: OrtSession? = null
    private var decoder: OrtSession? = null
    private var vocab: List<String> = emptyList()
    private var meta: Meta? = null

    fun dir(context: Context): File = File(context.getExternalFilesDir(null) ?: context.filesDir, DIR)

    /** 公式模型包是否已放到设备上。 */
    fun isAvailable(context: Context): Boolean = dir(context).let { d ->
        listOf("formula_encoder.onnx", "formula_decoder.onnx", "formula_vocab.json", "formula_meta.json")
            .all { File(d, it).isFile }
    }

    @Synchronized
    fun ensureLoaded(context: Context): Boolean {
        if (encoder != null && decoder != null) return true
        if (!isAvailable(context)) return false
        return try {
            val d = dir(context)
            val m = JSONObject(File(d, "formula_meta.json").readText())
            meta = Meta(m.getInt("seg_len"), m.getInt("seg_feat"), m.getInt("max_points"), m.getInt("max_tokens"),
                m.getDouble("min_step").toFloat(), m.getDouble("wide_ratio").toFloat(), m.getInt("bos"), m.getInt("eos"))
            val v = JSONArray(File(d, "formula_vocab.json").readText())
            vocab = List(v.length()) { v.getString(it) }
            val e = OrtEnvironment.getEnvironment()
            val options = OrtSession.SessionOptions().apply { setIntraOpNumThreads(2) }
            encoder = e.createSession(File(d, "formula_encoder.onnx").path, options)
            decoder = e.createSession(File(d, "formula_decoder.onnx").path, options)
            env = e
            true
        } catch (t: Throwable) {
            Log.e(TAG, "load formula model failed", t)
            FileLogger.e(TAG, "load formula model failed", t)
            release()
            false
        }
    }

    @Synchronized
    fun release() {
        runCatching { encoder?.close() }
        runCatching { decoder?.close() }
        encoder = null
        decoder = null
    }

    /** 识别整段公式，返回 (LaTeX, 置信度 0~1) 按置信度降序；模型不可用或没有笔迹时为空。 */
    @Synchronized
    fun recognize(context: Context, strokes: List<List<Pair<Float, Float>>>): List<Pair<String, Float>> {
        if (!ensureLoaded(context)) return emptyList()
        val m = meta ?: return emptyList()
        val feats = featurize(strokes, m) ?: return emptyList()
        val segs = segments(feats, m)
        if (segs.isEmpty()) return emptyList()
        val e = env ?: return emptyList()
        val s = segs.size.toLong()
        val p = FloatArray(segs.size * m.segLen * 6)
        val pm = BooleanArray(segs.size * m.segLen)
        val c = FloatArray(segs.size * m.segFeat)
        segs.forEachIndexed { i, seg ->
            seg.points.forEachIndexed { j, row ->
                System.arraycopy(row, 0, p, (i * m.segLen + j) * 6, 6)
                pm[i * m.segLen + j] = true
            }
            System.arraycopy(seg.feat, 0, c, i * m.segFeat, m.segFeat)
        }
        val sm = BooleanArray(segs.size) { true }
        return try {
            OnnxTensor.createTensor(e, FloatBuffer.wrap(p), longArrayOf(1, s, m.segLen.toLong(), 6)).use { tp ->
                boolTensor(e, pm, longArrayOf(1, s, m.segLen.toLong())).use { tpm ->
                    OnnxTensor.createTensor(e, FloatBuffer.wrap(c), longArrayOf(1, s, m.segFeat.toLong())).use { tc ->
                        boolTensor(e, sm, longArrayOf(1, s)).use { tsm ->
                            encoder!!.run(mapOf("P" to tp, "PM" to tpm, "C" to tc, "SM" to tsm)).use { encOut ->
                                val mem = encOut[0] as OnnxTensor
                                beamSearch(e, mem, tsm, m)
                            }
                        }
                    }
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "formula recognize failed", t)
            FileLogger.e(TAG, "formula recognize failed", t)
            emptyList()
        }
    }

    /**
     * 只跑编码器，把一个字（或一段笔迹）变成 256 维单位向量：各段输出取平均再归一化。
     * 边用边学的模板比对用它（见 [HandwritingTemplates]）；离线测过假名 1-shot 首选约 78%。
     */
    @Synchronized
    fun embed(context: Context, strokes: List<List<Pair<Float, Float>>>): FloatArray? {
        if (!ensureLoaded(context)) return null
        val m = meta ?: return null
        val e = env ?: return null
        val feats = featurize(strokes, m) ?: return null
        val segs = segments(feats, m)
        if (segs.isEmpty()) return null
        val s = segs.size.toLong()
        val p = FloatArray(segs.size * m.segLen * 6)
        val pm = BooleanArray(segs.size * m.segLen)
        val c = FloatArray(segs.size * m.segFeat)
        segs.forEachIndexed { i, seg ->
            seg.points.forEachIndexed { j, row ->
                System.arraycopy(row, 0, p, (i * m.segLen + j) * 6, 6)
                pm[i * m.segLen + j] = true
            }
            System.arraycopy(seg.feat, 0, c, i * m.segFeat, m.segFeat)
        }
        return try {
            OnnxTensor.createTensor(e, FloatBuffer.wrap(p), longArrayOf(1, s, m.segLen.toLong(), 6)).use { tp ->
                boolTensor(e, pm, longArrayOf(1, s, m.segLen.toLong())).use { tpm ->
                    OnnxTensor.createTensor(e, FloatBuffer.wrap(c), longArrayOf(1, s, m.segFeat.toLong())).use { tc ->
                        boolTensor(e, BooleanArray(segs.size) { true }, longArrayOf(1, s)).use { tsm ->
                            encoder!!.run(mapOf("P" to tp, "PM" to tpm, "C" to tc, "SM" to tsm)).use { out ->
                                @Suppress("UNCHECKED_CAST")
                                val mem = (out[0].value as Array<Array<FloatArray>>)[0]
                                val v = FloatArray(mem[0].size)
                                for (row in mem) for (k in v.indices) v[k] += row[k]
                                val norm = kotlin.math.sqrt(v.fold(0f) { acc, x -> acc + x * x }).coerceAtLeast(1e-6f)
                                FloatArray(v.size) { v[it] / norm }
                            }
                        }
                    }
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "formula embed failed", t)
            null
        }
    }

    private fun boolTensor(e: OrtEnvironment, data: BooleanArray, shape: LongArray): OnnxTensor {
        // Java ORT 的布尔张量从多维 boolean 数组创建；这里形状最多 3 维且首维为 1
        return when (shape.size) {
            2 -> OnnxTensor.createTensor(e, arrayOf(data))
            3 -> {
                val rows = shape[1].toInt()
                val cols = shape[2].toInt()
                OnnxTensor.createTensor(e, arrayOf(Array(rows) { r -> data.copyOfRange(r * cols, (r + 1) * cols) }))
            }
            else -> error("unsupported bool shape")
        }
    }

    private fun beamSearch(e: OrtEnvironment, mem: OnnxTensor, sm: OnnxTensor, m: Meta): List<Pair<String, Float>> {
        data class Hyp(val tokens: List<Int>, val logp: Double, val done: Boolean)
        var beams = listOf(Hyp(listOf(m.bos), 0.0, false))
        repeat(m.maxTokens) {
            if (beams.all { it.done }) return@repeat
            val next = mutableListOf<Hyp>()
            for (h in beams) {
                if (h.done) { next += h; continue }
                val y = LongArray(h.tokens.size) { h.tokens[it].toLong() }
                val logits = OnnxTensor.createTensor(e, LongBuffer.wrap(y), longArrayOf(1, y.size.toLong())).use { ty ->
                    decoder!!.run(mapOf("mem" to mem, "SM" to sm, "y" to ty)).use { out ->
                        @Suppress("UNCHECKED_CAST")
                        (out[0].value as Array<FloatArray>)[0]
                    }
                }
                val lse = logSumExp(logits)
                logits.indices.sortedByDescending { logits[it] }.take(BEAM).forEach { t ->
                    next += Hyp(h.tokens + t, h.logp + (logits[t] - lse), t == m.eos)
                }
            }
            // 按平均对数概率排序，避免短候选天然占优
            beams = next.sortedByDescending { it.logp / it.tokens.size }.take(BEAM)
        }
        return beams.map { h ->
            val ids = h.tokens.drop(1).filter { it != m.eos }
            detokenize(ids) to exp(h.logp / max(h.tokens.size - 1, 1)).toFloat()
        }.filter { it.first.isNotBlank() }.distinctBy { it.first }
    }

    private fun logSumExp(v: FloatArray): Double {
        val mx = v.maxOrNull() ?: 0f
        var s = 0.0
        for (x in v) s += exp((x - mx).toDouble())
        return mx + kotlin.math.ln(s)
    }

    /** 记号拼回 LaTeX：命令后面紧跟字母时补一个空格（\alpha x），其它直接相连。 */
    internal fun detokenize(ids: List<Int>, table: List<String> = vocab): String {
        val sb = StringBuilder()
        var prevCommand = false
        for (id in ids) {
            val t = table.getOrNull(id) ?: continue
            if (t.startsWith("<")) continue
            if (prevCommand && t.first().isLetter()) sb.append(' ')
            sb.append(t)
            prevCommand = t.length > 1 && t[0] == '\\' && t.drop(1).all { it.isLetter() }
        }
        return sb.toString()
    }

    // ── 预处理（与训练脚本逐项一致）──────────────────────────────────

    /** 每点 [x, y, dx, dy, 笔画结束, 笔画开始]；按笔画分组返回。 */
    internal fun featurize(strokes: List<List<Pair<Float, Float>>>, m: Meta): List<List<FloatArray>>? {
        val all = strokes.flatten()
        if (all.size < 2) return null
        val minX = all.minOf { it.first }
        val minY = all.minOf { it.second }
        val h = all.maxOf { it.second } - minY
        val w = all.maxOf { it.first } - minX
        val scale = maxOf(h, w / m.wideRatio, 1e-3f)
        val out = mutableListOf<MutableList<FloatArray>>()
        var total = 0
        for (st in strokes) {
            if (st.isEmpty()) continue
            val sx = FloatArray(st.size) { (st[it].first - minX) / scale }
            val sy = FloatArray(st.size) { (st[it].second - minY) / scale }
            var keep = st.indices.toList()
            if (st.size > 2) {
                var cum = 0.0
                var lastBucket = -1.0
                val k = mutableListOf<Int>()
                for (i in st.indices) {
                    if (i > 0) cum += (abs(sx[i] - sx[i - 1]) + abs(sy[i] - sy[i - 1])).toDouble()
                    val bucket = floor(cum / m.minStep)
                    if (bucket > lastBucket) { k += i; lastBucket = bucket }
                }
                if (k.last() != st.size - 1) k += st.size - 1
                keep = k
            }
            val rows = keep.mapIndexed { j, i ->
                floatArrayOf(sx[i], sy[i], 0f, 0f, if (j == keep.size - 1) 1f else 0f, if (j == 0) 1f else 0f)
            }.toMutableList()
            out += rows
            total += rows.size
        }
        if (total > m.maxPoints) return null
        // dx、dy 按所有点拼接后的顺序算（第一个点为 0）
        var prev: FloatArray? = null
        for (stroke in out) for (row in stroke) {
            prev?.let { row[2] = row[0] - it[0]; row[3] = row[1] - it[1] }
            prev = row
        }
        return out
    }

    internal class Seg(val points: List<FloatArray>, val feat: FloatArray)

    internal fun segments(strokes: List<List<FloatArray>>, m: Meta): List<Seg> {
        val out = mutableListOf<Seg>()
        strokes.forEachIndexed { si, st ->
            val n = max(1, ceil(st.size / m.segLen.toDouble()).toInt())
            val bounds = IntArray(n + 1) { Math.rint(it * st.size.toDouble() / n).toInt() }
            for (k in 0 until n) {
                val p = st.subList(bounds[k], bounds[k + 1])
                if (p.isEmpty()) continue
                val mnx = p.minOf { it[0] }; val mxx = p.maxOf { it[0] }
                val mny = p.minOf { it[1] }; val mxy = p.maxOf { it[1] }
                val cx = (mnx + mxx) / 2; val cy = (mny + mxy) / 2
                val q = p.map { it.copyOf().also { r -> r[0] -= cx; r[1] -= cy } }
                if (k == 0) { q[0][2] = 0f; q[0][3] = 0f }
                val f = FloatArray(m.segFeat)
                val base = floatArrayOf(cx, cy, mxx - mnx, mxy - mny, if (k == 0) 1f else 0f, if (k == n - 1) 1f else 0f)
                System.arraycopy(base, 0, f, 0, minOf(6, m.segFeat))
                if (m.segFeat >= 8) {
                    f[6] = si / max(strokes.size - 1, 1).toFloat()
                    f[7] = 1f
                }
                out += Seg(q, f)
            }
        }
        return out
    }
}
