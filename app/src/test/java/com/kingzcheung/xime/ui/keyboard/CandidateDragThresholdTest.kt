package com.kingzcheung.xime.ui.keyboard

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

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

    // 用户 10-31：距离可调，按键盘区宽或高的比例算；设置存成 20–80%，每档 10%。
    @Test fun configuredFractionMovesTheThreshold() {
        assertFalse(isCandidateDrag(Offset(-300f, 0f), keyboard, 0.3f))
        assertTrue(isCandidateDrag(Offset(-325f, 0f), keyboard, 0.3f))
        assertFalse(isCandidateDrag(Offset(-600f, -500f), keyboard, 0.8f))
        assertTrue(isCandidateDrag(Offset(0f, -561f), keyboard, 0.8f))
        assertEquals(50f, normalizeCandidateDragPercent(Float.NaN))
        assertEquals(20f, normalizeCandidateDragPercent(5f))
        assertEquals(80f, normalizeCandidateDragPercent(100f))
        assertEquals(40f, normalizeCandidateDragPercent(37f))
    }

    @Test fun unknownKeyboardAreaNeverDrags() {
        assertFalse(isCandidateDrag(Offset(-2000f, -2000f), Size.Zero))
    }

    // 用户 10-31：注释是拼音（没装释义词典）时九键拖不动了。拖动选词跟着「下一项」，不跟词典。
    @Test fun nineKeyDragFollowsTheNextKeyNotTheGlossDictionary() {
        val source = File("src/main/java/com/kingzcheung/xime/ui/keyboard/T9KeyboardLayout.kt").readText()
        assertTrue(source.contains(".dragToCandidate(nextCandidate,"))
        assertFalse(source.contains(".dragToCandidate(glossHoldEnabled"))
    }
}
