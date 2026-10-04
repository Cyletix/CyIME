package com.kingzcheung.xime.ui.keyboard

import com.kingzcheung.xime.settings.CustomKeyboardLayouts
import com.kingzcheung.xime.settings.EngineProfile
import com.kingzcheung.xime.settings.InputProfile
import com.kingzcheung.xime.settings.KeysConfigHelper
import com.kingzcheung.xime.settings.LayoutKind
import kotlin.math.floor
import kotlin.math.roundToInt

/** Available space, not a phone/tablet or orientation classification, determines the default. */
internal data class LetterKeyboardDefaults(
    val availableHeightDp: Int,
    val spacingX: Float? = null,
    val spacingY: Float? = null,
    val floating: Boolean = false,
)

/** Only the actual ten-column alphabetic layout uses the four-row letter geometry. */
internal fun letterKeyboardDefaults(
    profile: InputProfile, availableHeightDp: Int, isAsciiMode: Boolean, floating: Boolean = false,
    split: Boolean = false,
): LetterKeyboardDefaults? {
    if (profile.layout.kind != LayoutKind.ALPHABETIC || split) return null
    val custom = (profile.engineProfile as? EngineProfile.Rime)?.schemaId?.let(CustomKeyboardLayouts::find)
    val rows = KeysConfigHelper.getKeyRows(isAsciiMode)
    if (standardLetterRowGeometry(rows, 1f, custom != null, false) == null) return null
    val (x, y) = KeysConfigHelper.getKeyboardKeyConfig().spacingFor("qwerty")
    return LetterKeyboardDefaults(availableHeightDp, x, y, floating)
}

/** A 56dp minimum row keeps a narrow keyboard usable. Once width permits, letter caps
 * become square. The viewport height and an 88dp cap bound oversized defaults; neither
 * bound restricts manual resizing. Includes the 44dp toolbar and 8dp bottom padding. */
internal fun defaultLetterKeyboardSize(
    availableWidthDp: Int, preferredWidthDp: Int, defaults: LetterKeyboardDefaults,
): ProtectedKeyboardSize {
    val available = availableWidthDp.coerceAtLeast(1)
    val preferred = preferredWidthDp.coerceIn(1, available)
    val availableHeight = defaults.availableHeightDp.coerceAtLeast(1)
    val heightBudget = maxOf(228f, availableHeight * 0.45f).coerceAtMost(availableHeight.toFloat())
    // No four-row geometry can fit below the toolbar plus four 1dp rows. Preserve
    // horizontal usability rather than collapsing to a 1dp strip in this degenerate case.
    if (heightBudget < 56f) return ProtectedKeyboardSize(preferred, heightBudget.toInt(), 0)
    val minRow = ((heightBudget - 52f) / 4f).coerceIn(1f, 56f)
    val policy = KeyVisualPolicy.Qwerty.copy(maxKeyWidth = Float.MAX_VALUE)

    fun geometry(width: Float): Pair<Float, Float> {
        val cellWidth = ((width - 16f) / 10f).coerceAtLeast(1f)
        var rowHeight = minRow
        var capWidth = cellWidth
        // Gap Y is capped by the row height. Solve the same metrics used by rendering.
        repeat(6) {
            val metrics = keyboardGridMetrics(policy, width, rowHeight * 4f + 8f, 10f,
                allowShrink = defaults.floating, growthSpacing = defaults.spacingX to defaults.spacingY)
            val gapX = defaults.spacingX?.takeUnless { it == KeyVisualPolicy.DeclaredDefaultGap }
                ?: ((metrics.insetX ?: 0f) * 2f)
            val gapY = defaults.spacingY?.takeUnless { it == KeyVisualPolicy.DeclaredDefaultGap }
                ?: ((metrics.insetY ?: 0f) * 2f)
            capWidth = (cellWidth - gapX).coerceAtLeast(1f)
            rowHeight = maxOf(minRow, capWidth + gapY)
        }
        return capWidth to (52f + rowHeight * 4f)
    }

    fun fits(width: Float): Boolean {
        val (capWidth, height) = geometry(width)
        return capWidth <= 88f && height <= heightBudget + 0.001f
    }
    var width = preferred.toFloat()
    if (!fits(width)) {
        var low = 1f
        var high = width
        repeat(24) {
            val middle = (low + high) / 2f
            if (fits(middle)) low = middle else high = middle
        }
        width = low
    }
    val roundedWidth = floor(width).toInt().coerceIn(1, available)
    val height = geometry(roundedWidth.toFloat()).second.roundToInt()
        .coerceIn(1, heightBudget.toInt().coerceAtLeast(1))
    return ProtectedKeyboardSize(roundedWidth, height, 0)
}
