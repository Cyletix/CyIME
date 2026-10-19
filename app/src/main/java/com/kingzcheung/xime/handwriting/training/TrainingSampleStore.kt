package com.kingzcheung.xime.handwriting.training

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/** 一个采样点：作画区局部像素坐标（左上原点，y 向下），t 为相对本样本首次落笔的毫秒数。 */
data class TrainingPoint(val x: Float, val y: Float, val t: Long)

/** 一笔：落笔到抬笔之间的点，tool 为 `touch` / `stylus` / `mouse` 等。 */
data class TrainingStroke(val points: List<TrainingPoint>, val tool: String = "touch")

data class TrainingSample(
    val prompt: TrainingPrompt,
    val canvasWidthPx: Float,
    val canvasHeightPx: Float,
    val density: Float,
    val strokes: List<TrainingStroke>,
    val createdAtMs: Long,
    val id: String = UUID.randomUUID().toString(),
    /** 保存时内置模型给出的前几个候选（没做识别检查时为 null）。 */
    val recognized: List<String>? = null,
)

/** 识别检查认错过的题：[streak] 是之后连续认对的次数，连续认对 [TrainingSampleStore.CLEAR_STREAK] 次后移出。 */
data class TrainingMiss(val prompt: TrainingPrompt, val misses: Int, val streak: Int, val lastWrong: String)

/**
 * 训练样本 JSONL 编码。每行一个样本，供电脑侧训练脚本读取
 * （参考读取器：tools/handwriting_training/read_samples.py）。
 *
 * ```
 * {"format":"cyime-handwriting-training","version":1,"id":"…",
 *  "prompt_id":"kana:あ","type":"kana","label":"あ","latex":null,"source":"builtin",
 *  "canvas":{"w":1080.0,"h":1400.0,"density":2.75},
 *  "strokes":[{"tool":"touch","points":[[x,y,t],…]},…],
 *  "created_at":"2026-10-12T20:30:00+0900","created_at_ms":…,"app_version":"…","device":"…"}
 * ```
 * x/y 为像素，未归一化；t 为相对本样本首次落笔的毫秒数（跨笔连续计时，笔间停顿可还原）。
 * 做过识别检查的样本另有 `"recognized":["这","返",…]`（内置模型前几个候选）和 `"matched":true/false`。
 */
object TrainingSampleCodec {
    const val FORMAT = "cyime-handwriting-training"
    const val VERSION = 1

    fun encode(sample: TrainingSample, appVersion: String, device: String): String {
        val p = sample.prompt
        val obj = JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("id", sample.id)
            .put("prompt_id", p.id)
            .put("type", p.type.key)
            .put("label", p.label)
            .put("latex", p.latex ?: JSONObject.NULL)
            .put("source", p.source)
        if (p.repaired) obj.put("repaired", true)
        p.kind?.let { obj.put("kind", it) }
        if (p.long) obj.put("long", true)
        p.hint?.let { obj.put("hint", it) }
        obj.put(
            "canvas",
            JSONObject()
                .put("w", sample.canvasWidthPx.toDouble())
                .put("h", sample.canvasHeightPx.toDouble())
                .put("density", sample.density.toDouble()),
        )
        val strokes = JSONArray()
        for (stroke in sample.strokes) {
            val pts = JSONArray()
            for (pt in stroke.points) {
                pts.put(JSONArray().put(round2(pt.x)).put(round2(pt.y)).put(pt.t))
            }
            strokes.put(JSONObject().put("tool", stroke.tool).put("points", pts))
        }
        obj.put("strokes", strokes)
        sample.recognized?.let { top ->
            obj.put("recognized", JSONArray(top))
            obj.put("matched", top.firstOrNull() == p.label)
        }
        obj.put("created_at", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US).format(Date(sample.createdAtMs)))
        obj.put("created_at_ms", sample.createdAtMs)
        obj.put("app_version", appVersion)
        obj.put("device", device)
        // JSONL 要求单行；org.json 默认不换行
        return obj.toString()
    }

    private fun round2(v: Float): Double = Math.round(v * 100.0) / 100.0
}

/**
 * 训练模式本地存储，全部在 app 私有目录 `files/handwriting_training/`：
 * - samples.jsonl：已保存样本，逐行追加；
 * - formula_bank.jsonl：用户导入的公式题库（私人数据，不随安装包分发）；
 * 进度（每个题库下一题位置）存在 SharedPreferences。
 */
