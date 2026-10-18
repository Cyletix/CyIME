package com.kingzcheung.xime.ui.keyboard

import com.kingzcheung.xime.keyboard.QuickActionLayout
import com.kingzcheung.xime.keyboard.ToolbarButton
import com.kingzcheung.xime.settings.LayoutKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 布局手势开关是普通入口：可排列；只在适用的布局出现，状态取自当前设置。 */
class LayoutGestureMenuItemsTest {
    private val prefs = KeyboardInputPreferences(t9FlickLetters = true, fourteenLetterSwipe = false,
        kanaDirectionHints = true, flickDirectionDisc = false, glideTyping = true)

    private fun shown(kind: LayoutKind) = ToolbarButton.LAYOUT_GESTURES.filter { it.appliesTo(kind) }

    @Test fun eachLayoutShowsOnlyItsOwnToggles() {
        assertEquals(listOf(ToolbarButton.T9_FLICK, ToolbarButton.FLICK_DISC), shown(LayoutKind.T9))
        assertEquals(listOf(ToolbarButton.FOURTEEN_SWIPE, ToolbarButton.FLICK_DISC), shown(LayoutKind.MERGED))
        assertEquals(listOf(ToolbarButton.KANA_HINTS, ToolbarButton.FLICK_DISC), shown(LayoutKind.KANA_KEYPAD))
        assertEquals(listOf(ToolbarButton.GLIDE), shown(LayoutKind.ALPHABETIC))
        for (kind in listOf(LayoutKind.HANDWRITING, LayoutKind.NUMBER, LayoutKind.STROKE_KEYPAD))
            assertTrue(shown(kind).isEmpty())
    }

    @Test fun stateFollowsSettings() {
        assertEquals(listOf(true, true, false, true, false), ToolbarButton.LAYOUT_GESTURES.map { it.gestureOn(prefs) })
        assertEquals(listOf("flick_direction_disc_v2", "flick_direction_disc"), ToolbarButton.FLICK_DISC.gestureKeys())
    }

    @Test fun arrangeableTogglesInDefaultMenuBeforeArrange() {
        assertTrue(ToolbarButton.LAYOUT_GESTURES.all { it in ToolbarButton.CUSTOMIZABLE && it in ToolbarButton.TOGGLES })
        val menu = QuickActionLayout.defaultMenu(emptyList())
        assertEquals(ToolbarButton.ARRANGE.id, menu.last())
        assertEquals(ToolbarButton.LAYOUT_GESTURES.map { it.id }, menu.dropLast(1).takeLast(5))
    }
}
