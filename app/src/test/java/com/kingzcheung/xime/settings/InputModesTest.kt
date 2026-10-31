package com.kingzcheung.xime.settings

import org.junit.Assert.*
import org.junit.Test

class InputModesTest {
    private val modes = listOf("t9_pinyin", "rime_ice", "double_pinyin_flypy", "japanese", "japanese_kana")
        .map { SchemaInfo(it, it, "", "", "") }

    @Test fun chineseModesIncludeShuangpinButNeverJapaneseOrEnglish() {
        assertEquals(listOf("t9_pinyin", "rime_ice", "double_pinyin_flypy"),
            InputModes.inCurrentLanguage(modes, "double_pinyin_flypy").map { it.schemaId })
    }
    @Test fun japaneseAndEnglishPanelsOnlyShowTheirOwnModes() {
        assertEquals(listOf("japanese", "japanese_kana"),
            InputModes.inCurrentLanguage(modes, "japanese_kana").map { it.schemaId })
        assertEquals(listOf(InputModes.ENGLISH),
            InputModes.inCurrentLanguage(modes, InputModes.ENGLISH).map { it.schemaId })
    }
    @Test fun languageMenuRestoresEachLanguageModeFromEnglish() {
        val choices = InputModes.languageChoices(modes, InputModes.ENGLISH,
            mapOf(InputLanguage.CHINESE to "double_pinyin_flypy", InputLanguage.JAPANESE to "japanese_kana"))
        assertEquals(listOf("中文", "日语", "英文"), choices.map { it.name })
        assertEquals(listOf("double_pinyin_flypy", "japanese_kana", InputModes.ENGLISH), choices.map { it.schemaId })
    }
    @Test fun currentModeWinsAndRemovedModeFallsBackWithinItsLanguage() {
        val choices = InputModes.languageChoices(modes, "rime_ice",
            mapOf(InputLanguage.CHINESE to "t9_pinyin", InputLanguage.JAPANESE to "removed"))
        assertEquals(listOf("rime_ice", "japanese", InputModes.ENGLISH), choices.map { it.schemaId })
    }
    @Test fun newJapaneseModesCanDeclareTheirLanguageWithoutChangingTheMenu() {
        val extra = SchemaInfo("kana_flick", "假名滑行", "", "", "", language = InputLanguage.JAPANESE)
        assertTrue(InputModes.inCurrentLanguage(modes + extra, "japanese").contains(extra))
    }
    @Test fun reorderingChineseModesPreservesJapaneseAndEnglishSlots() {
        val current = listOf("t9_pinyin", "japanese", "rime_ice", InputModes.ENGLISH, "japanese_kana")
        assertEquals(listOf("rime_ice", "japanese", "t9_pinyin", InputModes.ENGLISH, "japanese_kana"),
            InputModes.mergeOrder(current, listOf("rime_ice", "t9_pinyin")))
    }

    @Test fun independentLanguageOrderKeepsTheRememberedInputMode() {
        // 这是所有语言的排位（含未开启的），显示时 languageOrder(context) 再只留已开启的。
        val order = InputModes.languageOrder(listOf("en", "ja", "ja", "unknown"))
        assertEquals(listOf(InputLanguage.ENGLISH, InputLanguage.JAPANESE, InputLanguage.CHINESE, InputLanguage.SPANISH), order)
        val choices = InputModes.languageChoices(modes, "rime_ice", mapOf(InputLanguage.JAPANESE to "japanese_kana"), order)
        assertEquals(listOf(InputModes.ENGLISH, "japanese_kana", "rime_ice"), choices.map { it.schemaId })
        assertEquals(listOf("rime_ice", "t9_pinyin"), InputModes.available(modes, InputModes.defaultModeOrder).take(2).map { it.schemaId })
        assertEquals("t9_pinyin", InputModes.available(modes, listOf("t9_pinyin", "rime_ice")).first().schemaId)
    }

    @Test fun japaneseComesAfterEnglishByDefaultAndWhenSwitchedOnLater() {
        assertEquals(listOf(InputLanguage.CHINESE, InputLanguage.ENGLISH, InputLanguage.JAPANESE, InputLanguage.SPANISH),
            InputModes.languageOrder(emptyList()))
        // A saved order from older versions put Japanese before English; switching Japanese on moves it last.
        // 未开启的西班牙语只占排位，开启时会再排到最后。
        assertEquals(listOf("zh", "en", "es", "ja"), InputModes.orderWithLanguageLast(listOf("zh", "ja", "en"), InputLanguage.JAPANESE))
        // 西班牙语后开启，同样排到已开启语言后面。
        assertEquals(listOf("zh", "en", "ja", "es"), InputModes.orderWithLanguageLast(listOf("zh", "en", "ja"), InputLanguage.SPANISH))
    }

    @Test fun englishIsAvailableWithNoEnabledSchemas() {
        assertEquals(listOf(InputModes.english), InputModes.available(emptyList()))
    }
    @Test fun repeatedNormalizationKeepsOnePermanentEnglishEntry() {
        val kana = SchemaInfo("japanese_kana", "日语九键", "", "", "")
        val available = InputModes.available(listOf(kana, InputModes.english, kana))
        assertEquals(listOf(kana, InputModes.english), InputModes.available(available))
    }
    @Test fun tapAndMenuUseTheSameEnglishSelectionIdentity() {
        assertEquals(InputModes.ENGLISH, InputModes.selectedId("japanese", true))
        assertEquals("japanese", InputModes.selectedId("japanese", false))
    }
    @Test fun customOrderSurvivesNormalizationAndNewSchemasAppend() {
        val kana = SchemaInfo("japanese", "日语", "", "", "")
        val chinese = SchemaInfo("t9", "拼音", "", "", "")
        val modes = InputModes.available(listOf(chinese, kana, chinese), listOf(InputModes.ENGLISH, "japanese", "missing"))
        assertEquals(listOf(InputModes.ENGLISH, "japanese", "t9"), modes.map { it.schemaId })
        assertEquals(modes, InputModes.available(modes))
    }
}