class TrainingSampleStore(private val context: Context) {

    private val dir = File(context.filesDir, "handwriting_training").apply { mkdirs() }
    private val samplesFile = File(dir, "samples.jsonl")
    private val formulaFile = File(dir, "formula_bank.jsonl")
    private val missFile = File(dir, "misses.jsonl")
    private val prefs = context.getSharedPreferences("handwriting_training", Context.MODE_PRIVATE)

    companion object {
        /** 认对一次就说明能认出来，移出认错列表。 */
        const val CLEAR_STREAK = 1
    }

    fun loadMisses(): List<TrainingMiss> {
        if (!missFile.exists()) return emptyList()
        val out = mutableListOf<TrainingMiss>()
        missFile.forEachLine(Charsets.UTF_8) { line ->
            if (line.isBlank()) return@forEachLine
            runCatching {
                val o = JSONObject(line)
                val type = TrainingPromptType.entries.firstOrNull { it.key == o.getString("type") } ?: return@runCatching
                out += TrainingMiss(
                    prompt = TrainingPrompt(
                        id = o.getString("id"), type = type, label = o.getString("label"),
                        latex = o.optString("latex").ifEmpty { null }, source = o.optString("source", "builtin"),
                    ),
                    misses = o.optInt("misses", 1),
                    streak = o.optInt("streak", 0),
                    lastWrong = o.optString("last_wrong"),
                )
            }
        }
        return out
    }

    /**
     * 记一次识别检查结果。认错：加入（或留在）认错列表并清零连续认对；
     * 认对：已在列表里的题连续认对数加一，够 [CLEAR_STREAK] 次就移出。返回更新后的列表。
     */
    fun recordCheck(prompt: TrainingPrompt, matched: Boolean, top: String): List<TrainingMiss> {
        val list = loadMisses().toMutableList()
        val i = list.indexOfFirst { it.prompt.id == prompt.id }
        if (!matched) {
            if (i >= 0) list[i] = list[i].copy(misses = list[i].misses + 1, streak = 0, lastWrong = top)
            else list += TrainingMiss(prompt, 1, 0, top)
        } else if (i >= 0) {
            val streak = list[i].streak + 1
            if (streak >= CLEAR_STREAK) list.removeAt(i) else list[i] = list[i].copy(streak = streak)
        } else {
            return list
        }
        writeMisses(list)
        return list
    }

    /** 只保留满足条件的认错记录（例如去掉早先误记的字母、符号题），返回保留后的列表。 */
    fun retainMisses(keep: (TrainingMiss) -> Boolean): List<TrainingMiss> {
        val all = loadMisses()
        val kept = all.filter(keep)
        if (kept.size != all.size) writeMisses(kept)
        return kept
    }

    private fun writeMisses(list: List<TrainingMiss>) {
        val tmp = File(dir, "misses.jsonl.tmp")
        tmp.bufferedWriter(Charsets.UTF_8).use { w ->
            for (m in list) {
                val o = JSONObject()
                    .put("id", m.prompt.id).put("type", m.prompt.type.key).put("label", m.prompt.label)
                    .put("latex", m.prompt.latex ?: "").put("source", m.prompt.source)
                    .put("misses", m.misses).put("streak", m.streak).put("last_wrong", m.lastWrong)
                w.write(o.toString()); w.write("\n")
            }
        }
        if (!tmp.renameTo(missFile)) { tmp.copyTo(missFile, overwrite = true); tmp.delete() }
    }

    fun append(sample: TrainingSample, appVersion: String, device: String) {
        samplesFile.appendText(TrainingSampleCodec.encode(sample, appVersion, device) + "\n", Charsets.UTF_8)
    }

    /**
     * 合并导入一份样本备份（训练模式导出的 .jsonl）：只收本格式的行，按样本 id 去重后追加。
     * 清除数据后用它恢复，不需要 adb。返回新加入的样本数，按类型（kana / latin …）计。
     */
    fun importSamples(lines: Sequence<String>): Map<String, Int> {
        val known = HashSet<String>()
        if (samplesFile.exists()) samplesFile.forEachLine(Charsets.UTF_8) { line ->
            runCatching { JSONObject(line).optString("id") }.getOrNull()?.takeIf { it.isNotEmpty() }?.let(known::add)
        }
        val added = LinkedHashMap<String, Int>()
        val out = StringBuilder()
        for (line in lines) {
            if (line.isBlank()) continue
            val o = runCatching { JSONObject(line) }.getOrNull() ?: continue
            val id = o.optString("id")
            if (o.optString("format") != TrainingSampleCodec.FORMAT || id.isEmpty() || !known.add(id)) continue
            out.append(line.trim()).append('\n')
            val type = o.optString("type")
            added[type] = (added[type] ?: 0) + 1
        }
        if (out.isNotEmpty()) samplesFile.appendText(out.toString(), Charsets.UTF_8)
        return added
    }

