package com.kingzcheung.xime.ui.keyboard

import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.kingzcheung.xime.keyboard.ToolPanelItem
import com.kingzcheung.xime.service.ToolPanelEditTextHolder

private val TOOL_PANEL_HEIGHT = 200

@Composable
fun ToolPanel(
    title: String,
    items: List<ToolPanelItem>,
    isFocused: Boolean,
    isLoading: Boolean = false,
    initialText: String = "",
    backgroundColor: Color,
    textColor: Color,
    accentColor: Color,
    cardBgColor: Color,
    onClose: () -> Unit,
    onFocusChange: (Boolean) -> Unit,
    onItemClick: (ToolPanelItem) -> Unit,
    onRegenerate: () -> Unit,
) {
    val closeButtonBg = androidx.compose.ui.graphics.lerp(
        MaterialTheme.colorScheme.surface,
        MaterialTheme.colorScheme.primary,
        0.35f
    )

    CandidateBarOverlayPanel(
        heightDp = TOOL_PANEL_HEIGHT,
        backgroundColor = backgroundColor,
        cardBgColor = cardBgColor,
        closeButtonBg = closeButtonBg,
        closeButtonColor = accentColor,
        title = title,
        titleColor = textColor,
        onCloseClick = onClose,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                factory = { context ->
                    android.widget.EditText(context).apply {
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                        setTextColor(textColor.hashCode())
                        setHintTextColor((textColor.copy(alpha = 0.4f)).hashCode())
                        hint = "输入上下文或指令"
                        textSize = 16f
                        isSingleLine = false
                        gravity = Gravity.TOP or Gravity.START
                        setPadding(4, 2, 4, 2)

                        setImeActionLabel("生成", EditorInfo.IME_ACTION_DONE)
                        imeOptions = EditorInfo.IME_FLAG_NO_ENTER_ACTION or
                            EditorInfo.IME_ACTION_DONE

                        onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
                            onFocusChange(hasFocus)
                        }
                        setOnClickListener { onFocusChange(true) }
                        ToolPanelEditTextHolder.editText = this
                        if (isFocused) {
                            post { requestFocus() }
                        }
                    }
                },
                update = { editText ->
                    if (initialText.isNotEmpty() && !editText.text.toString().equals(initialText)) {
                        editText.setText(initialText)
                        editText.setSelection(initialText.length)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = when {
                        isLoading -> "生成中..."
                        items.isEmpty() -> "点击生成或输入后按回车"
                        else -> "${items.size} 条候选"
                    },
                    color = textColor.copy(alpha = 0.5f),
                    fontSize = 11.sp,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (items.isNotEmpty() && !isLoading) {
                        androidx.compose.material3.TextButton(onClick = onRegenerate) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "重新生成",
                                tint = accentColor,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("重新生成", color = accentColor, fontSize = 12.sp)
                        }
                    }
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(items) { item ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onItemClick(item) }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                    ) {
                        Text(
                            text = item.text,
                            color = textColor,
                            fontSize = 14.sp,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}