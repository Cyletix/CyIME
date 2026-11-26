package com.kingzcheung.xime.settings

import com.kingzcheung.xime.service.CandidateAction
import java.io.File
import kotlin.math.ceil

/**
 * 九键拼音下“强行上屏”的原样词记忆。
 *
 * 与 [EnglishWordMemory]（26 键英文单词方案）和 Rime userdb（九键数字码整词）都不同：
 * 这里记的是用户拂动/角标敲出、回车直接上屏的字母串，以及它对应的九键数字码。
 *
 * 次数达到 [Policy.threshold] 才进候选，插在 [Policy.position] 指定的位置；
 * 打到全码的 [Policy.ratioPercent]% 即可前缀召回；`threshold = 0` 表示关闭
 * （已记数据保留，重新打开继续累加）。
 *
 * 文件放在 Rime 用户目录，一行一条：`数字码<TAB>原样词<TAB>次数`，新的在后。
 */
object T9RawWordMemory {
    const val FILE_NAME = "cyime_t9_raw_memory.txt"

    /** 次数档位：0 = 关闭；1、2 太容易误记，不提供。 */
    val THRESHOLD_STEPS = intArrayOf(0, 3, 4, 5, 6, 7, 8, 9)
    /** 默认关：新装用户不受影响，手动把滑块拉到 3 次及以上才启用。 */
    const val DEFAULT_THRESHOLD = 0
    const val MIN_THRESHOLD = 3
    const val MAX_THRESHOLD = 9
    const val DEFAULT_RATIO_PERCENT = 60
    const val MIN_RATIO_PERCENT = 30
    const val MAX_RATIO_PERCENT = 90
    const val RATIO_STEP_PERCENT = 10
    /** 前缀召回至少要打这么多位，避免两位就抢占位置。 */
    const val MIN_PREFIX_DIGITS = 4
    private const val MAX_ENTRIES = 400
    /** 同一轮最多插几个记忆词，避免候选栏被记忆占满。 */
    private const val MAX_INJECTED = 3
    private const val DIGITS = "22233344455566677778889999"
    private val WORD = Regex("[A-Za-z][A-Za-z0-9']{1,39}")
    private val DIGIT_CODE = Regex("[2-9]{2,32}")

    /** 记忆词插入候选的位置。第六位是不翻页时首行大致能看到的最后一格。 */
    enum class Position(val index: Int) {
        TOP(0),
        SECOND(1),
        SIXTH(5),
        ;

        companion object {
            fun of(name: String?): Position = entries.firstOrNull { it.name == name } ?: TOP
        }
    }

    /** 调用方每次从设置里读一遍构造；纯数据，便于单测。 */
    data class Policy(
        val threshold: Int = DEFAULT_THRESHOLD,
        val position: Position = Position.TOP,
        val ratioPercent: Int = DEFAULT_RATIO_PERCENT,
    ) {
        val enabled: Boolean get() = threshold >= 1

        /** 触发前缀召回所需的最短输入长度：全码的 [ratioPercent]%（向上取整），不低于 [MIN_PREFIX_DIGITS]。 */
        fun prefixThreshold(fullLength: Int): Int =
            maxOf(MIN_PREFIX_DIGITS, ceil(fullLength * ratioPercent / 100.0).toInt())
    }

    /** [prefixHit]：当前输入是 [digits] 的短前缀（未打全）时命中。 */
    data class Entry(
        val digits: String,
        val word: String,
        val count: Int,
        val order: Int,
        val prefixHit: Boolean = false,
    )

    data class Injected(
        val texts: List<String>,
        val comments: List<String>,
        val actions: List<CandidateAction>,
    )

    private var cacheKey: String? = null
    private var cacheStamp: Long = -1L
    private var cache: List<Entry> = emptyList()

    /** 与 native 的 kDigitOfLetter 同一张表；只保留字母和已有数字。 */
    fun digitsFor(text: String): String = buildString {
        for (ch in text.lowercase()) {
            when (ch) {
                in 'a'..'z' -> append(DIGITS[ch - 'a'])
                in '2'..'9' -> append(ch)
            }
        }
    }

    /** 只记像 winnative、CS2 这样的原样字母词，不把九键解码出的拼音串算进来。 */
    fun isForcedWord(text: String): Boolean = WORD.matches(text)

    /** 累计一次上屏，返回累计后的次数；非法输入返回 0 且不写文件。 */
    @Synchronized
    fun remember(rimeDir: File, digits: String, word: String): Int {
        if (!DIGIT_CODE.matches(digits) || !isForcedWord(word)) return 0
        val file = File(rimeDir, FILE_NAME)
        val lines = readLines(file)
        val index = lines.indexOfFirst { line ->
            val parts = line.split('\t')
            parts.size == 3 && parts[0] == digits && parts[1].equals(word, ignoreCase = true)
        }
        val count = if (index >= 0) {
            val parts = lines[index].split('\t')
            (parts[2].toIntOrNull() ?: 0) + 1
        } else 1
        if (index >= 0) lines[index] = "$digits\t$word\t$count" else lines.add("$digits\t$word\t$count")
        write(file, lines)
        invalidate(file)
        return count
    }