    /** 已保存样本数，按 prompt_id 计数。 */
    fun countsByPrompt(): Map<String, Int> {
        if (!samplesFile.exists()) return emptyMap()
        val counts = HashMap<String, Int>()
        samplesFile.forEachLine(Charsets.UTF_8) { line ->
            if (line.isBlank()) return@forEachLine
            val id = runCatching { JSONObject(line).optString("prompt_id") }.getOrNull().orEmpty()
            if (id.isNotEmpty()) counts[id] = (counts[id] ?: 0) + 1
        }
        return counts
    }

    fun clearSamples() {
        samplesFile.delete()
        missFile.delete()
    }

    fun loadFormulaBank(): List<TrainingPrompt> {
        if (!formulaFile.exists()) return emptyList()
        val out = mutableListOf<TrainingPrompt>()
        formulaFile.forEachLine(Charsets.UTF_8) { line ->
            if (line.isBlank()) return@forEachLine
            runCatching {
                val o = JSONObject(line)
                out += TrainingPrompt(
                    id = o.getString("id"),
                    type = TrainingPromptType.Formula,
                    label = o.getString("label"),
                    latex = o.optString("latex").ifEmpty { o.getString("label") },
                    source = o.optString("source", "import"),
                    repaired = o.optBoolean("repaired", false),
                )
            }
        }
        return out
    }

    /**
     * 导入一个文件的公式题：替换同名文件之前导入的题，保留其它文件导入的题（例如 annotations.csv 和 Math 题库可以并存）。
     * 返回合并后的题库。
     */
    fun mergeFormulaBank(sourceName: String, prompts: List<TrainingPrompt>): List<TrainingPrompt> {
        val kept = loadFormulaBank().filterNot { it.source.substringBeforeLast(':') == sourceName }
        val merged = (kept + prompts).distinctBy { it.id }
        saveFormulaBank(merged)
        return merged
    }

    fun saveFormulaBank(prompts: List<TrainingPrompt>) {
        val tmp = File(dir, "formula_bank.jsonl.tmp")
        tmp.bufferedWriter(Charsets.UTF_8).use { w ->
            for (p in prompts) {
                val o = JSONObject()
                    .put("id", p.id)
                    .put("label", p.label)
                    .put("latex", p.latex ?: p.label)
                    .put("source", p.source)
                if (p.repaired) o.put("repaired", true)
                w.write(o.toString()); w.write("\n")
            }
        }
        if (!tmp.renameTo(formulaFile)) {
            tmp.copyTo(formulaFile, overwrite = true); tmp.delete()
        }
    }

    fun position(bank: TrainingBank): Int = prefs.getInt("pos_${bank.id}", 0)

    fun setPosition(bank: TrainingBank, index: Int) {
        prefs.edit().putInt("pos_${bank.id}", index).apply()
    }

    var currentBank: TrainingBank
        get() = TrainingBank.entries.firstOrNull { it.id == prefs.getString("type", null) && it.inMenu }
            ?: TrainingBank.entries.first()
        set(value) = prefs.edit().putString("type", value.id).apply()

    /** 复制样本到导出文件（app 私有 exports/），返回该文件；无样本时返回 null。 */
    fun snapshotForExport(): File? {
        if (!samplesFile.exists() || samplesFile.length() == 0L) return null
        val exportDir = File(context.filesDir, "exports").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return File(exportDir, "cyime_handwriting_training_$stamp.jsonl").also { samplesFile.copyTo(it, overwrite = true) }
    }

    /** 保存到公共“下载”目录，返回显示路径。 */
    fun saveToDownloads(file: File): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, file.name)
                put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("MediaStore insert failed")
            context.contentResolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
                ?: throw IllegalStateException("openOutputStream failed")
            "Download/${file.name}"
        } else {
            @Suppress("DEPRECATION")
            val dest = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), file.name)
            file.copyTo(dest, overwrite = true)
            "Download/${file.name}"
        }
    }
}
