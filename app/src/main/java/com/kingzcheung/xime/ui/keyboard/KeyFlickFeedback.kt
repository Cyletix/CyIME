package com.kingzcheung.xime.ui.keyboard

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

enum class KeyFlickFeedback { NORMAL, DELETE, UNDO }

/** Semantic colors remain recognizable under any palette, including translucent glass. */
internal fun keyFlickFeedbackColors(base: Color, foreground: Color, accent: Color,
    feedback: KeyFlickFeedback, progress: Float, glass: Boolean): KeyboardKeyColors {
    if (feedback == KeyFlickFeedback.NORMAL || progress <= 0f) return KeyboardKeyColors(base, foreground)
    val dark = foreground.luminance() > .5f
    val semantic = when (feedback) {
        KeyFlickFeedback.DELETE -> if (dark) Color(0xFF954A50) else Color(0xFFF5C3BF)
        KeyFlickFeedback.UNDO -> if (dark) Color(0xFF355E89) else Color(0xFFBCD6F4)
        else -> base
    }
    val target = lerp(semantic, accent, .06f).copy(alpha = if (glass) .92f else 1f)
    val text = if (dark) Color(0xFFFFF8F7) else if (feedback == KeyFlickFeedback.DELETE)
        Color(0xFF642A31) else Color(0xFF203F61)
    return KeyboardKeyColors(lerp(base, target, progress.coerceIn(0f, 1f)),
        lerp(foreground, text, progress.coerceIn(0f, 1f)))
}
