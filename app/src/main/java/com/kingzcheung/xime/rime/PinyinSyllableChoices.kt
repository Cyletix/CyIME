package com.kingzcheung.xime.rime

/** A real edge in the active schema's prism, in original input-byte coordinates. */
data class PinyinSyllableOption(
    val syllableId: Int,
    val pinyin: String,
    val start: Int,
    val end: Int,
    val selected: Boolean = false,
)

/** A choice belongs to one engine revision and editor session, never just a label. */
data class PinyinSyllableChoices(
    val inputSessionId: Long,
    val schemaId: String,
    val engineRevision: Long,
    val inputText: String,
    val protectedLength: Int,
    val protectedText: String,
    val options: List<PinyinSyllableOption>,
) {
    val selectedIndex: Int get() = options.indexOfFirst { it.selected }

    internal fun matches(schema: String, revision: Long, input: String,
        confirmedLength: Int, confirmedText: String): Boolean =
        schemaId == schema && engineRevision == revision && inputText == input &&
            protectedLength == confirmedLength && protectedText == confirmedText
}

/** Reject malformed native records instead of exposing a non-actionable reading. */
internal fun parsePinyinSyllableOptions(records: Array<String>, input: String,
    protectedLength: Int): List<PinyinSyllableOption> = records.mapNotNull { record ->
    val parts = record.split('\t')
    if (parts.size != 5) return@mapNotNull null
    val id = parts[0].toIntOrNull() ?: return@mapNotNull null
    val start = parts[2].toIntOrNull() ?: return@mapNotNull null
    val end = parts[3].toIntOrNull() ?: return@mapNotNull null
    if (id < 0 || parts[1].isEmpty() || parts[1].any { it !in 'a'..'z' && it != 'ü' } ||
        start < 0 || start < protectedLength || end <= start || end > input.length ||
        input.substring(start, end).any { it.code > 127 } || parts[4] !in setOf("0", "1")) return@mapNotNull null
    PinyinSyllableOption(id, parts[1], start, end, parts[4] == "1")
}.distinctBy { Triple(it.syllableId, it.start, it.end) }
