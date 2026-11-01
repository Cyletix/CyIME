package com.kingzcheung.xime.settings

import com.charleskorn.kaml.YamlList
import com.charleskorn.kaml.YamlMap
import com.charleskorn.kaml.YamlScalar
import kotlinx.serialization.json.JsonPrimitive

/** Repairs duplicate append lists in the direct children of a Rime patch. Cell dictionary
 * entries stay inside their markers so disabling the dictionary removes only its translator. */
internal object TranslatorAppendPatch {
    private const val APPEND = "engine/translators/+"
    private val header = Regex("""^( +)(?:"engine/translators/\+"|'engine/translators/\+'|engine/translators/\+):(?:\s|$)""")
    private val patchHeader = Regex("""^patch:\s*(?:#.*)?$""")
    private data class Entry(val line: Int, val end: Int, val indent: String, val managed: Boolean, val values: List<String>)

    fun repair(source: String): String {
        if (source.indexOf(APPEND) == source.lastIndexOf(APPEND)) return source
        val lines = source.split('\n').toMutableList()
        val patch = lines.indexOfFirst { patchHeader.matches(it.removePrefix("\uFEFF").trimEnd('\r')) }
        if (patch < 0) return source
        val end = (patch + 1 until lines.size).firstOrNull { line ->
            lines[line].isNotBlank() && !lines[line].startsWith(" ") && !lines[line].trimStart().startsWith("#")
        } ?: lines.size
        val child = (patch + 1 until end).firstOrNull { lines[it].isNotBlank() && !lines[it].trimStart().startsWith("#") }
            ?: return source
        val indent = lines[child].takeWhile { it == ' ' }
        val headers = (patch + 1 until end).filter { header.find(lines[it])?.groupValues?.get(1) == indent }
        if (headers.size < 2) return source
        val entries = headers.map { i ->
            var until = i + 1
            for (next in i + 1 until end) {
                val line = lines[next]
                if (line.trim() == CellDictionaryRime.START || line.trim() == CellDictionaryRime.END) break
                if (line.isBlank() || line.trimStart().startsWith("#")) continue
                if (!line.startsWith(indent + " ")) break
                until = next + 1
            }
            val raw = lines.subList(i, until).joinToString("\n").trimIndent()
            val map = SchemaManager.yaml.parseToYamlNode(raw) as YamlMap
            val list = map.entries.values.single() as? YamlList ?: error("翻译器追加项不是列表")
            val values = list.items.map { (it as? YamlScalar)?.content ?: error("翻译器名称不是文本") }
            val managed = lines.subList(patch + 1, i).lastOrNull {
                it.trim() == CellDictionaryRime.START || it.trim() == CellDictionaryRime.END
            }?.trim() == CellDictionaryRime.START
            Entry(i, until, indent, managed, values)
        }
        val newline = if (source.contains("\r\n")) "\r" else ""
        fun replace(entry: Entry, line: String) {
            lines[entry.line] = entry.indent + line + newline
            for (i in entry.line + 1 until entry.end) if (!lines[i].trimStart().startsWith("#")) lines[i] = ""
        }
        val personal = entries.filterNot { it.managed }
        if (personal.isNotEmpty()) {
            val values = personal.flatMap { it.values }.distinct()
            replace(personal.first(), "\"$APPEND\": " + values.joinToString(", ", "[", "]") { JsonPrimitive(it).toString() })
            for (extra in personal.drop(1)) for (i in extra.line until extra.end)
                if (!lines[i].trimStart().startsWith("#")) lines[i] = ""
        }
        val cell = entries.filter { it.managed }
        if (cell.isNotEmpty()) {
            require(cell.size == 1 && personal.isNotEmpty() && cell.single().values.size == 1) { "分类词库的翻译器追加项不是单个条目" }
            val slot = listOf("engine/translators/@next", "engine/translators/@after last").firstOrNull { it !in source }
                ?: error("分类词库没有可用的翻译器追加位置")
            replace(cell.single(), "\"$slot\": " + JsonPrimitive(cell.single().values.single()))
        }
        return lines.joinToString("\n").also { SchemaManager.yaml.parseToYamlNode(it) }
    }
}
