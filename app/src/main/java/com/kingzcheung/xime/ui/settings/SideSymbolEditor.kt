package com.kingzcheung.xime.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kingzcheung.xime.settings.SettingsPreferences
import com.kingzcheung.xime.ui.keyboard.SideSymbolTarget
import com.kingzcheung.xime.ui.keyboard.rememberKeyboardInputPreferences

/**
 * 固定栏的按位置编辑：上方是这条栏在键盘上的样子，点哪格就编辑哪格；
 * 下方逐格编辑，可增删和上下移动，每格一个符号或短语。改动立即保存。
 */
@Composable
internal fun SideSymbolEditor(target: SideSymbolTarget) {
    val context = LocalContext.current
    val prefs = rememberKeyboardInputPreferences()
    val saved = remember(target, prefs) { prefs.symbolsFor(target) }
    var items by remember(target) { mutableStateOf(target.resolve(saved).ifEmpty { listOf("") }) }
    var selected by remember(target) { mutableIntStateOf(0) }
    var custom by remember(target) { mutableStateOf(SideSymbolTarget.parse(prefs.sideSymbols[target.key]) != null) }

    fun save(next: List<String>) {
        items = next
        custom = true
        SettingsPreferences.getPrefsPublic(context).edit()
            .putString(target.prefKey, next.joinToString("\n") { it.trim() }).apply()
    }

    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(target.where + if (target.slots != null) "，固定 ${target.slots} 格。" else "；格数不限，超出时可在键盘上滚动。",
            style = MaterialTheme.typography.bodySmall)
        Text("每格填一个符号或一个短语，点按即输入。" + if (!custom && saved != null) "当前沿用旧的共用快捷符号列表。" else "",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        // 键盘上的样子：竖栏或横排，点一格即选中下方对应的编辑行。
        val cell: @Composable (Int, String) -> Unit = { index, text ->
            Box(Modifier.widthIn(min = 44.dp).height(36.dp).clip(RoundedCornerShape(7.dp))
                .background(if (index == selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                    else MaterialTheme.colorScheme.surfaceVariant)
                .border(if (index == selected) 2.dp else 0.dp,
                    if (index == selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(7.dp))
                .clickable { selected = index }.padding(horizontal = 8.dp)
                .testTag("side-symbol-cell:$index"), contentAlignment = Alignment.Center) {
                Text(text.ifBlank { "空" }, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = if (text.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
            }
        }
        if (target.vertical) Column(Modifier.width(96.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items.forEachIndexed { index, text -> cell(index, text) }
        } else Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            items.forEachIndexed { index, text -> cell(index, text) }
        }

        items.forEachIndexed { index, text ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${index + 1}", Modifier.width(24.dp), style = MaterialTheme.typography.labelMedium,
                    color = if (index == selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(value = text, singleLine = true,
                    onValueChange = { value -> save(items.toMutableList().also { it[index] = value }); selected = index },
                    modifier = Modifier.weight(1f).testTag("side-symbol-field:$index"))
                IconButton(enabled = index > 0, onClick = {
                    save(items.toMutableList().also { it.add(index - 1, it.removeAt(index)) }); selected = index - 1
                }) { Icon(Icons.Default.KeyboardArrowUp, "上移") }
                IconButton(enabled = index < items.lastIndex, onClick = {
                    save(items.toMutableList().also { it.add(index + 1, it.removeAt(index)) }); selected = index + 1
                }) { Icon(Icons.Default.KeyboardArrowDown, "下移") }
                if (target.slots == null) IconButton(enabled = items.size > 1, onClick = {
                    save(items.toMutableList().also { it.removeAt(index) }); selected = selected.coerceAtMost(items.lastIndex)
                }) { Icon(Icons.Default.Delete, "删除这一格") }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (target.slots == null) OutlinedButton(onClick = { save(items + ""); selected = items.lastIndex }) {
                Icon(Icons.Default.Add, null, Modifier.size(18.dp)); Text(" 添加一格")
            }
            TextButton(onClick = {
                // 旧的共用列表会让“恢复默认”退回旧内容：先把它原样分给其他还没单独设置的栏，再删掉，
                // 这样其他栏保持不变，这一栏回到真正的默认。
                val edit = SettingsPreferences.getPrefsPublic(context).edit()
                val legacy = prefs.symbols()
                if (legacy != null) {
                    SideSymbolTarget.entries.filter { it != target && SideSymbolTarget.parse(prefs.sideSymbols[it.key]) == null }
                        .forEach { edit.putString(it.prefKey, legacy.joinToString("\n")) }
                    edit.putString("fixed_symbols", "")
                }
                edit.remove(target.prefKey).apply()
                custom = false
                items = target.resolve(null).ifEmpty { listOf("") }
                selected = 0
            }) { Text("恢复默认") }
        }
    }
}
