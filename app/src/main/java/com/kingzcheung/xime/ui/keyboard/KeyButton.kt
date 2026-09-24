package com.kingzcheung.xime.ui.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.kingzcheung.xime.settings.ButtonLayout
import com.kingzcheung.xime.util.CharInfo
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment

/** 按键视觉缩进（padding），用于消除 spacedBy 死区。
 *  pointerInput 在 padding 之前，触摸区=全尺寸；
 *  shadow/clip/background 在 padding 之后，视觉区=缩进后。
 *  所有布局默认同一比例；自定义 spacing_x/y 可显式覆盖。 */
val LocalKeyVisualPadding = staticCompositionLocalOf {
    PaddingValues(2.dp)
}

/** 按键圆角半径，由各布局在根层通过 CompositionLocalProvider 提供。
 *  独立于 shadow.shape_radius，为统一配置化而设。 */
val LocalKeyCornerRadius = staticCompositionLocalOf { 8.dp }

/** 按键内容随按键实际高度放大；手机尺寸下保持原字号。 */
internal fun adaptiveKeyContentScale(
    keyHeightDp: Float,
    referenceHeightDp: Float = 56f,
): Float {
    if (!keyHeightDp.isFinite() || keyHeightDp <= 0f) return 1f
    return (keyHeightDp / referenceHeightDp).coerceIn(1f, 1.5f)
}

/** 滑动提示在大按键上比主字符增长稍快，避免视觉上仍然偏小。 */
internal fun adaptiveHintScale(contentScale: Float): Float =
    (1f + (contentScale - 1f) * 1.5f).coerceIn(1f, 1.7f)

/** 气泡跟随提示放大，但略微收敛，避免在平板上显得过重。 */
internal fun adaptiveBubbleScale(contentScale: Float): Float =
    adaptiveHintScale(contentScale).coerceAtMost(1.5f)

/** 主字符放大时同步拉开上下提示，手机尺寸下保持原来的 14dp 间距。 */
internal fun adaptiveHintOffsetDp(contentScale: Float): Float =
    (14f + (contentScale - 1f) * 25f).coerceIn(14f, 24f)

data class SwipeState(
    val isSwiping: Boolean = false,
    val swipeText: String? = null,
    val isSwipeDown: Boolean = false,
    val charInfos: List<CharInfo> = emptyList(),
    val isPressed: Boolean = false,
    val pressedText: String? = null,
    val isDanger: Boolean = false,
    // 长按弹出选择
    val isLongPress: Boolean = false,
    val longPressItems: List<String> = emptyList(),
    val selectedLongPressIndex: Int = 0,
    val longPressDrawableIds: List<Int> = emptyList(),
)

private val shadowColorCache = HashMap<Color, Color>()

internal fun crispShadowColor(backgroundColor: Color): Color {
    return shadowColorCache.getOrPut(backgroundColor) {
        val r = backgroundColor.red
        val g = backgroundColor.green
        val b = backgroundColor.blue
        val maxChroma = maxOf(r, g, b) - minOf(r, g, b)
        val luminance = 0.299f * r + 0.587f * g + 0.114f * b
        if (maxChroma > 0.05f) {
            Color(r * 0.95f, g * 0.95f, b * 0.95f, backgroundColor.alpha)
        } else if (luminance > 0.5f) {
            Color.Black.copy(alpha = 0.10f)
        } else {
            Color.White.copy(alpha = 0.12f)
        }
    }
}

