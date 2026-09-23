package com.kingzcheung.xime.rime

/** Owns an uncommitted composition; editing never rewrites host text. */
data class PinyinEditSession(
    val inputSessionId: Long,
    val schemaId: String,
    val expectedInput: String,
    val protectedInput: String,
    val protectedText: String,
    val text: String,
    val caret: Int,
    val isT9: Boolean,
    val t9RemainingDigits: String = "",
    val t9SelectionCount: Long = 0,
) {
    private val active = java.util.concurrent.atomic.AtomicBoolean(true)
    internal fun invalidate() { active.set(false) }
    internal fun isActive(): Boolean = active.get()
}

internal object PinyinEditBuffer {
    fun supports(schema: String): Boolean = schema in setOf(
        "t9_pinyin", "pinyin_14jian", "rime_ice", "double_pinyin_flypy",
        "luna_pinyin", "luna_pinyin_simp", "pinyin_simp",
    ) || schema.startsWith("double_pinyin")

    fun normalizedT9(text: String): String = text.lowercase().replace('ü', 'v')
        .replace(' ', '\'').filter { it in 'a'..'z' || it in '2'..'9' || it == '\'' }

    fun normalized(text: String): String = text.lowercase().replace('ü', 'v')
        .replace(' ', '\'').filter { it in 'a'..'z' || it == '\'' }

}

/** Render engine syllable boundaries without inserting them into the editable Rime input. */
internal class PinyinEditDisplay(raw: String, preedit: String, isT9: Boolean = false) {
    val text: String
    private val offsets: List<Int>
    init {
        val visible = PinyinEditBuffer.normalized(preedit)
        val boundaries = mutableSetOf<Int>()
        val reading = visible.replace("'", "")
        val input = raw.replace("'", "")
        fun digit(c: Char): Char = when (c) {
            in 'a'..'c' -> '2'; in 'd'..'f' -> '3'; in 'g'..'i' -> '4'
            in 'j'..'l' -> '5'; in 'm'..'o' -> '6'; in 'p'..'s' -> '7'
            in 't'..'v' -> '8'; in 'w'..'z' -> '9'; else -> c
        }
        // Numeric keys remain ambiguous in the engine, but display its actual reading.
        // Literal letters and caret offsets retain their original identity.
        val matches = input.length == reading.length && input.indices.all {
            input[it] == reading[it] || isT9 && input[it] in '2'..'9' && input[it] == digit(reading[it])
        }
        if (matches) {
            var letters = 0
            visible.forEach { if (it == '\'') boundaries.add(letters) else letters++ }
        }
        val mapping = mutableListOf(0)
        text = buildString {
            var letters = 0
            raw.forEachIndexed { index, character ->
                if (character != '\'' && letters in boundaries && isNotEmpty() && last() != '\'') {
                    append('\''); mapping.add(index)
                }
                append(if (matches && character in '2'..'9') reading[letters] else character); mapping.add(index + 1)
                if (character != '\'') letters++
            }
        }
        offsets = mapping
    }
    fun rawOffset(display: Int): Int = offsets[display.coerceIn(0, offsets.lastIndex)]
    fun displayOffset(raw: Int): Int = offsets.indexOfLast { it <= raw }.coerceAtLeast(0)
}
