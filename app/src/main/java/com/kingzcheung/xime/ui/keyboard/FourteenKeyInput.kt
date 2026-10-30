package com.kingzcheung.xime.ui.keyboard

import androidx.compose.runtime.staticCompositionLocalOf
import kotlin.math.roundToInt

internal val LocalFourteenKeyLayout = staticCompositionLocalOf { false }

internal fun normalizeLetterSwipeDistance(value: Float): Float =
    (((value.takeIf { it.isFinite() } ?: 24f).coerceIn(12f, 72f) / 2f).roundToInt() * 2f)

/** Only paired alphabet keys participate; function keys and single letters never do. */
internal fun fourteenKeyLetters(group: String?, enabled: Boolean): Pair<String, String>? =
    group?.takeIf { enabled && it.length == 2 && it.all { char -> char.lowercaseChar() in 'a'..'z' } }
        ?.let { it[0].toString() to it[1].toString() }
