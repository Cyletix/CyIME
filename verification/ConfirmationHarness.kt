package com.kingzcheung.xime.service
import android.content.*
import com.kingzcheung.xime.settings.KeyboardHeightProfiles
import com.kingzcheung.xime.settings.SettingsPreferences

data class InputState(
 val showKeyboardResize: Boolean=true, val isFloatingMode: Boolean=true,
 val keyboardHeightDp: Int=320, val resizePreviewHeightDp: Int=180,
 val resizePreviewWidthDp: Int=260, val floatingWidthDp: Int=280,
 val keyboardBottomPaddingDp: Int=14, val floatingOffsetX: Int=12,
 val floatingOffsetY: Int=72, val keyboardOpacity: Float=1f,
)
class StateBox(var value: InputState)
// Only the confirm lambda is extracted from production. Android/UI dependencies are test doubles.
class XimeInputMethodService(landscape: Boolean=false): Context() {
 val screenIsLandscape=landscape
 val effectiveScreenH=if(landscape) 411 else 840
 val uiState=StateBox(InputState())
 var refreshCount=0
 var backgroundCount=0
 init { resources.configuration.screenWidthDp=if(landscape)840 else 411; resources.configuration.screenHeightDp=effectiveScreenH }
 fun refreshKeyboardGeometry() { refreshCount++ }
 fun applyWindowBackground() { backgroundCount++ }
val onConfirm: (Int, Int, Boolean, Float) -> Unit = { newHeight, newPadding, floatingMode, opacity ->
                                       // 先快照：写偏好会触发监听器，不能再读被监听器重载过的预览偏移。
                                       val preview = uiState.value
                                       val savedHeight = KeyboardHeightProfiles.save(
                                           this@XimeInputMethodService, floatingMode, screenIsLandscape,
                                           newHeight, effectiveScreenH,
                                       )
                                       val savedWidth = preview.resizePreviewWidthDp
                                           .takeIf { floatingMode && it > 0 } ?: preview.floatingWidthDp
                                       if (floatingMode) {
                                           SettingsPreferences.setFloatingWidthDp(this@XimeInputMethodService, savedWidth, screenIsLandscape)
                                           SettingsPreferences.setFloatingOffsetX(this@XimeInputMethodService, preview.floatingOffsetX, screenIsLandscape)
                                           SettingsPreferences.setFloatingOffsetY(this@XimeInputMethodService, preview.floatingOffsetY, screenIsLandscape)
                                       }
                                       val savedPadding = if (floatingMode) {
                                           SettingsPreferences.getKeyboardBottomPaddingDp(this@XimeInputMethodService)
                                       } else newPadding
                                       if (!floatingMode) SettingsPreferences.setKeyboardBottomPaddingDp(this@XimeInputMethodService, savedPadding)
                                       SettingsPreferences.setKeyboardOpacity(this@XimeInputMethodService, opacity)
                                       SettingsPreferences.setFloatingMode(this@XimeInputMethodService, floatingMode, screenIsLandscape)
                                       uiState.value = uiState.value.copy(
                                           showKeyboardResize = false,
                                           isFloatingMode = floatingMode,
                                           keyboardHeightDp = savedHeight,
                                           resizePreviewHeightDp = 0,
                                           keyboardBottomPaddingDp = savedPadding,
                                           floatingWidthDp = savedWidth,
                                           resizePreviewWidthDp = 0,
                                           floatingOffsetX = preview.floatingOffsetX,
                                           floatingOffsetY = preview.floatingOffsetY,
                                           keyboardOpacity = opacity,
                                       )
                                       // 不再重复 toggleFloatingMode；它会重新读取设置并覆盖刚确认的预览位置。
                                       refreshKeyboardGeometry()
                                       applyWindowBackground()
                                    }
}
