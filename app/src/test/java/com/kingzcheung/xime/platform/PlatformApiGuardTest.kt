package com.kingzcheung.xime.platform

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 系统版本只在 [AndroidPlatform] 读一次。别处再写 `Build.VERSION.SDK_INT`（或拿 VERSION_CODES 做比较），
 * 这里直接失败：先在 [PlatformFeature] 登记这项能力，调用处用 `AndroidPlatform.xxx` 判断。
 * `@RequiresApi(Build.VERSION_CODES.X)` 只是标注，允许。
 */
class PlatformApiGuardTest {
    private val root = File("src/main/java")
    private val owner = File(root, "com/kingzcheung/xime/platform/AndroidPlatform.kt").canonicalFile
    private val annotationOnly = Regex("""@(get:)?([\w.]+\.)?(RequiresApi|TargetApi|ChecksSdkIntAtLeast)\(""")

    @Test
    fun onlyAndroidPlatformReadsTheSystemVersion() {
        val offenders = root.walkTopDown()
            .filter { it.isFile && (it.extension == "kt" || it.extension == "java") && it.canonicalFile != owner }
            .flatMap { file ->
                file.readLines().mapIndexedNotNull { index, line ->
                    val code = line.substringBefore("//")
                    val raw = "SDK_INT" in code ||
                        ("VERSION_CODES" in code && !annotationOnly.containsMatchIn(code))
                    if (raw) "${file.relativeTo(root)}:${index + 1}: ${line.trim()}" else null
                }
            }.toList()
        assertTrue("改用 AndroidPlatform 的能力判断：\n" + offenders.joinToString("\n"), offenders.isEmpty())
    }
}
