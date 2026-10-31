package com.kingzcheung.xime.handwriting

import android.content.Context
import androidx.compose.runtime.mutableIntStateOf
import com.kingzcheung.xime.util.FileLogger
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * 中文手写候选的读音（带声调）：忘了怎么读的字写出来对一下，按候选注释的显示／自动／隐藏显示在候选旁。
 * 数据：assets/handwriting/hanzi_pinyin.tsv，由 tools/handwriting_training/build_hanzi_pinyin.py 生成；
 * 多音字列常用的几个读音，用 / 隔开。中文手写页打开时后台读进来，没读完时不显示注释。
 */
object HanziPinyin {
    private const val TAG = "HanziPinyin"
    private const val ASSET = "handwriting/hanzi_pinyin.tsv"

    /** 按码位排好的字和对应读音（同一读音共用一个字符串），二分查找。 */
    internal class Table(val codePoints: IntArray, val readings: Array<String>) {
        fun lookup(codePoint: Int): String? =
            codePoints.binarySearch(codePoint).takeIf { it >= 0 }?.let { readings[it] }
    }

    @Volatile private var table: Table? = null
    private val loading = AtomicBoolean(false)
    private val revisionState = mutableIntStateOf(0)

    /** 读音表读进来后递增；界面把它作为 remember 键，读完立即补上注释。 */
    val revision: Int get() = revisionState.intValue

    fun load(context: Context) {
        if (table != null || !loading.compareAndSet(false, true)) return
        val assets = context.applicationContext.assets
        thread(name = "hanzi-pinyin", isDaemon = true) {
            try {
                table = assets.open(ASSET).bufferedReader(Charsets.UTF_8).useLines(::parse)
                revisionState.intValue++
            } catch (error: Exception) {
                FileLogger.w(TAG, "手写读音表加载失败：${error.message}")
                loading.set(false)
            }
        }
    }

    internal fun parse(lines: Sequence<String>): Table {
        val shared = HashMap<String, String>()
        val rows = lines.mapNotNull { line ->
            val tab = line.indexOf('\t')
            if (tab <= 0 || tab == line.length - 1 || Character.charCount(line.codePointAt(0)) != tab) return@mapNotNull null
            val reading = line.substring(tab + 1)
            line.codePointAt(0) to shared.getOrPut(reading) { reading }
        }.sortedBy { it.first }.toList()
        return Table(IntArray(rows.size) { rows[it].first }, Array(rows.size) { rows[it].second })
    }

    /** 每个候选一条注释；表没读完时返回空表（不显示注释）。 */
    fun comments(candidates: List<String>): List<String> {
        val loaded = table ?: return emptyList()
        return candidates.map { reading(loaded, it) }
    }

    /**
     * 单字给出全部常用读音；连写的几个字逐字拼读音，但其中有多音字时读哪个查不准，不显示（错读音比没有更糟）。
     */
    internal fun reading(table: Table, text: String): String {
        val readings = mutableListOf<String>()
        var index = 0
        while (index < text.length) {
            val codePoint = text.codePointAt(index)
            readings += table.lookup(codePoint) ?: return ""
            index += Character.charCount(codePoint)
        }
        return when {
            readings.size == 1 -> readings[0]
            readings.any { '/' in it } -> ""
            else -> readings.joinToString(" ")
        }
    }
}
