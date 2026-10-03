package com.kingzcheung.xime.model

import android.content.Context
import com.kingzcheung.xime.speech.SpeechModelCatalog
import org.json.JSONArray

/** Offline edition: install on IO, then activate a usable local configuration once.
 * User changes/deletions after setup are preserved, including on subsequent upgrades. */
object BundledModelInstaller {
    val modelIds = setOf("ochwpro", DeviceModelProfiles.SMALL, DeviceModelProfiles.BASE,
        SpeechModelCatalog.PARAFORMER, SpeechModelCatalog.SENSEVOICE)
    suspend fun install(context: Context) {
        DeviceDefaults.initialize(context)
        val prefs = context.getSharedPreferences("bundled_models", Context.MODE_PRIVATE)
        val manifest = context.assets.open("bundled-models/manifest.json").bufferedReader().use { JSONArray(it.readText()) }
        val entries = (0 until manifest.length()).map { manifest.getJSONObject(it) }
        check(entries.map { it.getString("id") }.toSet() == modelIds) {
            "内置模型清单与应用不匹配"
        }
        for (entry in entries.sortedBy { when (it.getString("id")) {
            "ochwpro" -> 0; DeviceDefaults.predictionModel(context) -> 1; SpeechModelCatalog.PARAFORMER -> 2; else -> 3
        } }) {
            val id = entry.getString("id")
            val version = entry.getString("version")
            if (prefs.getString(id, null) == version) continue
            ModelManager.installBundled(context, entry)
            check(prefs.edit().putString(id, version).commit())
        }
        if (!prefs.getBoolean("defaults_applied", false)) {
            check(modelIds.all { ModelManager.isModelReady(context, it) })
            // Both editions use the same saved first-run defaults. Copying files never selects models.
            check(prefs.edit().putBoolean("defaults_applied", true).commit())
        }
        if (prefs.getInt("edition_profile", 0) < 2) {
            // Legacy small selections cannot be distinguished from manual choices. Preserve them.
            check(prefs.edit().putInt("edition_profile", 2).commit())
        }
    }
}
