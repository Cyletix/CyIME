package com.kingzcheung.xime.rime

/** Uppercase is an internal ambiguous-key token; lowercase always means one exact letter. */
internal object MergedLetterInput {
    val groups = setOf("qw", "er", "ty", "ui", "op", "as", "df", "gh", "jk", "zx", "cv", "bn")

    fun tapValue(group: String, configuredValue: String, enabled: Boolean, shifted: Boolean): String =
        if (enabled && !shifted && group in groups && configuredValue == group.take(1)) group
        else if (shifted) configuredValue.uppercase() else configuredValue

    fun isGroup(value: String, enabled: Boolean): Boolean = enabled && value in groups
}
