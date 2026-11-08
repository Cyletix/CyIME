package com.kingzcheung.xime.ui.settings

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import com.kingzcheung.xime.platform.DeviceVendor

/** OriginOS 6 用户反馈：第 3 步的系统输入法选择框只剩「取消」。只有 vivo / iQOO 换成去系统设置。 */
class SetupWizardPickerFallbackTest {
    @Test fun vivoAndIqooUseSystemSettings() {
        assertNotNull(imePickerFallbackHint(DeviceVendor.of("vivo", "vivo")))
        assertNotNull(imePickerFallbackHint(DeviceVendor.of("vivo", "iQOO")))
        assertNotNull(imePickerFallbackHint(DeviceVendor.of("VIVO", "")))
    }

    @Test fun otherRomsKeepThePicker() {
        listOf("samsung" to "samsung", "Xiaomi" to "Redmi", "HUAWEI" to "HONOR", "OPPO" to "OnePlus", "Google" to "google", "" to "")
            .forEach { (m, b) -> assertNull("$m/$b", imePickerFallbackHint(DeviceVendor.of(m, b))) }
    }
}
