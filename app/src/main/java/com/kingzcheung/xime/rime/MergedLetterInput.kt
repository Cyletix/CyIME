package com.kingzcheung.xime.rime

/** Uppercase is an internal ambiguous-key token; lowercase always means one exact letter. */
internal object MergedLetterInput {
    val groups = setOf("qw", "er", "ty", "ui", "op", "as", "df", "gh", "jk", "zx", "cv", "bn")

    fun tapValue(group: String, configuredValue: String, enabled: Boolean, shifted: Boolean): String =
        if (enabled && !shifted && group in groups && configuredValue == group.take(1)) group
        else if (shifted) configuredValue.uppercase() else configuredValue

    fun isGroup(value: String, enabled: Boolean): Boolean = enabled && value in groups

    /** One untouched pair, or its explicitly chosen lowercase letter. No suffix or confirmed text is rewritten. */
    fun singleKeyGroup(input: String, caret: Int?, confirmed: Int): String? {
        val key = input.singleOrNull() ?: return null
        if (caret != 1 || confirmed != 0) return null
        return groups.firstOrNull { key == it.first().uppercaseChar() || key in it }
    }
}
