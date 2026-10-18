package com.kingzcheung.xime.handwriting

import android.content.Context
import android.util.Log
import com.kingzcheung.xime.settings.InputLanguage
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 边用边学的手写模板库（假名、英文字母）。不训练模型：用公式模型的编码器把一个字的笔迹变成特征，
 * 和你写过的模板比相似度，最像的几个当候选；你选了哪个，这次的笔迹就存成那个字的新模板。
 *
 * 模板来源：训练模式里写的假名 / 英文字母样本（首次使用时在设备上算特征），以后每次选候选、
 * 每次在训练页保存都会追加。每个字最多保留 [MAX_PER_LABEL] 个，超出时丢最旧的。
 * 模板只存在 App 私有目录 `handwriting_templates/`，不上传。需要公式模型包（见 [FormulaRecognizer]）。
 */
object HandwritingTemplates {
    private const val TAG = "HandwritingTemplates"
    private const val DIR = "handwriting_templates"
    const val MAX_PER_LABEL = 12

    /** 一种文字的模板集合。 */
    enum class Script(val fileName: String, val sampleTypes: Set<String>) {
        KANA("kana.json", setOf("kana")),
        LATIN("latin.json", setOf("latin"));

        /** 能当这一类模板的字（防止把汉字、标点学进假名库）。 */
        fun accepts(label: String): Boolean {
            if (label.codePointCount(0, label.length) != 1) return false
            val cp = label.codePointAt(0)
            return when (this) {
                KANA -> Character.UnicodeBlock.of(cp) in setOf(Character.UnicodeBlock.HIRAGANA, Character.UnicodeBlock.KATAKANA)
                LATIN -> (cp in 'a'.code..'z'.code) || (cp in 'A'.code..'Z'.code) || (cp in '0'.code..'9'.code)
            }
        }

        companion object {
            fun forLanguage(language: InputLanguage): Script? = when (language) {
                InputLanguage.JAPANESE -> KANA
                InputLanguage.ENGLISH -> LATIN
                else -> null
            }
        }
    }

    private class Entry(val label: String, val vec: FloatArray, val source: String)

    private val cache = HashMap<Script, MutableList<Entry>>()

    /** 最近一次识别时每个字的特征，选候选后用来学习（按字的顺序）。 */
    @Volatile
    private var lastEmbeddings: List<FloatArray> = emptyList()
    @Volatile
    private var lastScript: Script? = null

    private fun dir(context: Context) = File(context.filesDir, DIR).apply { mkdirs() }

