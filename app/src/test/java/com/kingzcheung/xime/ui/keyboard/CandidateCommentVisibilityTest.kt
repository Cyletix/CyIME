package com.kingzcheung.xime.ui.keyboard

import org.junit.Assert.*
import org.junit.Test

class CandidateCommentVisibilityTest {
    @Test fun narrowFloatingAndPhoneWidthsHideWhileWidePanelsShow() {
        for (width in listOf(220f, 320f, 420f, 479f)) {
            assertFalse("width=$width", candidateCommentsVisible(width, 1f, 19f, true))
        }
        for (width in listOf(480f, 600f, 960f)) {
            assertTrue("width=$width", candidateCommentsVisible(width, 1f, 19f, true))
        }
    }

    @Test fun largeFontsNeedMoreSpaceButSmallFontsDoNotTurnPhonesIntoWidePanels() {
        assertFalse(candidateCommentsVisible(600f, 1.5f, 19f, true))
        assertTrue(candidateCommentsVisible(720f, 1.5f, 19f, true))
        assertFalse(candidateCommentsVisible(600f, 1f, 28f, true))
        assertTrue(candidateCommentsVisible(800f, 1f, 28f, true))
        assertFalse(candidateCommentsVisible(360f, .8f, 14f, true))
    }

    @Test fun explicitHideStillWinsOnWideScreens() {
        assertFalse(candidateCommentsVisible(1200f, 1f, 19f, false))
        assertFalse(candidateCommentsVisible(0f, 1f, 19f, true))
    }
}
