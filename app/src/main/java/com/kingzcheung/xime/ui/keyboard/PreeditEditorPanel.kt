package com.kingzcheung.xime.ui.keyboard

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kingzcheung.xime.rime.PinyinEditBuffer
import com.kingzcheung.xime.rime.PinyinEditSession

/** No focusable TextField or second IME connection: these keys edit a local code draft. */
@Composable
internal fun PreeditEditorPanel(
    session: PinyinEditSession,
    backgroundColor: Color,
    keyColor: Color,
    textColor: Color,
    accentColor: Color,
    onClose: () -> Unit,
    onApply: (String, Int, (Boolean) -> Unit) -> Unit,
    onFeedback: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (LocalOnBackPressedDispatcherOwner.current != null) BackHandler(onBack = onClose)
    var draft by remember(session) { mutableStateOf(session.text) }
    var caret by remember(session) { mutableIntStateOf(session.caret) }
    var pending by remember(session) { mutableStateOf(false) }
    var stale by remember(session) { mutableStateOf(false) }
    var textLayout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val scroll = rememberScrollState()
    fun insert(value: String) {
        if (pending) return
        draft = draft.take(caret) + value + draft.drop(caret)
        caret += value.length
    }
    LaunchedEffect(caret, draft, textLayout) {
        val layout = textLayout?.takeIf { it.layoutInput.text.text == draft.ifEmpty { " " } }
        layout?.getCursorRect(caret.coerceIn(0, layout.layoutInput.text.length))?.let { rect ->
            if (rect.left < scroll.value) scroll.scrollTo(rect.left.toInt().coerceAtLeast(0))
            else if (rect.right > scroll.value + scroll.viewportSize)
                scroll.scrollTo((rect.right - scroll.viewportSize + 12).toInt().coerceAtLeast(0))
        }
    }
    // A small floating keyboard keeps its outer height. The editor scrolls instead of
    // compressing four keyboard rows below usable letter/target heights.
    BoxWithConstraints(modifier.fillMaxWidth().background(backgroundColor).testTag("preedit-editor")) {
    val contentHeight = maxOf(maxHeight, 280.dp * LocalDensity.current.fontScale.coerceAtLeast(1f))
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
    Column(Modifier.fillMaxWidth().height(contentHeight).padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth().height(32.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (session.schemaId.startsWith("double_pinyin") || session.schemaId == "pinyin_14jian") "编辑编码" else "编辑拼音",
                color = textColor, fontSize = 16.sp, modifier = Modifier.weight(1f))
            if (session.protectedText.isNotEmpty()) Text("已选：${session.protectedText}", color = textColor.copy(alpha = .7f),
                fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Box(Modifier.size(32.dp).clip(RoundedCornerShape(50)).background(keyColor)
                .clickable(enabled = !pending, onClick = onClose).testTag("preedit-editor-close"), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Close, "取消编辑", tint = textColor, modifier = Modifier.size(21.dp))
            }
        }
        Box(Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(24.dp))
            .background(keyColor).border(1.dp, accentColor.copy(alpha = .65f), RoundedCornerShape(24.dp))
            .padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
            Text(draft.ifEmpty { " " }, color = textColor, fontSize = 22.sp, maxLines = 1, softWrap = false,
                onTextLayout = { textLayout = it },
                modifier = Modifier.fillMaxWidth().horizontalScroll(scroll).padding(vertical = 6.dp)
                    .testTag("preedit-editor-code")
                    .semantics {
                        editableText = AnnotatedString(draft)
                        textSelectionRange = TextRange(caret)
                        setSelection { start, end, _ ->
                            if (pending || start != end) false else { caret = start.coerceIn(0, draft.length); true }
                        }
                        setText { value ->
                            if (pending) false else {
                                draft = PinyinEditBuffer.normalized(value.text); caret = draft.length; true
                            }
                        }
                    }
                    .pointerInput(draft, pending) {
                        detectTapGestures { position ->
                            if (!pending) caret = textLayout?.getOffsetForPosition(position)?.coerceIn(0, draft.length) ?: caret
                        }
                    }
                    .drawBehind {
                        val layout = textLayout?.takeIf { it.layoutInput.text.text == draft.ifEmpty { " " } }
                        layout?.getCursorRect(caret.coerceIn(0, layout.layoutInput.text.length))?.let { rect ->
                            drawLine(accentColor, Offset(rect.left, rect.top), Offset(rect.left, rect.bottom), 2.dp.toPx())
                        }
                    })
        }
        if (stale) Text("无法应用，请检查编码或重新打开", color = textColor, fontSize = 13.sp)
        listOf("qwertyuiop", "asdfghjkl", "zxcvbnm").forEach { row ->
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (row.length < 10) Spacer(Modifier.weight((10 - row.length) / 2f))
                row.forEach { ch ->
                    Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(8.dp)).background(keyColor)
                        .clickable(enabled = !pending) { onFeedback(); insert(ch.toString()) }.testTag("preedit-letter:$ch"), contentAlignment = Alignment.Center) {
                        Text(ch.toString(), color = textColor, fontSize = 20.sp)
                    }
                }
                if (row.length < 10) Spacer(Modifier.weight((10 - row.length) / 2f))
            }
        }
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            @Composable fun action(tag: String, label: String, onClick: () -> Unit, icon: @Composable () -> Unit) {
                Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(10.dp)).background(keyColor)
                    .border(1.dp, accentColor.copy(alpha = .4f), RoundedCornerShape(10.dp))
                    .clickable(enabled = !pending) { onFeedback(); onClick() }.testTag(tag)
                    .semantics { contentDescription = label }, contentAlignment = Alignment.Center) { icon() }
            }
            action("preedit-caret-left", "拼音光标左移", { caret = (caret - 1).coerceAtLeast(0) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null, tint = textColor)
            }
            action("preedit-caret-right", "拼音光标右移", { caret = (caret + 1).coerceAtMost(draft.length) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = textColor)
            }
            action("preedit-separator", "拼音分隔", { insert("'") }) { Text("'", color = textColor, fontSize = 24.sp) }
            action("preedit-delete", "删除编码", {
                if (caret > 0) { draft = draft.removeRange(caret - 1, caret); caret-- }
            }) { Icon(Icons.AutoMirrored.Filled.Backspace, null, tint = textColor) }
            action("preedit-apply", "完成编辑", {
                pending = true
                onApply(draft, caret) { success -> pending = false; stale = !success; if (success) onClose() }
            }) { Icon(Icons.Default.Check, null, tint = accentColor) }
        }
    }
    }
    }
}
