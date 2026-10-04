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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
    val keyFlick: KeyFlickPreview? = null,
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
    var flickPreview by remember { mutableStateOf<KeyFlickPreview?>(null) }
    val density = LocalDensity.current
    val view = LocalView.current
    val keyFontFamily = AppFonts.keyFontFamily
    val keyLabelFontFamily = AppFonts.keyLabelFontFamily
    val currentActions by rememberUpdatedState(KeyGestureActions(
        text = text,
        onTap = onClick,
        onPress = { isPressed = true; onPress?.invoke() },
        onRelease = { isPressed = false; onRelease?.invoke() },
        onPreview = { flickPreview = it.keyFlick; onSwipeStateChange?.invoke(it) },
        inlineVerticalPreview = true,
        upText = swipeText,
        downText = swipeDownText,
        onUp = onSwipe,
        onDown = onSwipeDown,
        onLongPress = onLongClick,
        onLongPressFeedback = { view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS) },
    ))

    val frostedGlass = LocalKeyboardInputPreferences.current.frostedGlass
    val shadowModifier = remember(shadowEnabled, shadowElevation, shadowShapeRadius, density, backgroundColor, frostedGlass.enabled) {
        if (shadowEnabled && !frostedGlass.enabled) {
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
            .background(frostedKeyColor(
                backgroundColor, textColor, frostedGlass,
                legacyStateColor = if (isPressed) darkenColor(backgroundColor, 0.2f)
                else if (isHighlighted) backgroundColor.copy(alpha = 0.8f)
                else backgroundColor,
                pressed = isPressed, highlighted = isHighlighted,
            )).keyHeldHighlight(isPressed)),
        contentAlignment = Alignment.Center
    ) {
        val contentScale = keyContentScale(maxWidth.value, maxHeight.value)
        val hintScale = adaptiveHintScale(contentScale)
        val hintSize = 10f * hintScale
        val hintOffset = KeyboardKeyMetrics.hintOffsetDp(maxHeight.value, hintSize, density.fontScale, contentScale).dp
        val labelSize = keyLabelSizeSp(text,
            fontSize?.takeUnless { it == androidx.compose.ui.unit.TextUnit.Unspecified }?.value
                ?: KeyboardKeyMetrics.LabelSize.value,
            maxWidth.value, maxHeight.value, density.fontScale,
            LocalKeyboardInputPreferences.current.keyTextScale)
        val keyHeight = maxHeight
        Box(Modifier.fillMaxSize().graphicsLayer {
            alpha = 1f - (flickPreview?.progress ?: 0f)
        }, contentAlignment = Alignment.Center) {
            Text(
                text = punctuationKeyLabel(text),
                modifier = Modifier.fillMaxWidth().offset(y = if (!swipeText.isNullOrEmpty() && keyHeight >= 40.dp) 2.dp else 0.dp),
                color = textColor,
                fontSize = labelSize.sp,
                lineHeight = (labelSize * 1.2f).sp,
                fontWeight = if (text.length > 2) FontWeight.Medium else FontWeight.Normal,
                textAlign = TextAlign.Center,
                maxLines = 1,
                fontFamily = keyFontFamily
            )

            if (flickPreview?.fromTop != true && !swipeText.isNullOrEmpty() && swipeText != badgeText) {
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

            if (badgeText != null && !(flickPreview?.fromTop == true && flickPreview?.text == badgeText)) {
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
        flickPreview?.let { KeyFlickLabel(it, textColor, maxWidth.value, maxHeight.value, hintSize, labelSize) }
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
    /** Letter layouts opt in to the user's top/bottom symbol gesture direction. */
    followSymbolSwipeDirection: Boolean = false,
    /** Literal upper-hint input, independent of whether its visual hint is enabled. */
    symbolInputText: String? = null,
    onSwipe: ((String) -> Unit)? = null,
    onSwipeDown: ((String) -> Unit)? = null,
    onSwipeLeft: (() -> Unit)? = null,
    swipeLeftText: String? = null,
    letterGroup: String? = null,
    onLetterSelection: ((String) -> Unit)? = null,
    onSwipeStateChange: ((SwipeState, Rect) -> Unit)? = null,
    onPress: (() -> Unit)? = null,
    onRelease: (() -> Unit)? = null,
    onLongPressSelect: ((String) -> Unit)? = null,
    longPressItems: List<String>? = null,
    longPressDrawableIds: List<Int>? = null,
    /** Transparent row-edge space belongs to this key's gestures, but not its keycap. */
    edgeContentPadding: PaddingValues = PaddingValues(0.dp),
    /** 右上角角标文字（如 T9 数字键的数字浮标） */
    badgeText: String? = null,
    fontSize: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified,
    swipeFontSize: androidx.compose.ui.unit.TextUnit = 10.sp,
    shadowEnabled: Boolean = true,
    shadowElevation: Dp = 1.dp,
    shadowShapeRadius: Dp = 8.dp,
) {
    val customLayout = LocalCustomLayout.current
    val letterGeometry = LocalLetterGeometry.current
    val customAccent = LocalCustomAccent.current
    val resolvedColors = customLayoutKeyColors(customLayout, text, backgroundColor, textColor, customAccent)
    val resolvedBackground = resolvedColors.first
    val resolvedText = resolvedColors.second
    val symbolInputMode = LocalKeyboardInputPreferences.current.symbolInputMode
    val inputPreferences = LocalKeyboardInputPreferences.current
    val letters = fourteenKeyLetters(letterGroup, LocalFourteenKeyLayout.current &&
        inputPreferences.fourteenLetterSwipe && onLetterSelection != null)
    var isPressed by remember { mutableStateOf(false) }
    var flickPreview by remember { mutableStateOf<KeyFlickPreview?>(null) }
    var buttonBounds by remember { mutableStateOf(Rect.Zero) }

    val view = LocalView.current
    val density = LocalDensity.current
    val currentActions by rememberUpdatedState(KeyGestureActions(
        text = text,
        onTap = onClick,
        onPress = { isPressed = true; onPress?.invoke() },
        onRelease = { isPressed = false; onRelease?.invoke() },
        onPreview = { flickPreview = it.keyFlick; onSwipeStateChange?.invoke(it, buttonBounds) },
        inlineVerticalPreview = true,
        upText = swipeText,
        downText = swipeDownText,
        upPreviewText = swipeUpKeyLabel?.takeIf { it.isNotEmpty() } ?: swipeText,
        downPreviewText = swipeDownKeyLabel?.takeIf { it.isNotEmpty() } ?: swipeDownText,
        onUp = onSwipe,
        onDown = onSwipeDown,
        longPressItems = longPressItems.orEmpty(),
        onLeft = letters?.let { { onLetterSelection?.invoke(it.first); Unit } } ?: onSwipeLeft,
        leftText = letters?.first ?: swipeLeftText,
        onRight = letters?.let { { onLetterSelection?.invoke(it.second); Unit } },
        rightText = letters?.second,
        horizontalThresholdDp = if (letters != null) normalizeLetterSwipeDistance(inputPreferences.fourteenLetterSwipeDp) else 50f,
        reserveHorizontalSwipe = letters != null,
        inlineHorizontalPreview = letters != null,
        longPressDrawableIds = longPressDrawableIds.orEmpty(),
        keyWidth = buttonBounds.width,
        onLongPressSelect = onLongPressSelect,
        onLongPressFeedback = { view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS) },
    ).withSymbolInput(symbolInputMode, symbolInputText).let {
        if (followSymbolSwipeDirection) it.withSymbolSwipeDirection(inputPreferences.reverseSymbolSwipe) else it
    })

    val shiftTargets = LocalShiftSlideTargets.current
    val shiftToken = remember { Any() }
    val maxShiftLetters = if (LocalFourteenKeyLayout.current) 2 else 1
    val shiftLetters = (letterGroup ?: text).takeIf { group ->
        group.length in 1..maxShiftLetters && group.all { it.lowercaseChar() in 'a'..'z' }
    }
    val currentLetterSelection by rememberUpdatedState(onLetterSelection)
    androidx.compose.runtime.DisposableEffect(shiftTargets, shiftToken) {
        onDispose { shiftTargets?.remove(shiftToken) }
    }
    androidx.compose.runtime.SideEffect {
        if (shiftLetters != null) shiftTargets?.put(shiftToken, shiftLetters, buttonBounds) { uppercase ->
            currentActions.onPress()
            try { currentLetterSelection?.invoke(uppercase) ?: currentActions.onTap() }
            finally { currentActions.onRelease() }
        } else shiftTargets?.remove(shiftToken)
    }
    val shiftHovered = shiftLetters != null && shiftTargets?.hovered?.bounds?.center?.let(buttonBounds::contains) == true

    val frostedGlass = LocalKeyboardInputPreferences.current.frostedGlass
    val shadowModifier = remember(shadowEnabled, shadowElevation, shadowShapeRadius, density, resolvedBackground, frostedGlass.enabled) {
        if (shadowEnabled && !frostedGlass.enabled) {
            val offsetPx = with(density) { shadowElevation.toPx() }
            val cornerPx = with(density) { shadowShapeRadius.toPx() }
            val color = crispShadowColor(resolvedBackground)
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
            .padding(edgeContentPadding)
            .onGloballyPositioned { coordinates ->
                buttonBounds = coordinates.boundsInRoot()
                letterGeometry?.place(text, buttonBounds)
            }
            .padding(scaledKeyVisualPadding())
            .keyGlow(Modifier.then(shadowModifier)
            .clip(keyClipShape)
            .background(frostedKeyColor(
                resolvedBackground, resolvedText, frostedGlass,
                legacyStateColor = if (isPressed || shiftHovered) resolvedBackground.copy(alpha = 0.7f)
                else if (isHighlighted) resolvedBackground.copy(alpha = 0.8f)
                else resolvedBackground,
                pressed = isPressed || shiftHovered, highlighted = isHighlighted,
            )).keyHeldHighlight(isPressed || shiftHovered)),
        contentAlignment = if (layoutMode == ButtonLayout.COMPACT) Alignment.TopStart else Alignment.Center
    ) {
        val contentScale = keyContentScale(maxWidth.value, maxHeight.value)
        val defaultFontSize = KeyboardKeyMetrics.LabelSize.value
        val labelSize = keyLabelSizeSp(text,
            if (fontSize != androidx.compose.ui.unit.TextUnit.Unspecified) fontSize.value else defaultFontSize,
            maxWidth.value, maxHeight.value, density.fontScale,
            LocalKeyboardInputPreferences.current.keyTextScale)
        val hintScale = adaptiveHintScale(contentScale)
        val effectiveSwipeFontSize = (swipeFontSize.value * hintScale).sp
        val compactIconSize = keyIconSizeDp(maxWidth.value, maxHeight.value, 16f).dp
        val mainIconSize = keyIconSizeDp(maxWidth.value, maxHeight.value).dp
        val keyHeight = maxHeight
        Box(Modifier.fillMaxSize().graphicsLayer {
            alpha = 1f - (flickPreview?.progress ?: 0f)
        }, contentAlignment = if (layoutMode == ButtonLayout.COMPACT) Alignment.TopStart else Alignment.Center) {
            if (layoutMode == ButtonLayout.COMPACT) {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (icon != null) {
                        Icon(
                            painter = icon,
                            contentDescription = text,
                            tint = resolvedText,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(top = 2.dp, start = 4.dp)
                                .size(compactIconSize)
                        )
                    } else {
                        Text(
                            text = customLayoutLabel(customLayout, punctuationKeyLabel(text), resolvedBackground, customAccent, textColor),
                            color = resolvedText,
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
                            .padding(start = 4.dp, top = 4.dp, end = 4.dp, bottom = 2.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        val swipeUpHint = swipeUpKeyLabel ?: swipeText
                        if (flickPreview?.fromTop != true && !swipeUpHint.isNullOrEmpty()) {
                            val displayText = if (swipeUpHint.length <= 2) swipeUpHint else swipeUpHint.take(2)
                            Text(
                                text = punctuationKeyLabel(displayText),
                                color = resolvedText.copy(alpha = 0.6f),
                                fontSize = effectiveSwipeFontSize,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.End,
                                maxLines = 1,
                                lineHeight = 1.sp
                            )
                        }

                        val swipeDownHint = swipeDownKeyLabel
                        if (flickPreview?.fromTop != false && !swipeDownHint.isNullOrEmpty()) {
                            val hasChinese = swipeDownHint.any { it in '\u4e00'..'\u9fff' || it in '\u3400'..'\u4dbf' || it in '\uf900'..'\ufaff' }
                            val adjustedFontSize = if (hasChinese && effectiveSwipeFontSize > 6.sp) (effectiveSwipeFontSize.value * 0.85f).sp else effectiveSwipeFontSize
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.BottomStart
                            ) {
                                val displayText = if (swipeDownHint.length <= 12) swipeDownHint else swipeDownHint.take(12)
                                Text(
                                    text = punctuationKeyLabel(displayText),
                                    color = resolvedText.copy(alpha = 0.7f),
                                    fontSize = adjustedFontSize,
                                    fontWeight = FontWeight.Medium,
                                    textAlign = TextAlign.Start,
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
                        tint = resolvedText,
                        modifier = Modifier.size(mainIconSize)
                    )
                } else {
                    Text(
                        text = customLayoutLabel(customLayout, punctuationKeyLabel(text), resolvedBackground, customAccent, textColor),
                        modifier = Modifier.fillMaxWidth().offset(y = if (!(swipeUpKeyLabel ?: swipeText).isNullOrEmpty() && keyHeight >= 40.dp) 2.dp else 0.dp),
                        color = resolvedText,
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
                if (flickPreview?.fromTop != true && !(swipeUpKeyLabel ?: swipeText).isNullOrEmpty() && (swipeUpKeyLabel ?: swipeText) != badgeText) {
                    val keyLabel = (swipeUpKeyLabel ?: swipeText)!!
                    val displayText = if (keyLabel.length <= 4) keyLabel else keyLabel.take(4)
                    Text(
                        text = punctuationKeyLabel(displayText),
                        color = resolvedText.copy(alpha = 0.6f),
                        fontSize = effectiveSwipeFontSize,
                        lineHeight = (effectiveSwipeFontSize.value * 1.2f).sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier.align(Alignment.TopEnd).padding(top = 2.dp, end = 5.dp),
                        fontFamily = keyLabelFontFamily
                    )
                }

                if (flickPreview?.fromTop != false && !swipeDownKeyLabel.isNullOrEmpty()) {
                    val displayText = if (swipeDownKeyLabel.length <= 4) swipeDownKeyLabel else swipeDownKeyLabel.take(4)
                    Text(
                        text = punctuationKeyLabel(displayText),
                        color = resolvedText.copy(alpha = 0.5f),
                        fontSize = effectiveSwipeFontSize,
                        lineHeight = (effectiveSwipeFontSize.value * 1.2f).sp,
                        fontWeight = FontWeight.Normal,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier.align(Alignment.BottomStart).padding(start = 5.dp, bottom = 2.dp),
                        fontFamily = keyLabelFontFamily
                    )
                }

                if (badgeText != null && !(flickPreview?.fromTop == true && flickPreview?.text == badgeText)) {
                    Text(
                        text = badgeText,
                        color = resolvedText.copy(alpha = 0.5f),
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
        flickPreview?.let {
            KeyFlickLabel(it, resolvedText, maxWidth.value, maxHeight.value, effectiveSwipeFontSize.value, labelSize)
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

    val frostedGlass = LocalKeyboardInputPreferences.current.frostedGlass
    val shadowModifier = remember(shadowEnabled, shadowElevation, shadowShapeRadius, density, backgroundColor, frostedGlass.enabled) {
        if (shadowEnabled && !frostedGlass.enabled) {
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
            .background(frostedKeyColor(
                backgroundColor, iconColor, frostedGlass,
                legacyStateColor = if (isPressed) darkenColor(backgroundColor, 0.1f)
                else if (isHighlighted) darkenColor(backgroundColor, 0.2f)
                else backgroundColor,
                pressed = isPressed, highlighted = isHighlighted,
            )), materialLevel = com.kingzcheung.xime.ui.theme.MaterialLevel.RAISED),
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
    visualPadding: PaddingValues? = null,
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
    val currentSwipeText by rememberUpdatedState(swipeText)
    val currentSwipeUpLabel by rememberUpdatedState(swipeUpLabel)
    val currentSwipeDownLabel by rememberUpdatedState(swipeDownLabel)
    var isPressed by remember { mutableStateOf(false) }
    var flickPreview by remember { mutableStateOf<KeyFlickPreview?>(null) }
    var buttonBounds by remember { mutableStateOf(Rect(0f, 0f, 0f, 0f)) }
    val keyLabelFontFamily = AppFonts.keyLabelFontFamily
    val density = LocalDensity.current

    val frostedGlass = LocalKeyboardInputPreferences.current.frostedGlass
    val shadowModifier = remember(shadowEnabled, shadowElevation, shadowShapeRadius, density, backgroundColor, frostedGlass.enabled) {
        if (shadowEnabled && !frostedGlass.enabled) {
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
            .semantics { onClick { currentOnClick(); true } }
            .pointerInput(Unit) {
                detectRepeatingKeyGestures {
                    RepeatingKeyActions(
                        onTap = { currentOnClick() },
                        onPress = { isPressed = true; currentOnPress?.invoke() },
                        onRelease = { isPressed = false; currentOnRelease?.invoke() },
                        onRepeat = currentOnLongClick?.let { { currentOnLongClick?.invoke(); Unit } },
                        onUp = (currentOnSwipeUp ?: currentOnSwipe)?.let {
                            { (currentOnSwipeUp ?: currentOnSwipe)?.invoke(); Unit }
                        },
                        onDown = currentOnSwipeDown?.let { { currentOnSwipeDown?.invoke(); Unit } },
                        onLeft = currentOnSwipeLeft?.let { { currentOnSwipeLeft?.invoke(); Unit } },
                        upLabel = currentSwipeUpLabel ?: currentSwipeText,
                        downLabel = currentSwipeDownLabel,
                        onPreview = { state ->
                            flickPreview = state.keyFlick
                            currentOnSwipeStateChange?.invoke(state, buttonBounds)
                        },
                    )
                }
            }
            .onGloballyPositioned { coordinates ->
                buttonBounds = coordinates.boundsInRoot()
            }
            .padding(visualPadding ?: scaledKeyVisualPadding())
            .keyGlow(Modifier.then(shadowModifier)
            .clip(keyClipShape)
            .background(frostedKeyColor(
                backgroundColor, iconColor, frostedGlass,
                legacyStateColor = if (isPressed) darkenColor(backgroundColor, 0.2f)
                else if (isHighlighted) backgroundColor.copy(alpha = 0.8f)
                else backgroundColor,
                pressed = isPressed, highlighted = isHighlighted,
            )).keyHeldHighlight(isPressed), materialLevel = com.kingzcheung.xime.ui.theme.MaterialLevel.RAISED),
        contentAlignment = Alignment.Center
    ) {
        val contentScale = keyContentScale(maxWidth.value, maxHeight.value)
        val hintSize = 10f * adaptiveHintScale(contentScale)
        val renderedIconSize = keyIconSizeDp(maxWidth.value, maxHeight.value, iconSize.value).dp
        val hintOffset = KeyboardKeyMetrics.hintOffsetDp(maxHeight.value, hintSize, density.fontScale, contentScale).dp
        Box(Modifier.fillMaxSize().graphicsLayer {
            alpha = 1f - (flickPreview?.progress ?: 0f)
        }, contentAlignment = Alignment.Center) {
        Icon(
            painter = icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(renderedIconSize)
        )
        
        if (!swipeText.isNullOrEmpty()) {
            Text(
                text = punctuationKeyLabel(swipeText),
                color = iconColor.copy(alpha = 0.5f),
                fontSize = hintSize.sp,
                lineHeight = (hintSize * 1.2f).sp,
                fontWeight = FontWeight.Normal,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.offset(y = -hintOffset),
                fontFamily = keyLabelFontFamily
            )
        }
        }
        flickPreview?.let {
            val labelSize = keyLabelSizeSp(it.text, KeyboardKeyMetrics.LabelSize.value,
                maxWidth.value, maxHeight.value, density.fontScale)
            KeyFlickLabel(it, iconColor, maxWidth.value, maxHeight.value, hintSize, labelSize)
        }
    }
}
