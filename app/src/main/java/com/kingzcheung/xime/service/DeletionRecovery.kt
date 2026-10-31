package com.kingzcheung.xime.service

import android.content.Context
import com.kingzcheung.xime.util.FileLogger
import java.text.Normalizer
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * 删字后给同音字（用户反馈 10-31，别人的需求，默认关）：退格删掉刚才的字后，候选栏先放回删掉的字，后面跟它的同音字，
 * 删错了点第一个补回来，打错了同音字（在/再）直接点对的那个。
 *
 * 候选栏上的这一组只是显示：不挡退格（接着删照常删字，删掉的字接到前面）、不被空格上屏、不进九键选词，
 * 打字、移动光标、上屏后自然换掉。是不是这一组按 [CandidateState.isDeletionRecovery] 判断。
 */
internal object DeletionRecovery {
    /** 连着删超过这么多个字就不再给（按住退格删一大段时不是“删错一个字”）。 */
    const val MAX_RUN = 32
    const val HOMOPHONE_LIMIT = 12

    /**
     * 这一下退格删掉 [deleted] 之后要放回的那一段：接着上一次（[previousRun]，上一次删的还在候选栏上时）往前接。
     * 删的是空白、换行，或者连着删得太长，返回 null（不给）。
     */
    fun nextRun(previousRun: String?, deleted: String): String? {
        if (deleted.isEmpty() || deleted.any { it.isWhitespace() || it.isISOControl() }) return null
        val run = deleted + previousRun.orEmpty()
        return run.takeIf { it.codePointCount(0, it.length) <= MAX_RUN }
    }

    /** 放回删掉的那一段排第一，后面是刚删的那个字的同音字。 */
    fun words(run: String, homophones: List<String>): List<String> =
        (listOf(run) + homophones).filter { it.isNotEmpty() }.distinct()
}

/**
 * 同音字表：字 → 带调读音（assets/handwriting/hanzi_pinyin.tsv），按字频排（assets/handwriting/hanzi_frequency.tsv）。
 * 同音按不带调的音节算（zai：在/再/载/宰），只给最常用的 [COMMON_CHARS] 个字（生僻字、繁体字不出来）。
 * 排序看字频，同调的算三倍常用（是 → 事、市排在时前面）；只有不常用的读音对得上（妞 hào）的往后放。
 */
internal class HomophoneIndex(
    private val readings: Map<Int, List<String>>,
    private val bySyllable: Map<String, IntArray>,
    private val rank: Map<Int, Int>,
) {
    fun homophones(text: String, limit: Int = DeletionRecovery.HOMOPHONE_LIMIT): List<String> {
        if (text.isEmpty() || text.codePointCount(0, text.length) != 1) return emptyList()
        val codePoint = text.codePointAt(0)
        val own = readings[codePoint] ?: return emptyList()
        val toned = own.toSet()
        val syllables = own.map(::toneless).toSet()
        val pool = LinkedHashSet<Int>()
        syllables.forEach { syllable -> bySyllable[syllable]?.forEach(pool::add) }
        pool.remove(codePoint)
        return pool.sortedBy { candidate ->
            val theirs = readings[candidate].orEmpty()
            val sameTone = theirs.any(toned::contains)
            val usualReading = theirs.firstOrNull()?.let { toneless(it) in syllables } == true
            (rank[candidate] ?: COMMON_CHARS).toLong() * (if (sameTone) 1 else 3) * (if (usualReading) 1 else 10)
        }.take(limit).map { String(Character.toChars(it)) }
    }

    companion object {
        /** 字频表前这么多个字算常用字（约等于通用规范汉字表一级字的数目）。 */
        const val COMMON_CHARS = 3500

        fun parse(pinyinLines: Sequence<String>, frequencyLines: Sequence<String>): HomophoneIndex {
            val rank = HashMap<Int, Int>(16384)
            frequencyLines.forEachIndexed { index, line ->
                val tab = line.indexOf('\t')
                if (tab <= 0 || Character.charCount(line.codePointAt(0)) != tab) return@forEachIndexed
                rank.putIfAbsent(line.codePointAt(0), index)
            }
            val readings = HashMap<Int, List<String>>(32768)
            val bySyllable = HashMap<String, MutableList<Int>>()
            for (line in pinyinLines) {
                val tab = line.indexOf('\t')
                if (tab <= 0 || tab == line.length - 1 || Character.charCount(line.codePointAt(0)) != tab) continue
                val codePoint = line.codePointAt(0)
                val own = line.substring(tab + 1).split('/').map(String::trim).filter(String::isNotEmpty)
                if (own.isEmpty()) continue
                readings[codePoint] = own
                if ((rank[codePoint] ?: COMMON_CHARS) < COMMON_CHARS)
                    own.map(::toneless).distinct().forEach { bySyllable.getOrPut(it) { mutableListOf() } += codePoint }
            }
            return HomophoneIndex(readings, bySyllable.mapValues { (_, chars) ->
                chars.sortedBy { rank[it] ?: Int.MAX_VALUE }.toIntArray()
            }, rank)
        }

        /** 去掉声调，保留 ü 的两点（lǜ → lü，不和 lù 混）。 */
        internal fun toneless(reading: String): String =
            Normalizer.normalize(Normalizer.normalize(reading, Normalizer.Form.NFD)
                .filterNot { it in '̀'..'̄' || it == '̌' }, Normalizer.Form.NFC).lowercase()
    }
}

/** 只在开关打开后第一次删字时后台读表，读好之前只放回删掉的字。 */
internal object DeletionHomophones {
    @Volatile private var index: HomophoneIndex? = null
    private val loading = AtomicBoolean(false)

    fun homophones(context: Context, text: String): List<String> {
        index?.let { return it.homophones(text) }
        if (loading.compareAndSet(false, true)) {
            val assets = context.applicationContext.assets
            thread(name = "deletion-homophones", isDaemon = true) {
                try {
                    index = assets.open("handwriting/hanzi_pinyin.tsv").bufferedReader(Charsets.UTF_8).useLines { pinyin ->
                        assets.open("handwriting/hanzi_frequency.tsv").bufferedReader(Charsets.UTF_8).useLines { frequency ->
                            HomophoneIndex.parse(pinyin, frequency)
                        }
                    }
                } catch (error: Exception) {
                    FileLogger.w("DeletionHomophones", "同音字表加载失败：${error.message}")
                    loading.set(false)
                }
            }
        }
        return emptyList()
    }
}
