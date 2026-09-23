package com.kingzcheung.xime.ui.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 日本手机十二键：点按あ段，向左/上/右/下选择い/う/え/お段，松手提交。 */
@Composable
fun JapaneseKanaKeyboardLayout(
    onKanaAction: (JapaneseKanaAction) -> Unit,
    onKeyPress: (String) -> Unit,
    keyBackgroundColor: Color,
    keyTextColor: Color,
    specialKeyBackgroundColor: Color,
    specialKeyTextColor: Color = keyTextColor,
    keyboardBackgroundColor: Color = Color.Transparent,
    modifier: Modifier = Modifier,
    onKeyPressDown: ((String) -> Unit)? = null,
    shadowEnabled: Boolean = true,
    shadowElevation: Dp = 1.dp,
    shadowShapeRadius: Dp = 8.dp,
    keyCornerRadius: Dp = 8.dp,
    keySpacingX: Dp? = null,
    keySpacingY: Dp? = null,
    bottomPaddingDp: Int = 0,
    hasKanaInput: Boolean = false,
) {
    KeyboardKeySpacingScope(modifier) { bodyModifier ->
    CompositionLocalProvider(
        LocalKeyCornerRadius provides keyCornerRadius,
        LocalKeyVisualPadding provides PaddingValues(horizontal = (keySpacingX ?: 4.dp), vertical = (keySpacingY ?: 4.dp)),
    ) {
        Row(bodyModifier.fillMaxSize().background(keyboardBackgroundColor)
            .padding(start = 4.dp, end = 4.dp, bottom = bottomPaddingDp.dp)) {
            Column(Modifier.weight(1f).fillMaxHeight()) {
                val convert = KanaChoice("変換", "japanese_convert")
                val undo = KanaChoice("↶", "japanese_undo")
                KanaFlickButton(KanaFlickKey(listOf(convert, undo, undo, undo, undo)),
                    { if (it is JapaneseKanaAction.Input) onKeyPress(it.romaji) }, specialKeyBackgroundColor, specialKeyTextColor,
                    Modifier.weight(1f).testTag("kana-convert"), onPress = { onKeyPressDown?.invoke("japanese_convert") },
                    shadowEnabled = shadowEnabled, shadowElevation = shadowElevation, shadowShapeRadius = shadowShapeRadius)
                SwipeableIconKeyButton(icon = rememberVectorPainter(Icons.AutoMirrored.Filled.KeyboardArrowLeft),
                    onClick = { onKeyPress("japanese_left") }, onLongClick = { onKeyPressDown?.invoke("japanese_left"); onKeyPress("japanese_left") },
                    backgroundColor = specialKeyBackgroundColor, iconColor = specialKeyTextColor, modifier = Modifier.weight(1f).testTag("kana-left"),
                    onPress = { onKeyPressDown?.invoke("japanese_left") }, shadowEnabled = shadowEnabled)
                KeyButton("123", { onKeyPress("mode_change_number") }, specialKeyBackgroundColor, specialKeyTextColor,
                    Modifier.weight(1f).testTag("kana-number"), onPress = { onKeyPressDown?.invoke("number") }, fontSize = KeyboardKeyMetrics.LabelSize, shadowEnabled = shadowEnabled)
                KeyButton("記号", { onKeyPress("symbol") }, specialKeyBackgroundColor, specialKeyTextColor,
                    Modifier.weight(1f).testTag("kana-symbol"), fontSize = KeyboardKeyMetrics.LabelSize, onPress = { onKeyPressDown?.invoke("symbol") }, shadowEnabled = shadowEnabled)
            }
            Column(Modifier.weight(3f).fillMaxHeight()) {
                for (row in 0..2) Row(Modifier.weight(1f).fillMaxWidth()) {
                    for (column in 0..2) {
                        val key = japaneseKanaKeys[row * 3 + column]
                        KanaFlickButton(key, onKanaAction, keyBackgroundColor, keyTextColor,
                            Modifier.weight(1f).testTag("kana-key:${key.center.romaji}"),
                            onPress = { onKeyPressDown?.invoke(key.center.label) },
                            shadowEnabled = shadowEnabled, shadowElevation = shadowElevation, shadowShapeRadius = shadowShapeRadius)
                    }
                }
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    if (hasKanaInput) KeyButton("小゛゜", { onKanaAction(JapaneseKanaAction.Modify) }, specialKeyBackgroundColor, specialKeyTextColor,
                        Modifier.weight(1f).testTag("kana-modifier"), onPress = { onKeyPressDown?.invoke("japanese_modify") }, fontSize = KeyboardKeyMetrics.LabelSize,
                        shadowEnabled = shadowEnabled, shadowElevation = shadowElevation, shadowShapeRadius = shadowShapeRadius)
                    else KanaFlickButton(japaneseKanaKeys.last(), onKanaAction, keyBackgroundColor, keyTextColor,
                        Modifier.weight(1f).testTag("kana-punctuation"), onPress = { onKeyPressDown?.invoke("symbol") },
                        shadowEnabled = shadowEnabled, shadowElevation = shadowElevation, shadowShapeRadius = shadowShapeRadius)
                    KanaFlickButton(japaneseKanaKeys[9], onKanaAction, keyBackgroundColor, keyTextColor,
                        Modifier.weight(1f).testTag("kana-key:wa"), onPress = { onKeyPressDown?.invoke("wa") },
                        shadowEnabled = shadowEnabled, shadowElevation = shadowElevation, shadowShapeRadius = shadowShapeRadius)
                    LanguageKeyButton(icon = rememberVectorPainter(Icons.Default.Language), onClick = { onKeyPress("ime_switch") },
                        backgroundColor = specialKeyBackgroundColor, iconColor = specialKeyTextColor,
                        modifier = Modifier.weight(1f),
                        onPress = { onKeyPressDown?.invoke("ime_switch") }, shadowEnabled = shadowEnabled)
                }
            }
            Column(Modifier.weight(1f).fillMaxHeight()) {
                SwipeableIconKeyButton(icon = rememberVectorPainter(Icons.AutoMirrored.Filled.Backspace),
                    onClick = { onKeyPress("delete") }, onLongClick = { onKeyPress("delete") },
                    backgroundColor = specialKeyBackgroundColor, iconColor = specialKeyTextColor,
                    modifier = Modifier.weight(1f).testTag("kana-delete"), onPress = { onKeyPressDown?.invoke("delete") },
                    shadowEnabled = shadowEnabled, shadowElevation = shadowElevation, shadowShapeRadius = shadowShapeRadius)
                SwipeableIconKeyButton(icon = rememberVectorPainter(Icons.AutoMirrored.Filled.KeyboardArrowRight),
                    onClick = { onKeyPress("japanese_right") }, onLongClick = { onKeyPressDown?.invoke("japanese_right"); onKeyPress("japanese_right") },
                    backgroundColor = specialKeyBackgroundColor, iconColor = specialKeyTextColor, modifier = Modifier.weight(1f).testTag("kana-right"),
                    onPress = { onKeyPressDown?.invoke("japanese_right") }, shadowEnabled = shadowEnabled)
                SpaceKeyButton(onClick = { onKeyPress("space") }, backgroundColor = keyBackgroundColor,
                    textColor = keyTextColor, schemaName = "日本語", modifier = Modifier.weight(1f).testTag("kana-space"), onPress = { onKeyPressDown?.invoke("space") },
                    shadowEnabled = shadowEnabled, shadowElevation = shadowElevation, shadowShapeRadius = shadowShapeRadius)
                ActionKeyButton(text = "回车", onClick = { onKeyPress("enter") }, backgroundColor = specialKeyBackgroundColor,
                    textColor = specialKeyTextColor, modifier = Modifier.weight(1f).testTag("kana-enter"), onPress = { onKeyPressDown?.invoke("enter") },
                    shadowEnabled = shadowEnabled, shadowElevation = shadowElevation, shadowShapeRadius = shadowShapeRadius)
            }
        }
    }
    }
}

