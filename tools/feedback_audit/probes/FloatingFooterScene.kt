package com.kingzcheung.xime.audit

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import com.kingzcheung.xime.settings.KeysConfigHelper
import com.kingzcheung.xime.ui.keyboard.AppFonts
import com.kingzcheung.xime.ui.keyboard.KeyboardCallbacks
import com.kingzcheung.xime.ui.keyboard.KeyboardDispatchAction
import com.kingzcheung.xime.ui.keyboard.KeyboardView
import com.kingzcheung.xime.ui.theme.KeyboardThemes
import com.kingzcheung.xime.viewmodel.KeyboardUiState
import com.kingzcheung.xime.viewmodel.KeyboardViewModel

/** Device probe runs the normal KeyboardView with private temporary preferences. */
@Composable
internal fun FloatingFooterScene(app: Application, record: (String) -> Unit, atBottom: Boolean, dark: Boolean) {
    val vm = remember {
        KeysConfigHelper.loadConfig(app)
        KeyboardThemes.initFromConfig(app)
        AppFonts.initialize(app)
        KeyboardViewModel(app).also { it.dispatch(KeyboardDispatchAction.InputSessionStarted(false, "rime_ice")) }
    }
    val config = LocalConfiguration.current
    val wide = minOf(config.screenWidthDp, config.screenHeightDp) >= 600
    com.kingzcheung.xime.ui.theme.XimeTheme(darkTheme = dark) {
        KeyboardView(vm, KeyboardUiState(isFloatingMode = true, keyboardWidthDp = if (wide) 560 else 320,
            keyboardHeightDp = 244, floatingOffsetY = if (atBottom) 0 else 80, floatingMinOffsetY = 0,
            currentSchemaId = "rime_ice", isDarkTheme = dark),
            KeyboardCallbacks(onKeyPress = { key, _ -> record(key) }, onCandidateSelect = {},
                onFloatingKeyboardDrag = { _, _ -> record("move") }), inputSessionManagedByHost = true)
    }
}
