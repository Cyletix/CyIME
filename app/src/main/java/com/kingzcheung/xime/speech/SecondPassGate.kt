package com.kingzcheung.xime.speech

import java.io.File
import java.text.Normalizer

/**
 * 二次校正把关：第二遍（SenseVoice / X-ASR）只在它改动的汉字通过词组模型检查时才替换第一遍（流式预览）。
 *
 * 两遍文字按字对齐，逐处看第二遍改了哪些字。同样字数、只改汉字的地方，在左右各 [CONTEXT] 个字的上下文里，
 * 用九键词组模型（词三元，按最好的分词）给两种写法打分：第二遍多出模型没见过的词，或分数比第一遍低（低过
 * [MARGIN]），这一处就换回第一遍的字。数字、英文、增字漏字照旧采用第二遍。第二遍的标点和断句保持不变。
 * 词组模型没装时不把关（与以前相同）。
 */
internal class SecondPassGate(private val model: WordLanguageModel) {
    /** 第二遍文字，被否决的改动换回第一遍的字。 */
    fun merge(first: String, second: String): String {
        if (first.isBlank() || second.isBlank()) return second
        val f = units(first); val s = units(second)
        val changes = diffRegions(f.keys, s.keys)
        if (changes.isEmpty()) return second
        val keepFirst = changes.filter { r -> !acceptSecond(f.keys, s.keys, r) }
        if (keepFirst.isEmpty()) return second
        val out = StringBuilder(); var pos = 0
        for (r in keepFirst) {
            val (a0, a1) = if (r.s1 > r.s0) s.at[r.s0] to s.at[r.s1 - 1] + 1
                else (if (r.s0 < s.at.size) s.at[r.s0] else trailingMarks(second)).let { it to it }
            out.append(second, pos, a0)
            if (r.f1 > r.f0) out.append(first, f.at[r.f0], f.at[r.f1 - 1] + 1)
            pos = a1
        }
        out.append(second, pos, second.length)
        return out.toString()
    }

    private fun acceptSecond(f: List<String>, s: List<String>, r: Region): Boolean {
        val firstSpan = f.subList(r.f0, r.f1); val secondSpan = s.subList(r.s0, r.s1)
        // 数字（SenseVoice 的数字转写）、英文：没有可比的词，信第二遍。
        if ((firstSpan + secondSpan).any { !isHan(it) }) return true
        // 增字、漏字：字数听第二遍的（离线模型数字数比流式准；只比同样字数的写法）。
        if (firstSpan.size != secondSpan.size) return true
        val left = s.subList(maxOf(0, r.s0 - CONTEXT), r.s0).filter(::isHan).joinToString("")
        val right = s.subList(r.s1, minOf(s.size, r.s1 + CONTEXT)).filter(::isHan).joinToString("")
        val a = score(left + secondSpan.joinToString("") + right)
        val b = score(left + firstSpan.joinToString("") + right)
        return a.unseen <= b.unseen && a.logp >= b.logp - MARGIN
    }

    internal class Score(val logp: Double, val unseen: Int)

    /** 最好分词下的对数概率（词三元，按前两个词为状态的束搜索）和其中没见过的字数。 */
    internal fun score(text: String): Score {
        val n = text.length
        if (n == 0) return Score(0.0, 0)
        val ids = HashMap<String, Int>()
        fun id(w: String) = ids.getOrPut(w) { model.id(w) }
        // state = (word before last, last word); START marks the beginning of the window.
        val states = Array(n + 1) { HashMap<Long, DoubleArray>() }
        states[0][pack(START, START)] = doubleArrayOf(0.0, 0.0)
        for (i in 0 until n) {
            val here = states[i]
            if (here.isEmpty()) continue
            val best = here.entries.sortedByDescending { it.value[0] }.take(BEAM)
            for (len in 1..minOf(MAX_WORD, n - i)) {
                val w = id(text.substring(i, i + len))
                if (w < 0 && len > 1) continue
                for ((key, v) in best) {
                    val h2 = (key ushr 32).toInt(); val h1 = key.toInt()
                    val lp = when {
                        h1 == START -> model.unigram(w)
                        h2 == START -> model.bigram(h1, w)
                        else -> model.trigram(h2, h1, w)
                    } + if (w < 0) UNSEEN_PENALTY else 0.0
                    val next = pack(h1, w)
                    val total = v[0] + lp
                    val old = states[i + len][next]
                    if (old == null || total > old[0]) states[i + len][next] = doubleArrayOf(total, v[1] + if (w < 0) 1 else 0)
                }
            }
        }
        val end = states[n].values.maxByOrNull { it[0] } ?: return Score(-1e9, n)
        return Score(end[0], end[1].toInt())
    }

