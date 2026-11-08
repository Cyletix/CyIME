package com.kingzcheung.xime.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class LayoutMenuPreferencesTest {
    private val ctx get() = ApplicationProvider.getApplicationContext<Context>()
    private val zh = InputLanguage.CHINESE
    private val layouts = listOf(InputLayout.QWERTY, InputLayout.T9, InputLayout.STROKE)
    private fun visible(available: List<InputLayout>, language: InputLanguage = zh): List<InputLayout> {
        val menu = LayoutMenuPreferences.read(SettingsPreferences.getPrefsPublic(ctx), language)
        return menu.shown(menu.ordered(available))
    }

    @Test fun newAndTemporarilyMissingLayoutsKeepTheirPlacesAndLanguageIsolation() {
        assertEquals(layouts, visible(layouts))
        LayoutMenuPreferences.reorder(ctx, zh, layouts.reversed().map { it.id })
        LayoutMenuPreferences.setVisible(ctx, zh, layouts, InputLayout.STROKE.id, false)
        assertEquals(listOf(InputLayout.T9, InputLayout.QWERTY), visible(layouts))
        LayoutMenuPreferences.reorder(ctx, zh, listOf(InputLayout.QWERTY.id, InputLayout.T9.id))
        LayoutMenuPreferences.setVisible(ctx, zh, layouts, InputLayout.STROKE.id, true)
        assertEquals(listOf(InputLayout.STROKE, InputLayout.QWERTY, InputLayout.T9, InputLayout.MERGED14),
            visible(layouts + InputLayout.MERGED14))
        assertEquals(layouts, visible(layouts, InputLanguage.ENGLISH))
        LayoutMenuPreferences.setVisible(ctx, zh, layouts, InputLayout.QWERTY.id, false)
        val english = listOf(InputLayout.QWERTY, InputLayout("pinyin_colemak", "Colemak", LayoutKind.ALPHABETIC))
        assertEquals(english, visible(english, InputLanguage.ENGLISH))
    }

    @Test fun losingADeployedSchemeCannotLeaveTheMenuEmptyOrMakeARestoredRowUntoggleable() {
        LayoutMenuPreferences.setVisible(ctx, zh, layouts, InputLayout.STROKE.id, false)
        val onlyStroke = listOf(InputLayout.STROKE)
        assertEquals(onlyStroke, visible(onlyStroke))
        LayoutMenuPreferences.setVisible(ctx, zh, onlyStroke, InputLayout.STROKE.id, false)
        assertEquals(onlyStroke, visible(onlyStroke))
        for (layout in layouts.take(2)) LayoutMenuPreferences.setVisible(ctx, zh, layouts, layout.id, false)
        assertEquals(listOf(InputLayout.T9), visible(layouts))
        LayoutMenuPreferences.setVisible(ctx, zh, layouts, InputLayout.STROKE.id, true)
        assertEquals(listOf(InputLayout.T9, InputLayout.STROKE), visible(layouts))
    }
}
