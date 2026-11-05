package com.kingzcheung.xime.ui.keyboard

import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.kingzcheung.xime.service.ToolPanelEditTextHolder

internal val TOOL_PANEL_HEIGHT = 170

/** The same tool panel used as the clipboard search field: one line, no status row. */
internal const val CLIPBOARD_SEARCH_PANEL_HEIGHT = 96

/** Clipboard search on a short screen (landscape): close button and field share one row. */
internal const val CLIPBOARD_SEARCH_INLINE_PANEL_HEIGHT = 48

/** 翻译栏（display: live）：一行，和横屏剪贴板搜索栏一样高。 */
internal const val TOOL_PANEL_BAR_HEIGHT = 48

/** 工具面板在键盘上方撑出的高度；窗口高度和面板绘制用同一个数。PASSIVE 走全屏覆盖，不撑高。 */
internal fun toolPanelExtraHeight(visible: Boolean, display: String?): Int = when {
    !visible || display == "PASSIVE" -> 0
    display == "LIVE" -> TOOL_PANEL_BAR_HEIGHT
    else -> TOOL_PANEL_HEIGHT
}

/**
 * AI 工具插件输入面板（候选栏上方）。
 *
 * 仅承载输入与生成触发：输入框（预填上下文，键盘按键路由可注入）+ 生成状态提示。
 * 生成结果不再显示在此面板，多条结果由 passive 面板（InfoPanel 内 items 点选上屏）承载。
 */
@Composable
fun ToolPanel(
    title: String,
    isFocused: Boolean,
    isLoading: Boolean = false,
    initialText: String = "",
    backgroundColor: Color,
    textColor: Color,
    accentColor: Color,
    cardBgColor: Color,
    onClose: () -> Unit,
    onFocusChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    heightDp: Int = TOOL_PANEL_HEIGHT,
    hint: String = "输入上下文或指令",
    /** Null keeps the generate hint; an empty string hides the status row. */
    statusText: String? = null,
    singleLine: Boolean = false,
    onTextChange: ((String) -> Unit)? = null,
    /** Close button and field on one row, no title (see [CandidateBarOverlayPanel]). */
    inline: Boolean = false,
) {
    val closeButtonBg = androidx.compose.ui.graphics.lerp(
        MaterialTheme.colorScheme.surface,
        MaterialTheme.colorScheme.primary,
        0.35f
    )

    CandidateBarOverlayPanel(
        heightDp = heightDp,
        backgroundColor = backgroundColor,
        cardBgColor = cardBgColor,
        closeButtonBg = closeButtonBg,
        closeButtonColor = accentColor,
        title = title,
        titleColor = textColor,
        modifier = modifier,
        onCloseClick = onClose,
        inline = inline,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                factory = { context ->
                    toolPanelEditText(context, textColor, hint, singleLine, isFocused, onFocusChange, onTextChange)
                },
                update = { editText -> syncToolPanelEditText(editText, isFocused, initialText) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (singleLine) 36.dp else 52.dp),
            )

            if (statusText != "") Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = statusText ?: if (isLoading) "生成中..." else "输入上下文后按回车生成",
                    color = textColor.copy(alpha = 0.5f),
                    fontSize = 11.sp,
                )
            }
        }
    }
}

/** 面板输入框：键盘按键经 [ToolPanelEditTextHolder] 打进来。 */
private fun toolPanelEditText(
    context: android.content.Context,
    textColor: Color,
    hint: String,
    singleLine: Boolean,
    isFocused: Boolean,
    onFocusChange: (Boolean) -> Unit,
    onTextChange: ((String) -> Unit)?,
): android.widget.EditText = android.widget.EditText(context).apply {
    setBackgroundColor(android.graphics.Color.TRANSPARENT)
    setTextColor(textColor.hashCode())
    setHintTextColor((textColor.copy(alpha = 0.4f)).hashCode())
    this.hint = hint
    textSize = 16f
    isSingleLine = singleLine
    gravity = Gravity.TOP or Gravity.START
    setPadding(4, 2, 4, 2)

    setImeActionLabel("生成", EditorInfo.IME_ACTION_DONE)
    imeOptions = EditorInfo.IME_FLAG_NO_ENTER_ACTION or
        EditorInfo.IME_ACTION_DONE

    onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
        onFocusChange(hasFocus)
    }
    setOnClickListener { onFocusChange(true) }
    onTextChange?.let { change ->
        addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) { change(s?.toString().orEmpty()) }
        })
    }
    ToolPanelEditTextHolder.editText = this
    if (isFocused) {
        post { requestFocus() }
    }
}

private fun syncToolPanelEditText(editText: android.widget.EditText, isFocused: Boolean, initialText: String) {
    // Something else took view focus (the toolbar's layout switch rebuilds the keyboard
    // under the search field): give it back so the caret stays where keys go.
    if (isFocused && !editText.hasFocus()) editText.post { editText.requestFocus() }
    if (initialText.isNotEmpty() && !editText.text.toString().equals(initialText)) {
        editText.setText(initialText)
        editText.setSelection(initialText.length)
    }
}

/**
 * 翻译栏（插件 `display: live`，样子照三星键盘的翻译）：左边圆钮（插件给的第一个按钮，
 * 如目标语言「EN」），中间一行输入框，右边关闭。打的字进这一行，结果由宿主写进应用输入框。
 */
@Composable
fun ToolPanelBar(
    chipLabel: String?,
    hint: String,
    isFocused: Boolean,
    isLoading: Boolean,
    textColor: Color,
    accentColor: Color,
    cardBgColor: Color,
    onChipClick: () -> Unit,
    onClose: () -> Unit,
    onFocusChange: (Boolean) -> Unit,
    onTextChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 圆钮用按键底色和按键字色，跟着键盘主题走（原来取应用主题的浅紫，深色下白字看不清）
    val circleBg = cardBgColor
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(TOOL_PANEL_BAR_HEIGHT.dp)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (chipLabel != null) {
            // 触摸 36dp，和关闭按钮一样大
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(circleBg)
                    .clickable(onClick = onChipClick),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = chipLabel,
                    color = textColor,
                    fontSize = if (chipLabel.length > 2) 11.sp else 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.width(8.dp))
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(18.dp))
                .background(cardBgColor.copy(alpha = 0.5f))
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            AndroidView(
                factory = { context ->
                    toolPanelEditText(context, textColor, hint, true, isFocused, onFocusChange, onTextChange)
                        .apply { gravity = Gravity.CENTER_VERTICAL or Gravity.START }
                },
                update = { editText ->
                    syncToolPanelEditText(editText, isFocused, "")
                    if (editText.hint?.toString() != hint) editText.hint = hint
                },
                modifier = Modifier.fillMaxWidth().height(36.dp),
            )
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.CenterEnd).size(14.dp),
                    color = textColor.copy(alpha = 0.5f),
                    strokeWidth = 1.5.dp,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .size(36.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClose,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "关闭",
                tint = textColor.copy(alpha = 0.7f),
                modifier = Modifier.size(22.dp),
            )
        }
    }
}
