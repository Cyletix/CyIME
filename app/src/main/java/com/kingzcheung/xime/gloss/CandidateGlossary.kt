package com.kingzcheung.xime.gloss

import android.content.Context
import androidx.compose.runtime.mutableIntStateOf
import com.kingzcheung.xime.model.ModelStorage
import com.kingzcheung.xime.settings.CandidateCommentContent
import com.kingzcheung.xime.settings.CandidateCommentMode
import com.kingzcheung.xime.settings.SettingsPreferences
import com.kingzcheung.xime.util.FileLogger
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * 候选注释的中英释义：中文候选显示英文，中文键盘里混出的英文候选显示中文。
 *
 * 词典是商店里的可选下载包，只在注释未隐藏且“候选注释内容”选了释义时才映射进来；查表只做在候选
 * 显示层，每屏十几次二分查找，不进逐键解码。查不到的候选保留原注释（拼音或编码）。
 * 只替换显示用的注释：九键选词要用候选数据里的拼音注释定位，候选数据本身不能改。
 */
object CandidateGlossary {
    const val MODEL_ID = "candidate-gloss"
    const val FILE_NAME = "cyime_gloss.tsv"
    private const val TAG = "CandidateGlossary"

    @Volatile private var table: GlossTable? = null
    @Volatile private var signature: String? = null
    private val loading = AtomicBoolean(false)
    private val revisionState = mutableIntStateOf(0)

    /** 词典加载或释放时递增；界面把它作为 remember 键，加载完成后立即重排注释。 */
    val revision: Int get() = revisionState.intValue

    fun file(context: Context): File = File(ModelStorage.getModelDir(context, MODEL_ID), FILE_NAME)

    fun isInstalled(context: Context): Boolean = file(context).let { it.isFile && it.length() > 0 }

    /** 按当前设置在后台加载或释放词典；只读偏好和文件信息，可在主线程调用。 */
    fun prepare(context: Context) {
        val source = file(context)
        if (SettingsPreferences.candidateCommentContent(context) != CandidateCommentContent.GLOSS ||
            SettingsPreferences.candidateCommentMode(context) == CandidateCommentMode.HIDE || !source.isFile) {
            if (table != null) revisionState.intValue++
            table = null
            signature = null
            return
        }
        val current = "${source.absolutePath}:${source.length()}:${source.lastModified()}"
        if (current == signature || !loading.compareAndSet(false, true)) return
        thread(name = "candidate-gloss", isDaemon = true) {
            try {
                table = GlossTable.open(source)
                signature = current
                revisionState.intValue++
            } catch (error: Exception) {
                FileLogger.w(TAG, "释义词典加载失败：${error.message}")
            } finally {
                loading.set(false)
            }
        }
    }

    /** 把查得到释义的候选注释换成释义；未加载或查不到时原样返回。 */
    fun annotate(texts: List<String>, comments: List<String>): List<String> {
        val glossary = table ?: return comments
        return texts.mapIndexed { index, text -> glossary.lookup(keyOf(text)) ?: comments.getOrElse(index) { "" } }
    }

    fun annotate(text: String, comment: String): String = table?.lookup(keyOf(text)) ?: comment

    internal fun keyOf(text: String): String {
        val trimmed = text.trim()
        return if (trimmed.all { it.code < 0x80 }) trimmed.lowercase(Locale.ROOT) else trimmed
    }
}

/** 按 UTF-8 字节序排好的 `词\t释义` 行；内存映射，堆上只留每行起点。 */
internal class GlossTable private constructor(private val buffer: ByteBuffer, private val starts: IntArray) {
    val size: Int get() = starts.size

    fun lookup(key: String): String? {
        if (key.isEmpty()) return null
        val wanted = key.toByteArray(Charsets.UTF_8)
        var low = 0
        var high = starts.size - 1
        while (low <= high) {
            val mid = (low + high) ushr 1
            val order = compareKey(starts[mid], wanted)
            when {
                order < 0 -> low = mid + 1
                order > 0 -> high = mid - 1
                else -> return value(starts[mid] + wanted.size + 1)
            }
        }
        return null
    }

    private fun compareKey(start: Int, wanted: ByteArray): Int {
        var i = 0
        while (true) {
            val stored = buffer.get(start + i).toInt() and 0xff
            val ended = stored == TAB
            if (i == wanted.size) return if (ended) 0 else 1
            if (ended) return -1
            val expected = wanted[i].toInt() and 0xff
            if (stored != expected) return stored - expected
            i++
        }
    }

    private fun value(start: Int): String? {
        var end = start
        while (end < buffer.limit() && buffer.get(end).toInt() != NEWLINE) end++
        if (end <= start) return null
        val bytes = ByteArray(end - start)
        for (i in bytes.indices) bytes[i] = buffer.get(start + i)
        return String(bytes, Charsets.UTF_8)
    }

    companion object {
        private const val TAB = '\t'.code
        private const val NEWLINE = '\n'.code
        private const val COMMENT = '#'.code

        fun open(file: File): GlossTable {
            val buffer = RandomAccessFile(file, "r").use { it.channel.map(FileChannel.MapMode.READ_ONLY, 0, it.length()) }
            return fromBuffer(buffer)
        }

        fun fromBuffer(buffer: ByteBuffer): GlossTable {
            var starts = IntArray(1 shl 16)
            var count = 0
            var lineStart = 0
            val limit = buffer.limit()
            while (lineStart < limit) {
                var end = lineStart
                var hasTab = false
                while (end < limit) {
                    val b = buffer.get(end).toInt()
                    if (b == NEWLINE) break
                    if (b == TAB) hasTab = true
                    end++
                }
                if (hasTab && buffer.get(lineStart).toInt() != COMMENT) {
                    if (count == starts.size) starts = starts.copyOf(count * 2)
                    starts[count++] = lineStart
                }
                lineStart = end + 1
            }
            return GlossTable(buffer, starts.copyOf(count))
        }
    }
}
