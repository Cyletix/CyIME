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
 *
 * 假名另有内置默认模板（assets/handwriting/kana_default_strokes.json，KanjiVG 笔画加变体，
 * 由 tools/handwriting_training/kana_kanjivg.py 生成）：没录过样本也能直接写；自己的模板略加分，永远优先。
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

    /** 内置默认模板（只读，不参与 [MAX_PER_LABEL] 淘汰，不写进个人模板文件）。 */
    private val defaults = HashMap<Script, List<Entry>>()
    private const val DEFAULT_ASSET = "handwriting/kana_default_strokes.json"
    private const val DEFAULT_CACHE = "kana_default_v1.json"
    /** 电脑上用发布的公式模型预先算好的特征（float16 小端）及其模型哈希、标签顺序。 */
    private const val DEFAULT_VECTORS = "handwriting/kana_default_vectors.bin"
    private const val DEFAULT_VECTORS_META = "handwriting/kana_default_vectors.json"
    /** 自己写的模板比内置模板多算的相似度，同样像时排在前面。 */
    private const val PERSONAL_BONUS = 0.03f

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

    private fun sha256(file: File): String {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(1 shl 16)
            while (true) { val n = input.read(buf); if (n < 0) break; md.update(buf, 0, n) }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * 解开预算特征：[meta] 里的 model_sha256 与当前模型的 [modelSha] 不一致（换了模型）或文件长度对不上时返回 null，
     * 由调用方回退为在设备上现算。
     */
    internal fun decodePrecomputed(meta: String, bin: ByteArray, modelSha: String): List<Pair<String, FloatArray>>? {
        val o = JSONObject(meta)
        if (!o.optString("model_sha256").equals(modelSha, ignoreCase = true)) return null
        val dim = o.getInt("dim")
        val labels = o.getJSONArray("labels")
        if (dim <= 0 || bin.size != labels.length() * dim * 2) return null
        val buf = java.nio.ByteBuffer.wrap(bin).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        return (0 until labels.length()).map { i ->
            labels.getString(i) to FloatArray(dim) { halfToFloat(buf.short.toInt()) }
        }
    }

    private fun halfToFloat(h: Int): Float {
        val sign = (h ushr 15) and 1
        val exp = (h ushr 10) and 0x1f
        val mant = h and 0x3ff
        val bits = when {
            exp == 0 && mant == 0 -> sign shl 31
            exp == 0 -> { // 非规格化数
                var e = -14; var m = mant
                while (m and 0x400 == 0) { m = m shl 1; e-- }
                (sign shl 31) or ((e + 127) shl 23) or ((m and 0x3ff) shl 13)
            }
            exp == 31 -> (sign shl 31) or (0xff shl 23) or (mant shl 13)
            else -> (sign shl 31) or ((exp - 15 + 127) shl 23) or (mant shl 13)
        }
        return java.lang.Float.intBitsToFloat(bits)
    }

    fun count(context: Context, script: Script): Int = entries(context, script).size + (synchronized(this) { defaults[script]?.size } ?: 0)

    /**
     * 准备内置假名模板：第一次用公式模型把内置笔画算成特征并缓存（同一个模型文件只算一次），之后直接读缓存。
     * 在后台线程调用；没有公式模型或不是假名时什么也不做。
     */
    fun ensureDefaults(context: Context, script: Script): Unit = synchronized(defaultLock) { prepareDefaults(context, script) }

    private val defaultLock = Any()

    private fun prepareDefaults(context: Context, script: Script) {
        if (script != Script.KANA || synchronized(this) { defaults.containsKey(script) }) return
        if (!FormulaRecognizer.isAvailable(context)) return
        val stamp = File(FormulaRecognizer.dir(context), "formula_encoder.onnx").let { "${it.length()}:${it.lastModified()}" }
        val cacheFile = File(dir(context), DEFAULT_CACHE)
        val cached = runCatching {
            val o = JSONObject(cacheFile.readText())
            if (o.getString("model") != stamp) null else {
                val arr = o.getJSONArray("t")
                (0 until arr.length()).map { i ->
                    val e = arr.getJSONObject(i); val v = e.getJSONArray("v")
                    Entry(e.getString("l"), FloatArray(v.length()) { v.getDouble(it).toFloat() }, "builtin")
                }
            }
        }.getOrNull()
        val list = cached ?: runCatching {
            // 模型文件与预算时用的是同一个（哈希一致）就直接用预算特征，第一次打开也不用等。
            val sha = sha256(File(FormulaRecognizer.dir(context), "formula_encoder.onnx"))
            decodePrecomputed(context.assets.open(DEFAULT_VECTORS_META).bufferedReader(Charsets.UTF_8).use { it.readText() },
                context.assets.open(DEFAULT_VECTORS).use { it.readBytes() }, sha)
                ?.map { (label, vec) -> Entry(label, vec, "builtin") }
        }.onFailure { Log.w(TAG, "precomputed kana templates unreadable", it) }.getOrNull() ?: runCatching {
            val chars = JSONObject(context.assets.open(DEFAULT_ASSET).bufferedReader(Charsets.UTF_8).use { it.readText() })
                .getJSONObject("chars")
            val out = mutableListOf<Entry>()
            for (label in chars.keys()) {
                val variants = chars.getJSONArray(label)
                for (i in 0 until variants.length()) {
                    val strokes = variants.getJSONArray(i).let { st ->
                        (0 until st.length()).map { j ->
                            val pts = st.getJSONArray(j)
                            (0 until pts.length()).map { k -> pts.getJSONArray(k).let { p -> p.getDouble(0).toFloat() to p.getDouble(1).toFloat() } }
                        }
                    }
                    FormulaRecognizer.embed(context, strokes)?.let { out += Entry(label, it, "builtin") }
                }
            }
            val arr = JSONArray()
            for (e in out) arr.put(JSONObject().put("l", e.label).put("v", JSONArray().also { v -> e.vec.forEach { v.put(Math.round(it * 10000) / 10000.0) } }))
            val tmp = File(cacheFile.path + ".tmp")
            tmp.writeText(JSONObject().put("model", stamp).put("t", arr).toString())
            if (!tmp.renameTo(cacheFile)) { tmp.copyTo(cacheFile, overwrite = true); tmp.delete() }
            out
        }.onFailure { Log.w(TAG, "builtin kana templates failed", it) }.getOrNull() ?: return
        synchronized(this) { defaults[script] = list }
    }

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
        val builtin = synchronized(this) { defaults[script].orEmpty() }
        if (list.isEmpty() && builtin.isEmpty()) return emptyList()
        val vec = FormulaRecognizer.embed(context, strokes) ?: return emptyList()
        lastEmbeddings = listOf(vec)
        lastScript = script
        val best = HashMap<String, Float>()
        for (e in list) {
            var dot = PERSONAL_BONUS
            for (i in vec.indices) dot += vec[i] * e.vec[i]
            if (dot > (best[e.label] ?: -2f)) best[e.label] = dot
        }
        for (e in builtin) {
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
