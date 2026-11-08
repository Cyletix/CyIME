package com.kingzcheung.xime.audit

import android.app.Application
import android.app.Dialog
import android.content.SharedPreferences
import android.graphics.Insets
import android.view.WindowInsets
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kingzcheung.xime.service.extractBottomInset
import com.kingzcheung.xime.service.fixedKeyboardBottomRange
import com.kingzcheung.xime.settings.KeysConfigHelper
import com.kingzcheung.xime.settings.SettingsPreferences
import com.kingzcheung.xime.ui.keyboard.*
import com.kingzcheung.xime.ui.settings.FixedBottomCalibrationSettings
import com.kingzcheung.xime.ui.theme.KeyboardThemes
import com.kingzcheung.xime.ui.theme.XimeTheme
import com.kingzcheung.xime.viewmodel.KeyboardUiState
import com.kingzcheung.xime.viewmodel.KeyboardViewModel

/** Physical Android execution with controlled inset reports. The strip stands for SystemUI. */
@Composable
internal fun FixedBottomScene(app: Application, record: (String) -> Unit, mode: String) {
    val buttons = !mode.endsWith("gestures")
    var resizing by remember { mutableStateOf(mode.endsWith("resize")) }
    val context = LocalContext.current
    val prefs = SettingsPreferences
    val window = remember { checkNotNull(Dialog(context).window) }
    val vm = remember {
        prefs.setEdgeToEdgeKeyboard(app, true)
        prefs.setKeyboardBottomAdjustmentDp(app, -80)
        KeysConfigHelper.loadConfig(app)
        KeyboardThemes.initFromConfig(app)
        AppFonts.initialize(app)
        KeyboardViewModel(app).also { it.dispatch(KeyboardDispatchAction.InputSessionStarted(false, "t9_pinyin")) }
    }
    var adjustment by remember { mutableIntStateOf(prefs.getKeyboardBottomAdjustmentDp(app)) }
    var edge by remember { mutableStateOf(prefs.edgeToEdgeKeyboard(app)) }
    DisposableEffect(app) {
        val sp = prefs.getPrefsPublic(app)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            adjustment = prefs.getKeyboardBottomAdjustmentDp(app)
            edge = prefs.edgeToEdgeKeyboard(app)
        }
        sp.registerOnSharedPreferenceChangeListener(listener)
        onDispose { sp.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val insets = remember {
        WindowInsets.Builder().setInsets(WindowInsets.Type.navigationBars(), Insets.of(0, 0, 0, if (buttons) 48 else 0))
            .setInsets(WindowInsets.Type.mandatorySystemGestures(), Insets.of(0, 0, 0, 18)).build()
    }
    val normal = extractBottomInset(insets, window)
    val range = fixedKeyboardBottomRange(insets, window, normal, edge)
    // The device probe's virtual display is mdpi.
    SideEffect { prefs.setKeyboardBottomLoweringLimitDp(app, range.baselinePx - range.minimumPx) }
    val gap = range.insetPx(adjustment, 1f) + adjustment.coerceAtLeast(0)
    XimeTheme(darkTheme = false) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            Column(Modifier.fillMaxWidth().padding(12.dp)) { FixedBottomCalibrationSettings() }
            val extra = gap - range.minimumPx
            KeyboardView(vm, KeyboardUiState(currentSchemaId = "t9_pinyin", keyboardHeightDp = 244,
                keyboardBottomPaddingDp = if (resizing) extra else 0),
                KeyboardCallbacks(onKeyPress = { key, _ -> record(key) }, onCandidateSelect = {}),
                inputSessionManagedByHost = true, fixedBottomInsetDp = range.minimumPx,
                resizeOverlay = if (resizing) { {
                    KeyboardResizeOverlay(244, 244, extra, false,
                        defaultBottomPaddingDp = range.baselinePx - range.minimumPx,
                        onHeightChange = {}, onBottomPaddingChange = {}, onOpacityChange = {}, onReset = {},
                        onConfirm = { height, padding, _, _ ->
                            check(height == 244)
                            prefs.setKeyboardBottomAdjustmentDp(app, padding - (range.baselinePx - range.minimumPx))
                            resizing = false
                        }, onCancel = { resizing = false })
                } } else null,
                modifier = if (resizing) Modifier.fillMaxSize() else Modifier.align(Alignment.BottomCenter)
                    .offset(y = -gap.dp).fillMaxWidth().height(244.dp))
            if (buttons) Spacer(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(48.dp).background(Color.DarkGray))
        }
    }
}
