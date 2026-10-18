package com.kingzcheung.xime.association

import java.io.Reader

/**
 * Immutable public prior, separate from writable personal learning. Longest suffix wins.
 * Rows come from the same segmented corpus as the T9 word model (tools/ime_lab/ngram/
 * continuation_build.cc): score = P(continuation | trailing text), one probability scale.
 */
class BaseAssociationModel private constructor(private val rows: Map<String, List<AssociationCandidate>>) {
    fun predict(context: String, topK: Int = 10): List<AssociationCandidate> {
        if (topK <= 0) return emptyList()
        val tail = context.takeLastWhile { it in '一'..'鿿' }.takeLast(4)
        for (size in tail.length downTo 1) {
            rows[tail.takeLast(size)]?.let { return it.take(topK) }
        }
        return emptyList()
    }
    companion object {
        const val ASSET = "association/lccc-base-v1.bin"
        const val MAX_PER_CONTEXT = 16
        const val MAX_CONTEXTS = 60000
        fun read(reader: Reader): BaseAssociationModel {
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
            return BaseAssociationModel(groups.mapValues { (_, value) -> value.sortedByDescending { it.score }.toList() })
        }
    }
}
