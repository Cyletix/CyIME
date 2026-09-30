package com.kingzcheung.xime.model

import android.content.Context
import com.kingzcheung.xime.settings.SettingsPreferences
import com.kingzcheung.xime.speech.AsrModelManager
import com.kingzcheung.xime.speech.SpeechModelCatalog
import org.json.JSONArray

/** Offline edition: install on IO, then activate a usable local configuration once.
 * User changes/deletions after setup are preserved, including on subsequent upgrades. */
object BundledModelInstaller {
    val modelIds = setOf("ochwpro", "predictive-text-base", SpeechModelCatalog.PARAFORMER, SpeechModelCatalog.SENSEVOICE)
    suspend fun install(context: Context) {
        val prefs = context.getSharedPreferences("bundled_models", Context.MODE_PRIVATE)
        val manifest = context.assets.open("bundled-models/manifest.json").bufferedReader().use { JSONArray(it.readText()) }
        val entries = (0 until manifest.length()).map { manifest.getJSONObject(it) }
        check(entries.map { it.getString("id") }.toSet() == modelIds) {
            "内置模型清单与应用不匹配"
        }
        for (entry in entries.sortedBy { when (it.getString("id")) {
            "ochwpro" -> 0; "predictive-text-base" -> 1; SpeechModelCatalog.PARAFORMER -> 2; else -> 3
        } }) {
            val id = entry.getString("id")
            val version = entry.getString("version")
            if (prefs.getString(id, null) == version) continue
            ModelManager.installBundled(context, entry)
            check(prefs.edit().putString(id, version).commit())
        }
        if (!prefs.getBoolean("defaults_applied", false)) {
            check(modelIds.all { ModelManager.isModelReady(context, it) })
            // Activation defaults are shared with the standard edition. Never overwrite a
            // setting changed while bundled files were still being installed.
            val high = DeviceDefaults.supportsRefinement(context)
            AsrModelManager(context).setModel(if (high) SpeechModelCatalog.TWO_PASS else SpeechModelCatalog.PARAFORMER)
            if (!SettingsPreferences.getPrefsPublic(context).contains("key_glow_enabled")) {
                SettingsPreferences.getPrefsPublic(context).edit().putBoolean("key_glow_enabled", high).apply()
            }
            check(prefs.edit().putBoolean("defaults_applied", true).commit())
        }
        if (prefs.getInt("edition_profile", 0) < 2) {
            // Upgrade only the old bundled defaults; retain explicit alternative model choices.
            if (SettingsPreferences.getPredictionSelectedModel(context) == "predictive-text-small") {
                SettingsPreferences.setPredictionSelectedModel(context, "predictive-text-base")
            }
            val manager = AsrModelManager(context)
            if (manager.getSelectedModelId() == SpeechModelCatalog.ZIPFORMER_TWO_PASS) {
                manager.setModel(if (DeviceDefaults.supportsRefinement(context)) SpeechModelCatalog.TWO_PASS else SpeechModelCatalog.PARAFORMER)
            }
            check(prefs.edit().putInt("edition_profile", 2).commit())
        }
    }
}
