package com.kingzcheung.xime.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickActionLayoutTest {
    @Test fun existingToolbarIsKeptAndDefaultMenuSkipsItsEntries() {
        val toolbar = listOf("schema", "emoji", "ocr", "clipboard")
        val layout = QuickActionLayout.normalize(toolbar, QuickActionLayout.defaultMenu(toolbar))
        assertEquals(toolbar, layout.toolbar)
        assertEquals(listOf("settings", "float", "caption", "sound", "floating", "glass", "incognito", "full_shape", "arrange"), layout.menu.filter { it !in ToolbarButton.LAYOUT_GESTURES.map { g -> g.id } })
    }

    @Test fun eachEntryLivesInOnePlaceAndSettingsCannotBeRemoved() {
        val layout = QuickActionLayout.normalize(listOf("emoji", "emoji", "glass"), listOf("glass", "ocr"))
        assertEquals(listOf("emoji", "glass"), layout.toolbar)
        assertEquals(listOf("ocr", "settings"), layout.menu)
    }

    @Test fun draggingAcrossDividersMovesBetweenToolbarMenuAndUnused() {
        val start = QuickActionLayout(listOf("emoji", "voice"), listOf("settings", "sound"))
        val order = start.editorOrder(listOf("emoji", "voice", "settings", "sound", "glass", "ocr"))
        assertEquals(listOf("emoji", "voice", "#menu", "settings", "sound", "#unused", "glass", "ocr"), order)
        // voice down into the menu, glass up onto the toolbar, settings dropped into unused.
        val edited = listOf("emoji", "glass", "#menu", "voice", "sound", "#unused", "settings", "ocr")
        val layout = QuickActionLayout.fromEditorOrder(edited)
        assertEquals(listOf("emoji", "glass"), layout.toolbar)
        assertEquals(listOf("voice", "sound", "settings"), layout.menu)
        assertTrue("unused entries are not stored", "ocr" !in layout.toolbar + layout.menu)
    }
}
