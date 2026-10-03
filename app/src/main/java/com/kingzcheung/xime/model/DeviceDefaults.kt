package com.kingzcheung.xime.model

import android.content.Context
import com.kingzcheung.xime.runtime.adaptation.DeviceCapabilities
import com.kingzcheung.xime.runtime.adaptation.DeviceCapabilityProbe
import com.kingzcheung.xime.runtime.adaptation.DeviceKeyEffectDefaults
import com.kingzcheung.xime.settings.KeyEffectPreferences
import com.kingzcheung.xime.settings.SettingsPreferences
import com.kingzcheung.xime.speech.SpeechModelCatalog

/** A conservative first-run recommendation, not a benchmark or an ongoing override. */
object DeviceDefaults {
    internal const val VERSION = "device_model_defaults_version"
    internal const val TIER = "device_model_defaults_tier"
    private const val PREDICTION = "device_default_prediction_model"
    private const val VOICE = "device_default_voice_model"

    fun initialize(context: Context) = initialize(context) { DeviceCapabilityProbe.read(context) }

    /** Must run before other first-run preference writers. One atomic defaults snapshot. */
    @Synchronized internal fun initialize(context: Context, probe: () -> DeviceCapabilities) {
        val prefs = SettingsPreferences.getPrefsPublic(context)
        if (prefs.contains(VERSION)) return
        val fresh = prefs.all.isEmpty() && listOf("asr_model", "default_models", "bundled_models").all {
            context.getSharedPreferences(it, Context.MODE_PRIVATE).all.isEmpty()
        }
        val editor = prefs.edit()
        if (fresh) {
            val hardware = probe()
            val profile = DeviceModelProfiles.choose(hardware)
            val effects = DeviceKeyEffectDefaults.choose(hardware)
            editor.putString(TIER, profile.tier.name)
                .putString(PREDICTION, profile.predictionModel)
                .putString(VOICE, profile.voiceModel)
                .putBoolean(KeyEffectPreferences.GLOW, effects.glow)
                .putBoolean(KeyEffectPreferences.ANIMATION, effects.animation)
                .putLong("device_initial_memory_bytes", hardware.totalMemoryBytes ?: -1)
                .putInt("device_initial_cores", hardware.processors ?: -1)
                .putLong("device_initial_max_cpu_khz", hardware.maxCpuKHz ?: -1)
                .putString("device_initial_low_ram", hardware.lowRam?.toString() ?: "unknown")
                .putInt("device_initial_sdk", hardware.sdk)
                .putString("device_initial_abis", hardware.abis.joinToString(","))
        } else {
            // Missing keys on an existing installation do not prove the user wanted new defaults.
            editor.putString(TIER, "PRESERVED")
        }
        check(editor.putInt(VERSION, 1).commit()) { "无法保存设备模型默认配置" }
    }

    fun predictionModel(context: Context): String = SettingsPreferences.getPrefsPublic(context)
        .getString(PREDICTION, DeviceModelProfiles.BASE) ?: DeviceModelProfiles.BASE

    fun voiceModel(context: Context): String {
        val initial = SettingsPreferences.getPrefsPublic(context).getString(VOICE, null)
        // This key stores an automatic recommendation. Explicit selections live in asr_model
        // and take priority in AsrModelManager, including a deliberate Zipformer selection.
        return when (initial) {
            null, SpeechModelCatalog.ZIPFORMER, SpeechModelCatalog.ZIPFORMER_TWO_PASS -> SpeechModelCatalog.TWO_PASS
            else -> initial
        }
    }

    fun supportsRefinement(lowRam: Boolean, memoryBytes: Long, cores: Int): Boolean =
        !lowRam && memoryBytes >= 6L * 1024 * 1024 * 1024 && cores >= 4

    fun supportsRefinement(context: Context): Boolean {
        return DeviceModelProfiles.choose(DeviceCapabilityProbe.read(context)).tier == DeviceModelTier.ENHANCED
    }
}
