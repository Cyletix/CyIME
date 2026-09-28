package com.kingzcheung.xime.association

import java.io.Reader

/** Immutable public prior, separate from writable personal learning. Longest suffix wins. */
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
        fun read(reader: Reader): BaseAssociationModel {
            val groups = linkedMapOf<String, MutableList<AssociationCandidate>>()
            reader.buffered().useLines { lines -> lines.filterNot { it.startsWith("#") || it.isBlank() }.forEach { line ->
                val fields = line.split('\t')
                require(fields.size == 4)
                val count = fields[2].toInt()
                val total = fields[3].toInt()
                require(fields[0].length in 1..4 && fields[1].length in 1..6 && count > 0 && total >= count)
                val list = groups.getOrPut(fields[0]) { mutableListOf() }
                require(list.size < 6 && groups.size <= 40000)
                // Deliberately weak: learned personal choices and a downloaded model can override it.
                list.add(AssociationCandidate(fields[1], 0.25f * count / total))
            } }
            return BaseAssociationModel(groups.mapValues { (_, value) -> value.sortedByDescending { it.score }.toList() })
        }
    }
}
