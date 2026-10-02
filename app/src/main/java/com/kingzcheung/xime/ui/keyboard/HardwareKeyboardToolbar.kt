package com.kingzcheung.xime.ui.keyboard

import android.graphics.Rect
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlin.math.floor
import kotlin.math.ceil

/** The full-screen host has no input handler: only this capsule enters the IME touchable region. */
@Composable
internal fun HardwareKeyboardToolbar(
    onShowKeyboard: () -> Unit,
    onEmoji: () -> Unit,
    onClipboard: () -> Unit,
    onSwitchInputMethod: () -> Unit,
    backgroundColor: Color,
    contentColor: Color,
    onBoundsChanged: (Rect?) -> Unit,
    position: HardwareToolbarPosition,
    onPositionChange: (HardwareToolbarPosition) -> Unit,
) {
    val currentBoundsCallback by rememberUpdatedState(onBoundsChanged)
    val currentPositionCallback by rememberUpdatedState(onPositionChange)
    val currentPosition by rememberUpdatedState(position)
    DisposableEffect(Unit) { onDispose { currentBoundsCallback(null) } }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        // Keep normal margins when possible; do not shrink a 48dp recovery key merely for margins.
        val usableWidth = if (maxWidth >= 104.dp) maxWidth - 16.dp else maxWidth
        val mode = hardwareToolbarMode(usableWidth.value)
        val hasHandle = usableWidth >= 88.dp
        var expanded by remember(mode) { mutableStateOf(false) }
        val padding = if (hasHandle) 4.dp else 0.dp
        val buttonSize = minOf(48.dp, usableWidth).coerceAtLeast(0.dp)
        val toolbarWidth = when (mode) {
            HardwareToolbarMode.FULL -> 232.dp
            HardwareToolbarMode.COMPACT -> 184.dp
            HardwareToolbarMode.KEYBOARD_ONLY -> if (hasHandle) 88.dp else buttonSize
        }
        val expectedHeight = buttonSize + padding * 2 + if (mode == HardwareToolbarMode.COMPACT && expanded) 48.dp else 0.dp
        var measuredSize by remember(toolbarWidth, expectedHeight, maxWidth, maxHeight, density) {
            mutableStateOf(with(density) { IntSize(toolbarWidth.roundToPx(), minOf(expectedHeight, maxHeight).roundToPx()) })
        }
        val geometry = with(density) {
            hardwareToolbarGeometry(maxWidth.roundToPx(), maxHeight.roundToPx(), measuredSize.width, measuredSize.height, 8.dp.roundToPx())
        }
        val currentGeometry by rememberUpdatedState(geometry)
        var dragging by remember { mutableStateOf<HardwareToolbarOffset?>(null) }
        val offset = dragging?.let { geometry.constrain(it.x, it.y) } ?: geometry.offset(position)
        val shape = RoundedCornerShape(28.dp)
        Column(
            Modifier.absoluteOffset { IntOffset(offset.x.roundToInt(), offset.y.roundToInt()) }
                .width(toolbarWidth).heightIn(max = maxHeight)
                .onSizeChanged { measuredSize = it }
                .onGloballyPositioned { coordinates ->
                    val bounds = coordinates.boundsInWindow()
                    currentBoundsCallback(Rect(floor(bounds.left).toInt(), floor(bounds.top).toInt(), ceil(bounds.right).toInt(), ceil(bounds.bottom).toInt()))
                }
                .shadow(4.dp, shape).clip(shape).background(backgroundColor).padding(padding)
                .testTag("hardware-keyboard-toolbar"),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (hasHandle) Box(
                    Modifier.width(32.dp).height(buttonSize)
                        .semantics {
                            contentDescription = "移动输入工具条"
                            customActions = listOf(
                                CustomAccessibilityAction("靠左") { currentPositionCallback(currentPosition.copy(xFraction = 0f)); true },
                                CustomAccessibilityAction("居中") { currentPositionCallback(currentPosition.copy(xFraction = 0.5f)); true },
                                CustomAccessibilityAction("靠右") { currentPositionCallback(currentPosition.copy(xFraction = 1f)); true },
                            )
                        }
                        .testTag("hardware-toolbar-drag")
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { dragging = currentGeometry.offset(currentPosition) },
                                onDragCancel = { dragging = null },
                                onDragEnd = {
                                    dragging?.let { currentPositionCallback(currentGeometry.positionAt(it.x, it.y, currentPosition)) }
                                    dragging = null
                                },
                            ) { change, delta ->
                                change.consume()
                                val from = dragging ?: currentGeometry.offset(currentPosition)
                                dragging = currentGeometry.constrain(from.x + delta.x, from.y + delta.y)
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Default.DragIndicator, null, tint = contentColor, modifier = Modifier.size(20.dp)) }
                HardwareToolbarAction(Icons.Default.Keyboard, "展开屏幕键盘", "hardware-toolbar-keyboard", contentColor, buttonSize, onShowKeyboard)
                if (mode != HardwareToolbarMode.KEYBOARD_ONLY) {
                    HardwareToolbarAction(Icons.Default.EmojiEmotions, "表情", "hardware-toolbar-emoji", contentColor, onClick = onEmoji)
                    if (mode == HardwareToolbarMode.FULL) {
                        HardwareToolbarAction(Icons.Default.ContentPaste, "剪贴板", "hardware-toolbar-clipboard", contentColor, onClick = onClipboard)
                        HardwareToolbarAction(Icons.Default.Language, "切换输入法", "hardware-toolbar-language", contentColor, onClick = onSwitchInputMethod)
                    } else {
                        HardwareToolbarAction(Icons.Default.MoreHoriz, if (expanded) "收起工具" else "更多工具", "hardware-toolbar-more", contentColor) { expanded = !expanded }
                    }
                }
            }
            if (mode == HardwareToolbarMode.COMPACT && expanded) {
                // Inline overflow stays inside the same measured touch region and never covers recovery.
                Row(horizontalArrangement = Arrangement.Center) {
                    HardwareToolbarAction(Icons.Default.ContentPaste, "剪贴板", "hardware-toolbar-clipboard", contentColor) { expanded = false; onClipboard() }
                    HardwareToolbarAction(Icons.Default.Language, "切换输入法", "hardware-toolbar-language", contentColor) { expanded = false; onSwitchInputMethod() }
                }
            }
        }
    }
}

@Composable
private fun HardwareToolbarAction(
    icon: ImageVector,
    description: String,
    tag: String,
    contentColor: Color,
    size: Dp = 48.dp,
    onClick: () -> Unit,
) {
    Box(
        Modifier.size(size).clip(CircleShape).clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description }.testTag(tag),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = contentColor, modifier = Modifier.size(minOf(24.dp, size))) }
}
