package com.kingzcheung.xime.ui.keyboard

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.kingzcheung.xime.rime.PinyinEditSession
import com.kingzcheung.xime.rime.PinyinEditDisplay

/** A non-focusable editing strip above the IME: no replacement keyboard or extra row of keys. */
@Composable
internal fun PreeditEditorBar(
    session: PinyinEditSession,
    keyColor: Color,
    textColor: Color,
    accentColor: Color,
    onClose: () -> Unit,
    onCaret: (Int) -> Unit,
    onReplace: (String) -> Unit,
    preedit: String = session.text,
    backgroundColor: Color = keyColor,
) {
    if (LocalOnBackPressedDispatcherOwner.current != null) BackHandler(onBack = onClose)
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val scroll = rememberScrollState()
    val currentCaret by rememberUpdatedState(onCaret)
    val display = remember(session.text, preedit) { PinyinEditDisplay(session.text, preedit.removePrefix(session.protectedText).trim()) }
    val code = display.text.ifEmpty { " " }
    val displayCaret = display.displayOffset(session.caret)
    LaunchedEffect(session.caret, code, layout) {
        layout?.takeIf { it.layoutInput.text.text == code }?.getCursorRect(displayCaret.coerceIn(0, code.length))?.let {
            if (it.left < scroll.value) scroll.scrollTo(it.left.toInt().coerceAtLeast(0))
            else if (it.right > scroll.value + scroll.viewportSize)
                scroll.scrollTo((it.right - scroll.viewportSize + 16).toInt().coerceAtLeast(0))
        }
    }
    val gap = 0
    val position = remember(gap) { object : PopupPositionProvider {
        override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize,
            layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset = IntOffset(
            anchorBounds.left.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0)),
            (anchorBounds.top - popupContentSize.height - gap).coerceIn(0, (windowSize.height - popupContentSize.height).coerceAtLeast(0)))
    } }
    BoxWithConstraints(Modifier.fillMaxWidth().height(0.dp)) {
        val width = maxWidth
        Popup(popupPositionProvider = position,
            properties = PopupProperties(focusable = false, dismissOnBackPress = false, dismissOnClickOutside = false)) {
            Row(Modifier.width(width).height(68.dp).background(backgroundColor.copy(alpha = 1f))
                .padding(start = 8.dp, end = 4.dp, top = 6.dp, bottom = 6.dp).testTag("preedit-editor"),
                verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(28.dp))
                    .background(keyColor.copy(alpha = 1f)).padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterStart) {
                    Text(code, color = textColor, fontSize = 24.sp, maxLines = 1, softWrap = false,
                        onTextLayout = { layout = it }, modifier = Modifier.fillMaxWidth().horizontalScroll(scroll)
                            .padding(vertical = 4.dp).testTag("preedit-editor-code")
                            .semantics {
                                editableText = AnnotatedString(session.text)
                                textSelectionRange = TextRange(session.caret)
                                setSelection { start, end, _ ->
                                    if (start != end) false else { currentCaret(start.coerceIn(0, session.text.length)); true }
                                }
                                setText { onReplace(it.text); true }
                            }
                            .pointerInput(code) {
                                detectTapGestures { point -> layout?.let { currentCaret(display.rawOffset(it.getOffsetForPosition(point))) } }
                            }
                            .pointerInput(code) {
                                detectDragGestures { change, _ ->
                                    change.consume()
                                    layout?.let { currentCaret(display.rawOffset(it.getOffsetForPosition(change.position))) }
                                }
                            }
                            .drawBehind {
                                layout?.takeIf { it.layoutInput.text.text == code }?.getCursorRect(displayCaret.coerceIn(0, code.length))?.let {
                                    drawLine(accentColor, Offset(it.left, it.top), Offset(it.left, it.bottom), 2.dp.toPx())
                                }
                            })
                }
                Box(Modifier.size(48.dp).clickable(onClick = onClose).testTag("preedit-editor-close"), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Close, "关闭拼音编辑", tint = textColor, modifier = Modifier.size(28.dp))
                }
            }
        }
    }
}
