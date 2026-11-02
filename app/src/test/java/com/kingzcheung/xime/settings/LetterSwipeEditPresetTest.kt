package com.kingzcheung.xime.settings

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 11-02：「Z X C V 角标做撤销、剪切、复制、粘贴」开关并进「按键符号」，成了滑动动作的预设。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class LetterSwipeEditPresetTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private val prefs get() = SettingsPreferences.getPrefsPublic(app)

    @Before fun clear() { prefs.edit().clear().commit() }

    @Test fun presetUsesTheUpperCornerDirection() {
        val down = LetterSwipeBindings.withEditPreset(emptyMap(), reverse = false)
        assertEquals(mapOf("z.down" to "undo", "x.down" to "cut", "c.down" to "copy", "v.down" to "paste"), down)
        val up = LetterSwipeBindings.withEditPreset(mapOf("a.left" to "home", "z.up" to "paste"), reverse = true)
        assertEquals("home", up["a.left"])
        assertEquals("undo", up["z.up"])
        assertTrue(LetterSwipeBindings.hasEditPreset(up, reverse = true))
        assertFalse(LetterSwipeBindings.hasEditPreset(up, reverse = false))
    }

    @Test fun cornerLabelsFollowTheSymbolDirections() {
        val bindings = LetterSwipeBindings.withEditPreset(mapOf("z.up" to "text:@"), reverse = false)
        assertEquals("撤销" to "@", LetterSwipeBindings.cornerLabels(bindings, "Z", reverse = false))
        assertEquals("@" to "撤销", LetterSwipeBindings.cornerLabels(bindings, "z", reverse = true))
        assertEquals(null to null, LetterSwipeBindings.cornerLabels(bindings, "a", reverse = false))
        assertEquals("撤销", LetterSwipeBindings.actions["undo"])
    }

    @Test fun oldSwitchOnBecomesThePresetOnBothBoards() {
        prefs.edit().putBoolean(LetterSwipeBindings.LEGACY_ZXCV_KEY, true)
            .putStringSet(LetterSwipeBindings.ENGLISH, setOf("z.down\thome")).commit()
        val zh = LetterSwipeBindings.load(app, english = false)
        val en = LetterSwipeBindings.load(app, english = true)
        assertTrue(LetterSwipeBindings.hasEditPreset(zh, reverse = false))
        // 自己设过的方向不覆盖。
        assertEquals("home", en["z.down"])
        assertEquals("paste", en["v.down"])
        assertFalse(prefs.contains(LetterSwipeBindings.LEGACY_ZXCV_KEY))
    }

    @Test fun oldSwitchReversedUsesUpSwipe() {
        prefs.edit().putBoolean(LetterSwipeBindings.LEGACY_ZXCV_KEY, true).putBoolean("reverse_symbol_swipe", true).commit()
        assertTrue(LetterSwipeBindings.hasEditPreset(LetterSwipeBindings.load(app, english = false), reverse = true))
    }

    @Test fun oldSwitchOffAddsNothing() {
        prefs.edit().putBoolean(LetterSwipeBindings.LEGACY_ZXCV_KEY, false).commit()
        assertEquals(emptyMap<String, String>(), LetterSwipeBindings.load(app, english = false))
        assertFalse(prefs.contains(LetterSwipeBindings.LEGACY_ZXCV_KEY))
    }
}
