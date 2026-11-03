package com.kingzcheung.xime.handwriting.training

import android.content.Context
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPOutputStream

/**
 * 「贡献手写样本」：公开版里按固定题目写字，写好的样本分享成文件或上传给作者，用来训练手写模型。
 *
 * - 只收这一页写的字；输入法手写板平时写的内容不经过这里，也不会被保存或上传。
 * - 样本存在 app 私有目录 `files/handwriting_contribute/`，和训练模式的样本分开。
 * - 每条样本带一个本机随机生成的 `writer`（不含任何账号或设备标识），用来把同一个人写的字归在一起。
 * - 上传前要先同意一次「样本用于训练 CyIME 手写模型」。
 */
object HandwritingContribution {
    /** 样本目录和进度存储名，传给 [TrainingSampleStore]。 */
    const val STORE_NAME = "handwriting_contribute"

    /**
     * 接收上传的地址。还没建好接收端时为空，页面上只有「分享」。
     * 接收端只收 [TrainingSampleCodec.FORMAT] 格式、gzip 后不超过 [MAX_UPLOAD_BYTES] 的 JSONL。
     */
    const val UPLOAD_URL = ""

    const val MAX_UPLOAD_BYTES = 8L * 1024 * 1024

    const val CONSENT_TEXT = "写的字只用于训练 CyIME 手写模型，训练出的模型会随 CyIME 公开发布。" +
        "样本只包含这一页写的笔迹、题目、App 版本和手机型号，不包含你平时打的字。"

    private const val ASSET = "handwriting/contribute_prompts.tsv"
    private const val PREFS = "handwriting_contribute"

    fun uploadAvailable(): Boolean = UPLOAD_URL.isNotEmpty()

    /** 固定题目：常用字、生僻字、两字词，见 tools/handwriting_training/build_contribute_prompts.py。 */
    fun prompts(context: Context): List<TrainingPrompt> =
        context.assets.open(ASSET).bufferedReader(Charsets.UTF_8).useLines { parse(it) }

    internal fun parse(lines: Sequence<String>): List<TrainingPrompt> = lines.mapNotNull { line ->
        if (line.isBlank() || line.startsWith("#")) return@mapNotNull null
        val cols = line.split('\t')
        if (cols.size < 2 || cols[1].isBlank()) return@mapNotNull null
        val kind = cols[0].trim()
        val text = cols[1].trim()
        TrainingPrompt(
            id = "contribute:$kind:$text",
            type = TrainingPromptType.Chinese,
            label = text,
            source = "contribute",
            hint = cols.getOrNull(2)?.trim()?.ifEmpty { null },
            kind = kind,
        )
    }.toList()

    /** 本机随机生成、只用于给样本分组的书写人编号。 */
    fun writerId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString("writer", null)?.let { return it }
        val id = UUID.randomUUID().toString().replace("-", "").take(16)
        prefs.edit().putString("writer", id).apply()
        return id
    }

    fun consented(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("consent", false)

    fun setConsented(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("consent", true).apply()
    }

    /** 上次上传时样本文件已有的行数：再上传只传新写的。 */
    fun uploadedLines(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt("uploaded_lines", 0)

    private fun setUploadedLines(context: Context, lines: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt("uploaded_lines", lines).apply()
    }

    fun resetUploaded(context: Context) = setUploadedLines(context, 0)

    /** 上传结果：[count] 是这次传了几条；没有新样本时为 0。 */
    data class UploadResult(val count: Int)

    /**
     * 把样本文件里还没上传过的行 gzip 后上传。在 IO 线程调用。
     * 接收端返回 2xx 才记为已上传；失败抛异常，下次再传还是这些行。
     */
    fun upload(context: Context, samples: File, appVersion: String): UploadResult {
        check(uploadAvailable()) { "还不能上传" }
        check(consented(context)) { "还没同意" }
        val lines = if (samples.isFile) samples.readLines(Charsets.UTF_8).filter { it.isNotBlank() } else emptyList()
        val done = uploadedLines(context).coerceIn(0, lines.size)
        val fresh = lines.drop(done)
        if (fresh.isEmpty()) return UploadResult(0)
        val body = gzip(fresh.joinToString("\n", postfix = "\n").toByteArray(Charsets.UTF_8))
        check(body.size <= MAX_UPLOAD_BYTES) { "样本太多，请先分享文件" }
        val request = Request.Builder().url(UPLOAD_URL)
            .header("User-Agent", "CyIME/$appVersion")
            .header("X-CyIME-Writer", writerId(context))
            .header("X-CyIME-Format", "${TrainingSampleCodec.FORMAT}/${TrainingSampleCodec.VERSION}")
            .post(body.toRequestBody("application/gzip".toMediaType()))
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val message = runCatching { JSONObject(response.body.string()).optString("error") }.getOrNull()
                throw IllegalStateException(message?.ifEmpty { null } ?: "服务器返回 ${response.code}")
            }
        }
        setUploadedLines(context, lines.size)
        return UploadResult(fresh.size)
    }

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private fun gzip(bytes: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(bytes) }
        return out.toByteArray()
    }
}
