package com.kingzcheung.xime.service

import org.junit.Assert.assertEquals
import org.junit.Test

class ImeBottomSpaceTest {
    @Test fun navigationAndTaskbarKeepTheirFullHeight() {
        for (height in listOf(16, 24, 48, 80, 128)) assertEquals(height, imeBottomSpaceDp(height * 3, 3f))
    }
    @Test fun fractionalPixelsRoundUpRatherThanLeaveAnUnpaintedStrip() {
        assertEquals(19, imeBottomSpaceDp(50, 2.75f))
    }
    @Test fun hiddenNavigationDoesNotInventBottomPadding() {
        assertEquals(0, imeBottomSpaceDp(0, 3f))
        assertEquals(0, imeBottomSpaceDp(-1, 3f))
        assertEquals(0, imeBottomSpaceDp(48, 0f))
    }
}
