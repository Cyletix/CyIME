package com.kingzcheung.xime.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.kingzcheung.xime.settings.FrostedGlassConfig
import com.kingzcheung.xime.settings.FrostedGlassPreferences
import com.kingzcheung.xime.settings.SettingsPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class KeyEffectUiState(
    val soundEnabled: Boolean = true,
    val keyGlowEnabled: Boolean = false,
    val frostedGlass: FrostedGlassConfig = FrostedGlassConfig(),
    val soundVolume: Int = 50,
    val hapticMode: String = "following_system",
    val hapticOnKeyUp: Boolean = false,
    val pressDuration: Int = 0,
    val longPressDuration: Int = 0,
    val pressAmplitude: Int = 0,
    val longPressAmplitude: Int = 0,
    val hasAmplitudeControl: Boolean = false,
    val swipeUpHintsEnabled: Boolean = true,
    val swipeDownHintsEnabled: Boolean = true
)

class KeyEffectSettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext

    private val _uiState = MutableStateFlow(KeyEffectUiState(
        soundEnabled = SettingsPreferences.isSoundEnabled(context),
        keyGlowEnabled = SettingsPreferences.getPrefsPublic(context).getBoolean("key_glow_enabled", false),
        frostedGlass = FrostedGlassPreferences.read(context),
        soundVolume = SettingsPreferences.getSoundVolume(context),
        hapticMode = SettingsPreferences.getHapticMode(context),
        hapticOnKeyUp = SettingsPreferences.isHapticOnKeyUp(context),
        pressDuration = SettingsPreferences.getVibrationPressDuration(context),
        longPressDuration = SettingsPreferences.getVibrationLongPressDuration(context),
        pressAmplitude = SettingsPreferences.getVibrationPressAmplitude(context),
        longPressAmplitude = SettingsPreferences.getVibrationLongPressAmplitude(context),
        swipeUpHintsEnabled = SettingsPreferences.isSwipeUpHintsEnabled(context),
        swipeDownHintsEnabled = SettingsPreferences.isSwipeDownHintsEnabled(context)
    ))
    val uiState: StateFlow<KeyEffectUiState> = _uiState.asStateFlow()

    init {
        updateHasAmplitudeControl()
    }

    private fun updateHasAmplitudeControl() {
        val vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val vm = context.getSystemService(android.content.Context.VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as android.os.Vibrator
        }
        val hasAmp = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O && vibrator.hasAmplitudeControl()
        _uiState.update { it.copy(hasAmplitudeControl = hasAmp) }
    }

    fun setKeyGlowEnabled(enabled: Boolean) {
        SettingsPreferences.getPrefsPublic(context).edit().putBoolean("key_glow_enabled", enabled).apply()
        _uiState.update { it.copy(keyGlowEnabled = enabled) }
    }

    fun setFrostedGlassEnabled(enabled: Boolean) =
        updateFrostedGlass { it.copy(enabled = enabled) }

    fun setFrostedGlassBlurRadius(radiusDp: Float) =
        updateFrostedGlass { it.copy(blurRadiusDp = radiusDp) }

    fun setFrostedGlassBackgroundOpacity(opacity: Float) =
        updateFrostedGlass { it.copy(backgroundOpacity = opacity) }

    fun setFrostedGlassKeyOpacity(opacity: Float) =
        updateFrostedGlass { it.copy(keyOpacity = opacity) }

    private fun updateFrostedGlass(transform: (FrostedGlassConfig) -> FrostedGlassConfig) {
        val config = transform(_uiState.value.frostedGlass).normalized()
        FrostedGlassPreferences.save(context, config)
        _uiState.update { it.copy(frostedGlass = config) }
    }

    fun setSoundEnabled(enabled: Boolean) {
        SettingsPreferences.setSoundEnabled(context, enabled)
        _uiState.update { it.copy(soundEnabled = enabled) }
    }

    fun setSoundVolume(volume: Int) {
        SettingsPreferences.setSoundVolume(context, volume)
        _uiState.update { it.copy(soundVolume = volume) }
    }

    fun setHapticMode(mode: String) {
        SettingsPreferences.setHapticMode(context, mode)
        _uiState.update { it.copy(hapticMode = mode) }
    }

    fun setHapticOnKeyUp(enabled: Boolean) {
        SettingsPreferences.setHapticOnKeyUp(context, enabled)
        _uiState.update { it.copy(hapticOnKeyUp = enabled) }
    }

    fun setPressDuration(duration: Int) {
        SettingsPreferences.setVibrationPressDuration(context, duration)
        _uiState.update { it.copy(pressDuration = duration) }
    }

    fun setLongPressDuration(duration: Int) {
        SettingsPreferences.setVibrationLongPressDuration(context, duration)
        _uiState.update { it.copy(longPressDuration = duration) }
    }

    fun setPressAmplitude(amplitude: Int) {
        SettingsPreferences.setVibrationPressAmplitude(context, amplitude)
        _uiState.update { it.copy(pressAmplitude = amplitude) }
    }

    fun setLongPressAmplitude(amplitude: Int) {
        SettingsPreferences.setVibrationLongPressAmplitude(context, amplitude)
        _uiState.update { it.copy(longPressAmplitude = amplitude) }
    }

    fun setSwipeUpHintsEnabled(enabled: Boolean) {
        SettingsPreferences.setSwipeUpHintsEnabled(context, enabled)
        _uiState.update { it.copy(swipeUpHintsEnabled = enabled) }
    }

    fun setSwipeDownHintsEnabled(enabled: Boolean) {
        SettingsPreferences.setSwipeDownHintsEnabled(context, enabled)
        _uiState.update { it.copy(swipeDownHintsEnabled = enabled) }
    }
}
