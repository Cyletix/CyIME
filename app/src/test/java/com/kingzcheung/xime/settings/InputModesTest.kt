package com.kingzcheung.xime.settings

import org.junit.Assert.*
import org.junit.Test

class InputModesTest {
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
