package com.kingzcheung.xime.ui.keyboard

import android.graphics.Rect
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.layout.positionOnScreen
import com.kingzcheung.xime.service.HardwareCursorAnchor
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.LocalViewConfiguration
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun HardwareKeyboardToolbar(
    onShowKeyboard: () -> Unit,
    onEmoji: () -> Unit,
    onClipboard: () -> Unit,
    onSwitchLanguage: () -> Unit,
    backgroundColor: Color,
    contentColor: Color,
    onBoundsChanged: (Rect?) -> Unit,
    position: HardwareToolbarPosition,
    onPositionChange: (HardwareToolbarPosition) -> Unit,
    cursorAnchor: HardwareCursorAnchor? = null,
    editorBounds: HardwareCursorAnchor? = null,
    avoidEditor: Boolean = true,
    dockAtEdge: Boolean = true,
    languageActions: KeyboardInputActions = KeyboardInputActions(),
    surface: (@Composable () -> Modifier)? = null,
    bottomDocked: Boolean = false,
    bottomCandidateContent: (@Composable () -> Unit)? = null,
) {
    val currentBoundsCallback by rememberUpdatedState(onBoundsChanged)
    val currentPositionCallback by rememberUpdatedState(onPositionChange)
    val currentPosition by rememberUpdatedState(position)
    DisposableEffect(Unit) { onDispose { currentBoundsCallback(null) } }

    var hostScreenOrigin by remember { mutableStateOf(Offset.Zero) }
    BoxWithConstraints(Modifier.fillMaxSize().onGloballyPositioned { hostScreenOrigin = it.positionOnScreen() }) {
        val viewportHeight = maxHeight
        val density = LocalDensity.current
        // Keep normal margins when possible; do not shrink a 48dp recovery key merely for margins.
        val usableWidth = if (maxWidth >= 104.dp) maxWidth - 16.dp else maxWidth
        val vertical = !bottomDocked && dockAtEdge && (position.xFraction == 0f || position.xFraction == 1f) && maxHeight >= 248.dp && usableWidth >= 88.dp
        val mode = if (vertical) HardwareToolbarMode.FULL else hardwareToolbarMode(usableWidth.value)
        val hasHandle = usableWidth >= 88.dp
        var expanded by remember(mode) { mutableStateOf(false) }
        val padding = if (hasHandle) 4.dp else 0.dp
        val buttonSize = minOf(48.dp, usableWidth).coerceAtLeast(0.dp)
        val horizontalWidth = when (mode) {
            HardwareToolbarMode.FULL -> 232.dp
            HardwareToolbarMode.COMPACT -> 184.dp
            HardwareToolbarMode.KEYBOARD_ONLY -> if (hasHandle) 88.dp else buttonSize
        }
        val targetWidth = if (bottomDocked) minOf(720.dp, usableWidth) else if (vertical) 56.dp else horizontalWidth
        val toolbarWidth by animateDpAsState(targetWidth, tween(220), label = "hardware-dock-width")
        val expectedHeight = if (vertical) horizontalWidth else buttonSize + padding * 2 + if ((mode == HardwareToolbarMode.COMPACT || bottomDocked) && expanded) 48.dp else 0.dp
        var measuredSize by remember(maxWidth, maxHeight, density) {
            mutableStateOf(with(density) { IntSize(toolbarWidth.roundToPx(), minOf(expectedHeight, maxHeight).roundToPx()) })
        }
        val geometry = with(density) {
            hardwareToolbarGeometry(maxWidth.roundToPx(), maxHeight.roundToPx(), measuredSize.width, measuredSize.height, 8.dp.roundToPx())
        }
        val currentGeometry by rememberUpdatedState(geometry)
        var dragging by remember { mutableStateOf<HardwareToolbarOffset?>(null) }
        val shape = RoundedCornerShape(28.dp)
        val languageMenuActive = LocalSuppressCursorMove.current
        val touchSlop = LocalViewConfiguration.current.touchSlop
        val gap = with(density) { 12.dp.roundToPx() }
        val editor = editorBounds?.takeIf {
            (it.right - it.left) * (it.bottom - it.top) < constraints.maxWidth.toFloat() * constraints.maxHeight * 0.6f
        } ?: cursorAnchor?.let { HardwareCursorAnchor(it.left - gap * 3, it.top, it.right + gap * 3, it.bottom) }
        val exclusion = editor?.let { HardwareCandidateExclusion(
            (it.left - hostScreenOrigin.x).roundToInt(), (it.top - hostScreenOrigin.y).roundToInt(),
            (it.right - hostScreenOrigin.x).roundToInt(), (it.bottom - hostScreenOrigin.y).roundToInt()) }
        val resting = if (avoidEditor && !bottomDocked) avoidHardwareEditor(geometry, geometry.offset(position),
            measuredSize.width, measuredSize.height, exclusion, gap) else geometry.offset(position)
        val animatedOffset by animateOffsetAsState(
            targetValue = (dragging ?: resting).let { Offset(it.x, it.y) },
            animationSpec = if (dragging != null) snap() else tween(220), label = "hardware-dock-position")
        val displayedOffset = dragging ?: HardwareToolbarOffset(animatedOffset.x, animatedOffset.y)
        val currentResting by rememberUpdatedState(displayedOffset)
        val currentDock by rememberUpdatedState(dockAtEdge)
        var gestureOrigin by remember { mutableStateOf<HardwareToolbarOffset?>(null) }
        // The receiver stays at the DOWN origin while its child follows the finger. It covers
        // the complete capsule, but never the transparent full-screen host.
        Box(Modifier.absoluteOffset {
            val origin = gestureOrigin ?: displayedOffset
            IntOffset(origin.x.roundToInt(), origin.y.roundToInt())
        }.width(toolbarWidth).height(maxOf(expectedHeight, with(density) { measuredSize.height.toDp() }).coerceAtMost(viewportHeight))
            .testTag("hardware-toolbar-drag")
            .semantics {
                contentDescription = "移动输入工具条"
                customActions = listOf(
                    CustomAccessibilityAction("靠左") { currentPositionCallback(currentPosition.copy(xFraction = 0f, yFraction = 0.5f)); true },
                    CustomAccessibilityAction("居中") { currentPositionCallback(currentPosition.copy(xFraction = 0.5f)); true },
                    CustomAccessibilityAction("靠右") { currentPositionCallback(currentPosition.copy(xFraction = 1f, yFraction = 0.5f)); true },
                    CustomAccessibilityAction("停靠底部") { currentPositionCallback(HardwareToolbarPosition()); true },
                )
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val origin = currentResting
                    gestureOrigin = origin
                    var moved = false
                    try {
                        while (true) {
                            val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                            val distance = change.position - down.position
                            if (!languageMenuActive.value && !change.isConsumed && (moved || distance.getDistance() > touchSlop)) {
                                moved = true
                                dragging = currentGeometry.constrain(origin.x + distance.x, origin.y + distance.y)
                                change.consume() // cancels child clicks/long-press when this is a drag
                            }
                            if (!change.pressed) {
                                if (moved) currentPositionCallback(currentGeometry.positionAt(
                                    origin.x + distance.x, origin.y + distance.y, currentPosition, snapX = currentDock))
                                break
                            }
                        }
                    } finally { dragging = null; gestureOrigin = null }
                }
            }) {
        Column(
            Modifier.absoluteOffset {
                val origin = gestureOrigin ?: displayedOffset
                val offset = displayedOffset
                IntOffset((offset.x - origin.x).roundToInt(), (offset.y - origin.y).roundToInt())
            }
                .width(toolbarWidth).heightIn(max = viewportHeight)
                .onSizeChanged { measuredSize = it }
                .animateContentSize(tween(220))
                .onGloballyPositioned { coordinates ->
                    val bounds = coordinates.boundsInWindow()
                    currentBoundsCallback(Rect(floor(bounds.left).toInt(), floor(bounds.top).toInt(), ceil(bounds.right).toInt(), ceil(bounds.bottom).toInt()))
                }
                .shadow(8.dp, shape).clip(shape).then(surface?.invoke() ?: Modifier.background(backgroundColor))
                .border(1.dp, contentColor.copy(alpha = 0.45f), shape)
                .testTag("hardware-keyboard-toolbar").padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val actions: @Composable () -> Unit = {
                if (hasHandle) Box(
                    Modifier.width(if (vertical) buttonSize else 32.dp).height(if (vertical) 32.dp else buttonSize),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Default.DragIndicator, null, tint = contentColor, modifier = Modifier.size(20.dp)) }
                HardwareToolbarAction(Icons.Default.Keyboard, "展开屏幕键盘", "hardware-toolbar-keyboard", contentColor, buttonSize, onShowKeyboard)
                if (mode != HardwareToolbarMode.KEYBOARD_ONLY) {
                    HardwareToolbarAction(Icons.Default.EmojiEmotions, "表情", "hardware-toolbar-emoji", contentColor, onClick = onEmoji)
                    if (mode == HardwareToolbarMode.FULL) {
                        HardwareToolbarAction(Icons.Default.ContentPaste, "剪贴板", "hardware-toolbar-clipboard", contentColor, onClick = onClipboard)
                        HardwareToolbarLanguageKey(contentColor, languageActions, onSwitchLanguage)
                    } else {
                        HardwareToolbarAction(Icons.Default.MoreHoriz, if (expanded) "收起工具" else "更多工具", "hardware-toolbar-more", contentColor) { expanded = !expanded }
                    }
                }
            }
            if (bottomDocked && bottomCandidateContent != null) {
                Row(Modifier.width((toolbarWidth - padding * 2).coerceAtLeast(0.dp)),
                    verticalAlignment = Alignment.CenterVertically) {
                    HardwareToolbarLanguageKey(contentColor, languageActions, onSwitchLanguage)
                    Box(Modifier.weight(1f).testTag("hardware-docked-candidates")) { bottomCandidateContent() }
                    HardwareToolbarAction(Icons.Default.Keyboard, "展开屏幕键盘", "hardware-toolbar-keyboard", contentColor, onClick = onShowKeyboard)
                    HardwareToolbarAction(Icons.Default.MoreHoriz, if (expanded) "收起工具" else "更多工具", "hardware-toolbar-more", contentColor) { expanded = !expanded }
                }
            }
            else if (vertical) Column(horizontalAlignment = Alignment.CenterHorizontally) { actions() }
            else Row(verticalAlignment = Alignment.CenterVertically) { actions() }
            if ((mode == HardwareToolbarMode.COMPACT || bottomDocked) && expanded) {
                // Inline overflow stays inside the same measured touch region and never covers recovery.
                Row(horizontalArrangement = Arrangement.Center) {
                    HardwareToolbarAction(Icons.Default.ContentPaste, "剪贴板", "hardware-toolbar-clipboard", contentColor) { expanded = false; onClipboard() }
                    if (bottomDocked) HardwareToolbarAction(Icons.Default.EmojiEmotions, "表情", "hardware-toolbar-emoji", contentColor) { expanded = false; onEmoji() }
                    else HardwareToolbarLanguageKey(contentColor, languageActions, onSwitchLanguage)
                }
            }
        }
        } // stationary gesture receiver
        val displayed = displayedOffset
        val dockTarget = geometry.positionAt(displayed.x, displayed.y, position)
        val previewBottom = dockTarget.isBottomDocked(dockAtEdge) && usableWidth >= 320.dp
        val previewSide = !previewBottom && dockAtEdge && hasHandle && maxHeight >= 248.dp &&
            (dockTarget.xFraction == 0f || dockTarget.xFraction == 1f)
        if (dragging != null && (previewBottom || previewSide)) {
            // Preview is a separate drawing-only layer; release performs the animated resize.
            val pw = if (previewBottom) minOf(720.dp, usableWidth) else 56.dp
            val ph = if (previewBottom) 56.dp else horizontalWidth
            val previewGeometry = hardwareToolbarGeometry(constraints.maxWidth, constraints.maxHeight,
                with(density) { pw.roundToPx() }, with(density) { ph.roundToPx() }, with(density) { 8.dp.roundToPx() })
            val preview = previewGeometry.offset(dockTarget)
            Box(Modifier.absoluteOffset { IntOffset(preview.x.roundToInt(), preview.y.roundToInt()) }
                .width(pw).height(ph).border(2.dp, Color(0xFF64B5F6), shape)
                .testTag("hardware-toolbar-dock-preview"))
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

@Composable
private fun HardwareToolbarLanguageKey(color: Color, actions: KeyboardInputActions, onClick: () -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(LocalKeyboardInputActions provides actions) {
        Box(Modifier.size(48.dp).semantics(mergeDescendants = true) {}.testTag("hardware-toolbar-language")) {
            LanguageKeyButton(onClick = onClick, backgroundColor = Color.Transparent, textColor = color,
                shadowEnabled = false, shadowShapeRadius = 24.dp)
        }
    }
}
