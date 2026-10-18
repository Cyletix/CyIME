package com.kingzcheung.xime.ui.keyboard

import android.content.Context
import android.os.Build
import android.util.DisplayMetrics
import android.view.Display
import android.view.WindowManager

/** Both service sizing and the Compose docking preview read the same physical display. */
internal fun keyboardPhysicalMetrics(
    context: Context,
    attachedDisplay: Display? = null,
    pixelsPerDp: Float = context.resources.displayMetrics.density,
): KeyboardPhysicalMetrics? = KeyboardDisplayMetricsCache.read(context, attachedDisplay, pixelsPerDp)

private object KeyboardDisplayMetricsCache {
    private data class Key(
        val displayId: Int, val rotation: Int, val modeId: Int,
        val modeWidth: Int, val modeHeight: Int,
        val density: Float, val resourceXDpi: Float, val resourceYDpi: Float,
        val windowWidthDp: Int, val windowHeightDp: Int,
    )
    private var lastKey: Key? = null
    private var lastValue: KeyboardPhysicalMetrics? = null

    @Synchronized
    @Suppress("DEPRECATION") // Read physical dpi only, never use full-display bounds to size the keyboard.
    fun read(context: Context, attachedDisplay: Display?, density: Float): KeyboardPhysicalMetrics? = runCatching {
        val display = attachedDisplay ?: if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) context.display
            else context.getSystemService(WindowManager::class.java)?.defaultDisplay
        if (display == null || !display.isValid) return@runCatching null
        val resources = context.resources
        val reported = resources.displayMetrics
        val config = resources.configuration
        val mode = display.mode
        val key = Key(display.displayId, display.rotation, mode.modeId, mode.physicalWidth, mode.physicalHeight,
            density, reported.xdpi, reported.ydpi, config.screenWidthDp, config.screenHeightDp)
        if (key == lastKey) return@runCatching lastValue
        val physical = DisplayMetrics()
        display.getRealMetrics(physical)
        val value = KeyboardPhysicalMetrics.from(density, physical.xdpi)
        // Keep only numbers for the current display/configuration, never a Context or a View.
        lastKey = key
        lastValue = value
        value
    }.getOrNull()
}
