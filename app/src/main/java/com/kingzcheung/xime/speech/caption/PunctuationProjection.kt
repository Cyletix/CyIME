package com.kingzcheung.xime.speech.caption

private const val MARKS = "、。！？!?,.，"

/**
 * Japanese captions keep the Zipformer words (ReazonSpeech is the more accurate model) and borrow
 * only the punctuation of SenseVoice's reading of the same audio: the two texts are aligned
 * character by character (longest common subsequence) and each 、。？！ is inserted where its
 * neighbour landed. Characters are never added, removed or changed. PC check, FLEURS Japanese:
 * the Zipformer text stays identical while 74 of SenseVoice's 75 marks carry over.
 */
internal fun projectPunctuation(words: String, punctuated: String): String {
    val base = words.filterNot { it.isWhitespace() }
    if (base.isEmpty() || punctuated.isBlank()) return base
    val source = StringBuilder()
    val marks = HashMap<Int, Char>() // after `key` source characters
    for (c in punctuated) {
        when {
            c in MARKS -> marks[source.length] = when (c) { ',', '，', '、' -> '、'; '.', '。' -> '。'; '!' -> '！'; '?' -> '？'; else -> c }
            !c.isWhitespace() -> source.append(c)
        }
    }
    if (marks.isEmpty()) return base
    val n = source.length; val m = base.length
    val lcs = Array(n + 1) { IntArray(m + 1) }
    for (i in n - 1 downTo 0) for (j in m - 1 downTo 0)
        lcs[i][j] = if (source[i] == base[j]) lcs[i + 1][j + 1] + 1 else maxOf(lcs[i + 1][j], lcs[i][j + 1])
    // position in base after the first `i` source characters
    val at = IntArray(n + 1) { -1 }
    at[0] = 0
    var i = 0; var j = 0
    while (i < n && j < m) when {
        source[i] == base[j] -> { i++; j++; at[i] = j }
        lcs[i + 1][j] >= lcs[i][j + 1] -> { i++; if (at[i] < 0) at[i] = j }
        else -> j++
    }
    while (i < n) { i++; if (at[i] < 0) at[i] = m }
    val out = StringBuilder(base)
    var inserted = 0
    for (k in marks.keys.sorted()) {
        val position = (at[k].takeIf { it >= 0 } ?: m) + inserted
        if (position > 0 && out[position - 1] !in MARKS) { out.insert(position, marks.getValue(k)); inserted++ }
    }
    return out.toString()
}
