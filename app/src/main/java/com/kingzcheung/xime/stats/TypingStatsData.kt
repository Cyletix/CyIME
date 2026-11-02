package com.kingzcheung.xime.stats

import com.kingzcheung.xime.rime.CorrectionMark
import com.kingzcheung.xime.rime.CorrectionMarks

/**
 * 打字统计的数据：只有计数，没有打过的任何文字。纯 Kotlin，不碰 Android，电脑上能直接测。
 *
 * 每天一组计数（[Day] 的下标），另有一份累计（不随按天明细裁剪而丢失），
 * 以及时段、输入方式、纠错类型和「想按 x 按成 y」的字母对计数。
 */
class TypingStatsData {
    /** 一组计数的下标。新增只能往后加，旧文件少的列按 0 读。 */
    object Day {
        /** 打出来的字（不含空白、不含粘贴）。 */
        const val CHARS = 0
        /** 粘贴上屏的字，另算，不进打字量。 */
        const val PASTED = 1
        /** 屏幕键盘按键次数（含删除键）。 */
        const val KEYS = 2
        /** 删除键按下次数。 */
        const val DELETES = 3
        /** 点选候选次数。 */
        const val PICKS = 4
        /** 其中选第一个候选的次数。 */
        const val FIRST_PICKS = 5
        /** 选联想词上屏的字。 */
        const val ASSOC_CHARS = 6
        /** 选了输入法纠正过拼写的候选的次数。 */
        const val CORRECTIONS = 7
        /** 连续打字的时间（两次上屏间隔不超过 [TypingStatsData.ACTIVE_GAP_MS] 才算）。 */
        const val ACTIVE_MS = 8
        /** 连续打字时间里打出的字，和 [ACTIVE_MS] 一起算速度。 */
        const val ACTIVE_CHARS = 9
        /** 用屏幕键盘打出的字（不含语音、手写、实体键盘），算码长用。 */
        const val KEY_CHARS = 10
        const val SIZE = 11
    }

    /** 输入方式，顺序就是存储顺序，只能往后加。 */
    enum class Source(val label: String) {
        T9("九键"), PINYIN("全拼"), DOUBLE_PINYIN("双拼"), ENGLISH("英文"),
        VOICE("语音"), HANDWRITING("手写"), OTHER("其他");
    }

    /**
     * 每种「语言 · 方案 · 布局」各自的累计计数下标（不分天）。新增只能往后加，旧文件少的列按 0 读。
     * 用来比较不同布局的速度和使用量，见 [ComboStats]。
     */
    object Combo {
        const val CHARS = 0
        const val ACTIVE_MS = 1
        const val ACTIVE_CHARS = 2
        /** 屏幕键盘按键（含删除键），记在这些键之后上屏的那种输入方式上。 */
        const val KEYS = 3
        const val DELETES = 4
        const val KEY_CHARS = 5
        const val PICKS = 6
        const val FIRST_PICKS = 7
        /** 用过的天数，和最后用的那天（日序号），一起算天数。 */
        const val DAYS = 8
        const val LAST_DAY = 9
        const val SIZE = 10
    }

    /** 一种「语言 · 方案 · 布局」的累计；[label] 是最近一次记录时的名字，只来自内置名称，不含用户输入。 */
    class ComboStats(val id: String, var label: String, val counts: LongArray = LongArray(Combo.SIZE)) {
        val chars: Long get() = counts[Combo.CHARS]
        val days: Long get() = counts[Combo.DAYS]
        fun speed(): Long? = speedOf(counts[Combo.ACTIVE_CHARS], counts[Combo.ACTIVE_MS])
        fun keysPerChar(): Double? {
            val c = counts[Combo.KEY_CHARS]
            if (c < 50) return null
            return (counts[Combo.KEYS] - counts[Combo.DELETES]).coerceAtLeast(0) / c.toDouble()
        }
        fun firstPickRate(): Double? = ratio(counts[Combo.FIRST_PICKS], counts[Combo.PICKS], 20)
        fun deleteRate(): Double? = ratio(counts[Combo.DELETES], counts[Combo.KEYS], 100)
        fun copy() = ComboStats(id, label, counts.copyOf())
    }

