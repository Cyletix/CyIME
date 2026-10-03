package com.kingzcheung.xime.settings

import org.junit.Assert.*
import org.junit.Test

class LanguageSwitchPreferencesTest {
    private val zh = InputLanguage.CHINESE
    private val en = InputLanguage.ENGLISH
    private val ja = InputLanguage.JAPANESE
    private val all = linkedSetOf(zh, ja, en)

    @Test fun defaultKeepsCurrentNativeLanguageAndEnglishRegardlessOfLanguageOrder() {
        val options = LanguageSwitchOptions()
        assertEquals(en, LanguageSwitchPreferences.target(zh, zh, options, listOf(ja, zh, en), all))
        assertEquals(zh, LanguageSwitchPreferences.target(en, zh, options, listOf(ja, zh, en), all))
        assertEquals(en, LanguageSwitchPreferences.target(ja, ja, options, listOf(zh, en, ja), all))
        assertEquals(ja, LanguageSwitchPreferences.target(en, ja, options, listOf(zh, en, ja), all))
    }

    @Test fun defaultDoesNotEnableADisabledNativeLanguage() {
        assertNull(LanguageSwitchPreferences.target(en, zh, LanguageSwitchOptions(), listOf(en), setOf(en)))
    }

    @Test fun cycleUsesTheSavedLanguageOrderAndWraps() {
        val options = LanguageSwitchOptions(LanguageSwitchMode.CYCLE)
        val order = listOf(en, ja, zh)
        assertEquals(ja, LanguageSwitchPreferences.target(en, zh, options, order, all))
        assertEquals(zh, LanguageSwitchPreferences.target(ja, ja, options, order, all))
        assertEquals(en, LanguageSwitchPreferences.target(zh, zh, options, order, all))
    }

    @Test fun cycleSkipsDisabledOrUnavailableLanguagesAndDuplicateEntries() {
        val options = LanguageSwitchOptions(LanguageSwitchMode.CYCLE)
        assertEquals(en, LanguageSwitchPreferences.target(zh, zh, options, listOf(zh, ja, ja, en), setOf(zh, en)))
        assertNull(LanguageSwitchPreferences.target(en, zh, options, listOf(en), setOf(en)))
    }

    @Test fun specificChineseAndEnglishDoesNotDependOnTheEngineFallback() {
        val options = LanguageSwitchOptions(LanguageSwitchMode.SPECIFIC_ENGLISH, zh)
        assertEquals(zh, LanguageSwitchPreferences.target(en, ja, options, all.toList(), all))
        assertEquals(en, LanguageSwitchPreferences.target(zh, zh, options, all.toList(), all))
        assertEquals(zh, LanguageSwitchPreferences.target(ja, ja, options, all.toList(), all))
    }

    @Test fun specificJapaneseAndEnglishRetainsAnExplicitChoice() {
        val options = LanguageSwitchOptions(LanguageSwitchMode.SPECIFIC_ENGLISH, ja)
        assertEquals(ja, LanguageSwitchPreferences.target(zh, zh, options, all.toList(), all))
        assertEquals(en, LanguageSwitchPreferences.target(ja, ja, options, all.toList(), all))
        assertEquals(ja, LanguageSwitchPreferences.target(en, zh, options, all.toList(), all))
    }

    @Test fun invalidSpecificChoiceFallsBackToAnAvailableNativeLanguage() {
        val options = LanguageSwitchOptions(LanguageSwitchMode.SPECIFIC_ENGLISH, ja)
        assertEquals(zh, LanguageSwitchPreferences.target(en, zh, options, listOf(en, zh), setOf(en, zh)))
        assertNull(LanguageSwitchPreferences.target(en, zh, options, listOf(en), setOf(en)))
    }

    @Test fun switchingLanguageDoesNotOverrideSelectedSchemeOrLayout() {
        val entries = listOf("rime_ice", "t9_pinyin", "japanese", "japanese_kana").map { SchemaInfo(it, it, "", "", "") }
        val options = LanguageSwitchOptions(LanguageSwitchMode.SPECIFIC_ENGLISH, ja)
        val target = LanguageSwitchPreferences.target(en, zh, options, all.toList(), all)!!
        assertEquals("japanese_kana", InputProfileSelection.preferred(entries, target, "japanese_kana", "t9_pinyin")?.schemaId)
    }
}
