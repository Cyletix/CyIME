package com.kingzcheung.xime.settings

/**
 * 英文的两种方案（11-09）：
 * - 直接输入（[InputModes.ENGLISH]）：母语方案的 ascii 模式，字母逐字上屏，补全另给；
 * - 单词（[InputModes.ENGLISH_WORDS] 这类英文 Rime 方案）：字母先进组字区，从候选里选词上屏，和拼音一样。
 * 语言键、菜单切到英文时由这里决定换到哪个方案；在单词方案上按语言键回到进英文之前的语言。
 */
internal object EnglishSchemes {
    /** 引擎上的这个方案本身就是英文（单词方案），不是靠母语方案的 ascii 模式表示的英文。 */
    fun isEnglishSchema(id: String, entries: List<SchemaInfo> = emptyList()): Boolean =
        id.isNotBlank() && id != InputModes.ENGLISH && InputModes.languageOf(id, entries) == InputLanguage.ENGLISH

    data class Target(val schemaId: String, val asciiMode: Boolean)

    /**
     * 切到英文用哪个方案：[selected] 是已启用的英文方案（单词）就换到它、关 ascii；否则是直接输入，在母语方案上开 ascii。
     * 正在单词方案上改选直接输入时先回到 [nativeSchema] 再开 ascii；没有母语方案可回时返回 null（不切）。
     */
    fun target(entries: List<SchemaInfo>, selected: String?, currentSchema: String, nativeSchema: String?): Target? {
        val words = entries.firstOrNull { it.schemaId == selected && isEnglishSchema(it.schemaId, entries) }
        if (words != null) return Target(words.schemaId, asciiMode = false)
        val base = if (isEnglishSchema(currentSchema, entries)) nativeSchema?.takeUnless { isEnglishSchema(it, entries) }
            else currentSchema
        return base?.takeIf { it.isNotBlank() }?.let { Target(it, asciiMode = true) }
    }

    /**
     * 语言键的「母语」：当前方案是英文单词方案时，是进英文之前的语言 [before]；没记下（刚开机就在单词方案上）时
     * 按语言顺序取第一个能用的非英文语言。其他方案就是方案自己的语言。
     */
    fun nativeLanguage(schemaLanguage: InputLanguage, before: InputLanguage?, order: List<InputLanguage>,
        available: Set<InputLanguage>): InputLanguage {
        if (schemaLanguage != InputLanguage.ENGLISH) return schemaLanguage
        fun native(language: InputLanguage?) = language != null && language != InputLanguage.ENGLISH && language in available
        return before?.takeIf(::native) ?: (order + available).firstOrNull(::native) ?: InputLanguage.ENGLISH
    }
}
