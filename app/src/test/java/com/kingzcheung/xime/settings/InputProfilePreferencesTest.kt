package com.kingzcheung.xime.settings

import android.content.Context
import android.content.SharedPreferences
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.*

class InputProfilePreferencesTest {
    @Test fun `explicit selection survives passive session saves and legacy users retain their memory`() {
        val context = mock<Context>()
        val prefs = mock<SharedPreferences>()
        val editor = mock<SharedPreferences.Editor>()
        val values = mutableMapOf("last_input_mode_zh" to "t9_pinyin", "last_input_mode_ja" to "japanese_kana")
        whenever(context.getSharedPreferences(any(), any())).thenReturn(prefs)
        whenever(prefs.getString(any(), anyOrNull())).thenAnswer { values[it.getArgument<String>(0)] ?: it.getArgument<String?>(1) }
        whenever(prefs.edit()).thenReturn(editor)
        whenever(editor.putString(any(), anyOrNull())).thenAnswer {
            val value = it.getArgument<String?>(1)
            if (value != null) values[it.getArgument(0)] = value
            editor
        }
        assertEquals("t9_pinyin", InputModes.rememberedModes(context)[InputLanguage.CHINESE])
        InputModes.selectProfile(context, SchemaInfo("rime_ice", "26键", "", "", ""))
        InputModes.rememberMode(context, "t9_pinyin", InputLanguage.CHINESE)
        assertEquals("rime_ice", InputModes.rememberedModes(context)[InputLanguage.CHINESE])
        assertEquals("japanese_kana", InputModes.rememberedModes(context)[InputLanguage.JAPANESE])
        InputModes.selectProfile(context, SchemaInfo("double_pinyin_flypy", "双拼", "", "", ""))
        assertEquals("double_pinyin_flypy", InputModes.rememberedModes(context)[InputLanguage.CHINESE])
        val entries = listOf("rime_ice", "t9_pinyin", "double_pinyin_flypy", "japanese_kana").map {
            SchemaInfo(it, it, "", "", "")
        }
        val menu = InputModes.languageChoices(entries, InputModes.ENGLISH, InputModes.rememberedModes(context))
        assertEquals("double_pinyin_flypy", menu.first { it.language == InputLanguage.CHINESE }.schemaId)
        val currentLanguageMenu = InputModes.languageChoices(entries, "rime_ice", InputModes.rememberedModes(context),
            selectedProfiles = InputModes.selectedProfiles(context))
        assertEquals("double_pinyin_flypy", currentLanguageMenu.first { it.language == InputLanguage.CHINESE }.schemaId)
    }
}
