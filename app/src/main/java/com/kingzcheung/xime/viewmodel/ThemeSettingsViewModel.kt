package com.kingzcheung.xime.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.kingzcheung.xime.settings.SettingsPreferences
import com.kingzcheung.xime.ui.theme.KeyboardColorScheme
import com.kingzcheung.xime.ui.theme.KeyboardThemes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 2026-10-11 列表只留四个；2026-10-18 用户要求把 1.9.15 移除的主题按新配色加回，每个色系一套（[TonalThemes]）。
 * 跟随系统动态配色仍不在列表中。
 */
internal val OFFERED_THEMES = setOf(
    com.kingzcheung.xime.ui.theme.TransparentGlassTheme.ID,
    com.kingzcheung.xime.ui.theme.PureBlackTheme.ID,
    "lavender_purple",
    com.kingzcheung.xime.ui.theme.SoftBlueTheme.ID,
) + com.kingzcheung.xime.ui.theme.TonalThemes.create().map { it.id }

data class ThemeUiState(
    val darkMode: Int = 1,
    val colorTheme: String = SettingsPreferences.defaultKeyboardTheme,
    val colorThemes: List<KeyboardColorScheme> = KeyboardThemes.themes.filter { it.id in OFFERED_THEMES },
)

class ThemeSettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    
    private val _uiState = MutableStateFlow(ThemeUiState(
        darkMode = SettingsPreferences.getDarkMode(context),
        colorTheme = SettingsPreferences.getKeyboardTheme(context),
    ))
    val uiState: StateFlow<ThemeUiState> = _uiState.asStateFlow()
    
    fun setDarkMode(mode: Int) {
        SettingsPreferences.setDarkMode(context, mode)
        _uiState.update { it.copy(darkMode = mode) }
    }
    
    fun setColorTheme(themeId: String) {
        SettingsPreferences.setKeyboardTheme(context, themeId)
        _uiState.update { it.copy(colorTheme = themeId) }
    }
}
