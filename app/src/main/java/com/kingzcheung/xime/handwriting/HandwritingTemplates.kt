package com.kingzcheung.xime.handwriting

import android.content.Context
import android.util.Log
import com.kingzcheung.xime.settings.InputLanguage
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 边用边学的手写模板库（假名、英文字母）。不训练模型：把一个字的笔迹变成特征，和模板比相似度，
 * 最像的几个当候选；你选了哪个，这次的笔迹就存成那个字的新模板。
 *
 * 特征：假名用内置汉字手写模型（ochwpro）的输出——概率最高的若干类开方后归一化（稀疏向量），
 * 不需要公式模型；英文字母模板用公式模型编码器（需要公式模型包，没有时英文改走汉字模型里的字母）。
 *
 * 模板来源：训练模式里写的假名 / 英文字母样本（首次使用时在设备上算特征），以后每次选候选、
 * 每次在训练页保存都会追加。每个字最多保留 [MAX_PER_LABEL] 个，超出时丢最旧的。
 * 模板只存在 App 私有目录 `handwriting_templates/`，不上传。
 *
 * 假名另有内置默认模板（assets/handwriting/kana_default_*，KanjiVG 笔画加变体，
 * 由 tools/handwriting_training/kana_kanjivg.py 生成）：没录过样本也能直接写；自己的模板略加分，永远优先。
 */
object HandwritingTemplates {
    private const val TAG = "HandwritingTemplates"
    private const val DIR = "handwriting_templates"
    const val MAX_PER_LABEL = 12
    /** 假名特征取汉字模型概率最高的类数（与 kana_kanjivg.py --och-vectors 一致）。 */
    const val OCH_TOP_N = 320

    /** 一种文字的模板集合。假名换成汉字模型特征后用新文件，旧的公式特征文件不再读。 */
    enum class Script(val fileName: String, val sampleTypes: Set<String>) {
        KANA("kana_och.json", setOf("kana")),
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

        /** 这类模板现在能不能用：假名只要内置汉字手写模型；字母模板要公式模型。 */
        fun available(context: Context): Boolean = when (this) {
            KANA -> HandwritingEngine.hasModel(context, InputLanguage.CHINESE)
            LATIN -> FormulaRecognizer.isAvailable(context)
        }

        companion object {
            fun forLanguage(language: InputLanguage): Script? = when (language) {
                InputLanguage.JAPANESE -> KANA
                InputLanguage.ENGLISH -> LATIN
                else -> null
            }
        }
    }

    /** 已归一化的特征：稠密（公式模型）或按类序号升序的稀疏（汉字模型）；相似度为点积。 */
    internal class Feature(val dense: FloatArray? = null, val idx: IntArray? = null, val vals: FloatArray? = null) {
        fun dot(o: Feature): Float {
            if (dense != null && o.dense != null) {
                var d = 0f
                for (i in 0 until minOf(dense.size, o.dense.size)) d += dense[i] * o.dense[i]
                return d
            }
            val a = idx ?: return 0f
            val av = vals ?: return 0f
            val b = o.idx ?: return 0f
            val bv = o.vals ?: return 0f
            var i = 0
            var j = 0
            var d = 0f
            while (i < a.size && j < b.size) when {
                a[i] == b[j] -> { d += av[i] * bv[j]; i++; j++ }
                a[i] < b[j] -> i++
                else -> j++
            }
            return d
        }

        companion object {
            /** 汉字模型输出的 (类序号, 概率) → 开方、归一化、按序号排序的稀疏特征。 */
            fun fromProbabilities(pairs: List<Pair<Int, Float>>): Feature? {
                val kept = pairs.filter { it.second > 0f && it.second.isFinite() }.distinctBy { it.first }.sortedBy { it.first }
                if (kept.isEmpty()) return null
                val v = FloatArray(kept.size) { kotlin.math.sqrt(kept[it].second) }
                val norm = kotlin.math.sqrt(v.fold(0f) { acc, x -> acc + x * x }).coerceAtLeast(1e-9f)
                return Feature(idx = IntArray(kept.size) { kept[it].first }, vals = FloatArray(v.size) { v[it] / norm })
            }
        }
    }

