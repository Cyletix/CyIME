package com.kingzcheung.xime.ui.keyboard

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 0 键拖动选词：离按下点超过键盘区宽或高的一半才算拖动，之内都是键自己的滑动（用户 10-25）。 */
class CandidateDragThresholdTest {
    private val keyboard = Size(1080f, 700f)

    @Test fun shortAndLongLeftSwipesStayOnTheKey() {
        assertFalse(isCandidateDrag(Offset(-150f, 0f), keyboard))
        assertFalse(isCandidateDrag(Offset(-540f, -20f), keyboard))
    }

    @Test fun pastHalfWidthOrHalfHeightIsADrag() {
        assertTrue(isCandidateDrag(Offset(-541f, 0f), keyboard))
        assertTrue(isCandidateDrag(Offset(-100f, -351f), keyboard))
    }

    @Test fun unknownKeyboardAreaNeverDrags() {
        assertFalse(isCandidateDrag(Offset(-2000f, -2000f), Size.Zero))
    }
}
