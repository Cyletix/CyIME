package com.kingzcheung.xime.settings

import org.junit.Assert.assertEquals
import org.junit.Test

/** 固定键盘宽度按屏分开（GitHub #37：折叠屏外屏调好的宽度带到内屏）。 */
class FixedKeyboardScreenTest {
    private fun resolve(own: Int?, shared: Int, sharedScreen: Int, screen: Int) =
        SettingsPreferences.resolveFixedGeometry(own, shared, sharedScreen, screen)

    @Test fun oldValuesWithoutScreenKeepWorkingEverywhere() {
        // 旧版本存的值不知道是哪块屏：单屏手机、平板升级后照旧。
        assertEquals(380, resolve(null, 380, 0, 384))
        assertEquals(0, resolve(null, 0, 0, 384))
    }

    @Test fun sameScreenUsesTheLastAdjustment() {
        assertEquals(380, resolve(null, 380, 384, 384))
        // 自己那一份比最后一次调节旧：同一块屏上以最后一次为准。
        assertEquals(380, resolve(300, 380, 384, 384))
        // 改系统显示大小（最短边差不到 15%）仍算同一块屏。
        assertEquals(380, resolve(null, 380, 384, 411))
    }

    @Test fun foldableInnerScreenFillsUntilAdjusted() {
        // 外屏（最短边约 380dp）调到最大 380；展开内屏（约 650dp）没调过：铺满，不再沿用外屏的 380。
        assertEquals(0, resolve(null, 380, 380, 650))
        // 内屏调过 600、外屏再调成 380：展开回内屏用内屏自己的 600。
        assertEquals(600, resolve(600, 380, 380, 650))
        // 内屏最后调的 600，合上回外屏用外屏自己的。
        assertEquals(380, resolve(380, 600, 650, 380))
    }
}