@Composable
fun KeyButton(
    text: String,
    onClick: () -> Unit,
    backgroundColor: Color,
    textColor: Color,
    modifier: Modifier = Modifier,
    isHighlighted: Boolean = false,
    swipeText: String? = null,
    swipeDownText: String? = null,
    onSwipe: ((String) -> Unit)? = null,
    onSwipeDown: ((String) -> Unit)? = null,
    onSwipeStateChange: ((SwipeState) -> Unit)? = null,
    fontSize: androidx.compose.ui.unit.TextUnit? = null,
    onPress: (() -> Unit)? = null,
    onRelease: (() -> Unit)? = null,
    /** 长按回调（含震动反馈），点按仍走 [onClick] */
    onLongClick: (() -> Unit)? = null,
    /** 右上角角标文字（如 T9 数字键的数字浮标） */
    badgeText: String? = null,
    shadowEnabled: Boolean = true,
    shadowElevation: Dp = 1.dp,
    shadowShapeRadius: Dp = 8.dp,
) {
    var isPressed by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val view = LocalView.current
    val keyFontFamily = AppFonts.keyFontFamily
    val keyLabelFontFamily = AppFonts.keyLabelFontFamily
    val currentActions by rememberUpdatedState(KeyGestureActions(
        text = text,
        onTap = onClick,
        onPress = { isPressed = true; onPress?.invoke() },
        onRelease = { isPressed = false; onRelease?.invoke() },
        onPreview = { onSwipeStateChange?.invoke(it) },
        upText = swipeText,
        downText = swipeDownText,
        onUp = onSwipe,
        onDown = onSwipeDown,
        onLongPress = onLongClick,
        onLongPressFeedback = { view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS) },
    ))

    val shadowModifier = remember(shadowEnabled, shadowElevation, shadowShapeRadius, density, backgroundColor) {
        if (shadowEnabled) {
            val offsetPx = with(density) { shadowElevation.toPx() }
            val cornerPx = with(density) { shadowShapeRadius.toPx() }
            val color = crispShadowColor(backgroundColor)
            Modifier.drawBehind {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(0f, offsetPx),
                    size = size,
                    cornerRadius = CornerRadius(cornerPx)
                )
            }
        } else Modifier
    }
    val keyCornerRadius = LocalKeyCornerRadius.current
    val keyClipShape = remember(keyCornerRadius) { RoundedCornerShape(keyCornerRadius) }
    
    // 辅助函数：生成更深的颜色（混合黑色）
    fun darkenColor(color: Color, factor: Float = 0.15f): Color {
        return Color(
            red = (color.red * (1 - factor)).coerceIn(0f, 1f),
            green = (color.green * (1 - factor)).coerceIn(0f, 1f),
            blue = (color.blue * (1 - factor)).coerceIn(0f, 1f),
            alpha = color.alpha
        )
    }
    
        BoxWithConstraints(
            modifier = modifier
                .fillMaxHeight()
                .fillMaxWidth()
                .pointerInput(Unit) { detectExclusiveKeyGestures { currentActions } }
            .padding(scaledKeyVisualPadding())
            .keyGlow(Modifier.then(shadowModifier)
            .clip(keyClipShape)
            .background(
                if (isPressed) darkenColor(backgroundColor, 0.2f)
                else if (isHighlighted) backgroundColor.copy(alpha = 0.8f)
                else backgroundColor
            )),
        contentAlignment = Alignment.Center
    ) {
        val contentScale = keyContentScale(maxWidth.value, maxHeight.value)
        val hintScale = adaptiveHintScale(contentScale)
        val hintSize = 9f * hintScale
        val hintOffset = KeyboardKeyMetrics.hintOffsetDp(maxHeight.value, hintSize, density.fontScale, contentScale).dp
        val labelSize = keyLabelSizeSp(text,
            fontSize?.takeUnless { it == androidx.compose.ui.unit.TextUnit.Unspecified }?.value
                ?: if (text.length > 2) 14f else 16f,
            maxWidth.value, maxHeight.value, density.fontScale,
            LocalKeyboardInputPreferences.current.keyTextScale)
        Text(
            text = punctuationKeyLabel(text),
            modifier = Modifier.fillMaxWidth().offset(y = if (!swipeText.isNullOrEmpty()) 2.dp else 0.dp),
            color = textColor,
            fontSize = labelSize.sp,
            lineHeight = (labelSize * 1.2f).sp,
            fontWeight = if (text.length > 2) FontWeight.Medium else FontWeight.Normal,
            textAlign = TextAlign.Center,
            maxLines = 1,
            fontFamily = keyFontFamily
        )
        
        if (!swipeText.isNullOrEmpty() && swipeText != badgeText) {
            val displayText = if (swipeText.length <= 4) swipeText else swipeText.take(4)
            Text(
                text = punctuationKeyLabel(displayText),
                color = textColor.copy(alpha = 0.5f),
                fontSize = hintSize.sp,
                lineHeight = (hintSize * 1.2f).sp,
                fontWeight = FontWeight.Normal,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 2.dp, end = 5.dp),
                fontFamily = keyLabelFontFamily
            )
        }
        
        if (badgeText != null) {
            Text(
                text = badgeText,
                color = textColor.copy(alpha = 0.5f),
                fontSize = (10f * hintScale).sp,
                lineHeight = (12f * hintScale).sp,
                fontWeight = FontWeight.Normal,
                textAlign = TextAlign.End,
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 6.dp, end = 6.dp),
                fontFamily = keyLabelFontFamily
            )
        }
    }
}

