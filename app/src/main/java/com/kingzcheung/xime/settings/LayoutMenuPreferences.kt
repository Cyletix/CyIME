package com.kingzcheung.xime.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext

/** Layout IDs, independent of scheme order and of the layout currently used for typing. */
internal data class LayoutMenuPreferences(val order: List<String>, val hidden: Set<String>) {
    fun ordered(layouts: List<InputLayout>): List<InputLayout> {
        val byId = layouts.associateBy { it.id }
        return order.mapNotNull(byId::get) + layouts.filterNot { it.id in order }
    }

    fun shown(ordered: List<InputLayout>): List<InputLayout> =
        ordered.filterNot { it.id in hidden }.ifEmpty { ordered.take(1) }

    companion object {
        private fun orderKey(language: InputLanguage) = "layout_menu_order_${language.id}"
        private fun hiddenKey(language: InputLanguage) = "layout_menu_hidden_${language.id}"
        fun read(prefs: SharedPreferences, language: InputLanguage) = LayoutMenuPreferences(
            prefs.getString(orderKey(language), null).orEmpty().lineSequence().filter(String::isNotBlank).distinct().toList(),
            prefs.getStringSet(hiddenKey(language), emptySet()).orEmpty().toSet())

        @Composable
        fun observe(language: InputLanguage): LayoutMenuPreferences {
            val prefs = SettingsPreferences.getPrefsPublic(LocalContext.current)
            var value by remember(prefs, language) { mutableStateOf(read(prefs, language)) }
            DisposableEffect(prefs, language) {
                val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                    if (key == null || key == orderKey(language) || key == hiddenKey(language)) value = read(prefs, language)
                }
                prefs.registerOnSharedPreferenceChangeListener(listener)
                value = read(prefs, language)
                onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
            }
            return value
        }

        @Synchronized fun setVisible(context: Context, language: InputLanguage, layouts: List<InputLayout>, id: String, show: Boolean) {
            if (layouts.none { it.id == id }) return
            val prefs = SettingsPreferences.getPrefsPublic(context)
            val current = read(prefs, language)
            val shown = current.shown(current.ordered(layouts)).map { it.id }
            if (!show && shown == listOf(id)) return
            val hidden = current.hidden - shown.toSet()
            prefs.edit().putStringSet(hiddenKey(language), if (show) hidden - id else hidden + id).apply()
        }

        @Synchronized fun reorder(context: Context, language: InputLanguage, ids: List<String>) {
            val prefs = SettingsPreferences.getPrefsPublic(context)
            val all = (read(prefs, language).order + ids).distinct()
            prefs.edit().putString(orderKey(language), InputModes.mergeOrder(all, ids).joinToString("\n")).apply()
        }
    }
}
