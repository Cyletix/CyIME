package com.kingzcheung.xime.ui.keyboard

import org.junit.Assert.assertEquals
import org.junit.Test

/** 分体两半靠中缝那侧的圆角（GitHub #78）：和卡片外侧四角一致。 */
class SplitGapCornerTest {
    private val r = 48f

    @Test fun topCornerOnlyWhenTheGapReachesTheCardTop() {
        // 栏也分两侧：中缝通到卡片顶边，上角跟卡片顶角一样圆。
        assertEquals(r to 0f, SplitKeyboardGeometry.innerCornerRadii(r, roundedTop = true, roundedBottom = false,
            reachesTop = true, holeHeight = 600f))
        // 栏是整条（剪贴板预览等）：两半挂在栏下面，那里保持直角。
        assertEquals(0f to 0f, SplitKeyboardGeometry.innerCornerRadii(r, roundedTop = true, roundedBottom = false,
            reachesTop = false, holeHeight = 600f))
    }

    @Test fun cornersFollowTheCardCornerSwitches() {
        // 固定键盘关了顶部圆角：上角直角。
        assertEquals(0f to 0f, SplitKeyboardGeometry.innerCornerRadii(r, roundedTop = false, roundedBottom = false,
            reachesTop = true, holeHeight = 600f))
        // 悬浮或开了底部圆角：下角也圆。
        assertEquals(r to r, SplitKeyboardGeometry.innerCornerRadii(r, roundedTop = true, roundedBottom = true,
            reachesTop = true, holeHeight = 600f))
    }

    @Test fun shortGapSharesItsHeightBetweenTheTwoCorners() {
        assertEquals(30f to 30f, SplitKeyboardGeometry.innerCornerRadii(r, roundedTop = true, roundedBottom = true,
            reachesTop = true, holeHeight = 60f))
        assertEquals(0f to 0f, SplitKeyboardGeometry.innerCornerRadii(r, roundedTop = true, roundedBottom = true,
            reachesTop = true, holeHeight = 0f))
    }
}