@Composable
fun SwipeableKeyButton(
    text: String,
    onClick: () -> Unit,
    backgroundColor: Color,
    textColor: Color,
    modifier: Modifier = Modifier,
    isHighlighted: Boolean = false,
    layoutMode: ButtonLayout = ButtonLayout.STANDARD,
    icon: Painter? = null,
    swipeText: String? = null,
    swipeDownText: String? = null,
    /** 下滑文本显示在按键上（气泡为空，用于 display:key） */
    swipeDownKeyLabel: String? = null,
    /** 上滑文本显示在按键上（气泡则为空，用于 display:bubble） */
    swipeUpKeyLabel: String? = null,
    onSwipe: ((String) -> Unit)? = null,
    onSwipeDown: ((String) -> Unit)? = null,
    onSwipeStateChange: ((SwipeState, Rect) -> Unit)? = null,
    onPress: (() -> Unit)? = null,
    onRelease: (() -> Unit)? = null,
    onLongPressSelect: ((String) -> Unit)? = null,
    longPressItems: List<String>? = null,
    longPressDrawableIds: List<Int>? = null,
    /** 右上角角标文字（如 T9 数字键的数字浮标） */
    badgeText: String? = null,
    fontSize: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified,
    swipeFontSize: androidx.compose.ui.unit.TextUnit = 9.sp,
    shadowEnabled: Boolean = true,
    shadowElevation: Dp = 1.dp,
    shadowShapeRadius: Dp = 8.dp,
) {
    var isPressed by remember { mutableStateOf(false) }
    var buttonBounds by remember { mutableStateOf(Rect.Zero) }

    val view = LocalView.current
    val density = LocalDensity.current
    val currentActions by rememberUpdatedState(KeyGestureActions(
        text = text,
        onTap = onClick,
        onPress = { isPressed = true; onPress?.invoke() },
        onRelease = { isPressed = false; onRelease?.invoke() },
        onPreview = { onSwipeStateChange?.invoke(it, buttonBounds) },
        upText = swipeText,
        downText = swipeDownText,
        onUp = onSwipe,
        onDown = onSwipeDown,
        longPressItems = longPressItems.orEmpty(),
        longPressDrawableIds = longPressDrawableIds.orEmpty(),
        keyWidth = buttonBounds.width,
        onLongPressSelect = onLongPressSelect,
        onLongPressFeedback = { view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS) },
    ))

    val shiftTargets = LocalShiftSlideTargets.current
    val shiftToken = remember { Any() }
    val shiftLetter = text.lowercase().takeIf { it.length == 1 && it[0] in 'a'..'z' }
    androidx.compose.runtime.DisposableEffect(shiftTargets, shiftToken) {
        onDispose { shiftTargets?.remove(shiftToken) }
    }
    androidx.compose.runtime.SideEffect {
        if (shiftLetter != null) shiftTargets?.put(shiftToken, shiftLetter, buttonBounds) {
            currentActions.onPress()
            try { currentActions.onTap() } finally { currentActions.onRelease() }
        } else shiftTargets?.remove(shiftToken)
    }
    val shiftHovered = shiftLetter != null && shiftTargets?.hovered == shiftLetter

    val shadowModifier = remember(shadowEnabled, shadowElevation, shadowShapeRadius, density, backgroundColor) {
        if (shadowEnabled) {
            val offsetPx = with(density) { shadowElevation.toPx() }
            val cornerPx = with(density) { shadowShapeRadius.toPx() }
            val color = crispShadowColor(backgroundColor)
            Modifier.drawBehind {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(0f, offsetPx),
                    size = size,
                    cornerRadius = CornerRadius(cornerPx)
                )
            }
        } else Modifier
    }
    val keyCornerRadius = LocalKeyCornerRadius.current
    val keyClipShape = remember(keyCornerRadius) { RoundedCornerShape(keyCornerRadius) }
    val keyLabelFontFamily = AppFonts.keyLabelFontFamily
    val keyFontFamily = AppFonts.keyFontFamily

    BoxWithConstraints(
        modifier = modifier
            .fillMaxHeight()
            .fillMaxWidth()
            .pointerInput(Unit) { detectExclusiveKeyGestures { currentActions } }
            .onGloballyPositioned { coordinates ->
                buttonBounds = coordinates.boundsInRoot()
            }
            .padding(scaledKeyVisualPadding())
            .keyGlow(Modifier.then(shadowModifier)
            .clip(keyClipShape)
            .background(
                if (isPressed || shiftHovered) backgroundColor.copy(alpha = 0.7f)
                else if (isHighlighted) backgroundColor.copy(alpha = 0.8f)
                else backgroundColor
            )),
        contentAlignment = if (layoutMode == ButtonLayout.COMPACT) Alignment.TopStart else Alignment.Center
    ) {
        val contentScale = keyContentScale(maxWidth.value, maxHeight.value)
        val defaultFontSize = if (layoutMode == ButtonLayout.COMPACT) {
            if (text.length > 2) 13f else 16f
        } else if (text.length > 2) 14f else 18f
        val labelSize = keyLabelSizeSp(text,
            if (fontSize != androidx.compose.ui.unit.TextUnit.Unspecified) fontSize.value else defaultFontSize,
            maxWidth.value, maxHeight.value, density.fontScale,
            LocalKeyboardInputPreferences.current.keyTextScale)
        val hintScale = adaptiveHintScale(contentScale)
        val effectiveSwipeFontSize = (swipeFontSize.value * hintScale).sp
        // 缩窄/矮键盘中仍将提示留在键帽内，不能只按常规行高使用固定偏移。
        val hintHeightDp = with(LocalDensity.current) { effectiveSwipeFontSize.toDp().value * 1.2f }
        val hintOffset = adaptiveHintOffsetDp(contentScale)
            .coerceAtMost(((maxHeight.value - hintHeightDp) / 2f - 2f).coerceAtLeast(0f)).dp

        val compactIconSize = keyIconSizeDp(maxWidth.value, maxHeight.value, 16f).dp
        if (layoutMode == ButtonLayout.COMPACT) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (icon != null) {
                    Icon(
                        painter = icon,
                        contentDescription = text,
                        tint = textColor,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(top = 2.dp, start = 4.dp)
                            .size(compactIconSize)
                    )
                } else {
                    Text(
                        text = punctuationKeyLabel(text),
                        color = textColor,
                        fontSize = labelSize.sp,
                        fontWeight = if (text.length > 2) FontWeight.Medium else FontWeight.Normal,
                        textAlign = TextAlign.Start,
                        maxLines = 1,
                        lineHeight = (labelSize * 1.2f).sp,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(top = 2.dp, start = 4.dp),
                        fontFamily = keyFontFamily
                    )
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .fillMaxHeight()
                        .padding(top = 4.dp, end = 4.dp, bottom = 2.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    val swipeUpHint = swipeUpKeyLabel ?: swipeText
                    if (!swipeUpHint.isNullOrEmpty()) {
                        val displayText = if (swipeUpHint.length <= 2) swipeUpHint else swipeUpHint.take(2)
                        Text(
                            text = punctuationKeyLabel(displayText),
                            color = textColor.copy(alpha = 0.6f),
                            fontSize = effectiveSwipeFontSize,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.End,
                            maxLines = 1,
                            lineHeight = 1.sp
                        )
                    }

                    val swipeDownHint = swipeDownKeyLabel
                    if (!swipeDownHint.isNullOrEmpty()) {
                        val hasChinese = swipeDownHint.any { it in '\u4e00'..'\u9fff' || it in '\u3400'..'\u4dbf' || it in '\uf900'..'\ufaff' }
                        val adjustedFontSize = if (hasChinese && effectiveSwipeFontSize > 6.sp) (effectiveSwipeFontSize.value * 0.85f).sp else effectiveSwipeFontSize
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.BottomEnd
                        ) {
                            val displayText = if (swipeDownHint.length <= 12) swipeDownHint else swipeDownHint.take(12)
                            Text(
                                text = punctuationKeyLabel(displayText),
                                color = textColor.copy(alpha = 0.7f),
                                fontSize = adjustedFontSize,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Right,
                                maxLines = 3,
                                lineHeight = adjustedFontSize,
                                fontFamily = keyLabelFontFamily
                            )
                        }
                    }
                }
            }
        } else {
            if (icon != null) {
                Icon(
                    painter = icon,
                    contentDescription = text,
                    tint = textColor,
                    modifier = Modifier.size(keyIconSizeDp(maxWidth.value, maxHeight.value).dp)
                )
            } else {
                Text(
                    text = punctuationKeyLabel(text),
                    modifier = Modifier.fillMaxWidth().offset(y = if (!(swipeUpKeyLabel ?: swipeText).isNullOrEmpty()) 2.dp else 0.dp),
                    color = textColor,
                    fontSize = labelSize.sp,
                    lineHeight = (labelSize * 1.2f).sp,
                    fontWeight = if (text.length > 2) FontWeight.Medium else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    fontFamily = keyFontFamily
                )
            }

            // 上滑提示与角标文字相同（如九键/笔画上滑输入键面数字）时不再重复渲染提示，
            // 角标已表达该信息；swipeText 状态保持非空，上滑触发与气泡不受影响。
            if (!(swipeUpKeyLabel ?: swipeText).isNullOrEmpty() && (swipeUpKeyLabel ?: swipeText) != badgeText) {
                val keyLabel = (swipeUpKeyLabel ?: swipeText)!!
                val displayText = if (keyLabel.length <= 4) keyLabel else keyLabel.take(4)
                Text(
                    text = punctuationKeyLabel(displayText),
                    color = textColor.copy(alpha = 0.6f),
                    fontSize = effectiveSwipeFontSize,
                    lineHeight = (effectiveSwipeFontSize.value * 1.2f).sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.align(Alignment.TopEnd).padding(top = 2.dp, end = 5.dp),
                    fontFamily = keyLabelFontFamily
                )
            }

            if (!swipeDownKeyLabel.isNullOrEmpty()) {
                val displayText = if (swipeDownKeyLabel.length <= 4) swipeDownKeyLabel else swipeDownKeyLabel.take(4)
                Text(
                    text = punctuationKeyLabel(displayText),
                    color = textColor.copy(alpha = 0.5f),
                    fontSize = effectiveSwipeFontSize,
                    lineHeight = (effectiveSwipeFontSize.value * 1.2f).sp,
                    fontWeight = FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.offset(y = hintOffset),
                    fontFamily = keyLabelFontFamily
                )
            }

            if (badgeText != null) {
                Text(
                    text = badgeText,
                    color = textColor.copy(alpha = 0.5f),
                    fontSize = (10f * hintScale).sp,
                    fontWeight = FontWeight.Normal,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    lineHeight = 1.sp,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 6.dp, end = 6.dp)
                )
            }
        }
    }
}

