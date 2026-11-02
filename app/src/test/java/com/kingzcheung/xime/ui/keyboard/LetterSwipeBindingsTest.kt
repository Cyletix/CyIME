package com.kingzcheung.xime.ui.keyboard

import org.junit.Assert.*
import org.junit.Test

class LetterSwipeBindingsTest {
    @Test fun aPhysicalDirectionOverridesOnlyItsOwnKeyAndDirection() {
        val sent = mutableListOf<String>()
        val base = KeyGestureActions("a", { sent += "a" }, {}, {}, {},
            downText = "1", onDown = { sent += it })
        val overrides = mapOf("a.left" to "home", "a.up" to "text:@", "s.right" to "clipboard")
        val bound = base.withLetterSwipes("a", overrides, sent::add)
        bound.onTap()
        bound.onLeft!!()
        bound.onUp!!(bound.upText!!)
        bound.onDown!!("1")
        assertEquals(listOf("a", "home", "text:@", "1"), sent)
        assertNull(bound.onRight)
        assertSame(base, base.withLetterSwipes("b", overrides, sent::add))
    }
}
