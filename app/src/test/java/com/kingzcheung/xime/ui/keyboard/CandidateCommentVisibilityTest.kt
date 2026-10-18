package com.kingzcheung.xime.ui.keyboard

import org.junit.Assert.*
import org.junit.Test
import com.kingzcheung.xime.settings.CandidateCommentMode

class CandidateCommentVisibilityTest {
    @Test fun narrowFloatingAndPhoneWidthsHideWhileWidePanelsShow() {
        for (width in listOf(220f, 320f, 420f, 479f)) {
            assertFalse("width=$width", candidateCommentsVisible(width, 1f, 19f, CandidateCommentMode.AUTO))
        }
        for (width in listOf(480f, 600f, 960f)) {
            assertTrue("width=$width", candidateCommentsVisible(width, 1f, 19f, CandidateCommentMode.AUTO))
        }
    }

    @Test fun largeFontsNeedMoreSpaceButSmallFontsDoNotTurnPhonesIntoWidePanels() {
        assertFalse(candidateCommentsVisible(600f, 1.5f, 19f, CandidateCommentMode.AUTO))
        assertTrue(candidateCommentsVisible(720f, 1.5f, 19f, CandidateCommentMode.AUTO))
        assertFalse(candidateCommentsVisible(600f, 1f, 28f, CandidateCommentMode.AUTO))
        assertTrue(candidateCommentsVisible(800f, 1f, 28f, CandidateCommentMode.AUTO))
        assertFalse(candidateCommentsVisible(360f, .8f, 14f, CandidateCommentMode.AUTO))
    }

    @Test fun explicitShowWinsOnPhoneFloatingAndLargeFontWidths() {
        for (width in listOf(220f, 320f, 480f, 720f)) {
            assertTrue(candidateCommentsVisible(width, 2f, 28f, CandidateCommentMode.SHOW))
        }
    }

    @Test fun explicitHideStillWinsOnWideScreens() {
        assertFalse(candidateCommentsVisible(1200f, 1f, 19f, CandidateCommentMode.HIDE))
        assertFalse(candidateCommentsVisible(0f, 1f, 19f, CandidateCommentMode.AUTO))
    }
}