@Composable
fun KeyboardRow(
    keys: List<String>,
    onKeyPress: (String) -> Unit,
    keyBackgroundColor: Color,
    keyTextColor: Color,
    isShifted: Boolean,
    modifier: Modifier = Modifier,
    swipeKeys: List<String>? = null,
    swipeDownKeys: List<String>? = null,
    onSwipeKey: ((String) -> Unit)? = null,
    onSwipeDownKey: ((String) -> Unit)? = null,
    onSwipeStateChange: ((SwipeState, Rect) -> Unit)? = null,
    onKeyPressDown: ((String) -> Unit)? = null,
    onKeyRelease: ((String) -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
    ) {
        keys.forEachIndexed { index, key ->
            val swipeText = swipeKeys?.getOrNull(index)
            val swipeDownText = swipeDownKeys?.getOrNull(index)
            val rowOnClick = remember(key, onKeyPress) { { onKeyPress(key) } }
            val rowOnPress: (() -> Unit)? = remember(key, onKeyPressDown) { { onKeyPressDown?.invoke(key); Unit } }
            val rowOnRelease: (() -> Unit)? = remember(key, onKeyRelease) { { onKeyRelease?.invoke(key); Unit } }
            SwipeableKeyButton(
                text = if (isShifted) key.uppercase() else key,
                onClick = rowOnClick,
                backgroundColor = keyBackgroundColor,
                textColor = keyTextColor,
                modifier = Modifier.weight(1f),
                swipeText = swipeText,
                swipeDownText = swipeDownText,
                onSwipe = onSwipeKey,
                onSwipeDown = onSwipeDownKey,
                onSwipeStateChange = onSwipeStateChange,
                onPress = rowOnPress,
                onRelease = rowOnRelease
            )
        }
    }
}

