package com.kingzcheung.xime.ui.menubar

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback

/** The same whole-card reorder gesture is used in settings and on the keyboard. */
@Composable
internal fun Modifier.reorderOnLongPress(
    onStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onEnd: () -> Unit,
    onCancel: () -> Unit,
): Modifier {
    val start by rememberUpdatedState(onStart)
    val drag by rememberUpdatedState(onDrag)
    val end by rememberUpdatedState(onEnd)
    val cancel by rememberUpdatedState(onCancel)
    val haptic = LocalHapticFeedback.current
    return pointerInput(Unit) {
        detectDragGesturesAfterLongPress(
            onDragStart = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); start() },
            onDrag = { change, amount -> change.consume(); drag(amount.y) },
            onDragEnd = { end() },
            onDragCancel = { cancel() },
        )
    }
}
