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
import kotlin.math.exp
import kotlin.math.max

/**
 * 整段笔迹 → 记号序列的模型包（编码器 + 解码器 + 记号表 + 预处理参数，文件名沿用 formula_*）。
 * 公式（[FormulaRecognizer]）和英文整词（[EnglishWordRecognizer]）是同一种结构的两份模型，各用一个实例。
 * 预处理见 [FormulaRecognizer.featurize] / [FormulaRecognizer.segments]，与训练脚本逐项一致。
 */
internal class InkSeq2SeqModel(private val modelId: String, private val externalDir: String, private val name: String) {
    private var env: OrtEnvironment? = null
    private var encoder: OrtSession? = null
    private var decoder: OrtSession? = null
    private var vocab: List<String> = emptyList()
    private var meta: FormulaRecognizer.Meta? = null

    private fun complete(d: File) = FILES.all { File(d, it).isFile }

    /** 优先用模型下载页下载的版本；没有时用 adb 推到外部私有目录 [externalDir] 的个人模型包。 */
    fun dir(context: Context): File = com.kingzcheung.xime.model.ModelStorage.getModelDir(context, modelId).takeIf(::complete)
        ?: File(context.getExternalFilesDir(null) ?: context.filesDir, externalDir)

    fun isAvailable(context: Context): Boolean = complete(dir(context))

    @Synchronized
    fun ensureLoaded(context: Context): Boolean {
        if (encoder != null && decoder != null) return true
        if (!isAvailable(context)) return false
        return try {
            val d = dir(context)
            val m = JSONObject(File(d, "formula_meta.json").readText())
            meta = FormulaRecognizer.Meta(m.getInt("seg_len"), m.getInt("seg_feat"), m.getInt("max_points"), m.getInt("max_tokens"),
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
            Log.e(TAG, "load $name model failed", t)
            FileLogger.e(TAG, "load $name model failed", t)
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

    /**
     * 识别整段笔迹，返回 (文本, 置信度 0~1) 按置信度降序；模型不可用或没有笔迹时为空。
     * [allowed] 不为空时解码只在这些记号（和结束符）里选，例如英文只出字母。
     */
    @Synchronized
    fun recognize(context: Context, strokes: List<List<Pair<Float, Float>>>, beam: Int,
                  allowed: ((String) -> Boolean)? = null): List<Pair<String, Float>> {
        if (!ensureLoaded(context)) return emptyList()
        val m = meta ?: return emptyList()
        val e = env ?: return emptyList()
        val feats = FormulaRecognizer.featurize(strokes, m) ?: return emptyList()
        return try {
            encode(e, feats, m) { mem, sm ->
                val mask = allowed?.let { ok -> BooleanArray(vocab.size) { it == m.eos || ok(vocab[it]) } }
                beamSearch(e, mem, sm, m, beam, mask)
            } ?: emptyList()
        } catch (t: Throwable) {
            Log.e(TAG, "$name recognize failed", t)
            FileLogger.e(TAG, "$name recognize failed", t)
            emptyList()
        }
    }

    /** 只跑编码器：各段输出取平均再归一化成单位向量（字母模板比对用）。 */
    @Synchronized
    fun embed(context: Context, strokes: List<List<Pair<Float, Float>>>): FloatArray? {
        if (!ensureLoaded(context)) return null
        val m = meta ?: return null
        val e = env ?: return null
        // 字母模板比对：点很稀的笔画（快速一划、只有几个采样点）先沿笔迹补点，否则特征几乎为空，什么都认成 c
        val feats = FormulaRecognizer.featurize(FormulaRecognizer.densify(strokes, m), m) ?: return null
        return try {
            encode(e, feats, m) { mem, _ ->
                @Suppress("UNCHECKED_CAST")
                val rows = (mem.value as Array<Array<FloatArray>>)[0]
                val v = FloatArray(rows[0].size)
                for (row in rows) for (k in v.indices) v[k] += row[k]
                val norm = kotlin.math.sqrt(v.fold(0f) { acc, x -> acc + x * x }).coerceAtLeast(1e-6f)
                FloatArray(v.size) { v[it] / norm }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "$name embed failed", t)
            null
        }
    }

    private fun <T> encode(e: OrtEnvironment, feats: List<List<FloatArray>>, m: FormulaRecognizer.Meta,
                           use: (mem: OnnxTensor, sm: OnnxTensor) -> T): T? {
        val segs = FormulaRecognizer.segments(feats, m)
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
        return OnnxTensor.createTensor(e, FloatBuffer.wrap(p), longArrayOf(1, s, m.segLen.toLong(), 6)).use { tp ->
            boolTensor(e, pm, longArrayOf(1, s, m.segLen.toLong())).use { tpm ->
                OnnxTensor.createTensor(e, FloatBuffer.wrap(c), longArrayOf(1, s, m.segFeat.toLong())).use { tc ->
                    boolTensor(e, BooleanArray(segs.size) { true }, longArrayOf(1, s)).use { tsm ->
                        encoder!!.run(mapOf("P" to tp, "PM" to tpm, "C" to tc, "SM" to tsm)).use { out ->
                            use(out[0] as OnnxTensor, tsm)
                        }
                    }
                }
            }
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

    private fun beamSearch(e: OrtEnvironment, mem: OnnxTensor, sm: OnnxTensor, m: FormulaRecognizer.Meta, beam: Int,
                           mask: BooleanArray?): List<Pair<String, Float>> {
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
                // 不许出的记号不参与（概率在允许的记号里重新归一）
                if (mask != null) for (t in logits.indices) if (!mask[t]) logits[t] = Float.NEGATIVE_INFINITY
                val lse = logSumExp(logits)
                logits.indices.filter { logits[it] > Float.NEGATIVE_INFINITY }.sortedByDescending { logits[it] }.take(beam).forEach { t ->
                    next += Hyp(h.tokens + t, h.logp + (logits[t] - lse), t == m.eos)
                }
            }
            // 按平均对数概率排序，避免短候选天然占优
            beams = next.sortedByDescending { it.logp / it.tokens.size }.take(beam)
        }
        return beams.map { h ->
            val ids = h.tokens.drop(1).filter { it != m.eos }
            FormulaRecognizer.detokenize(ids, vocab) to exp(h.logp / max(h.tokens.size - 1, 1)).toFloat()
        }.filter { it.first.isNotBlank() }.distinctBy { it.first }
    }

    private fun logSumExp(v: FloatArray): Double {
        val mx = v.maxOrNull() ?: 0f
        var s = 0.0
        for (x in v) if (x > Float.NEGATIVE_INFINITY) s += exp((x - mx).toDouble())
        return mx + kotlin.math.ln(s)
    }

    companion object {
        private const val TAG = "InkSeq2SeqModel"
        private val FILES = listOf("formula_encoder.onnx", "formula_decoder.onnx", "formula_vocab.json", "formula_meta.json")
    }
}
