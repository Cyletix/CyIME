package com.kingzcheung.xime.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kingzcheung.xime.settings.*
import com.kingzcheung.xime.ui.keyboard.rememberKeyboardInputPreferences
import com.kingzcheung.xime.ui.keyboard.SymbolInputMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun QwertySymbolEditor(onBack: () -> Unit, initialTarget: String? = null) {
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
    // 26 键之外的固定栏（九键、笔画、手写、常用符号）；null 表示正在编辑 26 键角标。
    var sideTarget by rememberSaveable { mutableStateOf(initialTarget?.takeIf { key ->
        com.kingzcheung.xime.ui.keyboard.SideSymbolTarget.entries.any { it.key == key } }) }
    var reload by remember { mutableStateOf(0) }
    var pendingTab by remember { mutableStateOf<Pair<Boolean, String?>?>(null) }
    var dirty by remember { mutableStateOf(false) }
    var discard by remember { mutableStateOf(false) }
    var versions by remember { mutableStateOf<Map<String, Map<String, String>>>(emptyMap()) }
    var versionName by rememberSaveable { mutableStateOf("") }
    fun back() { if (!busy) { if (dirty) discard = true else onBack() } }
    BackHandler { back() }
    LaunchedEffect(english, reload) {
        loaded = false
        // Read the same effective gestures used by the keyboard, including existing YAML overrides.
        values = withContext(Dispatchers.IO) {
            KeysConfigHelper.loadConfig(context)
            KeysConfigHelper.qwertySymbolEditorValues(context, english)
        }
        originalOverrides = QwertySwipeSymbols.load(context, english)
        versions = withContext(Dispatchers.IO) { QwertySwipeSymbols.versions(context) }
        originalValues = values
        dirty = false
        loaded = true
    }
    fun applyTab(nextEnglish: Boolean, nextSide: String?) {
        val discarded = dirty
        english = nextEnglish; sideTarget = nextSide; message = ""
        if (discarded) reload++
    }
    // 26 键角标未保存时，切走前先确认是否放弃。
    fun changeTab(nextEnglish: Boolean, nextSide: String?) {
        if (nextEnglish == english && nextSide == sideTarget) return
        if (dirty && (nextSide != null || nextEnglish != english)) pendingTab = nextEnglish to nextSide
        else applyTab(nextEnglish, nextSide)
    }
    Scaffold(topBar = {
        TopAppBar(title = { Text("按键符号") }, navigationIcon = {
            IconButton(onClick = { back() }, enabled = !busy) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
            }
        })
    }) { padding ->
    Column(Modifier.fillMaxSize().padding(padding).imePadding()
        .verticalScroll(rememberScrollState()).padding(16.dp)) {
    // 一处编辑所有“某个位置的第二输入”：26 键角标与各固定栏，用标签页切换。
    val sideTargets = com.kingzcheung.xime.ui.keyboard.SideSymbolTarget.entries
    val sideIndex = sideTargets.indexOfFirst { it.key == sideTarget }
    PrimaryScrollableTabRow(selectedTabIndex = if (sideIndex >= 0) 2 + sideIndex else if (english) 1 else 0,
        edgePadding = 0.dp) {
        Tab(selected = sideTarget == null && !english, onClick = { changeTab(false, null) }, enabled = !busy,
            text = { Text("中文26键") })
        Tab(selected = sideTarget == null && english, onClick = { changeTab(true, null) }, enabled = !busy,
            text = { Text("英文26键") })
        sideTargets.forEach { target ->
            Tab(selected = sideTarget == target.key, onClick = { changeTab(english, target.key) }, enabled = !busy,
                text = { Text(target.label) }, modifier = Modifier.testTag("symbol-target:${target.key}"))
        }
    }
    Spacer(Modifier.height(12.dp))
    val side = sideTargets.firstOrNull { it.key == sideTarget }
    if (side != null) SettingsSection(title = side.label) { SideSymbolEditor(side) }
    else SettingsSection(title = "字母键符号") {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("先点一个字母，再在下面两个框里改它的上方、下方符号，最后点「保存符号」。" +
                "键盘上${topGesture}输入上方、${bottomGesture}输入下方；框里留空表示这个位置不放符号。",
                style = MaterialTheme.typography.bodySmall)
            Text(if (english) "英文26键有自己的一套半角符号，点「恢复预设」还原。"
                else "全拼、双拼等中文26键共用这一套：上方默认是数字和常用全角符号，下方是半角符号。" +
                    "这里写什么就输入什么，不随全角／半角变化；只有空格两边的逗号句号跟着全角／半角走。",
                style = MaterialTheme.typography.bodySmall)
            QwertySwipeSymbols.rows.forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    if (row.length < 10) Spacer(Modifier.weight((10 - row.length) / 2f))
                    row.forEach { char ->
                        val key = char.toString()
                        Surface(modifier = Modifier.weight(1f).height(64.dp).testTag("symbol-editor-key:$key")
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
                        enabled = loaded && !busy, modifier = Modifier.weight(1f).testTag("symbol-editor-$direction"))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // 中英文一致：点了立即恢复并保存。英文清掉修改即预设；中文写入预设符号（覆盖自定义 YAML）。
                TextButton(enabled = loaded && !busy, onClick = {
                    busy = true
                    val preset = if (english) emptyMap() else buildMap {
                        QwertySwipeSymbols.up.forEach { (k, v) -> put("up.$k", v) }
                        QwertySwipeSymbols.down.forEach { (k, v) -> put("down.$k", v) }
                    }
                    scope.launch {
                        runCatching { withContext(Dispatchers.IO) { QwertySwipeSymbols.save(context, english, preset) } }
                            .onSuccess { message = if (english) "英文26键已恢复预设" else "中文26键已恢复预设"; reload++ }
                            .onFailure { message = "保存失败：${it.message}" }
                        busy = false
                    }
                }) { Text("恢复预设") }
                Button(enabled = loaded && dirty && !busy, onClick = {
                    busy = true
                    val displayedSnapshot = values
                    val snapshot = originalOverrides + values.filter { (key, value) -> originalValues[key] != value }
                    scope.launch {
                        runCatching { withContext(Dispatchers.IO) { QwertySwipeSymbols.save(context, english, snapshot) } }
                            .onSuccess { originalOverrides = snapshot; originalValues = displayedSnapshot; dirty = false; message = "已保存，键盘上立即生效" }
                            .onFailure { message = "保存失败：${it.message}" }
                        busy = false
                    }
                }) { Text(if (busy) "保存中…" else "保存符号") }
            }
            if (message.isNotEmpty()) Text(message, style = MaterialTheme.typography.bodySmall)
            if (!english) {
                HorizontalDivider()
                Text("布局版本", style = MaterialTheme.typography.titleSmall)
                Text("把当前这套另存为版本，以后可以一键载入切换；载入后点「保存符号」生效。",
                    style = MaterialTheme.typography.bodySmall)
                versions.keys.forEach { name ->
                    Row(Modifier.fillMaxWidth().testTag("symbol-version:$name"), verticalAlignment = Alignment.CenterVertically) {
                        Text(name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        TextButton(enabled = loaded && !busy, onClick = {
                            values = versions.getValue(name); dirty = true
                            message = "已载入「$name」，点「保存符号」后生效"
                        }) { Text("载入") }
                        TextButton(enabled = !busy, onClick = {
                            runCatching { QwertySwipeSymbols.deleteVersion(context, name) }
                                .onSuccess { versions = QwertySwipeSymbols.versions(context); message = "已删除「$name」" }
                                .onFailure { message = "删除失败：${it.message}" }
                        }) { Text("删除") }
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = versionName, onValueChange = { versionName = it },
                        label = { Text("版本名称") }, singleLine = true, enabled = loaded && !busy,
                        modifier = Modifier.weight(1f).testTag("symbol-version-name"))
                    Button(enabled = loaded && !busy && QwertySwipeSymbols.versionName(versionName).isNotEmpty(), onClick = {
                        val name = QwertySwipeSymbols.versionName(versionName)
                        runCatching { QwertySwipeSymbols.saveVersion(context, name, values) }
                            .onSuccess {
                                versions = QwertySwipeSymbols.versions(context); versionName = ""
                                message = "已另存为「$name」，同名版本会被覆盖"
                            }
                            .onFailure { message = "另存失败：${it.message}" }
                    }) { Text("另存为版本") }
                }
            }
        }
    }
    }
    }
    if (discard) AlertDialog(onDismissRequest = { discard = false }, title = { Text("放弃未保存的符号修改？") },
        confirmButton = { TextButton(onClick = { discard = false; onBack() }) { Text("放弃") } },
        dismissButton = { TextButton(onClick = { discard = false }) { Text("继续编辑") } })
    pendingTab?.let { (nextEnglish, nextSide) ->
        AlertDialog(onDismissRequest = { pendingTab = null }, title = { Text("放弃未保存的符号修改？") },
            confirmButton = { TextButton(onClick = { pendingTab = null; applyTab(nextEnglish, nextSide) }) { Text("放弃") } },
            dismissButton = { TextButton(onClick = { pendingTab = null }) { Text("继续编辑") } })
    }
}
