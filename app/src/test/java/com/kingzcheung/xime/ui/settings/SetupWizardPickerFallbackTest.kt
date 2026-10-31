package com.kingzcheung.xime.ui.settings

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** OriginOS 6 用户反馈：第 3 步的系统输入法选择框只剩「取消」。只有 vivo / iQOO 换成去系统设置。 */
class SetupWizardPickerFallbackTest {
    @Test fun vivoAndIqooUseSystemSettings() {
        assertNotNull(imePickerFallbackHint("vivo", "vivo"))
        assertNotNull(imePickerFallbackHint("vivo", "iQOO"))
        assertNotNull(imePickerFallbackHint("VIVO", ""))
    }

    @Test fun otherRomsKeepThePicker() {
        listOf("samsung" to "samsung", "Xiaomi" to "Redmi", "HUAWEI" to "HONOR", "OPPO" to "OnePlus", "Google" to "google", "" to "")
            .forEach { (m, b) -> assertNull("$m/$b", imePickerFallbackHint(m, b)) }
    }
}
