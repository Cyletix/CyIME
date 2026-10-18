package com.kingzcheung.xime.ui

import android.view.WindowManager
import androidx.activity.ComponentActivity
import org.junit.Before

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kingzcheung.xime.settings.FrostedGlassConfig
import com.kingzcheung.xime.ui.keyboard.*
import com.kingzcheung.xime.ui.theme.keyboardBackground
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

class KeyboardPanelTransparencyTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Before fun keepFixtureVisible() {
        rule.runOnUiThread { rule.activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    @Test fun revealUsesOneGlassLayerAndOnlyHidesKeysInsideItsCurrentCoverage() {
        var visible by mutableStateOf(false)
        lateinit var reveal: KeyboardPanelRevealState
        val glass = FrostedGlassConfig(true, 0f, 0.3f, 0.2f)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f),
                LocalKeyboardInputPreferences provides KeyboardInputPreferences(frostedGlass = glass)) {
                MaterialTheme {
                    reveal = rememberKeyboardPanelRevealState(visible)
                    Box(Modifier.requiredSize(220.dp, 220.dp).background(Color.White).testTag("glass-layers")) {
                        Box(Modifier.fillMaxSize().keyboardBackground(null, true, Color.Black, glass)) {
                            Column(Modifier.fillMaxSize().excludeRevealedKeyboardPanels(true, reveal)) {
                                Box(Modifier.padding(start = 40.dp, top = 20.dp).size(130.dp, 45.dp).background(Color.Red)) {
                                    Text("QWERTY", color = Color.Green, fontSize = 18.sp)
                                }
                                Spacer(Modifier.height(85.dp))
                                Box(Modifier.padding(start = 40.dp).size(130.dp, 45.dp).background(Color.Red)) {
                                    Text("ZXCVBN", color = Color.Green, fontSize = 18.sp)
                                }
                            }
                            KeyboardPanelReveal(visible, Modifier.fillMaxSize(), revealState = reveal) {
                                Box(Modifier.fillMaxSize().inheritKeyboardBackground().keyboardPanelBackground(Color.Black))
                            }
                        }
                    }
                }
            }
        }
        rule.mainClock.advanceTimeByFrame()
        val before = shot()
        val material = before.getPixel(5, 5)
        assertGray(material, 179)
        assertEquals(android.graphics.Color.RED, before.getPixel(45, 55))
        assertEquals(android.graphics.Color.RED, before.getPixel(45, 185))
        assertTrue(greenPixels(before, 20, 65) > 0)
        rule.runOnIdle { visible = true }
        rule.mainClock.advanceTimeByFrame()
        // Find the middle by progress: the user's system animation multiplier may be 0.5x.
        // Never change that global preference merely to make a timing assertion pass.
        var frames = 0
        while (rule.runOnIdle { reveal.progress.value } < 0.3f && frames++ < 30) {
            rule.mainClock.advanceTimeByFrame()
            rule.waitForIdle()
        }
        val progress = rule.runOnIdle { reveal.progress.value }
        assertTrue("must inspect the moving reveal, not only its endpoints: $progress", progress in 0.3f..0.8f)
        val during = shot()
        assertNear(material, during.getPixel(45, 55))
        assertEquals(android.graphics.Color.RED, during.getPixel(45, 185))
        assertEquals(0, greenPixels(during, 20, 65))
        assertTrue(greenPixels(during, 150, 195) > 0)
        rule.mainClock.advanceTimeBy(300)
        val after = shot()
        assertNear(material, after.getPixel(45, 55))
        assertNear(material, after.getPixel(45, 185))
        assertNear(material, after.getPixel(5, 5))
        assertEquals(0, greenPixels(after, 0, 220))
        rule.runOnIdle { visible = false }
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeBy(220)
        assertEquals(android.graphics.Color.RED, shot().getPixel(45, 55))
    }

    private fun shot(): Bitmap = rule.onNodeWithTag("glass-layers").captureToImage().asAndroidBitmap()
    private fun greenPixels(image: Bitmap, top: Int, bottom: Int): Int {
        var count = 0
        for (y in top until bottom) for (x in 40 until 170) {
            val pixel = image.getPixel(x, y)
            if (android.graphics.Color.green(pixel) > android.graphics.Color.red(pixel) + 40 &&
                android.graphics.Color.green(pixel) > android.graphics.Color.blue(pixel) + 40) count++
        }
        return count
    }
    private fun assertGray(color: Int, value: Int) {
        assertTrue(abs(android.graphics.Color.red(color) - value) <= 2)
        assertTrue(abs(android.graphics.Color.green(color) - value) <= 2)
        assertTrue(abs(android.graphics.Color.blue(color) - value) <= 2)
    }
    private fun assertNear(expected: Int, actual: Int) {
        assertTrue(abs(android.graphics.Color.red(expected) - android.graphics.Color.red(actual)) <= 2)
        assertTrue(abs(android.graphics.Color.green(expected) - android.graphics.Color.green(actual)) <= 2)
        assertTrue(abs(android.graphics.Color.blue(expected) - android.graphics.Color.blue(actual)) <= 2)
    }
}
