package com.kingzcheung.xime.service

import org.junit.Assert.assertEquals
import org.junit.Test
import com.kingzcheung.xime.platform.DeviceVendor

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
        assertEquals(true, gestureStripVerifiedVendor(DeviceVendor.of("samsung")))
        assertEquals(true, gestureStripVerifiedVendor(DeviceVendor.of("Samsung")))
        for (vendor in listOf("vivo", "Xiaomi", "OPPO", "HONOR", "HUAWEI", "Google", "", null)) {
            assertEquals(vendor.toString(), false, gestureStripVerifiedVendor(DeviceVendor.of(vendor)))
        }
    }
    @Test fun noSystemBarStaysZero() {
        assertEquals(0, imeBottomInsetPx(0, tappablePx = 0, navigationMode = 2, imeNavButtons = false, gestureBarPx = 72))
    }

    // 调节键盘的底边把手要避开的底部手势区（密度 3：最小 32dp = 96px）。
    @Test fun gestureZoneFollowsSystemNavigationMode() {
        assertEquals(144, bottomGestureZoneHeightPx(144, tappablePx = 0, navigationMode = 2, vendorGesture = null, minZonePx = 96))
        assertEquals(null, bottomGestureZoneHeightPx(144, tappablePx = 144, navigationMode = 0, vendorGesture = null, minZonePx = 96))
        assertEquals(null, bottomGestureZoneHeightPx(144, tappablePx = 144, navigationMode = 0, vendorGesture = false, minZonePx = 96))
    }
    // 10-31 用户反馈：拖底部留白触发全面屏手势。厂商手势不写 navigation_mode（仍为 0）时按厂商开关算。
    @Test fun vendorGestureCountsEvenWhenNavigationModeSaysButtons() {
        assertEquals(144, bottomGestureZoneHeightPx(144, tappablePx = 144, navigationMode = 0, vendorGesture = true, minZonePx = 96))
    }
    @Test fun missingOrTinyGestureZoneUsesTheMinimum() {
        assertEquals(96, bottomGestureZoneHeightPx(0, tappablePx = 0, navigationMode = 2, vendorGesture = null, minZonePx = 96))
        assertEquals(96, bottomGestureZoneHeightPx(30, tappablePx = 0, navigationMode = 0, vendorGesture = true, minZonePx = 96))
    }
    @Test fun unknownModeFallsBackToTappableButtons() {
        assertEquals(96, bottomGestureZoneHeightPx(60, tappablePx = 0, navigationMode = null, vendorGesture = null, minZonePx = 96))
        assertEquals(null, bottomGestureZoneHeightPx(60, tappablePx = 144, navigationMode = null, vendorGesture = null, minZonePx = 96))
        assertEquals(null, bottomGestureZoneHeightPx(0, tappablePx = 0, navigationMode = null, vendorGesture = null, minZonePx = 96))
    }
    @Test fun vendorGestureKeysPerManufacturer() {
        val settings = mapOf(
            (true to "force_fsg_nav_bar") to 1,
            (false to "navigation_gesture_on") to 1,
            (false to "hide_navigationbar_enable") to 0,
            (false to "secure_gesture_navigation") to 1,
            (true to "navigation_bar_gesture_while_hidden") to 1,
        )
        val read: (Boolean, String) -> Int? = { global, key -> settings[global to key] }
        assertEquals(true, vendorGestureNavigationOn(DeviceVendor.of("Xiaomi"), read))
        assertEquals(true, vendorGestureNavigationOn(DeviceVendor.of("vivo"), read))
        assertEquals(false, vendorGestureNavigationOn(DeviceVendor.of("OPPO"), read))
        assertEquals(true, vendorGestureNavigationOn(DeviceVendor.of("HONOR"), read))
        assertEquals(true, vendorGestureNavigationOn(DeviceVendor.of("samsung"), read))
        // 平板实测：三键导航时三星这项为 0。
        assertEquals(false, vendorGestureNavigationOn(DeviceVendor.of("samsung")) { global, key ->
            if (global && key == "navigation_bar_gesture_while_hidden") 0 else null })
        assertEquals(null, vendorGestureNavigationOn(DeviceVendor.of("Google"), read))
        assertEquals(null, vendorGestureNavigationOn(DeviceVendor.of(null), read))
        assertEquals(null, vendorGestureNavigationOn(DeviceVendor.of("vivo")) { _, _ -> null })
    }
}
