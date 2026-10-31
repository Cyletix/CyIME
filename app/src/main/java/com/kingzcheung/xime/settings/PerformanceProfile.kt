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

    /** 空闲释放时间的几个档：1、5、10、30、60 分钟和“永不”（0）。明细滑条和 [score] 共用。 */
    val idleLevels = listOf(1, 5, 10, 30, 60, 0)

    /** 落在第几档；旧设置里不在档上的值算最接近的那档（与滑条显示一致）。 */
    fun idleLevelIndex(minutes: Int): Int = idleLevels.indexOf(minutes).takeIf { it >= 0 }
        ?: idleLevels.indices.filter { idleLevels[it] != 0 }.minBy { kotlin.math.abs(idleLevels[it] - minutes) }

    /**
     * 配置高低，0～100：0 = 这页能调到的最省（低配），100 = 全开。按这页管的几项算：
     * 语音模型全部释放前常驻多久 40 分，二次校正模型常驻多久 30 分（最多跟全部释放一样久），
     * 按键动效 15 分，炫光 15 分；释放时间按滑条档位等分。三档都用同一算法，所以选中某档时正好落在那档的刻度上。
     */
    fun score(settings: PerformanceSettings): Int {
        val top = (idleLevels.size - 1).toDouble()
        val engine = idleLevelIndex(settings.speechEngineIdleMinutes) / top
        // 全部释放时校正模型也一起卸掉，比全部释放更晚的设置不多算。
        val refiner = minOf(idleLevelIndex(settings.speechRefinerIdleMinutes) / top, engine)
        val points = 40 * engine + 30 * refiner +
            (if (settings.keyAnimation) 15 else 0) + (if (settings.keyGlow) 15 else 0)
        return Math.round(points).toInt().coerceIn(0, 100)
    }

    /** 当前值对应的档位；null = 自定义。 */
    fun matching(settings: PerformanceSettings): PerformanceTier? =
        presets.entries.firstOrNull { it.value == settings }?.key

    /**
     * “自动”只在当前值仍是本机档位那一组时成立：在「按键效果」里单独改了动效、炫光，或别处改了语音释放，
     * 页面就显示“自定义”，不会一边勾着自动一边用着另一组值。
     */
    fun isAuto(context: Context): Boolean = isAuto(
        SettingsPreferences.getPrefsPublic(context).getBoolean(KEY_AUTO, false), read(context), detectedTier(context))

    fun isAuto(chosenAuto: Boolean, settings: PerformanceSettings, detected: PerformanceTier): Boolean =
        chosenAuto && matching(settings) == detected

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

    /**
     * 新装默认就是“自动”：按键效果本来就按同一档位初始化（[com.kingzcheung.xime.runtime.adaptation.DeviceKeyEffectDefaults]），
     * 语音释放也写同一档，否则新装就显示“自定义”，好像用户改过。
     */
    internal fun writeFirstRunAuto(editor: android.content.SharedPreferences.Editor, tier: PerformanceTier) {
        val settings = checkNotNull(presets[tier])
        // 动效、炫光由 DeviceDefaults 按 DeviceKeyEffectDefaults 写，与这一档相同（PerformanceProfileTest 检查）。
        editor.putInt(SettingsPreferences.KEY_STT_REFINER_IDLE_MINUTES, settings.speechRefinerIdleMinutes)
            .putInt(SettingsPreferences.KEY_STT_ENGINE_IDLE_MINUTES, settings.speechEngineIdleMinutes)
            .putBoolean(SettingsPreferences.KEY_STT_KEEP_ENGINE_ALIVE, settings.speechEngineIdleMinutes == 0)
            .putBoolean(KEY_AUTO, true)
    }

    private fun write(context: Context, settings: PerformanceSettings) {
        val prefs = SettingsPreferences.getPrefsPublic(context)
        KeyEffectPreferences.setAnimationEnabled(prefs, settings.keyAnimation)
        KeyEffectPreferences.setGlowEnabled(prefs, settings.keyGlow)
        SettingsPreferences.setSttIdleLadder(context, settings.speechRefinerIdleMinutes, settings.speechEngineIdleMinutes)
    }
}
