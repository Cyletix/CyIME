package com.kingzcheung.xime.settings

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Small, open interchange format for generated/reviewed dictionaries. No executable YAML input. */
object JsonCellDictionary {
    @Serializable
    private data class Entry(val word: String, val code: String)
    @Serializable
    private data class Document(
        val format: String, val name: String, val language: String, val scheme: String,
        val entries: List<Entry>,
    )

    fun parse(bytes: ByteArray): ScelDictionary.Parsed {
        require(bytes.size <= ScelDictionary.MAX_BYTES) { "词库超过 16 MB" }
        val document = Json.decodeFromString<Document>(bytes.toString(Charsets.UTF_8))
        require(document.format == "cyime.dictionary.v1") { "不支持的 JSON 词库格式" }
        require(document.language == "zh" && document.scheme == "pinyin") { "此词库入口仅支持中文拼音词条" }
        require(document.name.isNotBlank() && document.name.length <= 100 && document.name.none { it.isISOControl() }) {
            "词库名称无效"
        }
        require(document.entries.size in 1..ScelDictionary.MAX_ENTRIES) { "词库必须包含 1 至 25 万条词语" }
        val words = Regex("[\u3400-\u4dbf\u4e00-\u9fff]{2,32}")
        val codes = Regex("[a-z]{1,8}( [a-z]{1,8})*")
        val entries = document.entries.map { entry ->
            require(words.matches(entry.word) && codes.matches(entry.code) && entry.code.split(' ').size == entry.word.length) {
                "词条文字或拼音格式有误"
            }
            DictEntry(entry.word, entry.code, 100)
        }.distinctBy { it.word to it.code }
        return ScelDictionary.Parsed(document.name.trim(), entries, 0)
    }
}
