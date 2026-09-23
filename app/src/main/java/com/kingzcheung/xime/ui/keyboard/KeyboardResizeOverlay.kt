package com.kingzcheung.xime.ui.keyboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Check
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.stateDescription
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

    BoxWithConstraints(
        modifier = modifier
            .background(Color.Transparent)
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
    ) {
        val compact = maxHeight < 190.dp
        val singleLineControls = maxHeight < 160.dp
        val controlScale = minOf(maxWidth.value / 360f, maxHeight.value / 260f).coerceIn(1f, 1.25f)
        val textSize = (16f * controlScale).sp
        val actionSize = if (controlScale > 1.15f) 56.dp else 48.dp
        val iconSize = (24f * controlScale).dp
        val handleHeight = if (compact) 24.dp else 32.dp
        val surfaceColor = Color(0xFF10141A)
        val outlineColor = Color(0xFF99A4B3)
        val accentColor = MaterialTheme.colorScheme.primary.let { color ->
            // Controls sit on a dark surface even when the current keyboard is a light theme.
            androidx.compose.ui.graphics.lerp(color, Color.White, 0.35f)
        }
        val opacityLabel: @Composable (Modifier) -> Unit = { labelModifier ->
            // Give mixed CJK/Latin text a real layout width. Intrinsic single-line width can
            // round down at a glyph boundary and wrap the percentage into a clipped second line.
            Text("透明度 ${((1f - opacity) * 100).roundToInt()}%", modifier = labelModifier,
                color = Color.White, fontSize = textSize, lineHeight = textSize * 1.2f,
                fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false,
                textAlign = TextAlign.Center)
        }
        val opacitySlider: @Composable (Modifier) -> Unit = { sliderModifier ->
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides if (compact) 40.dp else 48.dp) {
                Slider(
                    value = 1f - opacity,
                    onValueChange = { opacity = 1f - it; onOpacityChange(opacity) },
                    valueRange = 0f..0.7f,
                    colors = SliderDefaults.colors(thumbColor = accentColor,
                        activeTrackColor = accentColor, inactiveTrackColor = Color(0xFF596373)),
                    modifier = sliderModifier.height(if (compact) 40.dp else 48.dp)
                        .testTag("keyboard-opacity-slider"),
                )
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxSize()
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.72f))
                .pointerInput(floatingMode) {
                    detectDragGestures(
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val paddingChangeDp = with(density) { -dragAmount.y.toDp().value }
                            if (!floatingMode) {
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
                    .height(handleHeight)
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
                        .width(72.dp)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color.White)
                )
            }

            Column(
                modifier = Modifier.fillMaxSize().padding(start = 12.dp, end = 12.dp,
                    top = handleHeight, bottom = if (compact) 4.dp else 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(
                        modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp)).background(surfaceColor)
                            .border(1.dp, outlineColor.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                            .padding(horizontal = 12.dp, vertical = if (compact) 2.dp else 12.dp)
                            .testTag("keyboard-resize-opacity-panel"),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (singleLineControls) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                opacityLabel(Modifier.weight(1.2f))
                                Spacer(Modifier.width(12.dp))
                                opacitySlider(Modifier.weight(1f))
                            }
                        } else {
                            opacityLabel(Modifier.fillMaxWidth())
                            opacitySlider(Modifier.fillMaxWidth())
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().height(actionSize),
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
                        modifier = Modifier.size(actionSize).background(surfaceColor, CircleShape)
                            .border(1.dp, outlineColor, CircleShape),
                    ) {
                        Icon(Icons.Default.RestartAlt, contentDescription = "重置", tint = Color.White,
                            modifier = Modifier.size(iconSize))
                    }
                    OutlinedButton(onClick = {
                        floatingMode = !floatingMode
                        onFloatingModeChange?.invoke(floatingMode)
                    }, modifier = Modifier.height(actionSize).widthIn(min = 104.dp)
                        .semantics {
                            contentDescription = "悬浮键盘"
                            stateDescription = if (floatingMode) "当前悬浮" else "当前固定"
                        }.testTag("keyboard-resize-floating-button"),
                        shape = RoundedCornerShape(24.dp), border = BorderStroke(1.5.dp, outlineColor),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = surfaceColor, contentColor = Color.White),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    ) {
                        Icon(if (floatingMode) Icons.Default.Keyboard else Icons.Default.OpenInNew,
                            contentDescription = null, modifier = Modifier.size(iconSize))
                        Spacer(Modifier.width(8.dp))
                        Text(if (floatingMode) "固定" else "悬浮", fontSize = textSize,
                            lineHeight = textSize * 1.2f, fontWeight = FontWeight.Medium, maxLines = 1)
                    }
                    IconButton(
                        onClick = { onConfirm(currentHeightDp.roundToInt(), currentBottomPaddingDpState.roundToInt(), floatingMode, opacity) },
                        modifier = Modifier.size(actionSize).background(surfaceColor, CircleShape)
                            .border(1.dp, outlineColor, CircleShape),
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "确认", tint = Color.White,
                            modifier = Modifier.size(iconSize))
                    }
                }
            }
        }
    }
}
