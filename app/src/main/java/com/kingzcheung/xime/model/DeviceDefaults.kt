package com.kingzcheung.xime.model

import android.app.ActivityManager
import android.content.Context

/** A conservative first-run recommendation, not a benchmark or an ongoing override. */
object DeviceDefaults {
    fun initialize(context: Context) {
        val prefs = com.kingzcheung.xime.settings.SettingsPreferences.getPrefsPublic(context)
        if (!prefs.contains("key_glow_enabled")) {
            prefs.edit().putBoolean("key_glow_enabled", supportsRefinement(context)).apply()
        }
    }

    fun supportsRefinement(lowRam: Boolean, memoryBytes: Long, cores: Int): Boolean =
        !lowRam && memoryBytes >= 6L * 1024 * 1024 * 1024 && cores >= 4

    fun supportsRefinement(context: Context): Boolean {
        val manager = context.getSystemService(ActivityManager::class.java)
        val memory = ActivityManager.MemoryInfo().also(manager::getMemoryInfo)
        return supportsRefinement(manager.isLowRamDevice, memory.totalMem, Runtime.getRuntime().availableProcessors())
    }
}
