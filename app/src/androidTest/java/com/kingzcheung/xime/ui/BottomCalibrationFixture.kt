package com.kingzcheung.xime.ui

/** Isolated IME tests restore both orientations, including values first created by a test. */
internal val bottomCalibrationFixtureKeys = listOf("_portrait", "_landscape").flatMap { suffix ->
    listOf("keyboard_bottom_padding_dp", "keyboard_bottom_lowering_dp", "keyboard_bottom_edge_lowering_dp",
        "keyboard_bottom_lowering_limit_dp", "keyboard_bottom_edge_lowering_limit_dp").map { it + suffix }
}

internal fun bottomPaddingFixtureKey(landscape: Boolean) =
    if (landscape) "keyboard_bottom_padding_dp_landscape" else "keyboard_bottom_padding_dp_portrait"
