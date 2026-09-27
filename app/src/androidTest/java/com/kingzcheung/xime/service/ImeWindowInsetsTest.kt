package com.kingzcheung.xime.service

import android.graphics.Insets
import android.view.WindowInsets
import androidx.test.filters.SdkSuppress
import org.junit.Assert.assertEquals
import org.junit.Test

@SdkSuppress(minSdkVersion = 30)
class ImeWindowInsetsTest {
    @Test fun tabletTaskbarIsNotReducedToTheSmallerGestureArea() {
        val insets = WindowInsets.Builder()
            .setInsets(WindowInsets.Type.navigationBars(), Insets.of(0,0,0,48))
            .setInsets(WindowInsets.Type.mandatorySystemGestures(), Insets.of(0,0,0,24))
            .setInsets(WindowInsets.Type.tappableElement(), Insets.of(0,0,0,80)).build()
        assertEquals(80, extractBottomInset(insets))
    }
    @Test fun gestureFallbackAndHiddenBarsRemainDistinct() {
        assertEquals(32, extractBottomInset(WindowInsets.Builder()
            .setInsets(WindowInsets.Type.systemGestures(), Insets.of(0,0,0,32)).build()))
        assertEquals(0, extractBottomInset(WindowInsets.Builder().build()))
    }
}
