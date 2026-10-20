package com.kingzcheung.xime.ui.keyboard

import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.kingzcheung.xime.clipboard.ClipboardItem
import com.kingzcheung.xime.service.ToolPanelEditTextHolder
import kotlinx.coroutines.delay

/**
 * Clipboard search above the keyboard, in the tool panel's style: a search field typed with
 * CyIME's own keys (through the tool-panel input channel) and one row of matching records.
 * Tapping a record pastes it; Enter shows all matches in the clipboard board.
 */
@Composable
fun ClipboardSearchPanel(
    textColor: Color,
    accentColor: Color,
    cardBgColor: Color,
    search: suspend (String) -> List<ClipboardItem>,
    onSelect: (ClipboardItem) -> Unit,
    onClose: () -> Unit,
    onFocusChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    initialResults: List<ClipboardItem> = emptyList(),
    initialQuery: String = "",
) {
    val closeButtonBg = androidx.compose.ui.graphics.lerp(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.primary, .35f)
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf(initialResults) }
    LaunchedEffect(query) {
        if (query.isBlank()) { results = initialResults; return@LaunchedEffect }
        delay(120) // Typing a word does not run a query per key.
        results = search(query)
    }
    CandidateBarOverlayPanel(
        heightDp = TOOL_PANEL_HEIGHT,
        backgroundColor = Color.Transparent,
        cardBgColor = cardBgColor,
        closeButtonBg = closeButtonBg,
        closeButtonColor = accentColor,
        title = "搜索剪贴板",
        titleColor = textColor,
        modifier = modifier.testTag("clipboard-search-panel"),
        onCloseClick = onClose,
    ) {
        Column(Modifier.fillMaxSize()) {
            AndroidView(
                factory = { context ->
                    android.widget.EditText(context).apply {
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                        setTextColor(textColor.hashCode())
                        setHintTextColor(textColor.copy(alpha = .4f).hashCode())
                        hint = "输入文字或拼音首字母"
                        textSize = 16f
                        isSingleLine = true
                        setPadding(4, 2, 4, 2)
                        imeOptions = EditorInfo.IME_ACTION_SEARCH
                        onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus -> onFocusChange(hasFocus) }
                        setOnClickListener { onFocusChange(true) }
                        addTextChangedListener(object : TextWatcher {
                            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                            override fun afterTextChanged(s: Editable?) { query = s?.toString().orEmpty() }
                        })
                        if (initialQuery.isNotEmpty()) { setText(initialQuery); setSelection(initialQuery.length) }
                        ToolPanelEditTextHolder.editText = this
                        post { requestFocus() }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(40.dp).testTag("clipboard-search-field"),
            )
            if (results.isEmpty()) {
                Text(if (query.isBlank()) "固定内容和历史都会搜，回车在剪贴板里看全部结果" else "没有找到",
                    color = textColor.copy(alpha = .5f), fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
            } else {
                LazyRow(Modifier.fillMaxWidth().padding(top = 4.dp).testTag("clipboard-search-results"),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(results, key = { it.id }) { item ->
                        Row(Modifier.width(150.dp).height(48.dp).clip(RoundedCornerShape(10.dp))
                            .background(cardBgColor).clickable { onSelect(item) }
                            .padding(horizontal = 8.dp).testTag("clipboard-search-result:${item.id}"),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text(item.text, Modifier.weight(1f), color = textColor, fontSize = 13.sp, lineHeight = 17.sp,
                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                            if (item.isPinned) Box(Modifier.padding(start = 4.dp)) {
                                Icon(Icons.Default.Lock, "已固定", Modifier.size(14.dp), tint = textColor.copy(alpha = .7f))
                            }
                        }
                    }
                }
            }
        }
    }
}
