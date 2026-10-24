package com.kingzcheung.xime.settings

import android.content.Context
import java.io.File

/** Where a word is found and with which codes: built-in dictionary, an imported one, or the user's phrases. */
data class DictionaryHit(val source: String, val word: String, val codes: List<String>)

/** Looks a word up by streaming the dictionary files line by line; nothing is loaded whole into memory. */
object DictionaryLookup {
    fun find(context: Context, word: String): List<DictionaryHit> {
        val target = word.trim()
        if (target.isEmpty()) return emptyList()
        val rimeDir = SchemaManager.getRimeDir(context)
        val hits = mutableListOf<DictionaryHit>()
        builtInTables(rimeDir).forEach { file ->
            codes(file, target).takeIf { it.isNotEmpty() }?.let { hits += DictionaryHit("内置词库", target, it) }
        }
        val store = CellDictionaryManager.store(context)
        val state = store.read()
        state.installed.forEach { entry ->
            val file = runCatching { store.dictionaryFile(entry.id) }.getOrNull() ?: return@forEach
            codes(file, target).takeIf { it.isNotEmpty() }?.let {
                hits += DictionaryHit("导入的词库「${entry.name}」" + if (entry.id in state.applied) "" else "（未开启）", target, it)
            }
        }
        PersonalDictManager.loadCustomPhrases(context, null).filter { it.word == target }
            .takeIf { it.isNotEmpty() }?.let { phrases -> hits += DictionaryHit("我的短语", target, phrases.map { it.code }) }
        // Several built-in tables may hold the same word: one row with all its codes.
        return hits.groupBy { it.source }.map { (source, rows) ->
            DictionaryHit(source, target, rows.flatMap { it.codes }.distinct())
        }
    }

    /** rime_ice.dict.yaml and the tables it imports (cn_dicts/base …), as listed in its import_tables. */
    internal fun builtInTables(rimeDir: File, main: String = "rime_ice"): List<File> {
        val seen = LinkedHashSet<File>()
        fun visit(name: String) {
            val file = File(rimeDir, "$name.dict.yaml")
            if (!file.isFile || !seen.add(file)) return
            importTables(file).forEach(::visit)
        }
        visit(main)
        return seen.toList()
    }

    internal fun importTables(file: File): List<String> {
        val names = mutableListOf<String>()
        var inList = false
        file.useLines { lines ->
            for (line in lines) {
                if (line.trim() == "...") break
                if (line.startsWith("import_tables:")) { inList = true; continue }
                if (inList) {
                    val item = line.trim()
                    when {
                        item.startsWith("- ") -> names += item.removePrefix("- ").substringBefore('#').trim()
                        item.isEmpty() || item.startsWith("#") -> Unit
                        else -> inList = false
                    }
                }
            }
        }
        return names
    }

    /** The codes the word has in a .dict.yaml (text<TAB>code[<TAB>weight] after the `...` header end). */
    internal fun codes(file: File, word: String): List<String> {
        if (!file.isFile) return emptyList()
        val prefix = word + "\t"
        val out = mutableListOf<String>()
        var body = false
        file.useLines { lines ->
            for (line in lines) {
                if (!body) { if (line.trim() == "...") body = true; continue }
                if (line.startsWith(prefix)) line.split('\t').getOrNull(1)?.takeIf { it.isNotBlank() }?.let(out::add)
            }
        }
        return out.distinct()
    }
}
