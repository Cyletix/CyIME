package com.kingzcheung.xime.platform

/**
 * 手机厂商，按「各家系统的做法一样」归类：同一类走同一条适配分支。
 *
 * 规则（`PlatformApiGuardTest` 会查）：只有 [AndroidPlatform] 读系统给的厂商名和品牌，
 * 别处用 `AndroidPlatform.vendor == DeviceVendor.SAMSUNG` 这样判断。新发现某家系统要单独处理，
 * 先在这里加一类（或给已有的类加厂商名），再在调用处用它。
 */
enum class DeviceVendor(val label: String, private vararg val names: String) {
    SAMSUNG("三星", "samsung"),
    XIAOMI("小米", "xiaomi", "redmi", "poco"),
    VIVO("vivo", "vivo", "iqoo"),
    OPPO("OPPO", "oppo", "oneplus", "realme"),
    HUAWEI("华为", "huawei"),
    HONOR("荣耀", "honor"),
    OTHER("其他");

    companion object {
        /** 先看厂商名；厂商名不认识时再看品牌（个别机型厂商写代工厂、品牌才是真名）。 */
        fun of(manufacturer: String?, brand: String? = null): DeviceVendor =
            match(manufacturer) ?: match(brand) ?: OTHER

        private fun match(name: String?): DeviceVendor? {
            val key = name?.trim()?.lowercase().orEmpty()
            if (key.isEmpty()) return null
            return entries.firstOrNull { key in it.names }
        }
    }
}
