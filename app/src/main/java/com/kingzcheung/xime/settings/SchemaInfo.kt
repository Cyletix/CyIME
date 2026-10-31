package com.kingzcheung.xime.settings

/**
 * Legacy package layout identifiers. Product code uses [InputLayout].
 */
enum class KeyboardLayout(val id: String, val displayName: String) {
    FULL("full", "26键"),
    NINEKEY("ninekey", "九键");

    companion object {
        fun fromId(id: String): KeyboardLayout =
            entries.find { it.id == id } ?: FULL
    }
}

data class SchemaInfo(
    val schemaId: String,
    val name: String,
    val version: String,
    val author: String,
    val description: String,
    val isDownloaded: Boolean = false,
    val needsUpdate: Boolean = false,
    /** 方案声明支持的键盘布局，默认只有全键盘 */
    val supportedLayouts: List<KeyboardLayout> = listOf(KeyboardLayout.FULL),
    /** 展开多布局时，标记此条目对应的布局 ID（null 表示单条目） */
    val displayLayoutId: String? = null,
    val language: InputLanguage = InputLanguage.forSchema(schemaId),
    val scheme: InputScheme = InputProfiles.describe(schemaId).scheme,
) {
    /** Rime/package identity is retained for compatibility, not exposed as an encoding scheme. */
    val profile: InputProfile get() = InputProfiles.arranged(baseProfile)
    /** Before English/Japanese romaji take their language's letter arrangement: the layout is the language's own. */
    val baseProfile: InputProfile get() = if (KeysConfigHelper.hasLoadedLayoutBindings)
        InputProfiles.current(schemaId, language = language, scheme = scheme, arranged = false)
        else InputProfiles.describe(schemaId, language, scheme)
    /** Encoding choices must not inherit legacy keyboard labels such as 中文26键. */
    val schemeLabel: String get() = when (scheme) {
        // 仓颉五代和仓颉速成同一编码类，方案行里按方案名分开
        InputScheme.EXTERNAL, InputScheme.DOUBLE_PINYIN, InputScheme.WUBI, InputScheme.WUBI_PINYIN, InputScheme.CANGJIE ->
            name.ifBlank { schemaId }
        else -> scheme.displayName
    }
    val selectionLabel: String get() = when (scheme) {
        InputScheme.EXTERNAL, InputScheme.DOUBLE_PINYIN, InputScheme.WUBI, InputScheme.WUBI_PINYIN, InputScheme.CANGJIE ->
            "$name · ${profile.layout.displayName}"
        else -> profile.summary
    }
    /** Resource entries represent installed engines: distinguish encoding, actual layout and ownership. */
    val resourceLabel: String get() = when {
        schemaId.startsWith("custom_pinyin_") -> "$name · 自定义"
        schemaId in BuiltinLetterLayouts.ids -> "$name · 内置"
        scheme == InputScheme.DOUBLE_PINYIN -> "$name · ${profile.layout.displayName}"
        else -> name
    }
}
