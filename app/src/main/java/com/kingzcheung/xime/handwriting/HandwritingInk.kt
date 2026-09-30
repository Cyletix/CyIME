package com.kingzcheung.xime.handwriting

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance

/** Key labels can be white on colored caps; handwriting is drawn on the panel. */
fun handwritingInk(preferred: Color, background: Color): Color {
    val surface = background.compositeOver(Color.White)
    fun contrast(ink: Color): Float {
        val a = ink.luminance(); val b = surface.luminance()
        return (maxOf(a, b) + 0.05f) / (minOf(a, b) + 0.05f)
    }
    val opaque = preferred.copy(alpha = 1f)
    return if (contrast(opaque) >= 4.5f) opaque
        else if (contrast(Color.Black) > contrast(Color.White)) Color.Black else Color.White
}