@Composable
internal fun KanaFlickButton(
    key: KanaFlickKey,
    onAction: (JapaneseKanaAction) -> Unit,
    background: Color,
    foreground: Color,
    modifier: Modifier = Modifier,
    onPress: (() -> Unit)? = null,
    shadowEnabled: Boolean = true,
    shadowElevation: Dp = 1.dp,
    shadowShapeRadius: Dp = 8.dp,
) {
    val currentAction by rememberUpdatedState(onAction)
    val currentPress by rememberUpdatedState(onPress)
    var pressed by remember { mutableStateOf(false) }
    var direction by remember { mutableStateOf(KanaFlickDirection.TAP) }
    val density = LocalDensity.current
    val suppressCursorMove = LocalSuppressCursorMove.current
    val shadow = remember(shadowEnabled, shadowElevation, shadowShapeRadius, density, background) {
        if (shadowEnabled) Modifier.drawBehind {
            drawRoundRect(crispShadowColor(background), topLeft = Offset(0f, shadowElevation.toPx()),
                size = size, cornerRadius = CornerRadius(shadowShapeRadius.toPx()))
        } else Modifier
    }
    val textScale = LocalKeyboardInputPreferences.current.keyTextScale
    BoxWithConstraints(
        modifier.fillMaxSize()
            .semantics {
                contentDescription = key.center.label
                role = Role.Button
                onClick { currentAction(JapaneseKanaAction.Input(key.center.romaji)); true }
                customActions = key.choices.drop(1).filterNotNull().map { choice ->
                    CustomAccessibilityAction(choice.label) { currentAction(JapaneseKanaAction.Input(choice.romaji)); true }
                }
            }
            .pointerInput(key) {
                val threshold = 18.dp.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown(pass = PointerEventPass.Initial)
                    down.consume()
                    val origin = down.position
                    suppressCursorMove.value = true
                    pressed = true
                    direction = KanaFlickDirection.TAP
                    currentPress?.invoke()
                    var choice: KanaChoice? = null
                    try {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (change.isConsumed) break
                            val delta = change.position - origin
                            val returnedToCenter = change.position.x in size.width * 0.3f..size.width * 0.7f &&
                                change.position.y in size.height * 0.3f..size.height * 0.7f
                            direction = if (returnedToCenter) KanaFlickDirection.TAP
                                else kanaFlickDirection(delta.x, delta.y, threshold)
                            change.consume()
                            if (!change.pressed) { choice = key.choice(direction); break }
                        }
                    } finally {
                        pressed = false
                        direction = KanaFlickDirection.TAP
                        suppressCursorMove.value = false
                    }
                    choice?.let { currentAction(JapaneseKanaAction.Input(it.romaji)) }
                }
            }
            .padding(scaledKeyVisualPadding())
            .then(shadow)
            .clip(RoundedCornerShape(LocalKeyCornerRadius.current))
            .background(if (pressed) foreground.copy(alpha = 0.18f).compositeOver(background) else background).keyGlow(),
        contentAlignment = Alignment.Center,
    ) {
        val contentScale = KeyboardKeyMetrics.contentScale(maxWidth.value, maxHeight.value)
        val fontSp = KeyboardKeyMetrics.labelSizeSp(key.center.label, KeyboardKeyMetrics.LabelSize.value,
            maxWidth.value, maxHeight.value, density.fontScale, textScale)
        if (pressed) KanaDirectionIndicator(direction, MaterialTheme.colorScheme.primary)
        Text(key.center.label, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
            color = foreground, fontSize = fontSp.sp, lineHeight = (fontSp * 1.2f).sp,
            maxLines = 1, softWrap = false, fontFamily = AppFonts.keyFontFamily)
        if (pressed) KanaFlickPreview(key, direction, background, foreground, contentScale)
    }
}
