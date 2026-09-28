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
    fun `outside before entering the menu does not select`() {
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
    @Test fun `vertical overflow retains first or last visible row after selection`() {
        for (y in listOf(-10000f, 199f, 200f)) {
            assertEquals("schema_0", languageMenuSelection(schemas, Offset(150f, y), viewport, 0, 50f, "schema_1"))
        }
        for (y in listOf(400f, 401f, 10000f)) {
            assertEquals("schema_3", languageMenuSelection(schemas, Offset(150f, y), viewport, 0, 50f, "schema_1"))
            assertEquals("schema_9", languageMenuSelection(schemas, Offset(150f, y), viewport, 300, 50f, "schema_7"))
        }
    }

    @Test fun `sideways overflow preserves last selection and returning follows pointer`() {
        val previous = languageMenuSelection(schemas, Offset(150f, 270f), viewport, 0, 50f)
        assertEquals("schema_1", languageMenuSelection(schemas, Offset(-500f, -100f), viewport, 0, 50f, previous))
        assertEquals("schema_1", languageMenuSelection(schemas, Offset(1000f, 500f), viewport, 0, 50f, previous))
        assertEquals("schema_2", languageMenuSelection(schemas, Offset(150f, 320f), viewport, 0, 50f, previous))
    }

    @Test fun `overflow scrolling selects edge rows without resetting to current language`() {
        var selected: String? = "schema_1"
        for (offset in 0..300 step 25) {
            selected = languageMenuSelection(schemas, Offset(150f, 1000f), viewport, offset, 50f, selected)
        }
        assertEquals("schema_9", selected)
        for (offset in 300 downTo 0 step 25) {
            selected = languageMenuSelection(schemas, Offset(150f, -1000f), viewport, offset, 50f, selected)
        }
        assertEquals("schema_0", selected)
    }

    @Test fun `invalid pointer and stale selection cannot trigger a switch`() {
        assertNull(languageMenuSelection(schemas, Offset.Unspecified, viewport, 0, 50f, "schema_1"))
        assertNull(languageMenuSelection(schemas, Offset(150f, -10f), viewport, 0, 50f, "removed"))
    }}
