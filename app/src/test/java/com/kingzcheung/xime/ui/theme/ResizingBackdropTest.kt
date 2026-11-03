package com.kingzcheung.xime.ui.theme

import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Test

class ResizingBackdropTest {
    private val collapsed = IntSize(1080, 700)
    private val dragged = IntSize(1080, 1100)

    @Test fun anImageIsNeverStretchedWhileTheClipboardIsDragged() {
        // Before the whole picture is ready the cached render keeps its own aspect ratio.
        assertEquals(BackdropResizeDraw.CoverCached, backdropResizeDraw(true, false, true, collapsed, dragged))
        assertEquals(BackdropResizeDraw.Picture, backdropResizeDraw(true, true, true, collapsed, dragged))
        // Released at a new height: the picture stays until the new render has arrived.
        assertEquals(BackdropResizeDraw.Picture, backdropResizeDraw(false, true, true, collapsed, dragged))
        assertEquals(BackdropResizeDraw.Cached, backdropResizeDraw(false, true, true, dragged, dragged))
    }

    @Test fun gradientsAndUntouchedSizesKeepTheCachedRender() {
        // A gradient is defined over the current height; following the height is its correct look.
        assertEquals(BackdropResizeDraw.Cached, backdropResizeDraw(true, false, false, collapsed, dragged))
        assertEquals(BackdropResizeDraw.Cached, backdropResizeDraw(false, false, true, collapsed, dragged))
        assertEquals(BackdropResizeDraw.Cached, backdropResizeDraw(true, false, true, dragged, dragged))
        assertEquals(BackdropResizeDraw.Cached, backdropResizeDraw(true, false, true, IntSize.Zero, dragged))
        assertEquals(BackdropResizeDraw.Cached, backdropResizeDraw(true, true, true, collapsed, IntSize.Zero))
    }

    @Test fun coverKeepsTheAspectRatioAndCentres() {
        val rect = coverRect(collapsed, dragged)
        assertEquals(collapsed.width.toFloat() / collapsed.height, rect.width / rect.height, 0.001f)
        assertEquals(dragged.height.toFloat(), rect.height, 0.01f)
        assertEquals(dragged.width / 2f, rect.center.x, 0.01f)
        assertEquals(dragged.height / 2f, rect.center.y, 0.01f)
        assertEquals(true, rect.width >= dragged.width)
    }
}