    @Synchronized
    private fun entries(context: Context, script: Script): MutableList<Entry> = cache.getOrPut(script) {
        val file = File(dir(context), script.fileName)
        val list = mutableListOf<Entry>()
        if (file.isFile) runCatching {
            val arr = JSONArray(file.readText())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val v = o.getJSONArray("v")
                list += Entry(o.getString("l"), FloatArray(v.length()) { v.getDouble(it).toFloat() }, o.optString("s"))
            }
        }.onFailure { Log.w(TAG, "read ${file.name} failed", it) }
        list
    }

    @Synchronized
    private fun save(context: Context, script: Script) {
        val list = cache[script] ?: return
        val arr = JSONArray()
        for (e in list) {
            val v = JSONArray()
            e.vec.forEach { v.put(Math.round(it * 10000) / 10000.0) }
            arr.put(JSONObject().put("l", e.label).put("v", v).put("s", e.source))
        }
        val file = File(dir(context), script.fileName)
        val tmp = File(file.path + ".tmp")
        tmp.writeText(arr.toString())
        if (!tmp.renameTo(file)) { tmp.copyTo(file, overwrite = true); tmp.delete() }
    }

    fun count(context: Context, script: Script): Int = entries(context, script).size

    /**
     * 把训练模式里还没算过特征的样本补进模板库（第一次打开日语 / 英文手写时在后台跑）。
     * 训练样本文件见 TrainingSampleStore（files/handwriting_training/samples.jsonl）。
     */
    fun importTrainingSamples(context: Context, script: Script): Int {
        // 内部样本之外，也读外部私有目录里的 samples.jsonl：清除数据后可用 adb 把电脑上的导出备份推回来。
        val files = listOf(File(File(context.filesDir, "handwriting_training"), "samples.jsonl"),
            File(com.kingzcheung.xime.handwriting.training.PromptBankFile.dir(context), "samples.jsonl")).filter { it.isFile }
        if (files.isEmpty() || !FormulaRecognizer.isAvailable(context)) return 0
        val known = synchronized(this) { entries(context, script).map { it.source }.toHashSet() }
        var added = 0
        for (file in files) file.forEachLine(Charsets.UTF_8) { line ->
            if (line.isBlank()) return@forEachLine
            val o = runCatching { JSONObject(line) }.getOrNull() ?: return@forEachLine
            if (o.optString("type") !in script.sampleTypes) return@forEachLine
            val id = "train:" + o.optString("id")
            val label = o.optString("label")
            if (id in known || !script.accepts(label)) return@forEachLine
            val strokes = parseStrokes(o.optJSONArray("strokes")) ?: return@forEachLine
            val vec = FormulaRecognizer.embed(context, strokes) ?: return@forEachLine
            add(context, script, label, vec, id, persist = false)
            known += id
            added++
        }
        if (added > 0) save(context, script)
        return added
    }

    private fun parseStrokes(arr: JSONArray?): List<List<Pair<Float, Float>>>? {
        arr ?: return null
        val out = mutableListOf<List<Pair<Float, Float>>>()
        for (i in 0 until arr.length()) {
            val pts = arr.getJSONObject(i).optJSONArray("points") ?: continue
            val stroke = (0 until pts.length()).map { j ->
                val p = pts.getJSONArray(j)
                p.getDouble(0).toFloat() to p.getDouble(1).toFloat()
            }
            if (stroke.isNotEmpty()) out += stroke
        }
        return out.ifEmpty { null }
    }

    @Synchronized
    private fun add(context: Context, script: Script, label: String, vec: FloatArray, source: String, persist: Boolean = true) {
        val list = entries(context, script)
        list += Entry(label, vec, source)
        val same = list.filter { it.label == label }
        if (same.size > MAX_PER_LABEL) list.remove(same.first())
        if (persist) save(context, script)
    }

    /** 训练页保存了一个假名 / 字母样本：顺便加成模板。 */
    fun addTrainingSample(context: Context, type: String, label: String, sampleId: String, strokes: List<List<Pair<Float, Float>>>) {
        val script = Script.entries.firstOrNull { type in it.sampleTypes } ?: return
        if (!script.accepts(label)) return
        val vec = FormulaRecognizer.embed(context, strokes) ?: return
        add(context, script, label, vec, "train:$sampleId")
    }

    /**
     * 识别一个字：和模板逐个比，每个字取最像的那个模板的相似度，按相似度排序。
     * 返回 (字, 0~1 的分数)；模板库为空或特征算不出来时为空。
     */
    fun recognize(context: Context, script: Script, strokes: List<List<Pair<Float, Float>>>, topK: Int = 8): List<Pair<String, Float>> {
        val list = synchronized(this) { entries(context, script).toList() }
        if (list.isEmpty()) return emptyList()
        val vec = FormulaRecognizer.embed(context, strokes) ?: return emptyList()
        lastEmbeddings = listOf(vec)
        lastScript = script
        val best = HashMap<String, Float>()
        for (e in list) {
            var dot = 0f
            for (i in vec.indices) dot += vec[i] * e.vec[i]
            if (dot > (best[e.label] ?: -2f)) best[e.label] = dot
        }
        return best.entries.sortedByDescending { it.value }.take(topK)
            .map { it.key to ((it.value + 1f) / 2f).coerceIn(0.0001f, 1f) }
    }

    /**
     * 选了候选：把这次识别的笔迹存成所选字的模板。[committed] 是上屏的文字，
     * 只有它正好是一个能学的字时才学（多段拼起来的词组暂不学，避免对不上号）。
     */
    fun learnSelection(context: Context, committed: String) {
        val script = lastScript ?: return
        val vec = lastEmbeddings.singleOrNull() ?: return
        lastEmbeddings = emptyList()
        if (!script.accepts(committed)) return
        val app = context.applicationContext
        // 写文件放后台，不卡上屏
        Thread { add(app, script, committed, vec, "pick:${System.currentTimeMillis()}") }.start()
    }

    /** 有公式模型包、模板库里至少有一个字时，就开放这种文字的手写。 */
    fun isUsable(context: Context, script: Script): Boolean =
        FormulaRecognizer.isAvailable(context) && count(context, script) > 0

    internal fun labelsWithCount(context: Context, script: Script): Map<String, Int> =
        synchronized(this) { entries(context, script).groupingBy { it.label }.eachCount() }
}