    /** 纠错类型，和 CorrectionMark 对应。 */
    enum class Fix(val label: String) { REPLACE("错键"), MISSING("漏键"), EXTRA("多键"), SWAP("颠倒") }

    val days = HashMap<Int, LongArray>()
    val total = LongArray(Day.SIZE)
    val hours = LongArray(24)
    val sources = LongArray(Source.entries.size)
    val fixes = LongArray(Fix.entries.size)
    /** [typed * 26 + intended]：想按 intended，按成了 typed。 */
    val wrongPairs = LongArray(26 * 26)
    /** 最快纪录，字/分钟。 */
    var bestSpeed = 0L
    /** 最多的一天：字数和那天的日序号。 */
    var bestDayChars = 0L
    var bestDay = 0
    /** 键是 [TypingStatsCombo.id]（和 [ComboStats.id] 相同），按第一次出现的顺序。 */
    val combos = LinkedHashMap<String, ComboStats>()

    fun day(index: Int): LongArray = days.getOrPut(index) { LongArray(Day.SIZE) }

    fun add(dayIndex: Int, field: Int, amount: Long) {
        if (amount == 0L) return
        val d = day(dayIndex)
        d[field] += amount
        total[field] += amount
        if (field == Day.CHARS && d[field] > bestDayChars) { bestDayChars = d[field]; bestDay = dayIndex }
    }

    /**
     * 点选了第 [index] 个候选。[correction] 是候选的纠错标记（见 CorrectionMarks，没纠错为空）：
     * 记纠错类型和「想按哪个键按成了哪个键」，不记候选本身。
     */
    fun recordPick(dayIndex: Int, index: Int, correction: String) {
        add(dayIndex, Day.PICKS, 1)
        if (index == 0) add(dayIndex, Day.FIRST_PICKS, 1)
        if (correction.isEmpty()) return
        val marks = runCatching { CorrectionMarks.parse(correction) }.getOrDefault(emptyList())
        if (marks.none { it !is CorrectionMark.Keep && it !is CorrectionMark.Gap }) return
        add(dayIndex, Day.CORRECTIONS, 1)
        for (mark in marks) when (mark) {
            is CorrectionMark.Replace -> {
                fixes[Fix.REPLACE.ordinal]++
                val typed = letter(mark.typed); val intended = letter(mark.correct)
                if (typed >= 0 && intended >= 0) wrongPairs[typed * 26 + intended]++
            }
            is CorrectionMark.Missing -> fixes[Fix.MISSING.ordinal]++
            is CorrectionMark.Extra -> {
                fixes[Fix.EXTRA.ordinal]++
                // 多打的键也算按错；没有「想按的键」，记成它自己，热力图只看按错的那个键。
                val typed = letter(mark.letter)
                if (typed >= 0) wrongPairs[typed * 26 + typed]++
            }
            is CorrectionMark.Swap -> fixes[Fix.SWAP.ordinal]++
            else -> Unit
        }
    }

    /** 取（没有就建）一种输入方式的累计；种类到上限后不再新建，返回 null。 */
    fun combo(id: String, label: String): ComboStats? {
        combos[id]?.let { if (it.label != label) it.label = label; return it }
        if (combos.size >= MAX_COMBOS) return null
        return ComboStats(id, label).also { combos[id] = it }
    }

    /** 记一笔「今天用了这种输入方式」：同一天只算一次。 */
    fun markComboDay(stats: ComboStats, dayIndex: Int) {
        if (stats.counts[Combo.DAYS] > 0 && stats.counts[Combo.LAST_DAY] == dayIndex.toLong()) return
        stats.counts[Combo.DAYS]++
        stats.counts[Combo.LAST_DAY] = dayIndex.toLong()
    }

    /** 打过字的输入方式，字多的在前。 */
    fun comboList(): List<ComboStats> = combos.values.filter { it.chars > 0 }.sortedByDescending { it.chars }

    private fun letter(c: Char): Int = (c.lowercaseChar() - 'a').takeIf { it in 0..25 } ?: -1

    fun isEmpty(): Boolean = total.all { it == 0L }

