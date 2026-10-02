package com.kingzcheung.xime.settings

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt-in delivery maintenance, never part of a normal test run or production APK. */
class AppearanceDeliveryTest {
    @Test fun backupAndResetAuthorizedAppearance() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("appearanceDelivery") == "backup-and-reset-20261046")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.cyletix.cyime", context.packageName)
        val prefs = context.getSharedPreferences("kime_settings", Context.MODE_PRIVATE)
        val before = prefs.all.toMap()
        val directory = File(context.getExternalFilesDir(null), "delivery-backups/20261046-${System.currentTimeMillis()}")
        assertTrue(directory.mkdirs())
        fun snapshot(values: Map<String, *>): String = JSONObject().apply {
            values.forEach { (key, value) -> put(key, JSONObject().apply {
                put("type", value?.javaClass?.simpleName)
                put("value", if (value is Set<*>) JSONArray(value.toList()) else value)
            }) }
        }.toString(2)
        File(directory, "settings-before.json").writeText(snapshot(before))
        val rawPrefs = File(context.filesDir.parentFile, "shared_prefs")
        if (rawPrefs.isDirectory) rawPrefs.copyRecursively(File(directory, "shared_prefs"))
        // Keep any custom layout source as a recovery aid; it is deliberately not edited.
        val custom = File(context.filesDir, "rime/xime.custom.yaml")
        if (custom.isFile) custom.copyTo(File(directory, "xime.custom.yaml"))
        val removed = setOf(
            "keyboard_height_dp", "keyboard_height_dp_landscape",
            "fixed_width_dp", "fixed_width_dp_landscape", "fixed_offset_x", "fixed_offset_x_landscape",
            "floating_width_dp", "floating_width_dp_landscape",
            "floating_offset_x", "floating_offset_x_landscape", "floating_offset_y", "floating_offset_y_landscape",
            "keyboard_bottom_padding_dp", "key_text_scale",
        )
        val changes = setOf("floating_mode", "floating_mode_landscape", "keyboard_opacity",
            "keyboard_opacity_landscape", "landscape_layout_v2", "keyboard_theme")
        val edit = prefs.edit()
        removed.forEach { edit.remove(it) }
        assertTrue(edit.putBoolean("landscape_layout_v2", true)
            .putBoolean("floating_mode", false).putBoolean("floating_mode_landscape", false)
            .putFloat("keyboard_opacity", 1f).putFloat("keyboard_opacity_landscape", 1f)
            .putString("keyboard_theme", "pure_black").commit())
        val after = prefs.all
        before.filterKeys { it !in removed && it !in changes }.forEach { (key, value) ->
            assertEquals("Unrelated preference changed: $key", value, after[key])
        }
        removed.forEach { assertTrue("Old override remained: $it", !prefs.contains(it)) }
        File(directory, "settings-after.json").writeText(snapshot(after))
        File(context.getExternalFilesDir(null), "delivery-backups/latest-20261046.txt").writeText(directory.absolutePath)
        // No preference values are emitted into instrumentation output.
        android.util.Log.i("AppearanceDelivery", "Appearance backup saved: ${directory.absolutePath}")
    }
}
