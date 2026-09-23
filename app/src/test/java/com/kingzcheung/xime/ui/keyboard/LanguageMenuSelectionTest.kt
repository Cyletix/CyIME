package com.kingzcheung.xime.ui.keyboard

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.kingzcheung.xime.settings.SchemaInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LanguageMenuSelectionTest {
    private val schemas = (0..9).map { SchemaInfo("schema_$it", "同名方案", "", "", "") }
    private val viewport = Rect(100f, 200f, 300f, 400f)

    @Test
    fun `same display names select distinct schema ids`() {
        assertEquals("schema_0", languageMenuSelection(schemas, Offset(150f, 220f), viewport, 0, 50f))
        assertEquals("schema_1", languageMenuSelection(schemas, Offset(150f, 270f), viewport, 0, 50f))
    }

    @Test
    fun `scrolled menu can reach schemas beyond the initially visible rows`() {
        assertEquals("schema_7", languageMenuSelection(schemas, Offset(150f, 270f), viewport, 300, 50f))
    }

    @Test
    fun `moving outside the menu cancels selection`() {
        for (point in listOf(Offset(99f, 220f), Offset(301f, 220f), Offset(150f, 199f), Offset(150f, 401f))) {
            assertNull(languageMenuSelection(schemas, point, viewport, 0, 50f))
        }
    }

    @Test
    fun `empty or unmeasured menu cannot switch schemas`() {
        assertNull(languageMenuSelection(emptyList(), Offset(150f, 220f), viewport, 0, 50f))
        assertNull(languageMenuSelection(schemas, Offset(150f, 220f), viewport, 0, 0f))
        assertNull(languageMenuSelection(schemas, Offset.Zero, Rect.Zero, 0, 50f))
    }
}