    /** 把 [other] 的计数加进来（读盘得到的旧数据 + 读盘前已经记下的新计数）。 */
    fun plus(other: TypingStatsData): TypingStatsData {
        val out = copy()
        other.days.forEach { (k, v) -> val d = out.day(k); for (i in v.indices) d[i] += v[i] }
        for (i in total.indices) out.total[i] += other.total[i]
        for (i in hours.indices) out.hours[i] += other.hours[i]
        for (i in sources.indices) out.sources[i] += other.sources[i]
        for (i in fixes.indices) out.fixes[i] += other.fixes[i]
        for (i in wrongPairs.indices) out.wrongPairs[i] += other.wrongPairs[i]
        other.combos.forEach { (id, b) ->
            val a = out.combos[id]
            if (a == null) { out.combos[id] = b.copy(); return@forEach }
            val sameDay = a.days > 0 && b.days > 0 && a.counts[Combo.LAST_DAY] == b.counts[Combo.LAST_DAY]
            for (i in 0 until Combo.SIZE) when (i) {
                Combo.DAYS -> a.counts[i] += b.counts[i] - (if (sameDay) 1 else 0)
                Combo.LAST_DAY -> a.counts[i] = maxOf(a.counts[i], b.counts[i])
                else -> a.counts[i] += b.counts[i]
            }
            a.label = b.label
        }
        out.bestSpeed = maxOf(bestSpeed, other.bestSpeed)
        out.recomputeBestDay()
        if (other.bestDayChars > out.bestDayChars) { out.bestDayChars = other.bestDayChars; out.bestDay = other.bestDay }
        return out
    }

    fun copy(): TypingStatsData {
        val out = TypingStatsData()
        days.forEach { (k, v) -> out.days[k] = v.copyOf() }
        total.copyInto(out.total); hours.copyInto(out.hours); sources.copyInto(out.sources)
        fixes.copyInto(out.fixes); wrongPairs.copyInto(out.wrongPairs)
        out.bestSpeed = bestSpeed; out.bestDayChars = bestDayChars; out.bestDay = bestDay
        combos.forEach { (id, c) -> out.combos[id] = c.copy() }
        return out
    }

    private fun recomputeBestDay() {
        days.forEach { (k, v) -> if (v[Day.CHARS] > bestDayChars) { bestDayChars = v[Day.CHARS]; bestDay = k } }
    }

    /** 按天明细只留最近 [KEEP_DAYS] 天；累计、纪录不受影响。 */
    fun prune(today: Int) {
        days.keys.removeAll { it <= today - KEEP_DAYS || it > today + 1 }
    }

    // ---------- 给页面用的结果 ----------

    fun chars(dayIndex: Int): Long = days[dayIndex]?.get(Day.CHARS) ?: 0L

    /** 连续打字天数：今天或昨天还在打才算连着；今天还没打不算断。 */
    fun streak(today: Int): Int {
        var d = if (chars(today) > 0) today else today - 1
        var n = 0
        while (chars(d) > 0) { n++; d-- }
        return n
    }

    /** 某天的平均速度（字/分钟）；连续打字不到半分钟时没有意义，返回 null。 */
    fun speed(dayIndex: Int): Long? {
        val d = days[dayIndex] ?: return null
        return speedOf(d[Day.ACTIVE_CHARS], d[Day.ACTIVE_MS])
    }

    fun overallSpeed(): Long? = speedOf(total[Day.ACTIVE_CHARS], total[Day.ACTIVE_MS])

    /** 平均码长：一个字按几下键（不算删除键）。字太少时返回 null。 */
    fun keysPerChar(): Double? {
        val chars = total[Day.KEY_CHARS]
        if (chars < 50) return null
        return (total[Day.KEYS] - total[Day.DELETES]).coerceAtLeast(0) / chars.toDouble()
    }

    fun firstPickRate(): Double? = ratio(total[Day.FIRST_PICKS], total[Day.PICKS], 20)

    fun deleteRate(): Double? = ratio(total[Day.DELETES], total[Day.KEYS], 100)

    /** 每个字母被按错（打错或多打）的次数。 */
    fun wrongKeyCounts(): LongArray {
        val out = LongArray(26)
        for (typed in 0 until 26) for (intended in 0 until 26) out[typed] += wrongPairs[typed * 26 + intended]
        return out
    }

