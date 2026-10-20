package com.kingzcheung.xime.gloss

import android.content.Context
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
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
 * 显示层，每屏十几次二分查找，不进逐键解码。词典已加载时查不到释义的候选不显示注释（不退回拼音）。
 * 按住 Shift 时有英文的中文候选中英对调，此时选它上屏英文（[englishFor]）。
 * 只替换显示用的注释：九键选词要用候选数据里的拼音注释定位，候选数据本身不能改。
 */
object CandidateGlossary {
    const val MODEL_ID = "candidate-gloss"
    /** 文件名带版本：v1 有错释义（几号 → heroin），v2 缺大量常用字词；装着旧版的设备自动换新。 */
    const val FILE_NAME = "cyime_gloss-v3.tsv"
    private val LEGACY_FILE_NAMES = listOf("cyime_gloss.tsv", "cyime_gloss-v2.tsv")
    private const val TAG = "CandidateGlossary"
    private val upgradeStarted = AtomicBoolean(false)

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
        val wanted = SettingsPreferences.candidateCommentContent(context) == CandidateCommentContent.GLOSS &&
            SettingsPreferences.candidateCommentMode(context) != CandidateCommentMode.HIDE
        if (wanted && !source.isFile) upgradeFromLegacy(context)
        if (!wanted || !source.isFile) {
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

    /** 用户已选释义、装的是旧版词典：后台换成新版（只下载一次），下完删掉旧文件并重新加载。 */
    private fun upgradeFromLegacy(context: Context) {
        val dir = ModelStorage.getModelDir(context, MODEL_ID)
        val legacy = LEGACY_FILE_NAMES.map { File(dir, it) }.filter { it.isFile }
        if (legacy.isEmpty() || !upgradeStarted.compareAndSet(false, true)) return
        val model = com.kingzcheung.xime.model.ModelManager.getModel(MODEL_ID) ?: return
        val app = context.applicationContext
        com.kingzcheung.xime.model.ModelManager.downloadModelInBackground(app, model, onProgress = { state ->
            when (state) {
                is com.kingzcheung.xime.model.ModelDownloadState.Complete -> {
                    legacy.forEach { it.delete() }
                    prepare(app)
                }
                is com.kingzcheung.xime.model.ModelDownloadState.Error -> upgradeStarted.set(false)
                else -> Unit
            }
        })
    }

    /** 词典已加载（释义模式且注释未隐藏）。 */
    val isLoaded: Boolean get() = table != null

    /** 物理键盘的 Shift 正被按住；屏幕键盘看 ShiftMode.HELD。按住时候选中英对调。 */
    val physicalShiftHeld = mutableStateOf(false)

    /** 九键长按“下一项”切到的中英对调（代替 Shift，松手保持）；上屏英文、再长按或编码结束时恢复。 */
    val t9NextHeld = mutableStateOf(false)

    /** 任一种“按住 Shift”生效中：屏幕 Shift 键按住（[screenShiftHeld]）、物理 Shift、九键长按“下一项”，或从它拖到候选上。 */
    /** 正从九键“下一项”拖着经过候选：此刻松手就上屏英文，候选先显示成英文。 */
    val t9DragSwap = mutableStateOf(false)

    fun swapHeld(screenShiftHeld: Boolean): Boolean =
        screenShiftHeld || physicalShiftHeld.value || t9NextHeld.value || t9DragSwap.value

    /** 注释换成释义；词典未加载时原样返回，已加载但查不到的候选不显示注释。 */
    fun annotate(texts: List<String>, comments: List<String>): List<String> = annotate(table, texts, comments)

    fun annotate(text: String, comment: String): String = annotate(table, listOf(text), listOf(comment)).first()

    /** 按住 Shift 选这条候选时上屏的英文；没有完整英文时为 null，照常上中文。 */
    fun englishFor(text: String): String? = table?.let { englishFor(it, text) }

    /** 按住 Shift 时的显示：有英文的中文候选显示英文、中文退作注释，其余候选同 [annotate]。 */
    fun swapped(texts: List<String>, comments: List<String>): Pair<List<String>, List<String>> =
        swapped(table, texts, comments)

    internal fun annotate(table: GlossTable?, texts: List<String>, comments: List<String>): List<String> {
        if (table == null) return comments
        return texts.map { table.lookup(keyOf(it)).orEmpty() }
    }

    internal fun englishFor(table: GlossTable, text: String): String? {
        // 本身是英文的候选直接上屏它自己；被截断的释义（以 … 结尾）不是完整英文，不拿来上屏。
        if (text.isBlank() || text.all { it.code < 0x80 }) return null
        return table.lookup(keyOf(text))?.takeUnless { it.isBlank() || it.endsWith("…") }
    }

    internal fun swapped(table: GlossTable?, texts: List<String>, comments: List<String>): Pair<List<String>, List<String>> {
        if (table == null) return texts to comments
        val notes = annotate(table, texts, comments)
        val words = ArrayList<String>(texts.size)
        val shown = ArrayList<String>(texts.size)
        texts.forEachIndexed { index, text ->
            val english = englishFor(table, text)
            words += english ?: text
            shown += if (english != null) text else notes[index]
        }
        return words to shown
    }

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
