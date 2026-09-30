package com.kingzcheung.xime.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class LanguagePreferencesTest {
    @Test fun disabledChineseChoosesEnabledJapaneseNativeSchema() {
        assertEquals("japanese_kana", LanguagePreferences.nativeSchema(
            "rime_ice", listOf("rime_ice", "japanese_kana"),
            setOf(InputLanguage.JAPANESE, InputLanguage.ENGLISH)))
        assertEquals("japanese_kana", LanguagePreferences.nativeSchema(
            "japanese", listOf("rime_ice", "japanese_kana"),
            setOf(InputLanguage.JAPANESE, InputLanguage.ENGLISH)))
        assertEquals("japanese", LanguagePreferences.nativeSchema(
            "japanese", listOf("rime_ice", "japanese", "japanese_kana"),
            setOf(InputLanguage.JAPANESE, InputLanguage.ENGLISH)))
    }

    @Test fun englishOnlyKeepsRealNativeFallback() {
        assertEquals("rime_ice", LanguagePreferences.nativeSchema(
            InputModes.ENGLISH, listOf("rime_ice", "japanese_kana"), setOf(InputLanguage.ENGLISH)))
        assertEquals("rime_ice", LanguagePreferences.nativeSchema(
            "rime_ice", emptyList(), setOf(InputLanguage.ENGLISH)))
    }
}
