package com.kingzcheung.xime.association

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 西班牙语补全词表：[words] 按词频从高到低（assets/spanish/words.txt，tools/spanish/build_words.py 生成）。
 * 打字不用打重音和 ñ：cancion 给出 canción、ano 给出 año；打了重音的那一位只认这个重音。
 */
internal class SpanishLexicon(private val words: List<String>) {
    /** 词的下标按去重音小写排好，前缀查找用二分。 */
    private val keys: Array<String>
    private val byKey: IntArray

    init {
        val folded = Array(words.size) { fold(words[it]) }
        byKey = words.indices.sortedWith(compareBy({ folded[it] }, { it })).toIntArray()
        keys = Array(byKey.size) { folded[byKey[it]] }
    }

    /**
     * 打到一半的 [typed] 可以补成的词，最多 [limit] 个：去掉重音后整词一样的排前面（esta → está），
     * 再按词频排补全。不含与 [typed] 完全相同的词（它已经在候选栏第一个）。首字母大写时候选也大写。
     */
    fun complete(typed: String, limit: Int): List<String> {
        if (typed.isEmpty() || limit <= 0) return emptyList()
        val prefix = fold(typed)
        val lower = typed.lowercase()
        val exact = mutableListOf<Int>()
        val longer = mutableListOf<Int>()
        var position = lowerBound(prefix)
        while (position < keys.size && keys[position].startsWith(prefix)) {
            val index = byKey[position]
            if (keepsTypedAccents(lower, words[index].lowercase())) {
                if (keys[position].length == prefix.length) exact += index else longer += index
            }
            position++
        }
        exact.sort()
        longer.sort()
        return (exact + longer).asSequence().map { cased(words[it], typed) }
            .filter { it != typed }.distinct().take(limit).toList()
    }

    private fun lowerBound(prefix: String): Int {
        var low = 0
        var high = keys.size
        while (low < high) {
            val middle = (low + high) ushr 1
            if (keys[middle] < prefix) low = middle + 1 else high = middle
        }
        return low
    }

    companion object {
        /** 西班牙语在 a–z 之外的字母，长按元音和 ñ 键打出来。 */
        const val EXTRA_LETTERS = "áéíóúüñÁÉÍÓÚÜÑ"

        fun fold(text: String): String = buildString(text.length) {
            for (char in text.lowercase()) append(when (char) {
                'á' -> 'a'; 'é' -> 'e'; 'í' -> 'i'; 'ó', 'ö' -> 'o'; 'ú', 'ü' -> 'u'; 'ñ' -> 'n'
                else -> char
            })
        }

        /** 打了重音（或 ñ、ü）的位置，词在这一位必须是同一个字母；没打重音的位置哪个都行。 */
        private fun keepsTypedAccents(typed: String, word: String): Boolean =
            typed.indices.all { typed[it] == fold(typed[it].toString())[0] || word.getOrNull(it) == typed[it] }

        /** Cancion → Canción，CANC → CANCIÓN；词表里本来大写的（Madrid）保持大写。 */
        fun cased(word: String, typed: String): String = when {
            typed.length > 1 && typed.all { !it.isLetter() || it.isUpperCase() } -> word.uppercase()
            typed.first().isUpperCase() -> word.replaceFirstChar { it.uppercaseChar() }
            else -> word
        }
    }
}

/** 随包西班牙语词表，第一次用到时在 IO 线程读入。 */
internal object SpanishCompletion {
    private const val TAG = "SpanishCompletion"
    private const val ASSET = "spanish/words.txt"
    private val loading = Mutex()
    @Volatile private var lexicon: SpanishLexicon? = null

    suspend fun complete(context: Context, typed: String, limit: Int): List<String> {
        val loaded = lexicon ?: loading.withLock {
            lexicon ?: withContext(Dispatchers.IO) {
                runCatching {
                    SpanishLexicon(context.assets.open(ASSET).bufferedReader().useLines { lines ->
                        lines.map { it.trim() }.filter { it.isNotEmpty() }.toList()
                    })
                }.onFailure { Log.e(TAG, "Spanish word list could not load", it) }.getOrNull()
            }?.also { lexicon = it }
        } ?: return emptyList()
        return withContext(Dispatchers.Default) { loaded.complete(typed, limit) }
    }
}
