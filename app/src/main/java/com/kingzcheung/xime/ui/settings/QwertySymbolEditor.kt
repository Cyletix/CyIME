package com.kingzcheung.xime.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kingzcheung.xime.settings.*
import com.kingzcheung.xime.ui.keyboard.rememberKeyboardInputPreferences
import com.kingzcheung.xime.ui.keyboard.SymbolInputMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun QwertySymbolEditor() {
    val context = LocalContext.current
    val inputSettings = rememberKeyboardInputPreferences()
    val reverseSwipe = inputSettings.reverseSymbolSwipe
    val topGesture = if (inputSettings.symbolInputMode == SymbolInputMode.LONG_PRESS) "长按"
        else if (reverseSwipe) "上拉" else "下拉"
    val bottomGesture = if (reverseSwipe) "下拉" else "上拉"
    val scope = rememberCoroutineScope()
    var english by rememberSaveable { mutableStateOf(false) }
    var selected by rememberSaveable { mutableStateOf("q") }
    var values by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var originalOverrides by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var originalValues by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var loaded by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var pendingLanguage by remember { mutableStateOf<Boolean?>(null) }
    var dirty by remember { mutableStateOf(false) }
    LaunchedEffect(english) {
        loaded = false
        // Read the same effective gestures used by the keyboard, including existing YAML overrides.
        values = withContext(Dispatchers.IO) {
            KeysConfigHelper.loadConfig(context)
            KeysConfigHelper.qwertySymbolEditorValues(context, english)
        }
        originalOverrides = QwertySwipeSymbols.load(context, english)
        originalValues = values
        dirty = false
        loaded = true
    }
    fun changeLanguage(next: Boolean) {
        if (next == english) return
        if (dirty) pendingLanguage = next else { english = next; message = "" }
    }
    SettingsSection(title = "26键上下拉符号") {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !english, onClick = { changeLanguage(false) }, enabled = !busy,
                    label = { Text("中文26键") })
                FilterChip(selected = english, onClick = { changeLanguage(true) }, enabled = !busy,
                    label = { Text("英文26键") })
            }
            Text("点选字母修改符号：${topGesture}输入上方、${bottomGesture}输入下方；留空关闭该符号。",
                style = MaterialTheme.typography.bodySmall)
            QwertySwipeSymbols.rows.forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    if (row.length < 10) Spacer(Modifier.weight((10 - row.length) / 2f))
                    row.forEach { char ->
                        val key = char.toString()
                        Surface(modifier = Modifier.weight(1f).height(76.dp)
                            .clickable(enabled = loaded && !busy) { selected = key },
                            shape = RoundedCornerShape(8.dp),
                            color = if (selected == key) MaterialTheme.colorScheme.secondaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.SpaceEvenly) {
                                Text(values["up.$key"].orEmpty(), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                                Text(key.uppercase(), style = MaterialTheme.typography.titleMedium)
                                Text(values["down.$key"].orEmpty(), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                            }
                        }
                    }
                    if (row.length < 10) Spacer(Modifier.weight((10 - row.length) / 2f))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("up" to "上方（$topGesture）", "down" to "下方（$bottomGesture）").forEach { (direction, label) ->
                    val id = "$direction.$selected"
                    OutlinedTextField(value = values[id].orEmpty(), onValueChange = { value ->
                        values = values + (id to value); dirty = true; message = ""
                    }, label = { Text("${selected.uppercase()} $label") }, singleLine = true,
                        enabled = loaded && !busy, modifier = Modifier.weight(1f))
                }
            }
            OutlinedButton(enabled = loaded && !busy, modifier = Modifier.fillMaxWidth(), onClick = {
                val before = values
                values = before.toMutableMap().apply {
                    for (key in QwertySwipeSymbols.keys) {
                        put("up.$key", before["down.$key"].orEmpty())
                        put("down.$key", before["up.$key"].orEmpty())
                    }
                }
                dirty = true
                message = "已交换当前语言全部字母键的上下方符号，保存后生效"
            }) { Text("交换上下拉配置") }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(enabled = loaded && !busy, onClick = {
                    values = buildMap {
                        QwertySwipeSymbols.up.forEach { (k, v) -> put("up.$k", v) }
                        QwertySwipeSymbols.down.forEach { (k, v) -> put("down.$k", v) }
                    }; dirty = true; message = ""
                }) { Text("恢复预设") }
                Button(enabled = loaded && dirty && !busy, onClick = {
                    busy = true
                    val displayedSnapshot = values
                    val snapshot = originalOverrides + values.filter { (key, value) -> originalValues[key] != value }
                    scope.launch {
                        runCatching { withContext(Dispatchers.IO) { QwertySwipeSymbols.save(context, english, snapshot) } }
                            .onSuccess { originalOverrides = snapshot; originalValues = displayedSnapshot; dirty = false; message = "已保存" }
                            .onFailure { message = "保存失败：${it.message}" }
                        busy = false
                    }
                }) { Text(if (busy) "保存中…" else "保存符号") }
            }
            if (message.isNotEmpty()) Text(message, style = MaterialTheme.typography.bodySmall)
        }
    }
    pendingLanguage?.let { target ->
        AlertDialog(onDismissRequest = { pendingLanguage = null }, title = { Text("放弃未保存的符号修改？") },
            confirmButton = { TextButton(onClick = { pendingLanguage = null; english = target; message = "" }) { Text("放弃") } },
            dismissButton = { TextButton(onClick = { pendingLanguage = null }) { Text("继续编辑") } })
    }
}
