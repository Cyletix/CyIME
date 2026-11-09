package com.kingzcheung.xime.settings

import android.content.Context

/**
 * 导入的方案没写 `language:`（声笔 sbxm、sbyp 等）时，用户在设置里给它选的语言（GitHub #86）。
 * 只用于方案没声明、CyIME 也不认识的方案；方案自己写了语言时以方案为准。
 * 存在设置里，不改方案文件，也不用重新部署：字母布局、语音、手写都按 [InputProfiles] 里的语言走。
 * 没选过时按方案名先猜一个（[guess]），选过的（包括「不指定」）为准。
 */
object SchemaLanguageChoice {
    private const val PREFIX = "schema_language_"

    /** 能选的语言。英文和西班牙、德、法、俄、韩语直接打字母、不经过 Rime 方案，不列。 */
    val choices: List<InputLanguage>
        get() = InputLanguage.supported.filter { it != InputLanguage.ENGLISH && !it.typesLettersDirectly }

    /**
     * 用户选的语言：选了「不指定」是 [InputLanguage.UNSPECIFIED]；没选过、存着不能选的旧值时为 null。
     * 读不到设置（安装检查等只给了文件目录的场合）时当作没选。
     */
    fun get(context: Context, schemaId: String): InputLanguage? = runCatching {
        SettingsPreferences.getPrefsPublic(context).getString(PREFIX + schemaId, null)
    }.getOrNull()?.let(InputLanguage::fromId)?.takeIf { it in choices || it == InputLanguage.UNSPECIFIED }

    /** 存下选择（null 是「不指定」，留在「未标注语言」），重新扫一遍方案让键盘用上，并打开这个语言。在 IO 线程调用。 */
    fun choose(context: Context, schemaId: String, language: InputLanguage?) {
        val prefs = SettingsPreferences.getPrefsPublic(context).edit()
        // 「不指定」也要记下来，不然下次又按方案名猜回中文。
        prefs.putString(PREFIX + schemaId, (language ?: InputLanguage.UNSPECIFIED).id)
        prefs.commit()
        SchemaManager.discoverSchemas(context)
        if (language != null) LanguagePreferences.enableForSchema(context, schemaId)
    }

    /**
     * CyIME 自带或自己生成的方案（英文词典依赖、Easy English Nano、全拼漏键候选等）也没写语言，
     * 但不是用户导入的输入方案，不给选语言，也不列进「导入方案的语言」。
     */
    internal fun resolve(meta: SchemaMeta, appProvided: Boolean, chosen: () -> InputLanguage?): SchemaMeta = when {
        !meta.languageAssignable -> meta
        appProvided -> meta.copy(languageAssignable = false)
        else -> applyTo(meta, chosen())
    }

    /** 方案文件来自 APK（内置资源清单、assets/rime）或由 CyIME 生成。 */
    fun isAppProvided(context: Context, schemaId: String): Boolean =
        com.kingzcheung.xime.rime.RimeConfigHelper.isAppSchema(context, schemaId) || schemaId in rimeAssetSchemaIds(context)

    @Volatile private var rimeAssetSchemaIdsCache: Set<String>? = null
    private fun rimeAssetSchemaIds(context: Context): Set<String> = rimeAssetSchemaIdsCache ?: runCatching {
        context.assets.list("rime").orEmpty().filter { it.endsWith(".schema.yaml") }
            .map { it.removeSuffix(".schema.yaml") }.toSet()
    }.getOrNull()?.also { rimeAssetSchemaIdsCache = it }.orEmpty()

    internal fun applyTo(meta: SchemaMeta, chosen: InputLanguage?): SchemaMeta = when {
        !meta.languageAssignable -> meta
        chosen != null && chosen in choices -> meta.copy(language = chosen, languageChosen = true)
        chosen == InputLanguage.UNSPECIFIED -> meta.copy(language = InputLanguage.UNSPECIFIED, languageChosen = true)
        else -> guess(meta)?.let { meta.copy(language = it) } ?: meta
    }

    /**
     * 没写语言、用户也没选过的导入方案先猜语言：方案名里有汉字、没有假名和谚文、也不像日语方案的，当中文。
     * 以前一律放在「未标注语言」：白霜、万象、虎码这类拼音、形码方案部署好了，中文的方案栏里却没有，
     * 中英切换也切不到，只能长按语言键找「未标注语言」（GitHub #183，用户 11-09「部署的方案还是看不到」）。
     * 猜不出（英文名、假名、谚文、日语方案）时仍是未标注语言。细胞词库按 [SchemaMeta.languageChosen] 判断，不受影响。
     */
    internal fun guess(meta: SchemaMeta): InputLanguage? {
        fun script(c: Char) = Character.UnicodeScript.of(c.code)
        val han = meta.name.any { script(it) == Character.UnicodeScript.HAN }
        val otherScript = (meta.name + meta.schemaId).any {
            script(it) == Character.UnicodeScript.HIRAGANA || script(it) == Character.UnicodeScript.KATAKANA ||
                script(it) == Character.UnicodeScript.HANGUL
        }
        val japanese = JAPANESE_ID.containsMatchIn(meta.schemaId) || JAPANESE_NAMES.any { it in meta.name }
        return if (han && !otherScript && !japanese) InputLanguage.CHINESE else null
    }

    private val JAPANESE_ID = Regex("jap|jp|nihon|roma|kana|mozc|azoo", RegexOption.IGNORE_CASE)
    private val JAPANESE_NAMES = listOf("日语", "日文", "日本", "和文")
}
