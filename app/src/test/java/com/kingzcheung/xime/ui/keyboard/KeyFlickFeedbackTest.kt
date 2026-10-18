package com.kingzcheung.xime.ui.keyboard

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.*
import org.junit.Test

class KeyFlickFeedbackTest {
    @Test fun semanticFillRemainsRedOrBlueAndTextReadableAcrossPalettes() {
        for (dark in listOf(true, false)) for (glass in listOf(true, false)) {
            val background = if (dark) Color(0xFF24272C) else Color(0xFFF0F1F3)
            val foreground = if (dark) Color.White else Color.Black
            for (accent in listOf(Color(0xFFB291D5), Color(0xFF94C4B4), Color(0xFFFFBB65))) {
                for (feedback in listOf(KeyFlickFeedback.DELETE, KeyFlickFeedback.UNDO)) {
                    val colors = keyFlickFeedbackColors(background, foreground, accent, feedback, 1f, glass)
                    if (feedback == KeyFlickFeedback.DELETE) assertTrue(colors.background.red > colors.background.blue)
                    else assertTrue(colors.background.blue > colors.background.red)
                    val contrast = (maxOf(colors.background.luminance(), colors.foreground.luminance()) + .05f) /
                        (minOf(colors.background.luminance(), colors.foreground.luminance()) + .05f)
                    assertTrue("Readable $feedback: $contrast", contrast >= 4.5f)
                    assertTrue(colors.background.alpha >= .9f)
                    assertEquals(KeyboardKeyColors(background, foreground),
                        keyFlickFeedbackColors(background, foreground, accent, feedback, 0f, glass))
                }
            }
        }
    }
}
