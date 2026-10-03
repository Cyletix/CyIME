package com.kingzcheung.xime.settings

/** Product language, never a keyboard shape or a recognizer. */
enum class InputLanguage(val id: String, val displayName: String) {
    CHINESE("zh", "中文"), JAPANESE("ja", "日语"), ENGLISH("en", "英文"),
    UNSPECIFIED("und", "未标注语言");

    companion object {
        val supported = listOf(CHINESE, JAPANESE, ENGLISH)
        fun fromId(id: String): InputLanguage? = entries.firstOrNull { it.id == id }
        /** Compatibility boundary for stored Rime IDs. New business code uses InputProfile. */
        fun forSchema(id: String): InputLanguage = InputProfiles.describe(id).language
    }
}

/** Rules that turn codes into text. Layout and the particular dictionary are independent. */
enum class InputScheme(val id: String, val displayName: String) {
    PINYIN("pinyin", "拼音"), DOUBLE_PINYIN("double_pinyin", "双拼"),
    WUBI("wubi", "五笔"), WUBI_PINYIN("wubi_pinyin", "五笔拼音混输"), STROKE("stroke", "笔画"),
    ROMAJI("romaji", "罗马字"), KANA("kana", "假名"), DIRECT("direct", "直接输入"),
    EXTERNAL("external", "自定义编码");

    companion object { fun fromId(id: String): InputScheme? = entries.firstOrNull { it.id == id } }
}

enum class InputMode { KEYBOARD, HANDWRITING, VOICE }
enum class LayoutKind { ALPHABETIC, T9, MERGED, KANA_KEYPAD, STROKE_KEYPAD, HANDWRITING, NUMBER }

data class InputLayout(val id: String, val displayName: String, val kind: LayoutKind) {
    companion object {
        val QWERTY = InputLayout("qwerty", "26键", LayoutKind.ALPHABETIC)
        val T9 = InputLayout("t9", "九键", LayoutKind.T9)
        val MERGED14 = InputLayout("qwerty_14", "14键", LayoutKind.MERGED)
        val KANA = InputLayout("japanese_kana", "假名九键", LayoutKind.KANA_KEYPAD)
        val STROKE = InputLayout("stroke", "笔画键盘", LayoutKind.STROKE_KEYPAD)
        val HANDWRITING = InputLayout("handwriting", "手写板", LayoutKind.HANDWRITING)
        val NUMBER = InputLayout("number", "数字键盘", LayoutKind.NUMBER)
    }
}

sealed interface EngineProfile {
    data class Rime(val schemaId: String) : EngineProfile
    data object Direct : EngineProfile
    data object Handwriting : EngineProfile
}

/** Capabilities describe backend semantics, not guessed substrings in a schema ID. */
data class InputCapabilities(
    val editablePinyin: Boolean = false,
    val kanaCase: Boolean = false,
    val japaneseConversion: Boolean = false,
)

data class InputProfile(
    val language: InputLanguage,
    val scheme: InputScheme,
    val layout: InputLayout,
    val engineProfile: EngineProfile,
    val mode: InputMode = InputMode.KEYBOARD,
    val capabilities: InputCapabilities = InputCapabilities(),
) {
    val summary: String get() = if (mode == InputMode.HANDWRITING) "手写" else
        "${scheme.displayName} · ${layout.displayName}"

    /** Entering handwriting retains language and the keyboard scheme to return to. */
    fun handwriting(): InputProfile = copy(layout = InputLayout.HANDWRITING,
        mode = InputMode.HANDWRITING, engineProfile = EngineProfile.Handwriting,
        capabilities = InputCapabilities())
}

/** The only product ↔ built-in backend mapping. No file IO, deployment, or preference writes. */
object InputProfiles {
    private val descriptions = java.util.concurrent.ConcurrentHashMap<String, InputProfile>()
    @Volatile private var installed: Map<String, InputProfile> = emptyMap()
    private val pinyinIds = setOf("rime_ice", "pinyin_simp", "luna_pinyin", "luna_pinyin_simp",
        "luna_pinyin_fluency", "t9", "t9_pinyin", "pinyin_14jian", "pinyin_17jian", "pinyin_18jian")
    private val doublePinyinIds = setOf("double_pinyin", "double_pinyin_flypy", "double_pinyin_flypy_14jian", "double_pinyin_abc",
        "double_pinyin_mspy", "double_pinyin_sogou", "double_pinyin_ziguang", "double_pinyin_pyjj")
    private val wubiIds = setOf("wubi86", "wubi86_trad", "wubi98")
    private val wubiPinyinIds = setOf("wubi86_pinyin", "wubi86_trad_pinyin")

    fun describe(id: String, language: InputLanguage? = null, scheme: InputScheme? = null): InputProfile {
        // Built-in descriptions are reused on every key; custom layouts remain live after edits.
        val base = installed[id] ?: builtin(id)
        val custom = CustomKeyboardLayouts.find(id)
        val actualLayout = custom?.let(::customLayout) ?: base.layout
        if ((language == null || language == base.language) && (scheme == null || scheme == base.scheme) && actualLayout == base.layout) return base
        val actualScheme = scheme ?: base.scheme
        return base.copy(language = language ?: base.language, scheme = actualScheme,
            layout = actualLayout, capabilities = capabilities(actualScheme))
    }

    internal fun builtin(id: String): InputProfile = descriptions.getOrPut(id) { describeBackend(id) }

