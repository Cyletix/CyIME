package com.kingzcheung.xime.settings

import android.content.Context

/**
 * 导入的方案没写 `language:`（声笔 sbxm、sbyp 等）时，用户在设置里给它选的语言（GitHub #86）。
 * 只用于方案没声明、CyIME 也不认识的方案；方案自己写了语言时以方案为准。
 * 存在设置里，不改方案文件，也不用重新部署：字母布局、语音、手写都按 [InputProfiles] 里的语言走。
 */
object SchemaLanguageChoice {
    private const val PREFIX = "schema_language_"

    /** 能选的语言。英文和西班牙、德、法、俄、韩语直接打字母、不经过 Rime 方案，不列。 */
    val choices: List<InputLanguage>
        get() = InputLanguage.supported.filter { it != InputLanguage.ENGLISH && !it.typesLettersDirectly }

    /** 读不到设置（安装检查等只给了文件目录的场合）时当作没选。 */
    fun get(context: Context, schemaId: String): InputLanguage? = runCatching {
        SettingsPreferences.getPrefsPublic(context).getString(PREFIX + schemaId, null)
    }.getOrNull()?.let(InputLanguage::fromId)?.takeIf { it in choices }

    /** 存下选择（null 回到「未标注语言」），重新扫一遍方案让键盘用上，并打开这个语言。在 IO 线程调用。 */
    fun choose(context: Context, schemaId: String, language: InputLanguage?) {
        val prefs = SettingsPreferences.getPrefsPublic(context).edit()
        if (language == null) prefs.remove(PREFIX + schemaId) else prefs.putString(PREFIX + schemaId, language.id)
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

    internal fun applyTo(meta: SchemaMeta, chosen: InputLanguage?): SchemaMeta =
        if (meta.languageAssignable && chosen != null && chosen in choices) meta.copy(language = chosen, languageChosen = true)
        else meta
}
