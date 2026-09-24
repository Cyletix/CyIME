package com.kingzcheung.xime.ui.keyboard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import com.kingzcheung.xime.service.punctuationWidth

internal data class KeyboardPunctuation(val full: Boolean, val japanese: Boolean)
internal val LocalKeyboardPunctuation = staticCompositionLocalOf<KeyboardPunctuation?> { null }

/** Render literal key labels through the exact same policy used by commitLiteralText. */
@Composable
internal fun punctuationKeyLabel(raw: String): String {
    val policy = LocalKeyboardPunctuation.current ?: return raw
    return punctuationWidth(raw, policy.full, policy.japanese)
}
