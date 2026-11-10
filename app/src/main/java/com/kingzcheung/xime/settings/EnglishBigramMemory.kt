package com.kingzcheung.xime.settings

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Locale

/**
 * English word pairs the user actually typed (my → name, thank → you).
 * One occurrence per line in the Rime user directory, so an append is cheap; past [MAX_LINES] the
 * file is rewritten with counts. The first next-word request loads the file into a small in-memory
 * index, and later commits update that index without re-reading it.
 */
object EnglishBigramMemory {
    const val FILE_NAME = "cyime_en_pairs.txt"
    internal const val MAX_PAIRS = 4000
    internal const val MAX_NEXT_PER_PREVIOUS = 8
    internal const val MAX_LINES = MAX_PAIRS * 4

    private val WORD = Regex("[A-Za-z][A-Za-z']{0,39}")

    private class Cache(
        val file: File,
        var signature: String,
        val rows: HashMap<String, LinkedHashMap<String, Int>>,
        var lines: Int,
    )

    @Volatile private var cache: Cache? = null

    /** [previous]/[next] are kept as typed; lookup is case- and apostrophe-insensitive. */
    @Synchronized
    fun remember(rimeDir: File, previous: String, next: String) {
        val key = normalizeKey(previous) ?: return
        val word = normalizeWord(next) ?: return
        if (key == word.replace("'", "")) return
        val file = File(rimeDir, FILE_NAME)
        val data = load(file)
        val row = data.rows.getOrPut(key) { LinkedHashMap() }
        val count = row.remove(word) ?: 0
        row[word] = count + 1
        while (row.size > MAX_NEXT_PER_PREVIOUS) {
            val oldest = row.keys.firstOrNull() ?: break
            row.remove(oldest)
        }
        data.lines++
        runCatching {
            file.parentFile?.mkdirs()
            file.appendText("$key\t$word\n")
        }
        if (data.lines > MAX_LINES) compact(file, data)
        data.signature = signature(file)
    }

    /** Learned continuations first, most often typed first; then the most recent one. */
    @Synchronized
    fun next(rimeDir: File, previous: String, limit: Int): List<String> {
        if (limit <= 0) return emptyList()
        val key = normalizeKey(previous) ?: return emptyList()
        val row = load(File(rimeDir, FILE_NAME)).rows[key] ?: return emptyList()
        val order = row.keys.withIndex().associate { it.value to it.index }
        return row.entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }
                .thenByDescending { order[it.key] ?: 0 })
            .asSequence().map { display(it.key) }.take(limit).toList()
    }

    @Synchronized
    fun release() {
        cache = null
    }

    private fun load(file: File): Cache {
        val signature = signature(file)
        cache?.let { if (it.file == file && it.signature == signature) return it }
        val rows = HashMap<String, LinkedHashMap<String, Int>>()
        var lines = 0
        if (file.isFile) {
            runCatching {
                file.bufferedReader().useLines { sequence ->
                    for (raw in sequence) {
                        lines++
                        val line = raw.trim()
                        if (line.isEmpty() || line.startsWith("#")) continue
                        val parts = line.split('\t')
                        val count: Int
                        val previous: String
                        val next: String
                        when (parts.size) {
                            2 -> { count = 1; previous = parts[0]; next = parts[1] }
                            3 -> {
                                count = parts[0].toIntOrNull()?.takeIf { it > 0 } ?: continue
                                previous = parts[1]; next = parts[2]
                            }
                            else -> continue
                        }
                        val key = normalizeKey(previous) ?: continue
                        val word = normalizeWord(next) ?: continue
                        val row = rows.getOrPut(key) { LinkedHashMap() }
                        row[word] = (row[word] ?: 0) + count
                        while (row.size > MAX_NEXT_PER_PREVIOUS) row.remove(row.keys.first())
                    }
                }
            }.onFailure { rows.clear(); lines = 0 }
        }
        return Cache(file, signature, rows, lines).also { cache = it }
    }

    private fun compact(file: File, data: Cache) {
        val entries = ArrayList<Triple<String, String, Int>>()
        for ((previous, row) in data.rows) {
            for ((next, count) in row) entries += Triple(previous, next, count)
        }
        val kept = entries.sortedByDescending { it.third }.take(MAX_PAIRS)
        val text = buildString {
            append("# CyIME English word pairs; one count per line. Managed by CyIME.\n")
            for ((previous, next, count) in kept) append(count).append('\t').append(previous).append('\t').append(next).append('\n')
        }
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, "${file.name}.tmp")
        runCatching {
            temp.writeText(text, Charsets.UTF_8)
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE)
        }.onFailure {
            file.delete()
            temp.renameTo(file)
        }
        data.rows.clear()
        for ((previous, next, count) in kept) {
            data.rows.getOrPut(previous) { LinkedHashMap() }[next] = count
        }
        data.lines = kept.size + 1
    }

    private fun signature(file: File): String = "${file.length()}:${file.lastModified()}"

    private fun normalizeKey(value: String): String? {
        val text = value.trim().lowercase(Locale.ROOT)
        if (!WORD.matches(text)) return null
        return text.replace("'", "").takeIf { it.isNotEmpty() }
    }

    private fun normalizeWord(value: String): String? {
        val text = value.trim().lowercase(Locale.ROOT)
        return text.takeIf { WORD.matches(it) }
    }

    private fun display(word: String): String = when {
        word == "i" -> "I"
        word.startsWith("i'") -> "I" + word.drop(1)
        else -> word
    }
}
