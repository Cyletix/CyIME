package com.kingzcheung.xime.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
 * 固定栏的编辑：只画一份和键盘上一样的栏，点哪格弹小框改哪格（改字、前后移、删除），末尾「+」加一格。
 * 改动立即保存。恢复默认由页面右上角菜单调用 [onResetReady] 登记的函数。
 */
@Composable
internal fun SideSymbolEditor(target: SideSymbolTarget, onResetReady: ((() -> Unit)?) -> Unit = {}) {
    val context = LocalContext.current
    val prefs = rememberKeyboardInputPreferences()
    var items by remember(target) { mutableStateOf(target.resolve(prefs.symbolsFor(target))) }
    // 正在弹框编辑的格；等于 items.size 表示正在新加一格。
    var editing by rememberSaveable(target) { mutableStateOf<Int?>(null) }
    // 小框里还没点「完成」的字；前后移动这一格时不丢。
    var draft by rememberSaveable(target) { mutableStateOf("") }

    fun save(next: List<String>) {
        items = next
        SettingsPreferences.getPrefsPublic(context).edit()
            .putString(target.prefKey, next.joinToString("\n") { it.trim() }).apply()
    }

    fun reset() {
        // 旧的共用列表会让“恢复默认”退回旧内容：先把它原样分给其他还没单独设置的栏，再删掉，
        // 这样其他栏保持不变，这一栏回到真正的默认。
        val edit = SettingsPreferences.getPrefsPublic(context).edit()
        val legacy = prefs.symbols()
        if (legacy != null) {
            SideSymbolTarget.entries.filter { it != target && it.usesLegacyList &&
                SideSymbolTarget.parse(prefs.sideSymbols[it.key]) == null }
                .forEach { edit.putString(it.prefKey, legacy.joinToString("\n")) }
            edit.putString("fixed_symbols", "")
        }
        edit.remove(target.prefKey).apply()
        items = target.resolve(null)
        editing = null
    }
    DisposableEffect(target, prefs) {
        onResetReady(::reset)
        onDispose { onResetReady(null) }
    }

    val canGrow = target.slots == null
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("${target.where}，点格子修改", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        val cell: @Composable (Modifier, Int, String) -> Unit = { modifier, index, text ->
            val active = index == editing
            Box(modifier.height(40.dp).clip(RoundedCornerShape(7.dp))
                .background(if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                    else MaterialTheme.colorScheme.surfaceVariant)
                .border(if (active) 2.dp else 0.dp,
                    if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(7.dp))
                .clickable { draft = items.getOrNull(index).orEmpty(); editing = index }.padding(horizontal = 8.dp)
                .testTag(if (index < items.size) "side-symbol-cell:$index" else "side-symbol-add"),
                contentAlignment = Alignment.Center) {
                if (index >= items.size) Icon(Icons.Default.Add, "加一格")
                else Text(text.ifBlank { "空" }, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = if (text.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
            }
        }
        // 和键盘上一样：九键、笔画、手写侧栏竖排；数字行所有格平分一排；其余横排，放不下时左右滑。
        when {
            target.vertical -> Column(Modifier.width(96.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items.forEachIndexed { index, text -> cell(Modifier.fillMaxWidth(), index, text) }
                if (canGrow) cell(Modifier.fillMaxWidth(), items.size, "")
            }
            target == SideSymbolTarget.NUMBER_ROW -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                items.forEachIndexed { index, text -> cell(Modifier.weight(1f), index, text) }
                cell(Modifier.width(44.dp), items.size, "")
            }
            else -> Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                items.forEachIndexed { index, text -> cell(Modifier.widthIn(min = 44.dp), index, text) }
                if (canGrow) cell(Modifier.widthIn(min = 44.dp), items.size, "")
            }
        }
    }

    editing?.let { index ->
        val adding = index >= items.size
        AlertDialog(onDismissRequest = { editing = null }, modifier = Modifier.testTag("side-symbol-dialog"),
            title = { Text(if (adding) "加一格" else "第 ${index + 1} 格") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = draft, onValueChange = { draft = it }, singleLine = true,
                        placeholder = { Text("符号或短语") },
                        modifier = Modifier.fillMaxWidth().testTag("side-symbol-field"))
                    // 前后移动、删除马上生效，背后的栏跟着变；框里的字点「完成」才写入。
                    if (!adding) Row(verticalAlignment = Alignment.CenterVertically) {
                        val vertical = target.vertical
                        MoveButton(enabled = index > 0, vertical = vertical, earlier = true) {
                            save(items.toMutableList().also { it.add(index - 1, it.removeAt(index)) }); editing = index - 1
                        }
                        MoveButton(enabled = index < items.lastIndex, vertical = vertical, earlier = false) {
                            save(items.toMutableList().also { it.add(index + 1, it.removeAt(index)) }); editing = index + 1
                        }
                        // 常用符号栏可以删空（只剩默认符号）；其他栏删空会退回默认内容，至少留一格。
                        if (canGrow) IconButton(enabled = items.size > 1 || target == SideSymbolTarget.COMMON, onClick = {
                            save(items.toMutableList().also { it.removeAt(index) }); editing = null
                        }, modifier = Modifier.testTag("side-symbol-delete")) { Icon(Icons.Default.Delete, "删除这一格") }
                    }
                }
            },
            confirmButton = { TextButton(onClick = {
                editing = null
                val value = draft.trim()
                if (adding) { if (value.isNotEmpty()) save(items + value) }
                else if (value != items[index]) save(items.toMutableList().also { it[index] = value })
            }) { Text("完成") } },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("取消") } })
    }
}

@Composable
private fun RowScope.MoveButton(enabled: Boolean, vertical: Boolean, earlier: Boolean, onClick: () -> Unit) {
    IconButton(enabled = enabled, onClick = onClick) {
        Icon(when {
            vertical && earlier -> Icons.Default.KeyboardArrowUp
            vertical -> Icons.Default.KeyboardArrowDown
            earlier -> Icons.AutoMirrored.Filled.KeyboardArrowLeft
            else -> Icons.AutoMirrored.Filled.KeyboardArrowRight
        }, if (vertical) (if (earlier) "上移" else "下移") else (if (earlier) "左移" else "右移"))
    }
}