    /** 最常见的「想按 intended 按成 typed」：(typed, intended, 次数)，按次数从多到少。 */
    fun topWrongPairs(limit: Int): List<Triple<Char, Char, Long>> = wrongPairs.indices
        .filter { wrongPairs[it] > 0 }
        .sortedByDescending { wrongPairs[it] }
        .take(limit)
        .map { Triple('a' + it / 26, 'a' + it % 26, wrongPairs[it]) }

    // ---------- 存取 ----------

    /** 一行一项的纯文本：`键=数,数,…`。没有文字内容，只有数字。 */
    fun encode(): String = buildString {
        append("v=1\n")
        append("total=").append(total.joinToString(",")).append('\n')
        append("hours=").append(hours.joinToString(",")).append('\n')
        append("sources=").append(sources.joinToString(",")).append('\n')
        append("fixes=").append(fixes.joinToString(",")).append('\n')
        append("pairs=").append(wrongPairs.indices.filter { wrongPairs[it] > 0 }
            .joinToString(",") { "$it:${wrongPairs[it]}" }).append('\n')
        append("best=").append(bestSpeed).append(',').append(bestDayChars).append(',').append(bestDay).append('\n')
        // c/<id>=计数;名字。id 只有字母数字和 /_.-，名字是内置名称。
        combos.forEach { (id, c) ->
            append("c/").append(id).append('=').append(c.counts.joinToString(",")).append(';')
                .append(c.label.replace('\n', ' ').replace('\r', ' ')).append('\n')
        }
        days.keys.sorted().forEach { k -> append('d').append(k).append('=').append(days.getValue(k).joinToString(",")).append('\n') }
    }

    companion object {
        const val KEEP_DAYS = 400
        /** 两次上屏间隔超过这么久算停下来了，不进打字时间。 */
        const val ACTIVE_GAP_MS = 8_000L
        /** 输入方式种类的上限，防止异常情况下文件无限变大。 */
        const val MAX_COMBOS = 64

        fun speedOf(chars: Long, ms: Long): Long? = if (ms < 30_000 || chars <= 0) null else chars * 60_000 / ms

        internal fun ratio(part: Long, whole: Long, minWhole: Long): Double? =
            if (whole < minWhole) null else part.toDouble() / whole

        /** 读不懂的行跳过；整份坏了就当没有统计，不影响打字。 */
        fun decode(text: String): TypingStatsData {
            val out = TypingStatsData()
            for (line in text.lineSequence()) {
                val eq = line.indexOf('=')
                if (eq <= 0) continue
                val key = line.substring(0, eq)
                val value = line.substring(eq + 1)
                runCatching {
                    when {
                        key == "total" -> fill(out.total, value)
                        key == "hours" -> fill(out.hours, value)
                        key == "sources" -> fill(out.sources, value)
                        key == "fixes" -> fill(out.fixes, value)
                        key == "pairs" -> value.split(',').filter { it.isNotEmpty() }.forEach {
                            val (i, n) = it.split(':')
                            val index = i.toInt()
                            if (index in out.wrongPairs.indices) out.wrongPairs[index] = n.toLong()
                        }
                        key == "best" -> value.split(',').let {
                            out.bestSpeed = it.getOrNull(0)?.toLongOrNull() ?: 0
                            out.bestDayChars = it.getOrNull(1)?.toLongOrNull() ?: 0
                            out.bestDay = it.getOrNull(2)?.toIntOrNull() ?: 0
                        }
                        key.startsWith("c/") -> {
                            val id = key.substring(2)
                            val semi = value.indexOf(';')
                            val numbers = if (semi < 0) value else value.substring(0, semi)
                            val label = if (semi < 0) id else value.substring(semi + 1)
                            if (id.isNotEmpty() && out.combos.size < MAX_COMBOS) {
                                val c = ComboStats(id, label)
                                fill(c.counts, numbers)
                                out.combos[id] = c
                            }
                        }
                        key.startsWith("d") -> fill(out.day(key.substring(1).toInt()), value)
                    }
                }
            }
            return out
        }

        private fun fill(target: LongArray, value: String) {
            value.split(',').forEachIndexed { i, s -> if (i < target.size) target[i] = s.toLongOrNull() ?: 0 }
        }
    }
}
