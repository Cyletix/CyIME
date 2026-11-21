package com.kingzcheung.xime.ui.keyboard

import android.app.Application
import android.text.style.BackgroundColorSpan
import android.text.style.UnderlineSpan
import android.widget.Magnifier
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kingzcheung.xime.service.SENTENCE_CODE_HIGHLIGHT_COLOR
import com.kingzcheung.xime.service.sentenceCodeHighlight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements

@RunWith(AndroidJUnit4::class)
@Config(
    sdk = [34],
    application = Application::class,
    qualifiers = "w600dp-h400dp-xhdpi",
    shadows = [CandidateLongPressTest.NoOpMagnifierShadow::class],
)
class CandidateLongPressTest {
    @Implements(Magnifier::class)
    class NoOpMagnifierShadow {
        @Implementation fun show(sourceCenterX: Float, sourceCenterY: Float) {}
        @Implementation fun show(sourceCenterX: Float, sourceCenterY: Float, magnifierCenterX: Float, magnifierCenterY: Float) {}
        @Implementation fun dismiss() {}
        @Implementation fun update() {}
    }

    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test fun sentenceCodeHighlightKeepsUniformCharacterSpansAndUnderline() {
        val first = sentenceCodeHighlight("nihao", 0, 2)
        val second = sentenceCodeHighlight("nihao", 2, 5)
        for ((styled, from, to) in listOf(Triple(first, 0, 2), Triple(second, 2, 5))) {
            val bgSpans = styled.getSpans(0, styled.length, BackgroundColorSpan::class.java)
            val underlineSpans = styled.getSpans(0, styled.length, UnderlineSpan::class.java)
            assertEquals(5, bgSpans.size)
            assertEquals(1, underlineSpans.size)
            assertEquals(0, styled.getSpanStart(underlineSpans[0]))
            assertEquals(5, styled.getSpanEnd(underlineSpans[0]))
            bgSpans.forEach { span ->
                val start = styled.getSpanStart(span)
                val end = styled.getSpanEnd(span)
                assertEquals(1, end - start)
                val expected = if (start < to && end > from) SENTENCE_CODE_HIGHLIGHT_COLOR else android.graphics.Color.TRANSPARENT
                assertEquals(expected, span.backgroundColor)
            }
        }
        assertEquals(
            first.getSpans(0, first.length, BackgroundColorSpan::class.java).map { first.getSpanStart(it) to first.getSpanEnd(it) },
            second.getSpans(0, second.length, BackgroundColorSpan::class.java).map { second.getSpanStart(it) to second.getSpanEnd(it) },
        )
    }

    @Test fun regularCandidateDelaysPinPopupUntilExtendedLongPress() {
        var clicks = 0
        var longClicks = 0
        rule.setContent {
            Row {
                CandidateItem(
                    text = "测试",
                    index = 1,
                    onClick = { clicks++ },
                    onLongClick = { longClicks++ },
                    textColor = Color.Black,
                    modifier = Modifier.testTag("cand"),
                )
            }
        }
        rule.onNodeWithTag("cand").performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(650L)
        rule.waitForIdle()
        assertEquals(0, longClicks)
        rule.onNodeWithTag("cand").performTouchInput { up() }
        rule.waitForIdle()
        assertEquals(0, longClicks)
        assertEquals(1, clicks)

        clicks = 0
        rule.onNodeWithTag("cand").performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(CANDIDATE_LONG_PRESS_TIMEOUT_MS + 100L)
        rule.waitForIdle()
        assertEquals(1, longClicks)
        rule.onNodeWithTag("cand").performTouchInput { up() }
        rule.waitForIdle()
        assertEquals(1, longClicks)
        assertEquals(0, clicks)
    }

    @Test fun sentenceCandidateSupportsSingleCharHoldBeforeExtendedLongPress() {
        var clicks = 0
        var longClicks = 0
        var selectedRange: Pair<Int, Int>? = null
        var dragEnds = 0
        rule.setContent {
            Row {
                CandidateItem(
                    text = "今天你好",
                    index = 0,
                    onClick = { clicks++ },
                    onLongClick = { longClicks++ },
                    onSentenceSelection = { start, end -> selectedRange = start to end },
                    onSentenceSelectionEnd = { dragEnds++ },
                    textColor = Color.Black,
                    modifier = Modifier.testTag("sentence"),
                )
            }
        }
        // Hold past system long-press (~400ms) for magnifier, lift before extended 1.5s popup -> single-char local edit.
        rule.onNodeWithTag("sentence").performTouchInput { down(centerLeft) }
        rule.mainClock.advanceTimeBy(650L)
        rule.waitForIdle()
        assertEquals(0, longClicks)
        rule.onNodeWithTag("sentence").performTouchInput { up() }
        rule.waitForIdle()
        assertEquals(0, longClicks)
        assertEquals(0, clicks)
        assertEquals(0 to 1, selectedRange)
        assertTrue(dragEnds >= 1)

        // Hold stationary past extended 1.5s popup -> triggers pin/forget popup cleanly without sentence edit.
        selectedRange = null
        rule.onNodeWithTag("sentence").performTouchInput { down(centerLeft) }
        rule.mainClock.advanceTimeBy(CANDIDATE_LONG_PRESS_TIMEOUT_MS + 100L)
        rule.waitForIdle()
        assertEquals(1, longClicks)
        rule.onNodeWithTag("sentence").performTouchInput { up() }
        rule.waitForIdle()
        assertEquals(1, longClicks)
        assertEquals(0, clicks)
        assertNull(selectedRange)
    }
}
