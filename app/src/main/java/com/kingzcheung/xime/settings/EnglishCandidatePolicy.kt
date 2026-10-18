package com.kingzcheung.xime.settings

/** Upgrade the app's layout-generated schemas without replacing their spelling or dictionary rules. */
internal object EnglishCandidatePolicy {
    fun install(source: String): String {
        if (!Regex("(?m)^ +-[ \\t]+['\"]?table_translator@melt_eng").containsMatchIn(source) ||
            Regex("(?m)^ +-[ \\t]+['\"]?cyime_candidate_policy").containsMatchIn(source)) return source
        val engine = Regex("(?ms)^engine:\\r?\\n.*?(?=^[a-zA-Z_][a-zA-Z_0-9]*:|\\z)").find(source) ?: return source
        val newline = if (engine.value.contains("\r\n")) "\r\n" else "\n"
        val filters = Regex("(?m)^  filters:[ \\t]*\\r?$")
        val updated = if (filters.containsMatchIn(engine.value))
            engine.value.replaceFirst(filters, "  filters:${newline}    - cyime_candidate_policy")
        else engine.value.trimEnd() + "${newline}  filters:${newline}    - cyime_candidate_policy${newline}${newline}"
        return source.replaceRange(engine.range, updated)
    }
}
