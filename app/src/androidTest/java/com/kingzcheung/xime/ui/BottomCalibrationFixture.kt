package com.kingzcheung.xime.ui

/**
 * Isolated IME tests save and restore every bottom-calibration value, including values first created by a test.
 * Since 7b2b1bff the bottom padding is one value for both orientations again (as in 1.15.66), plus the
 * 「底部位置」 choice and the edge-to-edge switch it writes.
 */
internal val bottomCalibrationFixtureKeys = listOf(
    "keyboard_bottom_padding_dp", "keyboard_bottom_lowering_dp", "keyboard_bottom_edge_lowering_dp",
    "keyboard_bottom_lowering_limit_dp", "keyboard_bottom_position", "edge_to_edge_keyboard",
)

@Suppress("UNUSED_PARAMETER")
internal fun bottomPaddingFixtureKey(landscape: Boolean) = "keyboard_bottom_padding_dp"
