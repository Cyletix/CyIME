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

    // 密度 3：48dp = 144px，手势条 24dp = 72px。
    @Test fun threeButtonNavigationKeepsTheButtonBar() {
        assertEquals(144, imeBottomInsetPx(144, tappablePx = 144, navigationMode = 0, imeNavButtons = false, gestureBarPx = 72))
        assertEquals(144, imeBottomInsetPx(144, tappablePx = 0, navigationMode = 0, imeNavButtons = false, gestureBarPx = 72))
        assertEquals(144, imeBottomInsetPx(144, tappablePx = 144, navigationMode = null, imeNavButtons = false, gestureBarPx = 72))
        // 平板 SM-X800 三键导航实测（10-31）：nav = tappable = mandatory = 90px，不变。
        assertEquals(90, imeBottomInsetPx(90, tappablePx = 90, navigationMode = 0, imeNavButtons = true, gestureBarPx = 72))
    }
    @Test fun gestureNavigationWithoutButtonsLeavesOnlyTheGestureBar() {
        assertEquals(72, imeBottomInsetPx(144, tappablePx = 0, navigationMode = 2, imeNavButtons = false, gestureBarPx = 72))
        assertEquals(48, imeBottomInsetPx(144, tappablePx = 0, navigationMode = null, imeNavButtons = false, gestureBarPx = 48))
        // 系统把输入法的 tappableElement 也给成按钮框高时，导航模式说是手势就照手势算。
        assertEquals(72, imeBottomInsetPx(144, tappablePx = 144, navigationMode = 2, imeNavButtons = false, gestureBarPx = 72))
        // 系统给的本来就比手势条小，不加高。
        assertEquals(40, imeBottomInsetPx(40, tappablePx = 0, navigationMode = 2, imeNavButtons = false, gestureBarPx = 72))
    }
    @Test fun gestureNavigationKeepsTheBarWhenTheSystemDrawsKeyboardButtonsThere() {
        assertEquals(144, imeBottomInsetPx(144, tappablePx = 0, navigationMode = 2, imeNavButtons = true, gestureBarPx = 72))
    }
    // vivo 手势导航时键盘下面有「切换输入法」按钮（10-31 用户反馈压住了）：只有三星缩到手势条。
    @Test fun onlyVerifiedVendorsShrinkToTheGestureBar() {
        assertEquals(true, gestureStripVerifiedVendor("samsung"))
        assertEquals(true, gestureStripVerifiedVendor("Samsung"))
        for (vendor in listOf("vivo", "Xiaomi", "OPPO", "HONOR", "HUAWEI", "Google", "", null)) {
            assertEquals(vendor.toString(), false, gestureStripVerifiedVendor(vendor))
        }
    }
    @Test fun noSystemBarStaysZero() {
        assertEquals(0, imeBottomInsetPx(0, tappablePx = 0, navigationMode = 2, imeNavButtons = false, gestureBarPx = 72))
    }
}
