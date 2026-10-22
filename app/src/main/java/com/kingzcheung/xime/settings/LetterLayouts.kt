package com.kingzcheung.xime.settings

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * One letter arrangement per language for input typed letter by letter: English direct input and
 * Japanese romaji. The choices are the letter layouts Chinese pinyin offers (QWJRTK, Cyletix10,
 * Colemak and the user's own), and rows, names, swipe positions and moved-letter colours all come
 * from that same [CustomKeyboardLayout]. Layouts with merged keys are left out: a two-letter key
 * cannot type one letter directly, and romaji has no decoder that splits it.
 */
object LetterLayouts {
    private const val PREFIX = "letter_layout_"
    private var chosen by mutableStateOf<Map<InputLanguage, String>>(emptyMap())

    fun appliesTo(language: InputLanguage, scheme: InputScheme): Boolean =
        language == InputLanguage.ENGLISH && scheme == InputScheme.DIRECT ||
            language == InputLanguage.JAPANESE && scheme == InputScheme.ROMAJI

    fun typable(layout: CustomKeyboardLayout): Boolean = layout.mergedGroups.isEmpty()

    /** null keeps the language's own 26 keys; a deleted custom layout falls back the same way. */
    fun chosen(language: InputLanguage): CustomKeyboardLayout? =
        chosen[language]?.let(CustomKeyboardLayouts::find)?.takeIf(::typable)

    /** Exactly the letter layouts the Chinese entries offer, plus the one already chosen. */
    fun choices(entries: List<SchemaInfo>, language: InputLanguage): List<CustomKeyboardLayout> =
        (entries.filter { it.language == InputLanguage.CHINESE }.mapNotNull { CustomKeyboardLayouts.find(it.schemaId) } +
            listOfNotNull(chosen(language))).filter(::typable).distinctBy { it.id }

    fun load(context: Context) {
        val prefs = SettingsPreferences.getPrefsPublic(context)
        chosen = InputLanguage.entries.mapNotNull { language ->
            prefs.getString(PREFIX + language.id, null)?.let { language to it }
        }.toMap()
    }

    /** [layoutId] that is not a typable letter layout (the language's own 26 keys) clears the choice. */
    fun select(context: Context, language: InputLanguage, layoutId: String) {
        val id = CustomKeyboardLayouts.find(layoutId)?.takeIf(::typable)?.id
        SettingsPreferences.getPrefsPublic(context).edit().apply {
            if (id == null) remove(PREFIX + language.id) else putString(PREFIX + language.id, id)
        }.apply()
        chosen = if (id == null) chosen - language else chosen + (language to id)
        KeysConfigHelper.applyLetterLayouts()
    }

    /** A layout tap resolved to [entry]; Chinese entries carry their layout in the schema itself. */
    fun selectFor(context: Context, entry: SchemaInfo, layoutId: String) {
        val base = entry.baseProfile
        if (base.layout.kind == LayoutKind.ALPHABETIC && appliesTo(base.language, base.scheme))
            select(context, base.language, layoutId)
    }

    internal fun setForTest(language: InputLanguage, layoutId: String?) {
        chosen = if (layoutId == null) chosen - language else chosen + (language to layoutId)
    }
}
