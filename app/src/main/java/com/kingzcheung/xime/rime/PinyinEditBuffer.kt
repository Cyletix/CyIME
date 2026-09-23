package com.kingzcheung.xime.rime

/** Editing is a draft until Done: closing cannot commit or rewrite the host text. */
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

    fun normalized(text: String): String = text.lowercase().replace('ü', 'v')
        .replace(' ', '\'').filter { it in 'a'..'z' || it == '\'' }

}
