package com.kingzcheung.xime.settings

import android.content.Context

/** Legacy backend-entry ordering/storage. Product semantics live in [InputProfile]. */
object InputModes {
    const val ENGLISH = "__xime_english"
    const val ORDER_KEY = "input_mode_order"
    private const val LANGUAGE_ORDER_KEY = "input_language_order"
    val defaultModeOrder = listOf("rime_ice", "t9_pinyin", "double_pinyin_flypy", "pinyin_14jian", "japanese", "japanese_kana", ENGLISH, QwjrtkLayout.ID)

    fun languageOrder(ids: List<String>): List<InputLanguage> =
        (ids.mapNotNull { id -> InputLanguage.supported.firstOrNull { it.id == id } } + InputLanguage.supported).distinct()

    fun languageOrder(context: Context): List<InputLanguage> = languageOrder(
        SettingsPreferences.getPrefsPublic(context).getString(LANGUAGE_ORDER_KEY, "").orEmpty().lines())
        .filter { it in LanguagePreferences.enabled(context) }

    fun saveLanguageOrder(context: Context, ids: List<String>) {
        val prefs = SettingsPreferences.getPrefsPublic(context)
        val current = languageOrder(prefs.getString(LANGUAGE_ORDER_KEY, "").orEmpty().lines()).map { it.id }
        prefs.edit().putString(LANGUAGE_ORDER_KEY, mergeOrder(current, ids).joinToString("\n")).apply()
    }
    val english = SchemaInfo(ENGLISH, "英文", "", "", "内置英文模式，始终启用", isDownloaded = true)

    fun available(schemas: List<SchemaInfo>, order: List<String> = emptyList()): List<SchemaInfo> {
        val available = schemas.map { it.schemaId }.toSet()
        val visible = schemas.filter { it.schemaId == ENGLISH || CyimeInputDefaults.visibleSchema(it.schemaId, available) }
            .map { it.copy(name = ChineseSchemas.displayName(it.schemaId, it.name)) }
        val unique = (visible + english).distinctBy { it.schemaId }
        val byId = unique.associateBy { it.schemaId }
        val canonicalOrder = order.flatMap { id ->
            if (id == ENGLISH) listOf(id) else CyimeInputDefaults.canonicalIds(listOf(id), available)
        }
        val ordered = canonicalOrder.distinct().mapNotNull { byId[it] }
        val used = ordered.mapTo(mutableSetOf()) { it.schemaId }
        return ordered + unique.filterNot { it.schemaId in used }
            .let { remaining -> if (order.isEmpty()) remaining else remaining.sortedBy { it.schemaId == QwjrtkLayout.ID } }
    }

    fun ordered(context: Context, schemas: List<SchemaInfo>): List<SchemaInfo> =
        available(schemas, SettingsPreferences.getPrefsPublic(context).getString(ORDER_KEY, null)?.lines() ?: defaultModeOrder)

    fun saveOrder(context: Context, ids: List<String>) {
        SettingsPreferences.getPrefsPublic(context).edit()
            .putString(ORDER_KEY, ids.distinct().joinToString("\n")).apply()
    }

    /** Save a reordered visible subset without dropping disabled or uncompiled mode slots. */
    fun saveReorderedModes(context: Context, allModeIds: List<String>, visibleOrder: List<String>,
        reordered: List<String>): Boolean {
        if (reordered == visibleOrder || reordered.size != visibleOrder.size ||
            reordered.toSet() != visibleOrder.toSet()) return false
        val saved = SettingsPreferences.getPrefsPublic(context).getString(ORDER_KEY, null)
            ?.lines()?.filter { it.isNotBlank() } ?: defaultModeOrder
        val fullOrder = (saved + allModeIds + defaultModeOrder + visibleOrder).distinct()
        saveOrder(context, mergeOrder(fullOrder, reordered))
        return true
    }

    fun languageOf(modeId: String, schemas: List<SchemaInfo> = emptyList()): InputLanguage =
        schemas.firstOrNull { it.schemaId == modeId }?.language ?: InputLanguage.forSchema(modeId)

    fun inCurrentLanguage(schemas: List<SchemaInfo>, currentModeId: String): List<SchemaInfo> {
        val modes = available(schemas)
        val language = languageOf(currentModeId, modes)
        return modes.filter { it.language == language }
    }

    /** One entry per language, pointing to that language's last available mode. */
    fun languageChoices(schemas: List<SchemaInfo>, currentModeId: String,
        remembered: Map<InputLanguage, String>, languageOrder: List<InputLanguage> = InputLanguage.entries,
        selectedProfiles: Map<InputLanguage, String> = emptyMap()): List<SchemaInfo> {
        val modes = available(schemas)
        val order = languageOrder + if (modes.any { it.language == InputLanguage.UNSPECIFIED })
            listOf(InputLanguage.UNSPECIFIED) else emptyList()
        return order.distinct().mapNotNull { language ->
            val group = modes.filter { it.language == language }
            val chosen = group.firstOrNull { it.schemaId == selectedProfiles[language] }
                ?: group.firstOrNull { it.schemaId == currentModeId }
                ?: group.firstOrNull { it.schemaId == remembered[language] }
                ?: group.firstOrNull()
            chosen?.copy(name = language.displayName)
        }
    }

    fun selectedProfiles(context: Context): Map<InputLanguage, String> {
        val prefs = SettingsPreferences.getPrefsPublic(context)
        return InputLanguage.entries.mapNotNull { language ->
            prefs.getString("selected_input_profile_${language.id}", null)?.let { language to it }
        }.toMap()
    }

    fun rememberedModes(context: Context, schemas: List<SchemaInfo> = emptyList()): Map<InputLanguage, String> {
        val prefs = SettingsPreferences.getPrefsPublic(context)
        val previous = SettingsPreferences.getCurrentSchema(context)
        return InputLanguage.entries.associateWith { language ->
            prefs.getString("selected_input_profile_${language.id}", null)
                ?: prefs.getString("last_input_mode_${language.id}", null)
                ?: previous.takeIf { languageOf(it, schemas) == language }.orEmpty()
        }
    }

    /** Explicit product choice; passive session persistence must not overwrite this selection. */
    fun selectProfile(context: Context, entry: SchemaInfo) {
        SettingsPreferences.getPrefsPublic(context).edit()
            .putString("selected_input_profile_${entry.profile.language.id}", entry.schemaId).apply()
    }

    fun rememberMode(context: Context, schemaId: String, language: InputLanguage = languageOf(schemaId)) {
        if (schemaId.isBlank() || schemaId == ENGLISH) return
        SettingsPreferences.getPrefsPublic(context).edit()
            .putString("last_input_mode_${language.id}", schemaId).apply()
    }

    /** Reordering one language must not move or discard the other languages' slots. */
    fun mergeOrder(current: List<String>, reordered: List<String>): List<String> {
        val replacements = reordered.distinct().filter { it in current }.iterator()
        val changed = reordered.toSet()
        return current.map { if (it in changed && replacements.hasNext()) replacements.next() else it }
    }

    fun selectedId(schemaId: String, isAsciiMode: Boolean): String = if (isAsciiMode) ENGLISH else schemaId
}