    /** Publish after discovery, outside the key path. Removing metadata also removes its old meaning. */
    internal fun updateInstalled(entries: List<SchemaMeta>) {
        installed = entries.associate { entry ->
            val base = builtin(entry.schemaId)
            entry.schemaId to base.copy(language = entry.language, scheme = entry.scheme,
                capabilities = capabilities(entry.scheme))
        }
    }

    private fun capabilities(scheme: InputScheme) = InputCapabilities(
        editablePinyin = scheme == InputScheme.PINYIN || scheme == InputScheme.DOUBLE_PINYIN,
        kanaCase = scheme == InputScheme.ROMAJI,
        japaneseConversion = scheme == InputScheme.ROMAJI || scheme == InputScheme.KANA,
    )

    private fun describeBackend(id: String): InputProfile {
        val inputScheme = when {
            id == InputModes.ENGLISH -> InputScheme.DIRECT
            id in pinyinIds || CustomKeyboardLayouts.isCustom(id) -> InputScheme.PINYIN
            id in doublePinyinIds -> InputScheme.DOUBLE_PINYIN
            id in wubiIds -> InputScheme.WUBI
            id in wubiPinyinIds -> InputScheme.WUBI_PINYIN
            id == "stroke" -> InputScheme.STROKE
            id == "japanese" || id == "jaroomaji" -> InputScheme.ROMAJI
            id == "japanese_kana" -> InputScheme.KANA
            else -> InputScheme.EXTERNAL
        }
        val inputLanguage = when {
            id == InputModes.ENGLISH -> InputLanguage.ENGLISH
            inputScheme in setOf(InputScheme.ROMAJI, InputScheme.KANA) -> InputLanguage.JAPANESE
            inputScheme in setOf(InputScheme.PINYIN, InputScheme.DOUBLE_PINYIN, InputScheme.WUBI,
                InputScheme.WUBI_PINYIN, InputScheme.STROKE) || id == "handwriting" -> InputLanguage.CHINESE
            else -> InputLanguage.UNSPECIFIED
        }
        val layout = when {
            id == "t9_pinyin" || id == "t9" -> InputLayout.T9
            id == "pinyin_14jian" || id == "double_pinyin_flypy_14jian" -> InputLayout.MERGED14
            id == "pinyin_17jian" -> InputLayout("qwerty_17", "17键", LayoutKind.MERGED)
            id == "pinyin_18jian" -> InputLayout("qwerty_18", "18键", LayoutKind.MERGED)
            id == "japanese_kana" -> InputLayout.KANA
            id == "stroke" -> InputLayout.STROKE
            id == "handwriting" -> InputLayout.HANDWRITING
            id == "numbers" -> InputLayout.NUMBER
            else -> InputLayout.QWERTY
        }
        return InputProfile(inputLanguage, inputScheme, layout,
            if (id == InputModes.ENGLISH) EngineProfile.Direct else EngineProfile.Rime(id),
            capabilities = capabilities(inputScheme)).let { if (id == "handwriting") it.handwriting() else it }
    }

    private fun customLayout(layout: CustomKeyboardLayout) = InputLayout(layout.id, layout.name,
        if (layout.mergedGroups.isEmpty()) LayoutKind.ALPHABETIC else LayoutKind.MERGED)

    /** Actual layout bindings win over built-in defaults, including an explicitly unbound schema. */
    fun resolveLayout(section: String?, custom: CustomKeyboardLayout? = null): InputLayout = when (section) {
        "qwerty", "qwerty_en", "qwerty_japanese" -> InputLayout(section, "26键", LayoutKind.ALPHABETIC)
        "t9" -> InputLayout.T9
        "stroke" -> InputLayout.STROKE
        "handwriting" -> InputLayout.HANDWRITING
        "japanese_kana" -> InputLayout.KANA
        else -> if (custom != null) customLayout(custom) else when (section) {
            null -> InputLayout.QWERTY
            "qwerty_14" -> InputLayout.MERGED14
            "qwerty_17" -> InputLayout("qwerty_17", "17键", LayoutKind.MERGED)
            "qwerty_18" -> InputLayout("qwerty_18", "18键", LayoutKind.MERGED)
            else -> InputLayout(section, section, LayoutKind.MERGED)
        }
    }

    /** Runtime projection. Legacy schema storage stays at this boundary during migration. */
    fun current(id: String, asciiMode: Boolean = false, language: InputLanguage? = null,
        scheme: InputScheme? = null): InputProfile {
        if (asciiMode || id == InputModes.ENGLISH) return describe(InputModes.ENGLISH)
        val profile = describe(id, language, scheme)
        val layout = resolveLayout(KeysConfigHelper.boundSectionForSchema(id), CustomKeyboardLayouts.find(id))
        return if (layout.kind == LayoutKind.HANDWRITING) profile.handwriting()
            else if (profile.mode == InputMode.HANDWRITING) profile.copy(layout = layout,
                mode = InputMode.KEYBOARD, engineProfile = EngineProfile.Rime(id))
            else if (layout == profile.layout) profile else profile.copy(layout = layout)
    }

    /** A combination only exists when a corresponding installed entry exists. Never invent a schema ID. */
    fun matching(entries: List<SchemaInfo>, language: InputLanguage, scheme: InputScheme,
        layoutId: String): List<SchemaInfo> = entries.filter {
        val profile = it.profile
        profile.language == language && profile.scheme == scheme && profile.layout.id == layoutId
    }
}
