package com.kingzcheung.xime.settings

import android.content.Context
import kotlinx.coroutines.launch

object LanguagePreferences {
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)
    const val KEY = "enabled_input_languages"
    fun enabled(context: Context): Set<InputLanguage> {
        val prefs = SettingsPreferences.getPrefsPublic(context)
        val stored = prefs.getStringSet(KEY, null)
        // Imported profiles without language metadata remain usable, without pretending to be Chinese.
        return (if (stored == null) setOf(InputLanguage.CHINESE, InputLanguage.ENGLISH)
        else InputLanguage.entries.filterTo(mutableSetOf()) { it.id in stored } + InputLanguage.ENGLISH) + InputLanguage.UNSPECIFIED
    }
    fun initialize(context: Context) {
        val prefs = SettingsPreferences.getPrefsPublic(context)
        if (prefs.getStringSet(KEY, null)?.contains(InputLanguage.ENGLISH.id) != true) {
            prefs.edit().putStringSet(KEY, enabled(context).map { it.id }.toSet()).commit()
        }
    }
    /** The engine needs a real schema even when English is the only visible language. */
    internal fun nativeSchema(saved: String, available: List<String>, languages: Set<InputLanguage>): String =
        saved.takeIf { it in available && it != InputModes.ENGLISH && InputLanguage.forSchema(it) in languages }
            ?: available.firstOrNull { it.isNotBlank() && it != InputModes.ENGLISH && InputLanguage.forSchema(it) in languages }
            ?: available.firstOrNull { it.isNotBlank() && it != InputModes.ENGLISH }
            ?: "rime_ice"

    /** English always remains available; language switches never delete dictionaries. */
    fun save(context: Context, language: InputLanguage, enabled: Boolean) {
        require(language != InputLanguage.ENGLISH && language != InputLanguage.UNSPECIFIED)
        val next = this.enabled(context).toMutableSet().apply { if (enabled) add(language) else remove(language) }
        if (language == InputLanguage.JAPANESE) {
            val schemas = SchemaManager.getEnabledSchemas(context)
            SchemaManager.setEnabledSchemas(context, if (enabled) (schemas + JapaneseSchemas.ids).distinct()
                else schemas.filterNot { InputLanguage.forSchema(it) == InputLanguage.JAPANESE })
        }
        // The English entry is virtual. Keep a real Rime schema as the engine fallback.
        val current = SettingsPreferences.getCurrentSchema(context)
        if (current == InputModes.ENGLISH || InputLanguage.forSchema(current) !in next) {
            val fallback = nativeSchema(current, SchemaManager.getEnabledSchemas(context), next)
            SettingsPreferences.setCurrentSchema(context, fallback)
        }
        SettingsPreferences.setDeploymentDone(context, false)
        SettingsPreferences.getPrefsPublic(context).edit().putStringSet(KEY, next.map { it.id }.toSet()).commit()
        scope.launch { com.kingzcheung.xime.rime.RimeConfigHelper.prepareAutomatically(context.applicationContext) }
    }
}
