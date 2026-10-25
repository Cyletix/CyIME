package com.kingzcheung.xime.handwriting

import android.content.Context
import java.io.File
import kotlin.math.abs
import kotlin.math.ceil
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
    /** 点数超上限时每次把留点间距放宽到这个倍数，最多放宽到训练时的 [MAX_STEP_SCALE] 倍。 */
    private const val STEP_GROWTH = 1.15f
    private const val MAX_STEP_SCALE = 4f
    private const val DIR = "handwriting_models/formula"
    private const val BEAM = 3

    data class Meta(val segLen: Int, val segFeat: Int, val maxPoints: Int, val maxTokens: Int,
                    val minStep: Float, val wideRatio: Float, val bos: Int, val eos: Int)

    /** 模型下载页里的公式手写模型 id（BuiltinModelCatalog），下载到 filesDir/models/<id>/。 */
    const val MODEL_ID = "handwriting-formula"

    private val model = InkSeq2SeqModel(MODEL_ID, DIR, "formula")

    /** 优先用模型下载页下载的版本；没有时用 adb 推到外部私有目录 `handwriting_models/formula/` 的个人模型包。 */
    fun dir(context: Context): File = model.dir(context)

    /** 公式模型包是否已放到设备上。 */
    fun isAvailable(context: Context): Boolean = model.isAvailable(context)

    fun ensureLoaded(context: Context): Boolean = model.ensureLoaded(context)

    fun release() = model.release()

    /** 识别整段公式，返回 (LaTeX, 置信度 0~1) 按置信度降序；模型不可用或没有笔迹时为空。 */
    fun recognize(context: Context, strokes: List<List<Pair<Float, Float>>>): List<Pair<String, Float>> =
        model.recognize(context, strokes, BEAM)

    /**
     * 只跑编码器，把一个字（或一段笔迹）变成 256 维单位向量：各段输出取平均再归一化。
     * 边用边学的模板比对用它（见 [HandwritingTemplates]）；离线测过假名 1-shot 首选约 78%。
     */
    fun embed(context: Context, strokes: List<List<Pair<Float, Float>>>): FloatArray? = model.embed(context, strokes)

    /** 记号拼回 LaTeX：命令后面紧跟字母时补一个空格（\alpha x），其它直接相连。 */
    internal fun detokenize(ids: List<Int>, table: List<String>): String {
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
    /**
     * 相邻两点隔得比留点间距（[Meta.minStep]，按与 [featurize] 相同的缩放）的 4 倍还远时，在中间按该间距补点。
     * 正常书写的点本来就密，碰不到这个门槛，结果不变；只影响点很稀的笔画。
     */
    internal fun densify(strokes: List<List<Pair<Float, Float>>>, m: Meta): List<List<Pair<Float, Float>>> {
        val all = strokes.flatten()
        if (all.size < 2) return strokes
        val h = all.maxOf { it.second } - all.minOf { it.second }
        val w = all.maxOf { it.first } - all.minOf { it.first }
        val step = maxOf(h, w / m.wideRatio, 1e-3f) * m.minStep
        return strokes.map { st ->
            if (st.size < 2) return@map st
            val out = ArrayList<Pair<Float, Float>>(st.size)
            out += st[0]
            for (i in 1 until st.size) {
                val (x0, y0) = st[i - 1]
                val (x1, y1) = st[i]
                val gap = abs(x1 - x0) + abs(y1 - y0)
                val n = if (gap > 4 * step) kotlin.math.ceil(gap / step).toInt() else 1
                if (n > 1) for (j in 1 until n) out += (x0 + (x1 - x0) * j / n) to (y0 + (y1 - y0) * j / n)
                out += st[i]
            }
            out
        }
    }

    internal fun featurize(strokes: List<List<Pair<Float, Float>>>, m: Meta): List<List<FloatArray>>? {
        // 大公式按训练时的点距会超过模型的点数上限，以前直接不识别（停在上一次的结果上）：
        // 逐步放宽点距（最多 4 倍）直到放得下。演示的 74 笔矩阵公式放宽到约 1.3 倍后能整段认出。
        var step = m.minStep
        while (step <= m.minStep * MAX_STEP_SCALE) {
            featurize(strokes, m, step)?.let { return it }
            step *= STEP_GROWTH
        }
        return null
    }

    private fun featurize(strokes: List<List<Pair<Float, Float>>>, m: Meta, minStep: Float): List<List<FloatArray>>? {
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
                    val bucket = floor(cum / minStep)
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
