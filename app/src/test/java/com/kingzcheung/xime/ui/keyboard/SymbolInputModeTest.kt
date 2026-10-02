package com.kingzcheung.xime.ui.keyboard

import com.kingzcheung.xime.keyboard.GestureAction
import org.junit.Assert.*
import org.junit.Test

class SymbolInputModeTest {
    private fun actions(events: MutableList<String>) = KeyGestureActions(
        text = "ABC", onTap = { events += "tap" }, onPress = {}, onRelease = {}, onPreview = {},
        onUp = { events += "symbol:$it" }, onDown = { events += "down:$it" },
        onLeft = { events += "previous" }, longPressItems = listOf("A", "B", "C"),
        onLongPressSelect = { events += "letter:$it" },
    )

    @Test fun `hold commits through timeout action without a release menu and keeps unrelated gestures`() {
        val events = mutableListOf<String>()
        val original = actions(events)
        val held = original.withSymbolInput(SymbolInputMode.LONG_PRESS, "2")
        assertEquals(300L, held.longPressTimeoutMillis)
        assertNull(held.onUp)
        assertTrue(held.blockUpSwipe)
        assertTrue(held.longPressItems.isEmpty())
        assertNull(held.onLongPressSelect)
        assertNotNull(held.onLongPress)
        assertTrue(events.isEmpty())
        held.onLongPress?.invoke()
        assertEquals(listOf("symbol:2"), events)
        held.onRelease()
        assertEquals(listOf("symbol:2"), events)
        held.onTap()
        held.onDown?.invoke("!")
        held.onLeft?.invoke()
        assertEquals(listOf("symbol:2", "tap", "down:!", "previous"), events)
    }

    @Test fun `swipe mode preserves the complete original behavior`() {
        val events = mutableListOf<String>()
        val original = actions(events)
        assertSame(original, original.withSymbolInput(SymbolInputMode.SWIPE_UP, "2"))
        assertEquals(listOf("A", "B", "C"), original.longPressItems)
        original.onLongPressSelect?.invoke("B")
        assertEquals(listOf("letter:B"), events)
    }

    @Test fun `function keys and dedicated menus do not opt in by their labels`() {
        val original = actions(mutableListOf())
        for (key in listOf(original.copy(onUp = null), original.copy(onLongPress = {}),
            original.copy(longPressDrawableIds = listOf(1, 2)))) {
            assertSame(key, key.withSymbolInput(SymbolInputMode.LONG_PRESS, "2"))
        }
        for (label in listOf(null, "", " ")) {
            assertSame(original, original.withSymbolInput(SymbolInputMode.LONG_PRESS, label))
        }
    }

    @Test fun `hidden hints and absent letter menus still use the actual configured input`() {
        val events = mutableListOf<String>()
        val hidden = actions(events).copy(upText = null, longPressItems = emptyList())
            .withSymbolInput(SymbolInputMode.LONG_PRESS, "！")
        assertTrue(hidden.longPressItems.isEmpty())
        assertNull(hidden.onLongPressSelect)
        hidden.onLongPress?.invoke()
        assertEquals(listOf("symbol:！"), events)
    }

    @Test fun `configured editing actions are excluded regardless of displayed label`() {
        assertEquals("2", symbolInputValue("2", GestureAction.COMMIT))
        assertEquals("?", symbolInputValue("?", null))
        for (action in GestureAction.entries.filter { it != GestureAction.COMMIT }) {
            assertNull(symbolInputValue("2", action))
        }
        assertNull(symbolInputValue("", GestureAction.COMMIT))
    }

    @Test fun `five option T9 menu fits narrow keyboards at phone and tablet densities`() {
        for (density in listOf(1f, 1.875f, 3f)) {
            // Existing custom menus must still fit a resized keyboard.
            val constrained = KeyboardKeyMetrics.longPressMenuWidthPx(80f * density, 5, 360f * density, 4f * density)
            assertEquals(352f * density, constrained, 0.001f)
            assertTrue(constrained / 5 >= 48f * density)
            val normal = KeyboardKeyMetrics.longPressMenuWidthPx(32f * density, 4, 360f * density, 4f * density)
            assertEquals(128f * density, normal, 0.001f)
        }
    }
}
