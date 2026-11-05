package com.kingzcheung.xime.ui.keyboard

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.kingzcheung.xime.settings.SettingsPreferences
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 11-05：手写记住上次全屏/半屏，首次照旧半屏。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class HandwritingFullscreenMemoryTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()

    @Before fun clear() { SettingsPreferences.getPrefsPublic(app).edit().clear().commit() }

    @Test fun firstUseStaysHalfScreen() {
        assertFalse(HandwritingFullscreenMemory.read(app))
    }

    @Test fun remembersTheLastChoice() {
        HandwritingFullscreenMemory.write(app, true)
        assertTrue(HandwritingFullscreenMemory.read(app))
        HandwritingFullscreenMemory.write(app, false)
        assertFalse(HandwritingFullscreenMemory.read(app))
    }

    private fun restore(
        remembered: Boolean = true, handwritingPage: Boolean = true, alreadyExpanded: Boolean = false,
        compact: Boolean = false, voiceCollapsed: Boolean = false, resizing: Boolean = false, settingsPreview: Boolean = false,
    ) = HandwritingFullscreenMemory.shouldRestore(remembered, handwritingPage, alreadyExpanded, compact, voiceCollapsed,
        resizing, settingsPreview)

    @Test fun reopensFullscreenOnlyOnTheFullHandwritingKeyboard() {
        assertTrue(restore())
        assertFalse(restore(remembered = false))
        assertFalse(restore(handwritingPage = false))
        assertFalse(restore(alreadyExpanded = true))
        assertFalse(restore(compact = true))
        assertFalse(restore(voiceCollapsed = true))
        assertFalse(restore(resizing = true))
        assertFalse(restore(settingsPreview = true))
    }
}
