package com.kingzcheung.xime.settings

/**
 * English words of imported dictionaries (Ame, Team Spirit). The Chinese pack cannot take them: a pack only
 * holds codes its main dictionary already spells. So each keyboard gets its own small English table, typed
 * the way that keyboard's bundled English words are typed, beside the bundled ones:
 * 26 keys like melt_eng, 14 keys like k14_english, nine keys like t9_english.
 */
object CellDictionaryEnglish {
    enum class Kind(val dictionary: String, val translator: String, val options: String) {
        ALPHABETIC("user_cyime_cells_en26", "table_translator",
            "enable_sentence: false, enable_user_dict: false, initial_quality: 1.1, comment_format: [\"xform/.*//\"]"),
        MERGED("user_cyime_cells_en14", "t9_english_translator",
            "enable_completion: false, enable_sentence: false, enable_user_dict: false, initial_quality: -1, " +
                "comment_format: [], completion_limit: 3, completion_keys: \"QETUOADGJZCBlm\""),
        // Nine keys: quality above ordinary Chinese words; cyime_candidate_policy keeps it off the first place.
        T9("user_cyime_cells_en9", "t9_english_translator",
            "enable_completion: false, enable_sentence: false, enable_user_dict: false, initial_quality: 1.0, " +
                "comment_format: [], digit_suffixes: false, completion_limit: 3"),
    }

    const val NAMESPACE = "cyime_cells_en"
    private const val DIGITS = "22233344455566677778889999"
    private const val KEYS14 = "ABCDEDGGUJJlmBOOQEATUCQZTZ"

    fun kind(layout: LayoutKind): Kind? = when (layout) {
        LayoutKind.ALPHABETIC -> Kind.ALPHABETIC
        LayoutKind.MERGED -> Kind.MERGED
        LayoutKind.T9 -> Kind.T9
        else -> null
    }

    /** What is typed for a word on 26 keys: its letters and digits, lowercase. */
    fun letters(text: String): String = text.lowercase(java.util.Locale.ROOT).filter { it in 'a'..'z' || it in '0'..'9' }

    /** The code on [kind]'s keys, or null when that keyboard cannot type it (digits on 14 keys). */
    fun code(kind: Kind, text: String): String? {
        val typed = letters(text).takeIf { it.length >= 2 } ?: return null
        return when (kind) {
            Kind.ALPHABETIC -> typed
            Kind.MERGED -> if (typed.all { it in 'a'..'z' }) typed.map { KEYS14[it - 'a'] }.joinToString("") else null
            Kind.T9 -> typed.map { if (it in 'a'..'z') DIGITS[it - 'a'] else it }.joinToString("")
        }
    }

    fun dictionary(kind: Kind, words: Collection<String>): String = buildString {
        append("# English words of imported dictionaries; managed by CyIME\n---\nname: ${kind.dictionary}\n")
        append("version: \"1\"\nsort: by_weight\n...\n")
        words.distinct().forEach { word -> code(kind, word)?.let { append(word).append('\t').append(it).append("\t100\n") } }
    }

    /** Compilation-only schema, like cyime_k14_english: Rime builds a table's prism through a schema. */
    fun schema(kind: Kind): String = """
        |# Internal compilation dependency of imported English words; not an input mode. Managed by CyIME.
        |schema:
        |  schema_id: ${kind.dictionary}
        |  name: 导入词库英文依赖
        |  version: "1"
        |engine:
        |  processors: [speller, selector, express_editor]
        |  segmentors: [abc_segmentor]
        |  translators: [table_translator]
        |speller:
        |  alphabet: "${if (kind == Kind.MERGED) "QETUOADGJZCBlm" else "zyxwvutsrqponmlkjihgfedcba9876543210"}"
        |  algebra: []
        |translator:
        |  dictionary: ${kind.dictionary}
        |  enable_completion: false
        |  enable_sentence: false
        |""".trimMargin()

    /** Patch lines adding the table to a schema (inside CellDictionaryRime's marked block). */
    fun patchLines(kind: Kind): List<String> = listOf(
        "\"schema/dependencies/+\": [\"${kind.dictionary}\"]",
        // Rime elects among translations pairwise in list order, so on nine keys the table must sit before
        // the bundled English (t9_english, last); appended after it, its words came only after every Chinese one.
        if (kind == Kind.T9) "\"engine/translators/@before last\": \"${kind.translator}@$NAMESPACE\""
        else "\"engine/translators/+\": [\"${kind.translator}@$NAMESPACE\"]",
        "$NAMESPACE: {dictionary: ${kind.dictionary}, ${kind.options}}",
    )

    fun isDependency(schemaId: String): Boolean = Kind.entries.any { it.dictionary == schemaId }
}