@Composable
fun IconKeyButton(
    icon: Painter,
    onClick: () -> Unit,
    backgroundColor: Color,
    iconColor: Color,
    modifier: Modifier = Modifier,
    isHighlighted: Boolean = false,
    iconSize: androidx.compose.ui.unit.Dp = KeyboardKeyMetrics.FunctionIconSize,
    onPress: (() -> Unit)? = null,
    onRelease: (() -> Unit)? = null,
    shadowEnabled: Boolean = true,
    shadowElevation: Dp = 1.dp,
    shadowShapeRadius: Dp = 8.dp,
    contentDescription: String? = null,
) {
    var isPressed by remember { mutableStateOf(false) }
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnPress by rememberUpdatedState(onPress)
    val currentOnRelease by rememberUpdatedState(onRelease)
    val density = LocalDensity.current

    val shadowModifier = remember(shadowEnabled, shadowElevation, shadowShapeRadius, density, backgroundColor) {
        if (shadowEnabled) {
            val offsetPx = with(density) { shadowElevation.toPx() }
            val cornerPx = with(density) { shadowShapeRadius.toPx() }
            val color = crispShadowColor(backgroundColor)
            Modifier.drawBehind {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(0f, offsetPx),
                    size = size,
                    cornerRadius = CornerRadius(cornerPx)
                )
            }
        } else Modifier
    }
    val keyCornerRadius = LocalKeyCornerRadius.current
    val keyClipShape = remember(keyCornerRadius) { RoundedCornerShape(keyCornerRadius) }
    
    // 辅助函数：生成更深的颜色（混合黑色）
    fun darkenColor(color: Color, factor: Float = 0.15f): Color {
        return Color(
            red = (color.red * (1 - factor)).coerceIn(0f, 1f),
            green = (color.green * (1 - factor)).coerceIn(0f, 1f),
            blue = (color.blue * (1 - factor)).coerceIn(0f, 1f),
            alpha = color.alpha
        )
    }
    
    BoxWithConstraints(
        modifier = modifier
            .fillMaxHeight()
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        currentOnPress?.invoke()
                        tryAwaitRelease()
                        isPressed = false
                        currentOnRelease?.invoke()
                    },
                    onTap = {
                        currentOnClick()
                    }
                )
            }
            .padding(scaledKeyVisualPadding())
            .keyGlow(Modifier.then(shadowModifier)
            .clip(keyClipShape)
            .background(
                if (isPressed) darkenColor(backgroundColor, 0.1f)
                else if (isHighlighted) darkenColor(backgroundColor, 0.2f)
                else backgroundColor
            )),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = icon,
            contentDescription = contentDescription,
            tint = iconColor,
            modifier = Modifier.size(keyIconSizeDp(maxWidth.value, maxHeight.value, iconSize.value).dp)
        )

        // 右上角小圆点指示 — 仅在 isHighlighted 时显示
        if (isHighlighted) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(3.dp)
                    .size(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(iconColor)
            )
        }
    }
}

