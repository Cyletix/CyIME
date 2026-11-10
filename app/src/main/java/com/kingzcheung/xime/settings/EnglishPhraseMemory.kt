package com.kingzcheung.xime.settings

import java.io.File
import java.util.Locale

/**
 * Personal English phrases that are not dictionary words: an email, a handle, a domain, a name
 * with a dot. One per line in the Rime user directory, newest last; lua/cyime_en_phrases.lua
 * offers them as completions in the English words scheme. Plain words stay in
 * [EnglishWordMemory]; this file only takes tokens with @, a dot, a digit or an underscore,
 * so ordinary prose never fills it.
 */
object EnglishPhraseMemory {
    const val FILE_NAME = "cyime_en_phrases.txt"
    internal const val MAX_PHRASES = 500
    private val PHRASE = Regex("[A-Za-z0-9@][A-Za-z0-9@._%+&'\\-]{2,63}")

    internal fun isLearnable(text: String): Boolean {
        val value = text.trim()
        return PHRASE.matches(value) && value.any { it.isLetter() } &&
            (value.contains('@') || value.contains('.') || value.any { it.isDigit() } || value.contains('_'))
    }

    @Synchronized
    fun remember(rimeDir: File, text: String) {
        val value = text.trim()
        if (!isLearnable(value)) return
        val file = File(rimeDir, FILE_NAME)
        runCatching {
            val lines = if (file.exists()) file.readLines() else emptyList()
            if (lines.lastOrNull() == value) return
            if (lines.size >= MAX_PHRASES * 2) {
                write(file, compact(lines + value))
            } else {
                file.parentFile?.mkdirs()
                file.appendText("$value\n")
            }
        }
    }

    internal fun compact(lines: List<String>): List<String> =
        lines.map { it.trim() }.filter(::isLearnable).asReversed()
            .distinctBy { it.lowercase(Locale.ROOT) }.take(MAX_PHRASES).asReversed()

    private fun write(file: File, words: List<String>) {
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(words.joinToString("") { "$it\n" })
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }
}
