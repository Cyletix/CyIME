package com.kingzcheung.xime.ui.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

@Composable
fun KeyboardResizeOverlay(
    initialHeightDp: Int,
    defaultHeightDp: Int,
    currentBottomPaddingDp: Int,
    isFloatingMode: Boolean,
    initialOpacity: Float = 1f,
    onHeightChange: (Int) -> Unit,
    onBottomPaddingChange: (Int) -> Unit,
    onOpacityChange: (Float) -> Unit,
    onReset: (Int) -> Unit,
    onConfirm: (Int, Int, Boolean, Float) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    onFloatingModeChange: ((Boolean) -> Unit)? = null,
) {
    val density = LocalDensity.current
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val screenHeightDp = configuration.screenHeightDp
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp

    val bounds = keyboardHeightBounds(screenHeightDp, isLandscape)
    val minKeyboardHeightDp = bounds.first
    val maxKeyboardHeightDp = bounds.last
    val maxBottomPaddingDp = 80

    val safeDefaultHeightDp = defaultHeightDp.coerceIn(minKeyboardHeightDp, maxKeyboardHeightDp)
    val safeInitialHeightDp = initialHeightDp.coerceIn(minKeyboardHeightDp, maxKeyboardHeightDp)

    var currentHeightDp by remember { mutableFloatStateOf(safeInitialHeightDp.toFloat()) }
    var currentBottomPaddingDpState by remember { mutableFloatStateOf(currentBottomPaddingDp.toFloat()) }
    var floatingMode by remember(isFloatingMode) { mutableStateOf(isFloatingMode) }
    var opacity by remember { mutableFloatStateOf(initialOpacity.coerceIn(0.3f, 1f)) }
    if (LocalOnBackPressedDispatcherOwner.current != null) {
        BackHandler(onBack = onCancel)
    }

    val currentOnHeightChange by rememberUpdatedState(onHeightChange)
    val currentOnBottomPaddingChange by rememberUpdatedState(onBottomPaddingChange)
    val currentOnReset by rememberUpdatedState(onReset)

    Box(
        modifier = modifier
            .background(Color.Transparent)
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxSize()
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.5f))
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val paddingChangeDp = with(density) { -dragAmount.y.toDp().value }
                            if (!isFloatingMode) {
                                currentBottomPaddingDpState = (currentBottomPaddingDpState + paddingChangeDp)
                                    .coerceIn(0f, maxBottomPaddingDp.toFloat())
                            }
                        },
                        onDragEnd = {
                            currentOnBottomPaddingChange(currentBottomPaddingDpState.roundToInt())
                        }
                    )
                }
        ) {
            // Height drag handle at top
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(32.dp)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val heightChangeDp = with(density) { -dragAmount.y.toDp().value }
                                currentHeightDp = (currentHeightDp + heightChangeDp)
                                    .coerceIn(minKeyboardHeightDp.toFloat(), maxKeyboardHeightDp.toFloat())
                                currentOnHeightChange(currentHeightDp.roundToInt())
                            },
                            onDragEnd = {
                                currentOnHeightChange(currentHeightDp.roundToInt())
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(60.dp)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color.White.copy(alpha = 0.8f))
                )
            }

            Column(
                modifier = Modifier.fillMaxSize().padding(start = 20.dp, end = 20.dp, top = 36.dp, bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("透明度 ${((1f - opacity) * 100).roundToInt()}%", color = Color.White, fontSize = 13.sp)
                        Slider(
                            value = 1f - opacity,
                            onValueChange = { opacity = 1f - it; onOpacityChange(opacity) },
                            valueRange = 0f..0.7f,
                            modifier = Modifier.fillMaxWidth().testTag("keyboard-opacity-slider"),
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = {
                            currentHeightDp = safeDefaultHeightDp.toFloat()
                            currentBottomPaddingDpState = 0f
                            opacity = 1f
                            onOpacityChange(opacity)
                            currentOnReset(safeDefaultHeightDp)
                        },
                        modifier = Modifier.size(48.dp).background(Color.White.copy(alpha = 0.3f), CircleShape),
                    ) {
                        Icon(Icons.Default.RestartAlt, contentDescription = "重置", tint = Color.White)
                    }
                    TextButton(onClick = {
                        floatingMode = !floatingMode
                        onFloatingModeChange?.invoke(floatingMode)
                    }, modifier = Modifier.semantics { contentDescription = "悬浮键盘" }) {
                        Text(if (floatingMode) "固定" else "悬浮", color = Color.White, fontSize = 13.sp)
                    }
                    IconButton(
                        onClick = { onConfirm(currentHeightDp.roundToInt(), currentBottomPaddingDpState.roundToInt(), floatingMode, opacity) },
                        modifier = Modifier.size(48.dp).background(Color.White.copy(alpha = 0.3f), CircleShape),
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "确认", tint = Color.White)
                    }
                }
            }
        }
    }
}
