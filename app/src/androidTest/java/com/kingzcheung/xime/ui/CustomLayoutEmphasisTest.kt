package com.kingzcheung.xime.ui

import android.content.Context
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.kingzcheung.xime.settings.CustomKeyboardLayout
import com.kingzcheung.xime.ui.keyboard.*
import com.kingzcheung.xime.ui.theme.KeyboardThemes
import com.kingzcheung.xime.ui.theme.XimeTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class CustomLayoutEmphasisTest {
    @get:Rule val rule = createComposeRule()
    private val base = CustomKeyboardLayout.fresh().swap("s", "d")

    @Test fun bothSwitchesControlOnlyTheirLettersAcrossThemes() {
        KeyboardThemes.reload(ApplicationProvider.getApplicationContext<Context>())
        for (theme in KeyboardThemes.themes) for (dark in listOf(false, true)) {
            val bg = KeyboardThemes.getKeyBackgroundColor(theme.id, dark)
            val fg = KeyboardThemes.getKeyTextColor(theme.id, dark)
            val accent = KeyboardThemes.getPrimaryColor(theme.id, dark)
            for (vowels in listOf(false, true)) for (moved in listOf(false, true)) {
                val layout = base.copy(redVowels = vowels, redMoved = moved)
                for ((letter, enabled) in listOf("a" to vowels, "s" to moved, "q" to false)) {
                    val colors = customLayoutKeyColors(layout, letter, bg, fg, accent)
                    val label = customLayoutLabel(layout, letter, colors.first, accent, fg)
                    assertEquals("${theme.id}/$dark/$letter/$vowels/$moved", enabled, label.spanStyles.isNotEmpty())
                    if (enabled) {
                        assertTrue(layoutContrast(colors.second, colors.first) >= 4.5f)
                    } else assertEquals(bg, colors.first)
                }
            }
        }
    }

    @Test fun existingKeyRepaintsWhenEmphasisChangesWithoutChangingLayoutId() {
        val layout = mutableStateOf(base.copy(redVowels = false, redMoved = false))
        rule.setContent {
            XimeTheme(darkTheme = true) {
                CompositionLocalProvider(LocalCustomLayout provides layout.value,
                    LocalCustomAccent provides Color(0xFFD0BCFF)) {
                    SwipeableKeyButton("a", {}, Color(0xFF36313F), Color(0xFFD0BCFF),
                        Modifier.size(80.dp).testTag("letter"))
                }
            }
        }
        fun sample() = rule.onNodeWithTag("letter").captureToImage().toPixelMap().let { it[it.width / 2, it.height / 4] }
        val ordinary = sample()
        rule.runOnIdle { layout.value = layout.value.copy(redVowels = true) }
        assertNotEquals(ordinary, sample())
        rule.runOnIdle { layout.value = layout.value.copy(redVowels = false) }
        assertEquals(ordinary, sample())
    }
}
