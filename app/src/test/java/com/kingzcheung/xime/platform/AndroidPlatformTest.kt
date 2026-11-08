package com.kingzcheung.xime.platform

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AndroidPlatformTest {
    private val source = File("src/main/java/com/kingzcheung/xime/platform/AndroidPlatform.kt").readText()

    /** 只写进日志、调用处不用判断的项（键盘里统一处理掉了）。 */
    private val logOnly = setOf(PlatformFeature.STRETCH_OVERSCROLL)

    @Test
    fun everyPropertyAnnotationMatchesItsFeatureLevel() {
        val property = Regex(
            """@get:ChecksSdkIntAtLeast\(api = Build\.VERSION_CODES\.(\w+)\)\s+val (\w+): Boolean = has\(PlatformFeature\.(\w+)\)""")
        val found = property.findAll(source).map { it.groupValues }.toList()
        val codes = android.os.Build.VERSION_CODES::class.java.fields.associate { it.name to it.getInt(null) }
        for ((_, code, name, feature) in found) {
            val level = codes.getValue(code)
            assertEquals("$name 的 @ChecksSdkIntAtLeast 和 PlatformFeature.$feature 不一致",
                PlatformFeature.valueOf(feature).minSdk, level)
        }
        val covered = found.map { PlatformFeature.valueOf(it[3]) }.toSet()
        assertEquals("每项能力都要有属性（或登记为只写日志）", PlatformFeature.entries.toSet() - logOnly, covered)
        assertEquals("一项能力只对应一个属性", covered.size, found.size)
    }

    @Test
    fun everyFeatureIsAboveMinSdk() {
        val minSdk = File("../gradle/libs.versions.toml").readLines()
            .first { it.startsWith("androidMinSdk") }.substringAfter('"').substringBefore('"').toInt()
        // minSdk 及以下每台设备都有，不该再判断；minSdk 提高后这里会提示删掉过时的项和对应分支。
        for (feature in PlatformFeature.entries) {
            assertTrue("${feature.name} 从 API ${feature.minSdk} 起有，已不高于 minSdk $minSdk", feature.minSdk > minSdk)
        }
    }

    @Test
    fun summaryListsWhatThisVersionHasAndLacks() {
        val android9 = PlatformFeature.summary(28)
        assertTrue(android9, android9.startsWith("API 28 | 有：无 | 没有："))
        assertTrue(android9, "window_inset_types(30)" in android9)

        val android12 = PlatformFeature.summary(31)
        val (has, lacks) = android12.substringAfter("有：").split(" | 没有：")
        assertTrue(android12, "window_blur" in has && "stretch_overscroll" in has && "gesture_insets" in has)
        assertTrue(android12, "cursor_anchor_filter(33)" in lacks && "window_visibility_listener(34)" in lacks)
        assertTrue(android12, "window_blur" !in lacks)

        val android16 = PlatformFeature.summary(36)
        assertTrue(android16, android16.endsWith("| 没有：无"))
    }
}
