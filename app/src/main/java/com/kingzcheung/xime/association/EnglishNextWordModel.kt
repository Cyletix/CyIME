package com.kingzcheung.xime.association

import android.content.Context
import com.kingzcheung.xime.glide.EnglishContractions
import java.util.Locale

/**
 * Built-in English next-word table (english_bigrams.txt, the same corpus as glide input).
 * Keeps only the best [MAX_PER_PREVIOUS] continuations of each previous word, so a 5 MB asset
 * becomes a small read-only index; the first English next-word request loads it off the main thread.
 *
 * Scores in the asset are -ln P(next | previous) × 10, so lower is better.
 */
internal class EnglishNextWordModel private constructor(
    private val rows: Map<String, Array<String>>,
) {
    fun predict(previous: String?, limit: Int): List<String> {
        if (limit <= 0) return emptyList()
        val key = EnglishNextWordContext.normalize(previous) ?: return emptyList()
        return rows[key]?.take(limit).orEmpty()
    }

    companion object {
        const val ASSET = "english_bigrams.txt"
        private const val CONTRACTIONS_ASSET = "english_contractions.txt"
        internal const val MAX_PER_PREVIOUS = 8
        private val WORD = Regex("[a-z][a-z']{0,39}")

        fun load(context: Context): EnglishNextWordModel {
            val contractions = runCatching {
                context.assets.open(CONTRACTIONS_ASSET).bufferedReader().useLines { EnglishContractions(it) }
            }.getOrNull()
            fun display(word: String): String = when (word) {
                "i" -> "I"
                else -> contractions?.display(word) ?: word
            }
            return context.assets.open(ASSET).bufferedReader().useLines { lines -> read(lines) { word -> display(word) } }
        }

        internal fun read(lines: Sequence<String>, display: (String) -> String = { it }): EnglishNextWordModel {
            val rows = HashMap<String, MutableList<Entry>>()
            for (raw in lines) {
                val line = raw.trim()
                if (line.isEmpty() || line.startsWith("#")) continue
                val parts = line.split(' ')
                if (parts.size != 3) continue
                val previous = parts[0].lowercase(Locale.ROOT)
                val next = parts[1].lowercase(Locale.ROOT)
                val score = parts[2].toIntOrNull() ?: continue
                if (!WORD.matches(previous) || !WORD.matches(next) || score !in 0..255) continue
                val key = previous.replace("'", "")
                if (key == next.replace("'", "")) continue
                val list = rows.getOrPut(key) { ArrayList(MAX_PER_PREVIOUS) }
                if (list.size == MAX_PER_PREVIOUS && score >= list.last().score) continue
                var at = 0
                while (at < list.size && list[at].score <= score) at++
                list.add(at, Entry(next, score))
                if (list.size > MAX_PER_PREVIOUS) list.removeAt(MAX_PER_PREVIOUS)
            }
            val result = HashMap<String, Array<String>>(rows.size * 2)
            for ((key, list) in rows) {
                val words = LinkedHashSet<String>(list.size)
                for (entry in list) words += display(entry.word)
                result[key] = words.toTypedArray()
            }
            return EnglishNextWordModel(result)
        }

        private class Entry(val word: String, val score: Int)
    }
}
