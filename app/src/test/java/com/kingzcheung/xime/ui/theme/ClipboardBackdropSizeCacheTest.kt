package com.kingzcheung.xime.ui.theme

import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Test

class ClipboardBackdropSizeCacheTest {
    @Test fun dragAndSnapReuseOneTextureThenTheEndpointIsRendered() {
        val cache = ClipboardBackdropSizeCache()
        val original = IntSize(900, 300)
        assertEquals(original, cache.resolve(original, false))
        for (height in 301..700) assertEquals(original, cache.resolve(IntSize(900, height), true))
        val end = IntSize(900, 700)
        assertEquals(end, cache.resolve(end, false))
    }

    @Test fun cancellationAndLeavingClipboardRestoreTheActualSize() {
        val cache = ClipboardBackdropSizeCache()
        val original = IntSize(900, 300)
        cache.resolve(original, false)
        cache.resolve(IntSize(900, 450), true)
        assertEquals(original, cache.resolve(original, false))
        assertEquals(IntSize(900, 360), cache.resolve(IntSize(900, 360), false))
    }

    @Test fun firstLayoutAndRotationDoNotRetainAnInvalidViewport() {
        val cache = ClipboardBackdropSizeCache()
        assertEquals(IntSize.Zero, cache.resolve(IntSize.Zero, true))
        assertEquals(IntSize(900, 300), cache.resolve(IntSize(900, 300), true))
        assertEquals(IntSize(600, 500), cache.resolve(IntSize(600, 500), true))
        assertEquals(IntSize(600, 500), cache.resolve(IntSize.Zero, true))
    }
}
