package com.kingzcheung.xime.ui.keyboard

import android.graphics.Rect
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.ceil
import kotlin.math.floor

/**
 * 语音输入时收起键盘后的底部悬浮条（仿 Gboard）：语言、展开键盘、状态、退格、麦克风。
 * 外观沿用实体键盘浮条；宿主透明全屏，只有这条胶囊进入可触摸区域。
 */
@Composable
internal fun VoiceCollapsedPill(
    listening: Boolean,
    languageLabel: String,
    languageActions: KeyboardInputActions,
    onSwitchLanguage: () -> Unit,
    onShowKeyboard: () -> Unit,
    onBackspace: () -> Unit,
    onMic: () -> Unit,
    backgroundColor: Color,
    contentColor: Color,
    selectedTextColor: Color,
    accentColor: Color,
    frostedGlass: com.kingzcheung.xime.settings.FrostedGlassConfig,
    surface: @Composable () -> Modifier,
    onBoundsChanged: (Rect?) -> Unit,
) {
    val themeKey = com.kingzcheung.xime.ui.theme.LocalKeyboardPalette.current?.key ?: backgroundColor
    val shadowRadius = with(LocalDensity.current) { 1.dp.toPx() }
    val textSurface = remember(frostedGlass, backgroundColor, themeKey, shadowRadius, contentColor, selectedTextColor, accentColor) {
        if (frostedGlass.enabled) resolveFrostedTextSurface(backgroundColor, contentColor, selectedTextColor, accentColor, frostedGlass, themeKey, shadowRadius) else null
    }
    val color = textSurface?.text ?: contentColor
    val currentBoundsCallback by rememberUpdatedState(onBoundsChanged)
    DisposableEffect(Unit) { onDispose { currentBoundsCallback(null) } }
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr, LocalFrostedTextSurface provides textSurface) {
            Row(
                Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp).widthIn(max = 400.dp).fillMaxWidth()
                    .onGloballyPositioned { coordinates ->
                        val bounds = coordinates.boundsInWindow()
                        currentBoundsCallback(Rect(floor(bounds.left).toInt(), floor(bounds.top).toInt(), ceil(bounds.right).toInt(), ceil(bounds.bottom).toInt()))
                    }
                    .clip(RoundedCornerShape(HardwareCandidateCornerRadius)).then(surface())
                    .testTag("voice-collapsed-pill").padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HardwareToolbarLanguageKey(color, languageActions, languageLabel, onSwitchLanguage)
                HardwareToolbarAction(Icons.Default.Keyboard, "展开键盘", "voice-pill-keyboard", color, onClick = onShowKeyboard)
                Box(Modifier.weight(1f).padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                    Text(if (listening) "正在听取…" else "点按麦克风说话", color = color, fontSize = 14.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                HardwareToolbarAction(Icons.AutoMirrored.Filled.Backspace, "删除", "voice-pill-backspace", color, onClick = onBackspace)
                HardwareToolbarAction(Icons.Default.Mic, if (listening) "停止语音输入" else "语音输入", "voice-pill-mic",
                    color, active = listening, onClick = onMic)
            }
        }
    }
}
