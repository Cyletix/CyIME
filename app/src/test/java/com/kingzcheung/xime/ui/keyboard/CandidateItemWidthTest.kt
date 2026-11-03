package com.kingzcheung.xime.ui.keyboard

import org.junit.Assert.assertEquals
import org.junit.Test

/** 量候选宽度要和画出来的一样：纠错候选量纠错那段。 */
class CandidateItemWidthTest {
    @Test fun secondaryTextIsTheCommentWithoutCorrection() {
        assertEquals("yong", candidateSecondaryText("yong", "").text)
        assertEquals("", candidateSecondaryText("", "").text)
    }

    @Test fun secondaryTextIsTheCorrectionWhenThereIsOne() {
        // 注释不显示（如窄屏）时纠错照样画出来，宽度也要算上。
        assertEquals("yong hao", candidateSecondaryText("", "yogn=yong hao").text)
        assertEquals("yong", candidateSecondaryText("zong", "yogn=yong").text)
    }
}