    private fun pack(h2: Int, h1: Int) = (h2.toLong() shl 32) or (h1.toLong() and 0xffffffffL)

    companion object {
        /** 上下文字数和分数余量：2026-11-05 评论句带噪合成语音测评选定（docs/development/asr-second-pass-gate.md）。 */
        const val CONTEXT = 4
        const val MARGIN = 0.0
        private const val BEAM = 4
        private const val MAX_WORD = 6
        private const val UNSEEN_PENALTY = -4.0
        private const val START = -2

        @Volatile private var cached: Pair<String, SecondPassGate>? = null

        /** rime/t9_ngram.lm（九键词组模型，完整或精简版）；没装或读不了时为 null，二次校正照旧。 */
        fun forFiles(filesDir: File): SecondPassGate? {
            val file = File(filesDir, "rime/t9_ngram.lm")
            if (!file.isFile) return null
            val key = "${file.absolutePath}:${file.length()}:${file.lastModified()}"
            cached?.let { if (it.first == key) return it.second }
            return runCatching { SecondPassGate(WordNgram.open(file)) }.getOrNull()?.also { cached = key to it }
        }
    }
}

/** Letters and digits of [text] (NFKC, lower case) with their index in [text]. */
private class Units(val keys: List<String>, val at: IntArray)

private fun units(text: String): Units {
    val keys = ArrayList<String>(text.length); val at = ArrayList<Int>(text.length)
    var i = 0
    while (i < text.length) {
        val cp = text.codePointAt(i)
        val width = Character.charCount(cp)
        val k = Normalizer.normalize(String(Character.toChars(cp)), Normalizer.Form.NFKC).lowercase()
        if (k.isNotEmpty() && Character.isLetterOrDigit(k.codePointAt(0))) { keys.add(k); at.add(i) }
        i += width
    }
    return Units(keys, at.toIntArray())
}

private fun isHan(unit: String): Boolean = unit.length == 1 && unit[0] in '㐀'..'鿿'

/** Index of the first trailing sentence mark, so an insertion at the very end goes before 「。」. */
private fun trailingMarks(text: String): Int {
    var end = text.length
    while (end > 0 && (text[end - 1] in "，。？！、；：,.?!;: ")) end--
    return end
}

internal data class Region(val f0: Int, val f1: Int, val s0: Int, val s1: Int)

/** Maximal differing spans between [a] (first pass) and [b] (second pass), from one Levenshtein alignment. */
internal fun diffRegions(a: List<String>, b: List<String>): List<Region> {
    val n = a.size; val m = b.size
    val d = Array(n + 1) { IntArray(m + 1) }
    for (i in 0..n) d[i][0] = i
    for (j in 0..m) d[0][j] = j
    for (i in 1..n) for (j in 1..m)
        d[i][j] = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
    val ops = CharArray(n + m); var k = 0
    var i = n; var j = m
    while (i > 0 || j > 0) {
        if (i > 0 && j > 0 && d[i][j] == d[i - 1][j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1) {
            ops[k++] = if (a[i - 1] == b[j - 1]) '=' else 'S'; i--; j--
        } else if (i > 0 && d[i][j] == d[i - 1][j] + 1) { ops[k++] = 'D'; i-- }
        else { ops[k++] = 'I'; j-- }
    }
    val out = mutableListOf<Region>()
    var ai = 0; var bi = 0; var startA = -1; var startB = -1
    for (x in k - 1 downTo 0) {
        val op = ops[x]
        if (op == '=') {
            if (startA >= 0) { out += Region(startA, ai, startB, bi); startA = -1 }
            ai++; bi++; continue
        }
        if (startA < 0) { startA = ai; startB = bi }
        if (op == 'S' || op == 'D') ai++
        if (op == 'S' || op == 'I') bi++
    }
    if (startA >= 0) out += Region(startA, ai, startB, bi)
    return out
}
