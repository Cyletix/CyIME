package com.kingzcheung.xime.ui.theme

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PanelBackdropBlurTest {
    @Test fun aPanelOnASeeThroughKeyboardBlursTheAppBehind() {
        assertTrue(panelNeedsBackdropBlur(glassEnabled = false, panelOpen = true, keyboardOpacity = 0.5f))
    }

    @Test fun lettersKeepTheirPlainTransparencyAndOpaqueOrGlassKeyboardsAreUnchanged() {
        assertFalse(panelNeedsBackdropBlur(glassEnabled = false, panelOpen = false, keyboardOpacity = 0.5f))
        assertFalse(panelNeedsBackdropBlur(glassEnabled = false, panelOpen = true, keyboardOpacity = 1f))
        // Glass already blurs behind the whole keyboard.
        assertFalse(panelNeedsBackdropBlur(glassEnabled = true, panelOpen = true, keyboardOpacity = 0.5f))
    }
}
