package com.kingzcheung.xime.runtime.adaptation

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.SystemClock
import java.io.File
import java.io.IOException

/** Shared static facts for first-run defaults and runtime observation; no model/UI decisions. */
internal object DeviceCapabilityProbe {
    fun read(context: Context): DeviceCapabilities {
        fun <T> safe(action: () -> T?): T? = try { action() } catch (_: RuntimeException) { null }
        val manager = safe { context.getSystemService(ActivityManager::class.java) }
        val memory = safe { manager?.let { ActivityManager.MemoryInfo().also(it::getMemoryInfo).totalMem.takeIf { bytes -> bytes > 0 } } }
        return DeviceCapabilities(
            SystemClock.elapsedRealtime(), Build.VERSION.SDK_INT, Build.SUPPORTED_ABIS.toList(),
            safe { manager?.isLowRamDevice }, memory,
            safe { Runtime.getRuntime().availableProcessors().takeIf { it > 0 } },
            readMaxCpuKHz(File("/sys/devices/system/cpu")),
        )
    }

    /** Denied, missing or partial frequency data remains unknown; never substitute zero. */
    fun readMaxCpuKHz(root: File): Long? {
        fun children(directory: File): List<File> = try { directory.listFiles()?.toList().orEmpty() }
            catch (_: SecurityException) { emptyList() }
        fun frequency(file: File): Long? = try {
            file.bufferedReader().use { it.readLine()?.trim()?.toLongOrNull()?.takeIf { value -> value > 0 } }
        } catch (_: IOException) { null } catch (_: SecurityException) { null }
        // Policy directories also represent offline cores; do not mistake little-core speed for the maximum.
        val policies = children(File(root, "cpufreq")).filter { it.name.matches(Regex("policy\\d+")) }
        val perCore = children(root).filter { it.name.matches(Regex("cpu\\d+")) }.map { File(it, "cpufreq") }
        for (directories in listOf(policies, perCore)) {
            if (directories.isEmpty()) continue
            val frequencies = directories.map { frequency(File(it, "cpuinfo_max_freq")) }
            if (frequencies.all { it != null }) return frequencies.filterNotNull().maxOrNull()
        }
        return null
    }
}
