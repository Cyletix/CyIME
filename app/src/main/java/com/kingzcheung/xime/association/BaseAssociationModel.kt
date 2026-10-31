package com.kingzcheung.xime.association

import java.io.Reader

/**
 * Immutable public prior, separate from writable personal learning. Longest suffix wins.
 * Rows come from the same segmented corpus as the T9 word model (tools/ime_lab/ngram/
 * continuation_build.cc): score = P(continuation | trailing text), one probability scale.
 *
 * A row either starts the next word (吃饭 → 了) or finishes a word the context only began
 * (莫 → 名其妙, 我 → 们). The second kind is marked by [WORD_COMPLETIONS_ASSET]
 * (tools/ime_lab/ngram/tag_word_completions.py). It fits a bare character the user committed
 * on its own, not the end of a whole phrase: after 「你在吃饭莫」 the 莫 already ends a word.
 */
class BaseAssociationModel private constructor(private val rows: Map<String, Row>) {
    /** Candidates in score order; bit i of [completions] marks candidates[i] as finishing the last word. */
    private class Row(val candidates: List<AssociationCandidate>, private val completions: Int) {
        fun completesWord(index: Int): Boolean = completions and (1 shl index) != 0
        val nextWordShare: Float = candidates.sumOf { it.score.toDouble() }.let { total ->
            if (total <= 0.0) 1f
            else (candidates.withIndex().filterNot { completesWord(it.index) }.sumOf { it.value.score.toDouble() } / total).toFloat()
        }
    }

    /**
     * [afterWholeWord]: the text was committed as a phrase of two or more characters (a sentence,
     * a word, voice), so its last character ends a word. Rows that would continue that word are
     * skipped; when the table knows only the last character of a longer context and that row is
     * almost all word completions (莫 → 名/名其妙/非/斯科…), nothing from it fits and none is given.
     */
    fun predict(context: String, topK: Int = 10, afterWholeWord: Boolean = false): List<AssociationCandidate> {
        if (topK <= 0) return emptyList()
        val tail = context.takeLastWhile { it in '一'..'鿿' }.takeLast(4)
        for (size in tail.length downTo 1) {
            val row = rows[tail.takeLast(size)] ?: continue
            if (!afterWholeWord) return row.candidates.take(topK)
            if (size == 1 && tail.length >= 2 && row.nextWordShare < MIN_NEXT_WORD_SHARE) return emptyList()
            return row.candidates.filterIndexed { index, _ -> !row.completesWord(index) }.take(topK)
        }
        return emptyList()
    }

    companion object {
        const val ASSET = "association/lccc-base-v1.bin"
        const val WORD_COMPLETIONS_ASSET = "association/lccc-base-v1.completions.gz"
        const val WORD_COMPLETIONS_HEADER = "# cyime-base-word-completions-v1"
        const val MAX_PER_CONTEXT = 16
        const val MAX_CONTEXTS = 60000
        /** Backed-off single-character rows whose next-word part is below this share are not used after a whole word. */
        const val MIN_NEXT_WORD_SHARE = 0.15f

        fun read(reader: Reader): BaseAssociationModel = build(reader, emptyMap())

        /** [wordCompletions] from [readWordCompletions] for the same table file. */
        fun readWithWordCompletions(table: Reader, wordCompletions: Map<String, Int>): BaseAssociationModel =
            build(table, wordCompletions)

        private fun build(reader: Reader, masks: Map<String, Int>): BaseAssociationModel {
            val groups = linkedMapOf<String, MutableList<AssociationCandidate>>()
            reader.buffered().useLines { lines -> lines.filterNot { it.startsWith("#") || it.isBlank() }.forEach { line ->
                val fields = line.split('\t')
                require(fields.size == 4)
                val count = fields[2].toInt()
                val total = fields[3].toInt()
                require(fields[0].length in 1..4 && fields[1].length in 1..6 && count > 0 && total >= count)
                val list = groups.getOrPut(fields[0]) { mutableListOf() }
                require(list.size < MAX_PER_CONTEXT && groups.size <= MAX_CONTEXTS)
                list.add(AssociationCandidate(fields[1], count.toFloat() / total))
            } }
            return BaseAssociationModel(groups.mapValues { (context, list) ->
                val fileMask = masks[context] ?: 0
                require(fileMask ushr list.size == 0) { "联想补词标记超出 $context 的行数" }
                // Stable sort, same order as before; marks follow their rows from file order to score order.
                val order = list.indices.sortedByDescending { list[it].score }
                var mask = 0
                order.forEachIndexed { rank, fileIndex -> if (fileMask and (1 shl fileIndex) != 0) mask = mask or (1 shl rank) }
                Row(order.map { list[it] }, mask)
            })
        }

        /** Lines `context\tMASK` after the header; bit i = the i-th row of that context in table file order. */
        fun readWordCompletions(reader: Reader): Map<String, Int> {
            val masks = HashMap<String, Int>()
            reader.buffered().useLines { lines ->
                val iterator = lines.iterator()
                require(iterator.hasNext() && iterator.next().startsWith(WORD_COMPLETIONS_HEADER)) { "联想补词标记文件头不对" }
                iterator.forEach { line ->
                    if (line.isBlank()) return@forEach
                    val fields = line.split('\t')
                    require(fields.size == 2)
                    masks[fields[0]] = fields[1].toInt(16)
                }
            }
            return masks
        }
    }
}
