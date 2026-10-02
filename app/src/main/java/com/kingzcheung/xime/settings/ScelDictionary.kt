package com.kingzcheung.xime.settings

import java.io.InputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/** Reads the two common desktop SCEL variants. Source bytes are data, never YAML or paths. */
object ScelDictionary {
    const val MAX_BYTES = 16 * 1024 * 1024
    const val MAX_ENTRIES = 250_000
    data class Parsed(val name: String, val entries: List<DictEntry>, val skipped: Int)

    fun readBounded(input: InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            require(output.size() + count <= MAX_BYTES) { "词库超过 16 MB，请选择较小的词库" }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    fun parse(bytes: ByteArray): Parsed {
        require(bytes.size in 0x2628..MAX_BYTES && bytes.take(4) == listOf<Byte>(0x40, 0x15, 0, 0) &&
            bytes[5] == 0x43.toByte() && bytes[6] == 0x53.toByte() && bytes[7] == 1.toByte()) {
            "文件不是支持的搜狗 SCEL 词库，可能是下载失败或网页内容"
        }
        val wordStart = when (bytes[4].toInt() and 255) {
            0x44 -> 0x2628
            0x45 -> 0x26c4
            else -> error("暂不支持这个 SCEL 版本")
        }
        fun decode(start: Int, length: Int): String {
            require(length % 2 == 0 && start >= 0 && length <= bytes.size - start) { "词库数据不完整" }
            return Charsets.UTF_16LE.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes, start, length)).toString()
        }
        fun u16(at: Int): Int {
            require(at >= 0 && at + 2 <= bytes.size) { "词库数据不完整" }
            return (bytes[at].toInt() and 255) or ((bytes[at + 1].toInt() and 255) shl 8)
        }
        val name = decode(0x130, 0x208).substringBefore('\u0000').trim().take(100)
        val syllables = mutableMapOf<Int, String>()
        var at = 0x1544 // four-byte pinyin-table header
        while (at + 4 <= wordStart) {
            val index = u16(at)
            val length = u16(at + 2)
            if (length == 0) break // padding before the word table
            require(length in 2..16 && at + 4 + length <= wordStart) { "词库拼音表损坏" }
            val syllable = decode(at + 4, length).lowercase().replace('ü', 'v')
            require(syllable.matches(Regex("[a-z]{1,8}")) && index !in syllables) { "词库拼音表损坏" }
            syllables[index] = when (syllable) { "lue" -> "lve"; "nue" -> "nve"; else -> syllable }
            at += 4 + length
        }
        require(syllables.isNotEmpty() && bytes.size > wordStart) { "词库没有拼音或词条" }
        at = wordStart
        val entries = linkedMapOf<Pair<String, String>, DictEntry>()
        var total = 0
        var skipped = 0
        while (at < bytes.size) {
            val count = u16(at)
            val codeBytes = u16(at + 2)
            at += 4
            require(count > 0 && codeBytes > 0 && codeBytes % 2 == 0 && codeBytes <= 256) { "词库词条结构损坏" }
            val code = (0 until codeBytes step 2).joinToString(" ") { offset ->
                syllables[u16(at + offset)] ?: error("词库引用了不存在的拼音")
            }
            at += codeBytes
            repeat(count) {
                require(++total <= MAX_ENTRIES) { "词库超过 25 万条，请拆分后导入" }
                val length = u16(at)
                val word = decode(at + 2, length)
                at += 2 + length
                val extraLength = u16(at)
                at += 2
                require(extraLength >= 2 && extraLength <= bytes.size - at) { "词库数据不完整" }
                at += extraLength
                // SCEL's extra field is not a calibrated Rime weight. Keep supplemental terms modest.
                if (word.isBlank() || word.length > 128 || word.startsWith('#') ||
                    word.any { it.isISOControl() } || word.contains('\uFFFD')) {
                    skipped++
                } else {
                    entries.putIfAbsent(word to code, DictEntry(word, code, 100))
                }
            }
        }
        require(entries.isNotEmpty()) { "词库没有可用词条" }
        return Parsed(name, entries.values.toList(), skipped)
    }

    fun toRime(name: String, entries: List<DictEntry>): String {
        require(name.matches(Regex("[a-z0-9_]+")))
        return buildString {
            append("# Imported SCEL dictionary; managed by CyIME\n---\nname: $name\nversion: \"1\"\nsort: by_weight\nuse_preset_vocabulary: false\n...\n")
            entries.forEach { append(it.word).append('\t').append(it.code).append("\t100\n") }
        }
    }
}