    private class Entry(val label: String, val feat: Feature, val source: String)

    private val cache = HashMap<Script, MutableList<Entry>>()

    /** 内置默认模板（只读，不参与 [MAX_PER_LABEL] 淘汰，不写进个人模板文件）。 */
    private val defaults = HashMap<Script, List<Entry>>()
    /**
     * 内置模板组：假名，以及内置汉字模型认不出的日本字（込、働…，见 [JapaneseKanji]）。两组分开存放，
     * 每组 = 笔画（现算用）+ 预算特征 + 特征的模型哈希与标签。
     */
    private class BuiltinSet(val strokes: String, val vectors: String, val meta: String)
    private val BUILTIN_SETS = listOf(
        BuiltinSet("handwriting/kana_default_strokes.json", "handwriting/kana_default_och.bin", "handwriting/kana_default_och.json"),
        BuiltinSet("handwriting/japanese_kanji_strokes.json", "handwriting/japanese_kanji_och.bin", "handwriting/japanese_kanji_och.json"),
    )
    private const val DEFAULT_CACHE = "kana_default_och_v2.json"
    /** 自己写的模板比内置模板多算的相似度，同样像时排在前面。 */
    private const val PERSONAL_BONUS = 0.03f

    /** 最近一次识别时的特征，选候选后用来学习。 */
    @Volatile
    private var lastFeature: Feature? = null
    @Volatile
    private var lastScript: Script? = null

    private fun dir(context: Context) = File(context.filesDir, DIR).apply { mkdirs() }

    private fun Feature.toJson(o: JSONObject): JSONObject {
        dense?.let { d -> o.put("v", JSONArray().also { a -> d.forEach { a.put(Math.round(it * 10000) / 10000.0) } }) }
        val i = idx
        val v = vals
        if (i != null && v != null) {
            o.put("i", JSONArray().also { a -> i.forEach { a.put(it) } })
            o.put("v", JSONArray().also { a -> v.forEach { a.put(Math.round(it * 10000) / 10000.0) } })
        }
        return o
    }

    private fun featureFromJson(o: JSONObject): Feature {
        val v = o.getJSONArray("v")
        val values = FloatArray(v.length()) { v.getDouble(it).toFloat() }
        val i = o.optJSONArray("i") ?: return Feature(dense = values)
        return Feature(idx = IntArray(i.length()) { i.getInt(it) }, vals = values)
    }

