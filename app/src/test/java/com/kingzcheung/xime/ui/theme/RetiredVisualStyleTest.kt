package com.kingzcheung.xime.ui.theme

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kingzcheung.xime.settings.SettingsPreferences
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** 旧设置里存着「流光玻璃」「毛玻璃」：键盘按原有外观画，存的值不改（装回旧版仍是原来的样式）。 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class RetiredVisualStyleTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val prefs get() = SettingsPreferences.getPrefsPublic(context)

    @After fun clear() {
        prefs.edit().remove(SettingsPreferences.KEY_VISUAL_STYLE).commit()
        VisualStyles.current = VisualStyle.ORIGINAL
    }

    @Test fun storedFakeGlassReadsAsOriginalWithoutRewritingIt() {
        for (id in listOf("glass", "frost")) {
            prefs.edit().putString(SettingsPreferences.KEY_VISUAL_STYLE, id).commit()
            assertEquals(VisualStyle.ORIGINAL, SettingsPreferences.getVisualStyle(context))
            assertEquals(id, prefs.getString(SettingsPreferences.KEY_VISUAL_STYLE, null))
        }
    }

    @Test fun offeredStylesStillRoundTrip() {
        for (style in VisualStyle.offered) {
            prefs.edit().putString(SettingsPreferences.KEY_VISUAL_STYLE, style.id).commit()
            assertEquals(style, SettingsPreferences.getVisualStyle(context))
        }
    }
}
