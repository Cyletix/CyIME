package com.kingzcheung.xime.platform

import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceVendorTest {
    @Test fun manufacturerNamesMapToTheirFamily() {
        mapOf(
            "samsung" to DeviceVendor.SAMSUNG, "Samsung" to DeviceVendor.SAMSUNG,
            "Xiaomi" to DeviceVendor.XIAOMI, "Redmi" to DeviceVendor.XIAOMI, "POCO" to DeviceVendor.XIAOMI,
            "vivo" to DeviceVendor.VIVO, "iQOO" to DeviceVendor.VIVO,
            "OPPO" to DeviceVendor.OPPO, "OnePlus" to DeviceVendor.OPPO, "realme" to DeviceVendor.OPPO,
            "HUAWEI" to DeviceVendor.HUAWEI, "HONOR" to DeviceVendor.HONOR,
            "Google" to DeviceVendor.OTHER, "" to DeviceVendor.OTHER,
        ).forEach { (name, vendor) -> assertEquals(name, vendor, DeviceVendor.of(name)) }
        assertEquals(DeviceVendor.OTHER, DeviceVendor.of(null, null))
    }

    @Test fun manufacturerWinsAndBrandOnlyFillsAnUnknownOne() {
        assertEquals(DeviceVendor.XIAOMI, DeviceVendor.of("Xiaomi", "Redmi"))
        assertEquals(DeviceVendor.HUAWEI, DeviceVendor.of("HUAWEI", "HONOR"))
        assertEquals(DeviceVendor.HONOR, DeviceVendor.of("HONOR", "HONOR"))
        assertEquals(DeviceVendor.VIVO, DeviceVendor.of("", "vivo"))
        assertEquals(DeviceVendor.VIVO, DeviceVendor.of("unknown", "iQOO"))
        assertEquals(DeviceVendor.OTHER, DeviceVendor.of("Google", "google"))
    }
}
