package com.kingzcheung.xime.ui.settings

import com.kingzcheung.xime.settings.InputLanguage
import org.junit.Assert.assertEquals
import org.junit.Test

class LanguageRowsTest {
    private val all = InputLanguage.supported.map { it.id }

    @Test
    fun justTurnedOffLanguageAppearsOnce() {
        // 关掉西班牙语后的那一帧：拖动顺序还没更新，仍含 es。
        val staleOrder = listOf("zh", "en", "es", "de")
        val enabled = setOf(InputLanguage.CHINESE, InputLanguage.ENGLISH, InputLanguage.GERMAN, InputLanguage.UNSPECIFIED)
        val rows = languageRows(staleOrder, enabled)
        assertEquals(rows.distinct(), rows)
        assertEquals(all.toSet(), rows.toSet())
        assertEquals(listOf("zh", "en", "de"), rows.take(3))
    }

    @Test
    fun everyLanguageTurnedOffOneByOneStaysUnique() {
        val order = listOf("zh", "ja", "en", "es", "de", "fr", "it", "ru", "ko").filter { it in all }
        order.filter { it != "en" }.forEach { off ->
            val enabled = InputLanguage.supported.filter { it.id != off }.toSet()
            val rows = languageRows(order, enabled)
            assertEquals(rows.distinct(), rows)
            assertEquals(off, rows.last())
        }
    }

    @Test
    fun keepsDragOrderOfEnabledLanguages() {
        val enabled = setOf(InputLanguage.JAPANESE, InputLanguage.CHINESE, InputLanguage.ENGLISH)
        val rows = languageRows(listOf("ja", "en", "zh"), enabled)
        assertEquals(listOf("ja", "en", "zh"), rows.take(3))
    }
}