    @Synchronized
    private fun entries(context: Context, script: Script): MutableList<Entry> = cache.getOrPut(script) {
        val file = File(dir(context), script.fileName)
        val list = mutableListOf<Entry>()
        if (file.isFile) runCatching {
            val arr = JSONArray(file.readText())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list += Entry(o.getString("l"), featureFromJson(o), o.optString("s"))
            }
        }.onFailure { Log.w(TAG, "read ${file.name} failed", it) }
        list
    }

    @Synchronized
    private fun save(context: Context, script: Script) {
        val list = cache[script] ?: return
        val arr = JSONArray()
        for (e in list) arr.put(e.feat.toJson(JSONObject().put("l", e.label).put("s", e.source)))
        val file = File(dir(context), script.fileName)
        val tmp = File(file.path + ".tmp")
        tmp.writeText(arr.toString())
        if (!tmp.renameTo(file)) { tmp.copyTo(file, overwrite = true); tmp.delete() }
    }

    /** 一个字的笔迹算成特征；在后台线程调用（假名要走手写服务进程）。 */
    private fun feature(context: Context, script: Script, strokes: List<List<Pair<Float, Float>>>): Feature? = when (script) {
        Script.KANA -> kanaRaw(context, strokes).takeIf { it.isNotEmpty() }
            ?.let { raw -> Feature.fromProbabilities(raw.map { it.index to it.score }) }
        Script.LATIN -> FormulaRecognizer.embed(context, strokes)?.let { Feature(dense = it) }
    }

    /** 假名特征的来源：内置汉字手写模型概率最高的若干类（同一份结果也给出模型认出的漢字）。 */
    private fun kanaRaw(context: Context, strokes: List<List<Pair<Float, Float>>>): List<HandwritingRawCandidate> {
        HandwritingEngine.initialize(context, InputLanguage.JAPANESE)
        return HandwritingEngine.predictRaw(strokes, OCH_TOP_N)
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
     * 解开预算的假名特征：[meta] 里的 model_sha256 与当前汉字模型的 [modelSha] 不一致（换了模型、启用了个人模型）
     * 或文件长度对不上时返回 null，由调用方回退为在设备上现算。
     */
    internal fun decodePrecomputed(meta: String, bin: ByteArray, modelSha: String): List<Pair<String, Feature>>? {
        val o = JSONObject(meta)
        if (!o.optString("model_sha256").equals(modelSha, ignoreCase = true)) return null
        val n = o.getInt("n")
        val labels = o.getJSONArray("labels")
        if (n <= 0 || bin.size != labels.length() * n * 4) return null
        val buf = java.nio.ByteBuffer.wrap(bin).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        return (0 until labels.length()).map { t ->
            val pairs = (0 until n).map { (buf.short.toInt() and 0xffff) to halfToFloat(buf.short.toInt()) }.sortedBy { it.first }
            labels.getString(t) to Feature(idx = IntArray(n) { pairs[it].first }, vals = FloatArray(n) { pairs[it].second })
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
     * 准备内置假名模板：汉字手写模型就是安装包内置那一份（哈希一致）时直接读预算特征，第一次打开也不用等；
     * 换了模型或启用了个人模型时在设备上现算一次并缓存。在后台线程调用；不是假名或没有汉字模型时什么也不做。
     */
    fun ensureDefaults(context: Context, script: Script): Unit = synchronized(defaultLock) { prepareDefaults(context, script) }

    private val defaultLock = Any()

    private fun prepareDefaults(context: Context, script: Script) {
        if (script != Script.KANA || synchronized(this) { defaults.containsKey(script) }) return
        if (!script.available(context)) return
        val personal = HandwritingEngine.personalModelFile(context).takeIf { it.isFile }
        val model = personal ?: File(com.kingzcheung.xime.model.ModelStorage.getModelDir(context, "ochwpro"), "ochwpro.onnx")
        val stamp = "${model.path}:${model.length()}:${model.lastModified()}"
        val cacheFile = File(dir(context), DEFAULT_CACHE)
        val cached = runCatching {
            val o = JSONObject(cacheFile.readText())
            if (o.getString("model") != stamp) null else {
                val arr = o.getJSONArray("t")
                (0 until arr.length()).map { i -> arr.getJSONObject(i).let { e -> Entry(e.getString("l"), featureFromJson(e), "builtin") } }
            }
        }.getOrNull()
        val list = cached ?: runCatching {
            val sha = sha256(model)
            BUILTIN_SETS.flatMap { set ->
                decodePrecomputed(context.assets.open(set.meta).bufferedReader(Charsets.UTF_8).use { it.readText() },
                    context.assets.open(set.vectors).use { it.readBytes() }, sha)
                    ?.map { (label, feat) -> Entry(label, feat, "builtin") } ?: return@runCatching null
            }
        }.onFailure { Log.w(TAG, "precomputed kana templates unreadable", it) }.getOrNull() ?: runCatching {
            val out = mutableListOf<Entry>()
            for (set in BUILTIN_SETS) {
            val chars = JSONObject(context.assets.open(set.strokes).bufferedReader(Charsets.UTF_8).use { it.readText() })
                .getJSONObject("chars")
            for (label in chars.keys()) {
                val variants = chars.getJSONArray(label)
                for (i in 0 until variants.length()) {
                    val strokes = variants.getJSONArray(i).let { st ->
                        (0 until st.length()).map { j ->
                            val pts = st.getJSONArray(j)
                            (0 until pts.length()).map { k -> pts.getJSONArray(k).let { p -> p.getDouble(0).toFloat() to p.getDouble(1).toFloat() } }
                        }
                    }
                    feature(context, script, strokes)?.let { out += Entry(label, it, "builtin") }
                }
            }
            }
            if (out.isEmpty()) return
            val arr = JSONArray()
            for (e in out) arr.put(e.feat.toJson(JSONObject().put("l", e.label)))
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
        if (files.isEmpty() || !script.available(context)) return 0
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
            val feat = feature(context, script, strokes) ?: return@forEachLine
            add(context, script, label, feat, id, persist = false)
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
    private fun add(context: Context, script: Script, label: String, feat: Feature, source: String, persist: Boolean = true) {
        val list = entries(context, script)
        list += Entry(label, feat, source)
        val same = list.filter { it.label == label }
        if (same.size > MAX_PER_LABEL) list.remove(same.first())
        if (persist) save(context, script)
    }

    /** 训练页保存了一个假名 / 字母样本：顺便加成模板。 */
    fun addTrainingSample(context: Context, type: String, label: String, sampleId: String, strokes: List<List<Pair<Float, Float>>>) {
        val script = Script.entries.firstOrNull { type in it.sampleTypes } ?: return
        if (!script.accepts(label) || !script.available(context)) return
        val feat = feature(context, script, strokes) ?: return
        add(context, script, label, feat, "train:$sampleId")
    }

    /**
     * 识别一个字：和模板逐个比，每个字取最像的那个模板的相似度，按相似度排序。
     * 返回 (字, 0~1 的分数)；模板库为空或特征算不出来时为空。
     */
    fun recognize(context: Context, script: Script, strokes: List<List<Pair<Float, Float>>>, topK: Int = 8): List<Pair<String, Float>> {
        val list = synchronized(this) { entries(context, script).toList() }
        val builtin = synchronized(this) { defaults[script].orEmpty() }
        if (list.isEmpty() && builtin.isEmpty()) return emptyList()
        // 日语：同一次模型输出既当假名特征，也直接给出漢字（换成日语字形、按日语常用度）
        val raw = if (script == Script.KANA) kanaRaw(context, strokes) else emptyList()
        val query = (if (script == Script.KANA) raw.takeIf { it.isNotEmpty() }?.let { r -> Feature.fromProbabilities(r.map { it.index to it.score }) }
            else feature(context, script, strokes)) ?: return emptyList()
        lastFeature = query
        lastScript = script
        val kanji = if (script == Script.KANA) JapaneseKanji.tableFor(context) else null
        return rank(query, list.map { it.label to it.feat }, builtin.map { it.label to it.feat }, topK, raw, kanji)
    }

    /**
     * 每个字取最像的模板；自己的模板加 [PERSONAL_BONUS]。日语另加模型认出的漢字并按常用度微调。
     * 分数换算到 0~1。
     */
    internal fun rank(query: Feature, personal: List<Pair<String, Feature>>, builtin: List<Pair<String, Feature>>, topK: Int,
                      raw: List<HandwritingRawCandidate> = emptyList(), kanji: JapaneseKanji.Table? = null): List<Pair<String, Float>> {
        val best = HashMap<String, Float>()
        for ((label, feat) in personal) {
            val s = query.dot(feat) + PERSONAL_BONUS
            if (s > (best[label] ?: -2f)) best[label] = s
        }
        for ((label, feat) in builtin) {
            val s = query.dot(feat)
            if (s > (best[label] ?: -2f)) best[label] = s
        }
        if (kanji != null) {
            JapaneseKanji.addModelKanji(best, raw, query, kanji)
            JapaneseKanji.applyFrequency(best, kanji)
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
        val feat = lastFeature ?: return
        lastFeature = null
        if (!script.accepts(committed)) return
        val app = context.applicationContext
        // 写文件放后台，不卡上屏
        Thread { add(app, script, committed, feat, "pick:${System.currentTimeMillis()}") }.start()
    }

    /** 这种文字的特征模型在、模板库里至少有一个字时，就开放模板手写。 */
    fun isUsable(context: Context, script: Script): Boolean = script.available(context) && count(context, script) > 0

    internal fun labelsWithCount(context: Context, script: Script): Map<String, Int> =
        synchronized(this) { entries(context, script).groupingBy { it.label }.eachCount() }
}