    /**
     * 当前数字码下够格出现在候选里的词，次数高的、最近打过的在前。
     *
     * 两种命中，都要先达到 [Policy.threshold]：
     * - 精确命中（[Entry.digits] == 输入）；
     * - 前缀命中：输入是其与全码的前缀且长度达到 [Policy.prefixThreshold]。
     *   前缀命中不算“打全”，仍把完整码带给 [inject] 以生成正确的上屏动作。
     */
    @Synchronized
    fun lookup(rimeDir: File, digits: String, policy: Policy = Policy()): List<Entry> {
        if (!policy.enabled || !DIGIT_CODE.matches(digits)) return emptyList()
        return readCached(File(rimeDir, FILE_NAME))
            .mapNotNull { entry ->
                when {
                    entry.digits == digits && entry.count >= policy.threshold -> entry
                    entry.count >= policy.threshold &&
                        digits.length < entry.digits.length &&
                        digits.length >= policy.prefixThreshold(entry.digits.length) &&
                        entry.digits.startsWith(digits) -> entry.copy(prefixHit = true)
                    else -> null
                }
            }
            // 同一前缀可能命中多个词：完整度高的排前，其次次数、最近使用。
            .sortedWith(
                compareByDescending<Entry> { it.digits.length }
                    .thenByDescending { it.count }
                    .thenByDescending { it.order }
            )
    }

    /**
     * 把命中的记忆词插进九键候选：位置取 [Policy.position]，同一轮命中按顺序顺延。
     * 已经在候选里的文本不再插一遍，交给原生排序。
     */
    fun inject(
        texts: List<String>,
        comments: List<String>,
        actions: List<CandidateAction>,
        digits: String,
        entries: List<Entry>,
        policy: Policy = Policy(),
    ): Injected {
        if (!policy.enabled || entries.isEmpty() || !DIGIT_CODE.matches(digits)) {
            return Injected(texts, comments, actions)
        }
        val available = entries.filterNot { it.word in texts }.take(MAX_INJECTED)
        if (available.isEmpty()) return Injected(texts, comments, actions)

        val outTexts = texts.toMutableList()
        val outComments = comments.toMutableList()
        val outActions = actions.toMutableList()
        // 目标位置按插入前的长度取，避免候选不足时多个词被压到末尾后顺序倒过来。
        val base = minOf(policy.position.index, texts.size)
        // 必须从前往后插：目标下标随插入一起后移，块才能连成一段；
        // 倒着插会把前面尚未占用的位置留给原候选（[记忆, 候选0, 记忆, 候选1, ...]）。
        for ((offset, entry) in available.withIndex()) {
            val at = minOf(base + offset, outTexts.size)
            outTexts.add(at, entry.word)
            outComments.add(at, "")
            outActions.add(at, CandidateAction.remembered(entry.word, entry.digits))
        }
        return Injected(outTexts, outComments, outActions)
    }

    private fun readLines(file: File): MutableList<String> =
        (if (file.exists()) compact(file.readLines()) else emptyList()).toMutableList()

    private fun readCached(file: File): List<Entry> {
        val key = file.absolutePath
        val stamp = if (file.exists()) file.lastModified() else -1L
        if (cacheKey == key && cacheStamp == stamp) return cache
        val lines = if (file.exists()) compact(file.readLines()) else emptyList()
        cache = lines.mapIndexed { index, line ->
            val parts = line.split('\t')
            Entry(parts[0], parts[1], parts[2].toIntOrNull() ?: 1, index)
        }
        cacheKey = key
        cacheStamp = stamp
        return cache
    }

    private fun invalidate(file: File) {
        cacheKey = file.absolutePath
        cacheStamp = -1L
    }

    private fun write(file: File, lines: List<String>) {
        val words = compact(lines)
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(words.joinToString("") { "$it\n" })
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }

    private fun compact(lines: List<String>): List<String> {
        val byWord = LinkedHashMap<String, String>()
        for (line in lines) {
            val parts = line.split('\t')
            if (parts.size != 3 || !DIGIT_CODE.matches(parts[0]) || !isForcedWord(parts[1])) continue
            val count = (parts[2].toIntOrNull() ?: 1).coerceAtLeast(1)
            byWord["${parts[0]}\t${parts[1].lowercase()}"] = "${parts[0]}\t${parts[1]}\t$count"
        }
        return byWord.values.toList().takeLast(MAX_ENTRIES)
    }
}
