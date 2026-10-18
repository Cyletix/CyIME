package com.kingzcheung.xime.settings

import android.content.Context
import com.kingzcheung.xime.runtime.adaptation.DeviceCapabilityProbe
import com.kingzcheung.xime.runtime.adaptation.PerformanceTier
import com.kingzcheung.xime.runtime.adaptation.PerformanceTiers

/** 性能预设页管的几项。都是已有设置，预设只是一组值，不另存一份。 */
data class PerformanceSettings(
    val keyAnimation: Boolean,
    val keyGlow: Boolean,
    /** 空闲多少分钟后卸掉 SenseVoice 第二遍、保留 Paraformer；0 = 永不。 */
    val speechRefinerIdleMinutes: Int,
    /** 空闲多少分钟后卸掉全部语音模型；0 = 常驻。 */
    val speechEngineIdleMinutes: Int,
)

/**
 * 自动 / 高配 / 中配 / 低配 / 自定义。
 * 当前值和某一档完全一致就显示那一档，否则显示“自定义”；用户改任何一项都会关掉“自动”。
 */
object PerformanceProfile {
    private const val KEY_AUTO = "performance_profile_auto"

    val presets: Map<PerformanceTier, PerformanceSettings> = mapOf(
        PerformanceTier.LOW to PerformanceSettings(keyAnimation = false, keyGlow = false,
            speechRefinerIdleMinutes = 1, speechEngineIdleMinutes = 1),
        PerformanceTier.MEDIUM to PerformanceSettings(keyAnimation = true, keyGlow = false,
            speechRefinerIdleMinutes = 5, speechEngineIdleMinutes = 30),
        PerformanceTier.HIGH to PerformanceSettings(keyAnimation = true, keyGlow = true,
            speechRefinerIdleMinutes = 30, speechEngineIdleMinutes = 0),
    )

    fun read(context: Context): PerformanceSettings {
        val prefs = SettingsPreferences.getPrefsPublic(context)
        return PerformanceSettings(
            KeyEffectPreferences.animationEnabled(prefs), KeyEffectPreferences.glowEnabled(prefs),
            SettingsPreferences.getSttRefinerIdleMinutes(context), SettingsPreferences.getSttEngineIdleMinutes(context))
    }

    /** 当前值对应的档位；null = 自定义。 */
    fun matching(settings: PerformanceSettings): PerformanceTier? =
        presets.entries.firstOrNull { it.value == settings }?.key

    fun isAuto(context: Context): Boolean = SettingsPreferences.getPrefsPublic(context).getBoolean(KEY_AUTO, false)

    fun detectedTier(context: Context): PerformanceTier = PerformanceTiers.classify(DeviceCapabilityProbe.read(context))

    /** 选“自动”：按本机配置套用对应档，并记住是自动选的。 */
    fun applyAuto(context: Context): PerformanceTier {
        val tier = detectedTier(context)
        write(context, checkNotNull(presets[tier]))
        SettingsPreferences.getPrefsPublic(context).edit().putBoolean(KEY_AUTO, true).apply()
        return tier
    }

    fun applyPreset(context: Context, tier: PerformanceTier) {
        write(context, checkNotNull(presets[tier]))
        SettingsPreferences.getPrefsPublic(context).edit().putBoolean(KEY_AUTO, false).apply()
    }

    /** 单项手动改动：写入后不再算“自动”，页面按值自动显示对应档或“自定义”。 */
    fun applyCustom(context: Context, settings: PerformanceSettings) {
        write(context, settings)
        SettingsPreferences.getPrefsPublic(context).edit().putBoolean(KEY_AUTO, false).apply()
    }

    private fun write(context: Context, settings: PerformanceSettings) {
        val prefs = SettingsPreferences.getPrefsPublic(context)
        KeyEffectPreferences.setAnimationEnabled(prefs, settings.keyAnimation)
        KeyEffectPreferences.setGlowEnabled(prefs, settings.keyGlow)
        SettingsPreferences.setSttIdleLadder(context, settings.speechRefinerIdleMinutes, settings.speechEngineIdleMinutes)
    }
}