@Composable
fun SwipeableIconKeyButton(
    icon: Painter,
    onClick: () -> Unit,
    backgroundColor: Color,
    iconColor: Color,
    modifier: Modifier = Modifier,
    isHighlighted: Boolean = false,
    iconSize: androidx.compose.ui.unit.Dp = KeyboardKeyMetrics.FunctionIconSize,
    swipeText: String? = null,
    onSwipe: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onPress: (() -> Unit)? = null,
    onRelease: (() -> Unit)? = null,
    // 上滑/下滑/左滑增强
    swipeUpLabel: String? = null,
    swipeDownLabel: String? = null,
    onSwipeUp: (() -> Unit)? = null,
    onSwipeDown: (() -> Unit)? = null,
    onSwipeLeft: (() -> Unit)? = null,
    onSwipeStateChange: ((SwipeState, Rect) -> Unit)? = null,
    shadowEnabled: Boolean = true,
    shadowElevation: Dp = 1.dp,
    shadowShapeRadius: Dp = 8.dp,
) {
    // Gesture coroutines survive recomposition; route to the current editor/input callbacks.
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnRelease by rememberUpdatedState(onRelease)
    val currentOnPress by rememberUpdatedState(onPress)
    val currentOnLongClick by rememberUpdatedState(onLongClick)
    val currentOnSwipe by rememberUpdatedState(onSwipe)
    val currentOnSwipeUp by rememberUpdatedState(onSwipeUp)
    val currentOnSwipeDown by rememberUpdatedState(onSwipeDown)
    val currentOnSwipeLeft by rememberUpdatedState(onSwipeLeft)
    val currentOnSwipeStateChange by rememberUpdatedState(onSwipeStateChange)
    val currentSwipeUpLabel by rememberUpdatedState(swipeUpLabel)
    val currentSwipeDownLabel by rememberUpdatedState(swipeDownLabel)
    var isPressed by remember { mutableStateOf(false) }
    var dragOffsetY by remember { mutableStateOf(0f) }
    var dragOffsetX by remember { mutableStateOf(0f) }
    var hasTriggeredSwipe by remember { mutableStateOf(false) }
    var hasTriggeredSwipeDown by remember { mutableStateOf(false) }
    var hasTriggeredSwipeLeft by remember { mutableStateOf(false) }
    var isDragging by remember { mutableStateOf(false) }
    var isSwipingUp by remember { mutableStateOf(false) }
    var isSwipingDown by remember { mutableStateOf(false) }
    var isDangerZone by remember { mutableStateOf(false) }
    var hasReachedClearThreshold by remember { mutableStateOf(false) }
    var hasReachedUndoThreshold by remember { mutableStateOf(false) }
    var isLongPress by remember { mutableStateOf(false) }
    var hasTriggeredLongPress by remember { mutableStateOf(false) }
    var buttonBounds by remember { mutableStateOf(Rect(0f, 0f, 0f, 0f)) }
    var dragActivated by remember { mutableStateOf(false) }
    val keyLabelFontFamily = AppFonts.keyLabelFontFamily
    
    val density = LocalDensity.current
    val swipeUpThreshold = with(density) { (-50).dp.toPx() }
    val swipeDownThreshold = with(density) { 50.dp.toPx() }
    val swipeLeftThreshold = with(density) { (-50).dp.toPx() }
    val bubbleShowThresholdUp = swipeUpThreshold
    val bubbleShowThresholdDown = swipeDownThreshold
    
    // 上滑清空/下滑撤回需要更大的滑动距离，防止误触
    val clearActionThreshold = with(density) { (-50).dp.toPx() }
    val undoActionThreshold = with(density) { 50.dp.toPx() }
    // 水平位移超过该值视为横向手势（如键盘区滑动移动光标），不再触发点击。
    // 与 KeyboardView 光标手势激活阈值（activationThresholdPx = 60dp）对齐，
    // 消除 30~60dp 位移区间"点击被取消但光标手势未激活"的死区（打字吃键）。
    val horizontalClickCancelThreshold = with(density) { 60.dp.toPx() }
    
    LaunchedEffect(isLongPress) {
        if (isLongPress && currentOnLongClick != null) {
            hasTriggeredLongPress = true
            while (isLongPress) {
                // Each repeat is a new delete action, with the same configured feedback
                // as a physical press. Holding must not become silent after the first tick.
                currentOnPress?.invoke()
                currentOnLongClick?.invoke()
                // 长按重复间隔 30ms：80ms 时退格删除以 12.5Hz 离散更新
                // 候选栏，低于视觉融合阈值，看起来像"一闪一闪"；30ms 时更新更密集更顺滑。
                delay(30)
            }
        }
    }

    val shadowModifier = remember(shadowEnabled, shadowElevation, shadowShapeRadius, density, backgroundColor) {
        if (shadowEnabled) {
            val offsetPx = with(density) { shadowElevation.toPx() }
            val cornerPx = with(density) { shadowShapeRadius.toPx() }
            val color = crispShadowColor(backgroundColor)
            Modifier.drawBehind {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(0f, offsetPx),
                    size = size,
                    cornerRadius = CornerRadius(cornerPx)
                )
            }
        } else Modifier
    }
    val keyCornerRadius = LocalKeyCornerRadius.current
    val keyClipShape = remember(keyCornerRadius) { RoundedCornerShape(keyCornerRadius) }
    
    fun darkenColor(color: Color, factor: Float = 0.15f): Color {
        return Color(
            red = (color.red * (1 - factor)).coerceIn(0f, 1f),
            green = (color.green * (1 - factor)).coerceIn(0f, 1f),
            blue = (color.blue * (1 - factor)).coerceIn(0f, 1f),
            alpha = color.alpha
        )
    }
    
    BoxWithConstraints(
        modifier = modifier
            .fillMaxHeight()
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        currentOnPress?.invoke()
                        val released = tryAwaitRelease()
                        if (released || !dragActivated) {
                            isPressed = false
                            currentOnRelease?.invoke()
                            isLongPress = false
                            // 长按结束后必须重置：onTap 不会在长按后触发，
                            // 若残留 true 会吞掉下一次点击（退格键吃键）。
                            // onDragEnd/onDragCancel 虽也重置，但仅 drag 激活时触发，
                            // 长按无位移（drag 未激活）时走不到那里。
                            hasTriggeredLongPress = false
                        }
                    },
                    onTap = {
                        if (!dragActivated && !isDragging && !hasTriggeredLongPress) {
                            currentOnClick()
                        }
                        hasTriggeredLongPress = false
                    },
                    onLongPress = {
                        isLongPress = true
                    }
                )
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = {
                        dragActivated = true
                        isDragging = true
                        isPressed = true
                        dragOffsetY = 0f
                        dragOffsetX = 0f
                        hasTriggeredSwipe = false
                        hasTriggeredSwipeDown = false
                        hasTriggeredSwipeLeft = false
                        isSwipingUp = false
                        isSwipingDown = false
                        isDangerZone = false
                        hasReachedClearThreshold = false
                        hasReachedUndoThreshold = false
                        currentOnSwipeStateChange?.invoke(SwipeState(), buttonBounds)
                        currentOnPress?.invoke()
                    },
                    onDragEnd = {
                        if (hasReachedClearThreshold && currentOnSwipeUp != null) {
                            currentOnSwipeUp?.invoke()
                        } else if (hasReachedUndoThreshold && currentOnSwipeDown != null) {
                            currentOnSwipeDown?.invoke()
                        } else if (isSwipingUp && !hasTriggeredSwipe && currentOnSwipe != null) {
                            hasTriggeredSwipe = true
                            currentOnSwipe?.invoke()
                        } else if (dragOffsetY < swipeUpThreshold && !hasTriggeredSwipe && currentOnSwipe != null) {
                            hasTriggeredSwipe = true
                            currentOnSwipe?.invoke()
                        } else if (!hasTriggeredLongPress && !hasTriggeredSwipeLeft) {
                            currentOnClick()
                        }
                        dragActivated = false
                        isPressed = false
                        currentOnRelease?.invoke()
                        dragOffsetY = 0f
                        dragOffsetX = 0f
                        hasTriggeredSwipe = false
                        hasTriggeredSwipeDown = false
                        hasTriggeredSwipeLeft = false
                        isDragging = false
                        isSwipingUp = false
                        isSwipingDown = false
                        isDangerZone = false
                        hasReachedClearThreshold = false
                        hasReachedUndoThreshold = false
                        isLongPress = false
                        // 手势结束（含位移场景 tap 取消）必须重置，否则残留 true 会吞掉后续点击
                        hasTriggeredLongPress = false
                        currentOnSwipeStateChange?.invoke(SwipeState(), buttonBounds)
                    },
                    onDragCancel = {
                        dragActivated = false
                        isPressed = false
                        currentOnRelease?.invoke()
                        dragOffsetY = 0f
                        dragOffsetX = 0f
                        hasTriggeredSwipe = false
                        hasTriggeredSwipeDown = false
                        hasTriggeredSwipeLeft = false
                        isDragging = false
                        isSwipingUp = false
                        isSwipingDown = false
                        isDangerZone = false
                        hasReachedClearThreshold = false
                        hasReachedUndoThreshold = false
                        isLongPress = false
                        hasTriggeredLongPress = false
                        currentOnSwipeStateChange?.invoke(SwipeState(), buttonBounds)
                    },
                    onDrag = { change, dragAmount ->
                        dragOffsetY += dragAmount.y
                        dragOffsetX += dragAmount.x
                        
                        // 位移超过手势阈值才打断长按（轻微抖动不中断重复删除），
                        // 阈值与各手势触发阈值一致（左滑 -50dp / 上滑 -50dp / 下滑 50dp / 右滑 60dp）
                        if (isLongPress && (dragOffsetY < swipeUpThreshold || dragOffsetY > swipeDownThreshold || dragOffsetX < swipeLeftThreshold || dragOffsetX > horizontalClickCancelThreshold)) {
                            isLongPress = false
                        }
                        
                        if (dragOffsetX < swipeLeftThreshold && !hasTriggeredSwipeLeft && currentOnSwipeLeft != null) {
                            hasTriggeredSwipeLeft = true
                            currentOnSwipeLeft?.invoke()
                        }
                        
                        if (dragOffsetY < 0 && dragOffsetX >= swipeLeftThreshold) {
                            val showUp = dragOffsetY < bubbleShowThresholdUp && currentSwipeUpLabel != null
                            if (showUp != isSwipingUp) {
                                isSwipingUp = showUp
                                isSwipingDown = false
                                currentOnSwipeStateChange?.invoke(
                                    SwipeState(isSwiping = showUp, swipeText = currentSwipeUpLabel, isSwipeDown = false),
                                    buttonBounds
                                )
                            }
                            
                            val inDanger = dragOffsetY < clearActionThreshold
                            if (inDanger != isDangerZone) {
                                isDangerZone = inDanger
                                currentOnSwipeStateChange?.invoke(
                                    SwipeState(isSwiping = true, swipeText = currentSwipeUpLabel, isSwipeDown = false, isDanger = inDanger),
                                    buttonBounds
                                )
                            }
                            
                            hasReachedClearThreshold = inDanger
                        }
                        
                        if (dragOffsetY > 0 && dragOffsetX >= swipeLeftThreshold) {
                            val showDown = dragOffsetY > bubbleShowThresholdDown && currentSwipeDownLabel != null
                            if (showDown != isSwipingDown) {
                                isSwipingDown = showDown
                                isSwipingUp = false
                                currentOnSwipeStateChange?.invoke(
                                    SwipeState(isSwiping = showDown, swipeText = currentSwipeDownLabel, isSwipeDown = true),
                                    buttonBounds
                                )
                            }
                            
                            val inDanger = dragOffsetY > undoActionThreshold
                            if (inDanger != isDangerZone) {
                                isDangerZone = inDanger
                                currentOnSwipeStateChange?.invoke(
                                    SwipeState(isSwiping = true, swipeText = currentSwipeDownLabel, isSwipeDown = true, isDanger = inDanger),
                                    buttonBounds
                                )
                            }
                            
                            hasReachedUndoThreshold = inDanger
                        }
                    }
                )
            }
            .onGloballyPositioned { coordinates ->
                buttonBounds = coordinates.boundsInRoot()
            }
            .padding(scaledKeyVisualPadding())
            .keyGlow(Modifier.then(shadowModifier)
            .clip(keyClipShape)
            .background(
                if (isPressed) darkenColor(backgroundColor, 0.2f)
                else if (isHighlighted) backgroundColor.copy(alpha = 0.8f)
                else backgroundColor
            )),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(keyIconSizeDp(maxWidth.value, maxHeight.value, iconSize.value).dp)
        )
        
        if (!swipeText.isNullOrEmpty()) {
            val contentScale = keyContentScale(maxWidth.value, maxHeight.value)
            val hintSize = 9f * adaptiveHintScale(contentScale)
            Text(
                text = punctuationKeyLabel(swipeText),
                color = iconColor.copy(alpha = 0.5f),
                fontSize = hintSize.sp,
                lineHeight = (hintSize * 1.2f).sp,
                fontWeight = FontWeight.Normal,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.offset(y = -KeyboardKeyMetrics.hintOffsetDp(maxHeight.value, hintSize, density.fontScale, contentScale).dp),
                fontFamily = keyLabelFontFamily
            )
        }
    }
}
