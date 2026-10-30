package com.kingzcheung.xime.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kingzcheung.xime.settings.CellDictionaryCatalog
import com.kingzcheung.xime.settings.CellDictionaryCost
import com.kingzcheung.xime.settings.CellDictionaryOffer
import com.kingzcheung.xime.settings.InstalledCellDictionary
import com.kingzcheung.xime.viewmodel.CellDictionaryUiState
import com.kingzcheung.xime.viewmodel.CellDictionaryViewModel

@Composable
fun CellDictionarySettingsContent(onBack: () -> Unit, embedded: Boolean = false) {
    val model: CellDictionaryViewModel = viewModel()
    val state by model.uiState.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    var linkError by remember { mutableStateOf<String?>(null) }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(model::import)
    }
    CellDictionarySettingsPanel(state, onBack, model::download, model::select, model::apply,
        onImport = { importer.launch(arrayOf("*/*")) },
        onSource = { url ->
            runCatching { uriHandler.openUri(url) }.onFailure { linkError = "无法打开浏览器" }
        },
        onRemove = model::remove, embedded = embedded)
    linkError?.let { message ->
        AlertDialog(onDismissRequest = { linkError = null }, text = { Text(message) },
            confirmButton = { TextButton(onClick = { linkError = null }) { Text("知道了") } })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CellDictionarySettingsPanel(
    state: CellDictionaryUiState,
    onBack: () -> Unit,
    onDownload: (CellDictionaryOffer) -> Unit,
    onSelect: (String, Boolean) -> Unit,
    onApply: () -> Unit,
    onImport: () -> Unit,
    onSource: (String) -> Unit,
    onRemove: (String) -> Unit = {},
    /** Inside the 词库 page: no own top bar. */
    embedded: Boolean = false,
) {
    var confirmLarge by remember { mutableStateOf(false) }
    var showGuide by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val dictionaries = state.dictionaries
    val installed = dictionaries.installed.associateBy { it.id }
    val builtinIds = CellDictionaryCatalog.builtins.map { it.id }.toSet()
    val catalogIds = CellDictionaryCatalog.offers.map { it.id }.toSet() + builtinIds
    fun matches(name: String, group: String) =
        query.isBlank() || name.contains(query.trim(), ignoreCase = true) || group.contains(query.trim())
    // One list grouped by category: built-in first, then Sogou, then the user's own imports.
    val rows = buildList {
        CellDictionaryCatalog.builtins.forEach { builtin ->
            installed[builtin.id]?.let { add(DictionaryItem(builtin.id, builtin.name, builtin.category, it, null, builtin.sourceUrl)) }
        }
        CellDictionaryCatalog.offers.forEach { offer ->
            add(DictionaryItem(offer.id, offer.name, offer.category, installed[offer.id], offer, offer.sourceUrl))
        }
        dictionaries.installed.filter { it.id !in catalogIds }.forEach {
            add(DictionaryItem(it.id, it.name, MY_IMPORTS, it, null, ""))
        }
    }.filter { matches(it.name, it.category) }.groupBy { it.category }
    // Cost is only mentioned when it is noticeable (CellDictionaryCost).
    val selectedEntries = dictionaries.installed.filter { it.id in dictionaries.selected }.sumOf { it.count }
    val large = selectedEntries > CellDictionaryCost.WARN_ENTRIES
    Scaffold(topBar = {
        if (!embedded) TopAppBar(title = { Text("导入的词库") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
        })
    }, bottomBar = {
        if (dictionaries.pending || large) Surface(tonalElevation = 3.dp) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (large) Text("开启的词库较多，打字可能变慢约 ${CellDictionaryCost.slowdownPercent(selectedEntries)}%",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                if (dictionaries.pending) Button(onClick = { if (large) confirmLarge = true else onApply() },
                    enabled = !state.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text("应用更改")
                }
            }
        }
    }) { padding ->
        val listState = rememberLazyListState()
        LazyColumn(state = listState, modifier = Modifier.settingsGutterScroll(listState).fillMaxSize().padding(padding), contentPadding = PaddingValues(vertical = 8.dp)) {
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = query, onValueChange = { query = it }, placeholder = { Text("搜索词库") },
                        singleLine = true, modifier = Modifier.weight(1f))
                    TextButton(onClick = onImport, enabled = !state.busy, modifier = Modifier.padding(start = 8.dp)) {
                        Text("导入文件")
                    }
                }
            }
            // 导入认哪些文件：一行写出扩展名，点开看怎么导入和每种格式的示例。
            item {
                Row(Modifier.fillMaxWidth().clickable { showGuide = true }.padding(start = 16.dp, end = 4.dp)
                    .testTag("cell-dictionary-import-hint"), verticalAlignment = Alignment.CenterVertically) {
                    Text("可导入 .scel、.txt、.dict.yaml、.json 词库", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    TextButton(onClick = { showGuide = true }) { Text("格式与示例") }
                }
            }
            state.message?.let { message -> item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    if (state.busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(message, style = MaterialTheme.typography.bodySmall,
                        color = if (state.error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                }
            } }
            if (rows.isEmpty()) item {
                Text("没有找到词库", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            rows.forEach { (category, items) ->
                item(key = "header-$category") {
                    Text(category, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp))
                }
                items(items, key = { it.id }) { item ->
                    DictionaryRow(item, item.id in dictionaries.selected, item.id in dictionaries.applied, state.busy,
                        onSelect = { onSelect(item.id, it) }, onDownload = { item.offer?.let(onDownload) },
                        onSource = item.sourceUrl.takeIf { it.isNotBlank() }?.let { url -> { onSource(url) } },
                        onRemove = if (item.id in builtinIds) null else ({ onRemove(item.id) }))
                }
            }
            item {
                TextButton(onClick = { onSource(CellDictionaryCatalog.SOURCE_URL) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text("到搜狗词库网站找更多")
                }
            }
        }
    }
    if (showGuide) CellDictionaryImportGuide(onDismiss = { showGuide = false }, onImport = onImport)
    if (confirmLarge) {
        AlertDialog(onDismissRequest = { confirmLarge = false },
            title = { Text("词库较多") },
            text = { Text("开启的词库共约 ${selectedEntries / 10000} 万词，打字可能变慢约 " +
                "${CellDictionaryCost.slowdownPercent(selectedEntries)}%，候选里也会多一些不常用的词。仍要应用吗？") },
            confirmButton = { TextButton(onClick = { confirmLarge = false; onApply() }) { Text("仍然应用") } },
            dismissButton = { TextButton(onClick = { confirmLarge = false }) { Text("取消") } })
    }
}

private const val MY_IMPORTS = "我导入的"

private data class DictionaryItem(
    val id: String, val name: String, val category: String,
    val installed: InstalledCellDictionary?, val offer: CellDictionaryOffer?, val sourceUrl: String,
)

/** Number of words in plain terms: 1600 → 约 1600 词, 22029 → 约 2.2 万词. */
private fun words(count: Int): String =
    if (count >= 10_000) "约 ${"%.1f".format(count / 10_000.0).removeSuffix(".0")} 万词" else "约 $count 词"

@Composable
private fun DictionaryRow(
    item: DictionaryItem, selected: Boolean, applied: Boolean, busy: Boolean,
    onSelect: (Boolean) -> Unit, onDownload: () -> Unit, onSource: (() -> Unit)?, onRemove: (() -> Unit)?,
) {
    var menu by remember { mutableStateOf(false) }
    val installed = item.installed
    val detail = when {
        installed == null -> "${words(item.offer?.entryCount ?: 0)} · 需下载"
        selected != applied -> "${words(installed.count + installed.english)} · 应用后生效"
        else -> words(installed.count + installed.english)
    }
    // Delete only what is not in use: switch it off and apply first.
    val removable = installed != null && onRemove != null && !selected && !applied
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(item.name, style = MaterialTheme.typography.bodyLarge)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (installed != null) Switch(checked = selected, onCheckedChange = onSelect, enabled = !busy,
            modifier = Modifier.semantics { contentDescription = "启用${item.name}" })
        else TextButton(onClick = onDownload, enabled = !busy) { Text("下载") }
        if (onSource != null || removable) Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "更多") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                if (onSource != null) DropdownMenuItem(text = { Text("查看来源") }, onClick = { menu = false; onSource() })
                if (removable) DropdownMenuItem(text = { Text("删除", color = MaterialTheme.colorScheme.error) },
                    onClick = { menu = false; onRemove?.invoke() })
            }
        } else Spacer(Modifier.width(48.dp))
    }
}
