package com.kingzcheung.xime.ui.keyboard

import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.*
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.kingzcheung.xime.clipboard.ClipboardDraftSaveResult
import com.kingzcheung.xime.service.QuickSendFormCodeEditTextHolder
import com.kingzcheung.xime.service.QuickSendFormEditTextHolder

/** A separate editor window above the keyboard; existing IME input still targets the draft. */
@Composable
fun QuickSendFormArea(
    backgroundColor: Color,
    textColor: Color,
    accentColor: Color,
    isFocused: Boolean,
    initialText: String = "",
    initialCode: String = "",
    cardBgColor: Color,
    editingItemId: Long? = null,
    onClose: (text: String, code: String) -> Unit,
    onCancel: () -> Unit,
    onFocusChange: (Boolean) -> Unit,
    onCodeFocusChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = if (editingItemId != null) "编辑剪贴板" else "新增固定"
    val currentOnFocusChange by rememberUpdatedState(onFocusChange)
    val currentOnCodeFocusChange by rememberUpdatedState(onCodeFocusChange)
    val gap = with(LocalDensity.current) { 8.dp.roundToPx() }
    val position = remember(gap) { object : PopupPositionProvider {
        override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize,
            layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset = IntOffset(
            (anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2)
                .coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0)),
            (anchorBounds.top - popupContentSize.height - gap)
                .coerceIn(0, (windowSize.height - popupContentSize.height).coerceAtLeast(0)),
        )
    } }
    // Keep enough room for the draft after the title, optional code and footer in landscape.
    val height = (LocalConfiguration.current.screenHeightDp * 0.45f).coerceIn(200f, 240f).dp
    val save: () -> Unit = {
        val editor = QuickSendFormEditTextHolder.editText
        val text = editor?.text?.toString().orEmpty()
        val code = QuickSendFormCodeEditTextHolder.editText?.text?.toString().orEmpty()
        if (text.isBlank()) {
            editor?.error = ClipboardDraftSaveResult.EMPTY_TEXT.errorMessage
            editor?.requestFocus()
        } else onClose(text, code)
    }
    BoxWithConstraints(modifier.fillMaxWidth().height(0.dp).testTag("clipboard-editor-anchor")) {
        val width = (maxWidth - 16.dp).coerceAtLeast(1.dp).coerceAtMost(640.dp)
        Popup(popupPositionProvider = position,
            properties = PopupProperties(focusable = false, dismissOnBackPress = false,
                dismissOnClickOutside = false, clippingEnabled = false)) {
            Column(Modifier.width(width).height(height).clip(RoundedCornerShape(16.dp))
                .keyboardPanelBackground(if (backgroundColor.alpha > 0f) backgroundColor else cardBgColor)
                .padding(12.dp).testTag("clipboard-text-editor")) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, color = textColor, modifier = Modifier.weight(1f))
                    IconButton(onClick = onCancel, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, "关闭", tint = textColor, modifier = Modifier.size(20.dp))
                    }
                }
                // Only a different record starts a new draft. Focus and theme recompositions must
                // not replace text or move the cursor back to the initial saved content.
                key(editingItemId) {
                    Column(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        AndroidView(
                            factory = { context ->
                                android.widget.EditText(context).apply {
                                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                                    showSoftInputOnFocus = false
                                    hint = "输入剪贴板内容"
                                    textSize = 16f
                                    isSingleLine = false
                                    gravity = Gravity.TOP or Gravity.START
                                    setPadding(12, 8, 12, 8)
                                    setText(initialText)
                                    setSelection(initialText.length)

                                    setImeActionLabel("确定", EditorInfo.IME_ACTION_DONE)
                                    imeOptions = EditorInfo.IME_FLAG_NO_ENTER_ACTION or
                                        EditorInfo.IME_ACTION_DONE

                                    onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
                                        currentOnFocusChange(hasFocus)
                                    }
                                    setOnClickListener {
                                        currentOnFocusChange(true)
                                    }
                                    QuickSendFormEditTextHolder.editText = this
                                    if (isFocused) {
                                        post { requestFocus() }
                                    }
                                }
                            },
                            update = { editText ->
                                editText.setTextColor(textColor.toArgb())
                                editText.setHintTextColor(textColor.copy(alpha = 0.4f).toArgb())
                            },
                            onReset = null,
                            onRelease = { editText ->
                                if (QuickSendFormEditTextHolder.editText === editText) {
                                    QuickSendFormEditTextHolder.editText = null
                                }
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .weight(1f),
                        )
                        AndroidView(
                            factory = { context ->
                                android.widget.EditText(context).apply {
                                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                                    showSoftInputOnFocus = false
                                    hint = "编码（选填，如 dh）"
                                    textSize = 14f
                                    isSingleLine = true
                                    gravity = Gravity.START or Gravity.CENTER_VERTICAL
                                    setPadding(12, 4, 12, 4)
                                    setText(initialCode)
                                    setSelection(initialCode.length)

                                    setImeActionLabel("确定", EditorInfo.IME_ACTION_DONE)
                                    imeOptions = EditorInfo.IME_FLAG_NO_ENTER_ACTION or
                                        EditorInfo.IME_ACTION_DONE

                                    // 焦点回调 + 点击抢焦点：宿主按键输入按焦点路由（文本框 vs 编码框）
                                    onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
                                        currentOnCodeFocusChange(hasFocus)
                                    }
                                    setOnClickListener {
                                        requestFocus()
                                        currentOnCodeFocusChange(true)
                                    }
                                    QuickSendFormCodeEditTextHolder.editText = this
                                }
                            },
                            update = { editText ->
                                editText.setTextColor(textColor.toArgb())
                                editText.setHintTextColor(textColor.copy(alpha = 0.4f).toArgb())
                            },
                            onReset = null,
                            onRelease = { editText ->
                                if (QuickSendFormCodeEditTextHolder.editText === editText) {
                                    QuickSendFormCodeEditTextHolder.editText = null
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                Row(Modifier.fillMaxWidth().height(36.dp), horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onCancel) { Text("取消", color = textColor) }
                    TextButton(onClick = save) { Text("保存", color = accentColor) }
                }
            }
        }
    }
}
