package com.kingzcheung.xime.ui.keyboard

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kingzcheung.xime.keyboard.KeyboardInputPage
import com.kingzcheung.xime.keyboard.modeSlotTarget

val LocalModeKeyPadding = staticCompositionLocalOf { PaddingValues(4.dp) }
val LocalModeSlotWeight = staticCompositionLocalOf { 0.8f }
val LocalTextModeLabel = staticCompositionLocalOf { "中文" }

/** A mode key is deliberately a text key, never a language/globe key. */
@Composable
fun KeyboardModeKey(
    slot: Int,
    page: KeyboardInputPage = KeyboardInputPage.TEXT,
    onKeyPress: (String) -> Unit,
    backgroundColor: Color,
    textColor: Color,
    modifier: Modifier = Modifier,
    onKeyPressDown: ((String) -> Unit)? = null,
    shadowEnabled: Boolean = true,
    shadowElevation: Dp = 1.dp,
    shadowShapeRadius: Dp = 8.dp,
) {
    val target = modeSlotTarget(page, slot, LocalTextModeLabel.current)
    CompositionLocalProvider(LocalKeyVisualPadding provides LocalModeKeyPadding.current) {
    KeyButton(
        text = target.label,
        fontSize = 16.sp,
        onClick = { onKeyPress(target.action) },
        onPress = { onKeyPressDown?.invoke("mode_change") },
        backgroundColor = backgroundColor,
        textColor = textColor,
        modifier = modifier.testTag("mode-slot-$slot"),
        shadowEnabled = shadowEnabled,
        shadowElevation = shadowElevation,
        shadowShapeRadius = shadowShapeRadius,
    )
    }
}
