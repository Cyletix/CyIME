package com.kingzcheung.xime.ui.keyboard

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import com.kingzcheung.xime.settings.FrostedGlassConfig
import com.kingzcheung.xime.viewmodel.ShiftMode
import org.junit.Assert.*
import org.junit.Test

class ShiftKeyColorsTest {
    private val purple = Color(0xFFB291FF)
    private fun colors(mode: ShiftMode, opacity: Float = 0.2f, dark: Boolean = true,
        accent: Color = purple, pressed: Boolean = false, enabled: Boolean = true) = shiftKeyColors(
        if (dark) Color(0xFF525252) else Color(0xFFF4F4F4),
        if (dark) Color.White else Color.Black, accent, if (dark) Color(0xFF17151C) else Color.White,
        FrostedGlassConfig(enabled = enabled, keyOpacity = opacity), mode, pressed, Color.Red)

    @Test fun `single and locked shift remain distinct in both glass appearances`() {
        for (dark in listOf(false, true)) for (opacity in listOf(0.2f, 0.8f)) {
            val off = colors(ShiftMode.OFF, opacity, dark)
            val single = colors(ShiftMode.SINGLE, opacity, dark)
            val caps = colors(ShiftMode.CAPS, opacity, dark)
            assertTrue(single.background.alpha > off.background.alpha)
            assertTrue(caps.background.alpha > single.background.alpha)
            assertNotEquals(single.background, caps.background)
            assertTrue(colors(ShiftMode.CAPS, opacity, dark, pressed = true).background.alpha >= caps.background.alpha)
        }
    }

    @Test fun `theme accent changes the active fill rather than hard coding a highlight`() {
        val red = colors(ShiftMode.CAPS, accent = Color.Red).background
        val blue = colors(ShiftMode.CAPS, accent = Color.Blue).background
        assertTrue(red.red > red.blue)
        assertTrue(blue.blue > blue.red)
        assertNotEquals(colors(ShiftMode.SINGLE, 1f).background, colors(ShiftMode.CAPS, 1f).background)
    }

    @Test fun `active icon remains legible on light dark and imported accent colors`() {
        for (dark in listOf(false, true)) for (opacity in listOf(0f, 0.2f, 0.8f, 1f))
            for (accent in listOf(purple, Color.Black, Color.White, Color.Yellow, Color.Blue))
                for (mode in listOf(ShiftMode.SINGLE, ShiftMode.CAPS)) {
                    val state = colors(mode, opacity, dark, accent)
                    val surface = state.background.compositeOver(if (dark) Color(0xFF17151C) else Color.White)
                    val a = state.foreground.luminance()
                    val b = surface.luminance()
                    assertTrue((maxOf(a, b) + 0.05f) / (minOf(a, b) + 0.05f) >= 3f)
                }
    }

    @Test fun `transparent user choice and non glass colors are preserved`() {
        for (mode in ShiftMode.entries) {
            assertEquals(0f, colors(mode, 0f).background.alpha, 0f)
            assertEquals(Color.Red, colors(mode, enabled = false).background)
        }
    }
}
