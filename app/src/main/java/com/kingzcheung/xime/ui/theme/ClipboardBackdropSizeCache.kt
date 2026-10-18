package com.kingzcheung.xime.ui.theme

import androidx.compose.ui.unit.IntSize

/** Reuse the last texture during one clipboard drag and its settling animation. */
internal class ClipboardBackdropSizeCache {
    private var lastSize = IntSize.Zero

    fun resolve(actual: IntSize, inMotion: Boolean): IntSize {
        if (actual.width <= 0 || actual.height <= 0) return lastSize
        // A rotation/width change is a different viewport, not another height sample.
        if (!inMotion || lastSize.width != actual.width || lastSize.height <= 0) lastSize = actual
        return lastSize
    }
}
