package com.kingzcheung.xime.ui

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.kingzcheung.xime.settings.FrostedGlassConfig
import com.kingzcheung.xime.ui.keyboard.HandwritingKeyboardLayout
import com.kingzcheung.xime.ui.keyboard.KeyboardInputPreferences
import com.kingzcheung.xime.ui.keyboard.LocalKeyboardInputPreferences
import com.kingzcheung.xime.ui.keyboard.keyboardPanelBackground
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HandwritingFullscreenGlassTest {
    @get:Rule val rule = createComposeRule()

    @Test fun fullscreenFooterSharesTheGlassMaterialWithoutPaintingTheWritingAreaOrUnusedWidth() {
        var dark by mutableStateOf(true)
        var glassEnabled by mutableStateOf(true)
        var offset by mutableIntStateOf(-40)
        var action = ""
        rule.setContent {
            val background = if (dark) Color.Black else Color.White
            val foreground = if (dark) Color.White else Color.Black
            CompositionLocalProvider(
                LocalDensity provides Density(1f),
                LocalKeyboardInputPreferences provides KeyboardInputPreferences(
                    frostedGlass = FrostedGlassConfig.defaults(dark, glassEnabled)),
            ) {
                MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                    Column {
                        Box(Modifier.requiredSize(360.dp, 480.dp).background(Color.Magenta)
                            .testTag("fullscreen-handwriting-host")) {
                            HandwritingKeyboardLayout(
                                expanded = true,
                                expandedControlsWidthDp = 240,
                                expandedControlsOffsetX = offset,
                                bottomPaddingDp = 0,
                                panelBackgroundColor = background,
                                keyBackgroundColor = background,
                                specialKeyBackgroundColor = background,
                                keyTextColor = foreground,
                                specialKeyTextColor = foreground,
                                onKeyPress = { action = it },
                                expandedCandidateBar = {
                                    Box(Modifier.fillMaxWidth().height(44.dp))
                                },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        // Exact same-size material: catch solid covers on any footer row,
                        // not just a modifier that exists somewhere in the composition.
                        Box(Modifier.requiredSize(232.dp, 148.dp)
                            .keyboardPanelBackground(background).testTag("handwriting-glass-reference"))
                    }
                }
            }
        }

        for (isDark in listOf(true, false)) {
            rule.runOnIdle { dark = isDark; glassEnabled = true }
            val plain = if (isDark) android.graphics.Color.BLACK else android.graphics.Color.WHITE
            val reference = awaitImage("handwriting-glass-reference") { distance(it.getPixel(1, 1), plain) > 5 }
            for (position in listOf(-40, 40)) {
                rule.runOnIdle { offset = position }
                val footer = awaitImage("handwriting-controls") { image ->
                    listOf(1, 50, 105).all { y -> distance(image.getPixel(0, y), reference.getPixel(0, y)) <= 2 }
                }
                assertEquals(232, footer.width)
                assertEquals(148, footer.height)

                val paper = rule.onNodeWithTag("handwriting-canvas").fetchSemanticsNode().boundsInRoot
                val controls = rule.onNodeWithTag("handwriting-controls").fetchSemanticsNode().boundsInRoot
                assertEquals(paper.center.x + position, controls.center.x, 1f)
                assertTrue(paper.width > controls.width)
                val host = rule.onNodeWithTag("fullscreen-handwriting-host").captureToImage().asAndroidBitmap()
                assertEquals(android.graphics.Color.MAGENTA, host.getPixel(180, 100))
                assertEquals(android.graphics.Color.MAGENTA, host.getPixel(8, 440))
                assertEquals(android.graphics.Color.MAGENTA, host.getPixel(351, 440))
            }
            rule.runOnIdle { glassEnabled = false }
            awaitImage("handwriting-controls") { image ->
                listOf(1, 50, 105).all { y -> distance(image.getPixel(0, y), plain) <= 2 }
            }
        }
        rule.onNodeWithTag("handwriting-key:，").performClick()
        rule.runOnIdle { assertEquals("，", action) }
        rule.onNodeWithTag("handwriting-key:collapse").performClick()
        rule.runOnIdle { assertEquals("collapse", action) }
    }

    private fun distance(a: Int, b: Int) = maxOf(
        abs(android.graphics.Color.red(a) - android.graphics.Color.red(b)),
        abs(android.graphics.Color.green(a) - android.graphics.Color.green(b)),
        abs(android.graphics.Color.blue(a) - android.graphics.Color.blue(b)),
    )

    private fun awaitImage(tag: String, condition: (Bitmap) -> Boolean): Bitmap {
        var result: Bitmap? = null
        rule.waitUntil(timeoutMillis = 15_000) {
            val current = rule.onNodeWithTag(tag).captureToImage().asAndroidBitmap()
            if (condition(current)) { result = current; true } else false
        }
        return checkNotNull(result)
    }
}
