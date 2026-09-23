package com.kingzcheung.xime.ui.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SpaceBar
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/** 全键盘、九键、笔画和分栏布局共用的空格交互。 */
@Composable
fun SpaceKeyButton(
    onClick: () -> Unit,
    backgroundColor: Color,
    textColor: Color,
    schemaName: String = "",
    modifier: Modifier = Modifier,
    onPress: (() -> Unit)? = null,
    onRelease: (() -> Unit)? = null,
    isVoiceMode: Boolean = LocalKeyboardInputActions.current.isVoiceMode,
    voiceSticky: Boolean = LocalKeyboardInputActions.current.voiceSticky,
    isSttEnabled: Boolean = LocalKeyboardInputActions.current.isSttEnabled,
    onVoiceModeChange: ((Boolean) -> Unit)? = LocalKeyboardInputActions.current.onVoiceModeChange,
    shadowEnabled: Boolean = true,
    shadowElevation: Dp = 1.dp,
    shadowShapeRadius: Dp = 8.dp,
    fontSize: TextUnit = 14.sp,
) {
    val settings = LocalKeyboardInputPreferences.current
    val actions = LocalKeyboardInputActions.current
    val onMove by rememberUpdatedState(actions.onCursorMove)
    val onMoveVertical by rememberUpdatedState(actions.onCursorMoveVertical)
    val onCursorMode by rememberUpdatedState(actions.onCursorModeChange)
    DisposableEffect(Unit) { onDispose { onCursorMode?.invoke(false) } }
    val currentClick by rememberUpdatedState(onClick)
    val currentPress by rememberUpdatedState(onPress)
    val currentRelease by rememberUpdatedState(onRelease)
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val density = LocalDensity.current
    var pressed by remember { mutableStateOf(false) }
    var cursorActive by remember { mutableStateOf(false) }
    val shadow = remember(shadowEnabled, shadowElevation, shadowShapeRadius, density, backgroundColor) {
        if (shadowEnabled) Modifier.drawBehind {
            drawRoundRect(crispShadowColor(backgroundColor), topLeft = Offset(0f, shadowElevation.toPx()),
                size = size, cornerRadius = CornerRadius(shadowShapeRadius.toPx()))
        } else Modifier
    }
    BoxWithConstraints(
        modifier.fillMaxSize()
            .pointerInput(settings.spaceHold, settings.cursorStepDp) {
                val step = settings.cursorStepDp.dp.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    pressed = true
                    currentPress?.invoke()
                    var held = false
                    var movedBeforeHold = false
                    var lastX = down.position.x
                    var lastY = down.position.y
                    val cursorSteps = CursorStepAccumulator(step)
                    val verticalSteps = CursorStepAccumulator(step * 2f)
                    val timer = scope.launch {
                        delay(300L)
                        held = true
                        view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                        when (settings.spaceHold) {
                            SpaceHoldAction.CURSOR -> { cursorActive = true; onCursorMode?.invoke(true) }
                            SpaceHoldAction.REPEAT -> while (true) { currentClick(); delay(70L) }
                        }
                    }
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (change.isConsumed) break
                            if (held && settings.spaceHold == SpaceHoldAction.REPEAT &&
                                (change.position.x < 0f || change.position.x > size.width ||
                                    change.position.y < 0f || change.position.y > size.height)) break
                            val dx = change.position.x - lastX
                            lastX = change.position.x
                            val dy = change.position.y - lastY
                            lastY = change.position.y
                            if (!held && (abs(change.position.x - down.position.x) > viewConfiguration.touchSlop ||
                                abs(change.position.y - down.position.y) > viewConfiguration.touchSlop)) {
                                movedBeforeHold = true
                                timer.cancel()
                            }
                            if (cursorActive) {
                                val steps = cursorSteps.move(dx)
                                if (steps != 0) onMove?.invoke(steps)
                                val rows = verticalSteps.move(dy)
                                if (rows != 0) onMoveVertical?.invoke(rows)
                            }
                            change.consume()
                            if (!change.pressed) {
                                if (!held && !movedBeforeHold) {
                                    currentClick()
                                }
                                break
                            }
                        }
                    } finally {
                        timer.cancel()
                        pressed = false
                        cursorActive = false
                        onCursorMode?.invoke(false)
                        currentRelease?.invoke()
                    }
                }
            }
            .padding(LocalKeyVisualPadding.current)
            .then(shadow)
            .clip(RoundedCornerShape(LocalKeyCornerRadius.current))
            .background(if (pressed) backgroundColor.copy(alpha = 0.7f) else backgroundColor).keyGlow(),
        contentAlignment = Alignment.Center
    ) {
        val iconSize = KeyboardKeyMetrics.iconSizeDp(maxWidth.value, maxHeight.value)
        val labelHeight = (maxHeight.value - iconSize).coerceAtLeast(1f)
        val labelSize = minOf(KeyboardKeyMetrics.labelSizeSp(schemaName, 10f,
            maxWidth.value, maxHeight.value, density.fontScale, settings.keyTextScale),
            labelHeight / (density.fontScale * 1.4f)).coerceAtLeast(1f)
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            if (schemaName.isNotBlank() && schemaName != "空格") Text(
                schemaName.replace("拼音九键", "拼音"), color = textColor,
                fontSize = labelSize.sp, lineHeight = (labelSize * 1.2f).sp, maxLines = 1, softWrap = false,
                fontFamily = AppFonts.keyFontFamily)
            Icon(Icons.Default.SpaceBar, contentDescription = "空格", tint = textColor,
                modifier = Modifier.size(iconSize.dp))
        }
    }
}
