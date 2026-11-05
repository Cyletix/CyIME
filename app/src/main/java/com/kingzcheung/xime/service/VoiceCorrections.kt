package com.kingzcheung.xime.service

/**
 * 「语音纠错」(别人提的需求，默认关)：用户自己列的「错词 正词」，语音结果上屏前替换一次。
 *
 * - 每行一条；有 =、→、-> 或 Tab 时按它分开(错词里可以带空格)，否则按第一个空格分开。
 * - 从左往右扫，同一位置先配最长的错词；换上去的字不再参与匹配，A→B、B→C 不会连成 A→C。
 * - 英文错词只在整词处替换，不分大小写(cat 不动 category)；中文没有词边界，按原样匹配。
 * - 只在识别器给出的原文上调用一次(预览和定稿同一套)，不会在已上屏的文字上重复替换。
 */
internal class VoiceCorrections private constructor(
    /** 两字以上的错词按前两个字分桶(长的在前)，单字错词另放；几百条同字开头的也只比同一桶。 */
    private val byPrefix: Map<Int, List<Pair<String, String>>>,
    private val single: Map<Char, Pair<String, String>>,
) {
    val size: Int = byPrefix.values.sumOf { it.size } + single.size

    fun apply(text: String): String {
        if (size == 0 || text.isEmpty()) return text
        var out: StringBuilder? = null
        var copied = 0
        var i = 0
        while (i < text.length) {
            val first = text[i].lowercaseChar()
            val hit = (if (i + 1 < text.length) byPrefix[key(first, text[i + 1])]?.firstOrNull { (wrong, _) -> matchesAt(text, i, wrong) } else null)
                ?: single[first]?.takeIf { matchesAt(text, i, it.first) }
            if (hit == null) { i++; continue }
            val sb = out ?: StringBuilder(text.length + 8).also { out = it }
            sb.append(text, copied, i).append(hit.second)
            i += hit.first.length
            copied = i
        }
        return out?.append(text, copied, text.length)?.toString() ?: text
    }

    private fun matchesAt(text: String, at: Int, wrong: String): Boolean {
        if (!text.regionMatches(at, wrong, 0, wrong.length, ignoreCase = wrong.any(::isLatin))) return false
        val end = at + wrong.length
        if (isLatin(wrong.first()) && at > 0 && isLatin(text[at - 1])) return false
        if (isLatin(wrong.last()) && end < text.length && isLatin(text[end])) return false
        return true
    }

    companion object {
        private fun isLatin(c: Char) = c.code < 128 && c.isLetterOrDigit()
        private fun key(a: Char, b: Char) = (a.lowercaseChar().code shl 16) or b.lowercaseChar().code
        private val separators = listOf("->", "→", "=", "\t")

        /** One line → (错词, 正词), or null for a blank, unsplittable or no-op line. */
        internal fun parseLine(line: String): Pair<String, String>? {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) return null
            val sep = separators.mapNotNull { s -> trimmed.indexOf(s).takeIf { it > 0 }?.let { it to s.length } }.minByOrNull { it.first }
                ?: trimmed.indexOfFirst { it.isWhitespace() }.takeIf { it > 0 }?.let { it to 1 }
                ?: return null
            val wrong = trimmed.substring(0, sep.first).trim()
            val right = trimmed.substring(sep.first + sep.second).trim()
            return if (wrong.isEmpty() || right.isEmpty() || wrong == right) null else wrong to right
        }

        fun parse(rules: String): VoiceCorrections {
            // 同一个错词写了两次，后写的算数。
            val pairs = LinkedHashMap<String, String>()
            rules.lineSequence().mapNotNull(::parseLine).forEach { (wrong, right) -> pairs.remove(wrong); pairs[wrong] = right }
            val (longer, short) = pairs.entries.map { it.key to it.value }.partition { it.first.length > 1 }
            val byPrefix = longer.groupBy { key(it.first[0], it.first[1]) }.mapValues { (_, list) -> list.sortedByDescending { it.first.length } }
            return VoiceCorrections(byPrefix, short.associateBy { it.first[0].lowercaseChar() })
        }

        private val EMPTY = VoiceCorrections(emptyMap(), emptyMap())
        @Volatile private var cache: Pair<String, VoiceCorrections> = "" to EMPTY

        /** Parsed once per list text; every partial result reuses it. */
        fun of(rules: String): VoiceCorrections {
            val current = cache
            if (current.first == rules) return current.second
            return parse(rules).also { cache = rules to it }
        }
    }
}
