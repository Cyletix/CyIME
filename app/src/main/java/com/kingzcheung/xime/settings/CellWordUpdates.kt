package com.kingzcheung.xime.settings

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Cumulative, data-only vocabulary additions to named built-ins. No schema changes or paths.
 * [main] holds the maintainer's words for the main Chinese dictionary (雾凇 + CyIME, app/data/cyime_domain_terms.tsv
 * and cyime_user_fixes.tsv): they go into the same pack as the cell dictionaries, so a new word or a raised weight
 * reaches every pinyin keyboard without a new APK. A pack can add words and raise weights, never remove or lower one.
 * Published by scripts/dict/publish_word_updates.py.
 */
@Serializable
internal data class CellWordUpdates(val format: String, val revision: Int,
    val dictionaries: Map<String, List<CellPinyinWord>>, val main: List<CellMainWord> = emptyList()) {
    companion object {
        /** Later files may add fields; the format string marks an incompatible change. */
        private val json = Json { ignoreUnknownKeys = true }
        const val ASSET = "cell_dictionaries/word_updates.json"
        const val MAX_BYTES = 512 * 1024
        /** Imported by the cell pack, relative to the Rime directory (no .dict.yaml). */
        const val MAIN_TABLE = "cyime_cells/cyime_main_words"
        val empty = CellWordUpdates("cyime.cell-words.v1", 0, emptyMap())

        fun parse(bytes: ByteArray): CellWordUpdates {
            require(bytes.size <= MAX_BYTES) { "词库更新超过 512 KiB" }
            val update = json.decodeFromString<CellWordUpdates>(bytes.decodeToString(throwOnInvalidSequence = true))
            require(update.format == empty.format && update.revision > 0) { "不支持的词库更新格式或版本" }
            require(update.dictionaries.values.sumOf { it.size } + update.main.size <= 4096) { "词库更新条数过多" }
            require(update.main.map { it.word to it.code }.distinct().size == update.main.size) { "主词库更新有重复条目" }
            update.main.forEach(CellMainWord::validate)
            update.dictionaries.forEach { (id, words) ->
                require(id.matches(Regex("builtin_[a-z0-9]+")) && words.distinct().size == words.size) { "无效或重复的词库更新条目" }
                words.forEach(CellPinyinWord::validate)
            }
            return update
        }
    }

    /** Rime table for [main], imported by the cell pack; null when there is nothing to add. */
    fun mainTable(): String? = if (main.isEmpty()) null else buildString {
        append("# CyIME 词库在线更新 revision ").append(revision).append("，由 scripts/dict/publish_word_updates.py 生成\n")
        append("---\nname: ").append(MAIN_TABLE.substringAfterLast('/')).append("\nversion: \"").append(revision)
        append("\"\nsort: by_weight\n...\n")
        main.forEach { append(it.word).append('\t').append(it.code).append('\t').append(it.weight).append('\n') }
    }

    fun extend(id: String, base: String): String {
        val missing = dictionaries[id]?.toMutableSet() ?: return base
        if (missing.isEmpty()) return base
        // Preserve the bundled reading and frequency when a later APK incorporates a new term.
        base.lineSequence().dropWhile { it != "..." }.drop(1).forEach { line ->
            val first = line.indexOf('\t')
            val second = if (first >= 0) line.indexOf('\t', first + 1) else -1
            if (second > first && first > 0) missing.remove(CellPinyinWord(line.substring(0, first), line.substring(first + 1, second)))
        }
        if (missing.isEmpty()) return base
        return buildString {
            append(base)
            if (!base.endsWith('\n')) append('\n')
            missing.forEach { append(it.word).append('\t').append(it.code).append("\t1200\n") }
        }
    }
}

/** One row of the main dictionary update: added if missing, otherwise ranked by the higher of the two weights. */
@Serializable
internal data class CellMainWord(val word: String, val code: String, val weight: Int) {
    fun validate() {
        require(words.matches(word) && codes.matches(code) && code.count { it == ' ' } + 1 == word.length &&
            weight in 1..MAX_WEIGHT) { "主词库更新条目格式有误：$word" }
    }

    companion object {
        const val MAX_WEIGHT = 10_000_000
        private val words = Regex("[\u3400-\u4dbf\u4e00-\u9fff]{1,32}")
        private val codes = Regex("[a-z]{1,8}( [a-z]{1,8})*")
    }
}
