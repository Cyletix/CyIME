package com.kingzcheung.xime.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kingzcheung.xime.settings.CellDictionaryCatalog
import com.kingzcheung.xime.settings.CellDictionaryOffer
import com.kingzcheung.xime.settings.InstalledCellDictionary
import com.kingzcheung.xime.viewmodel.CellDictionaryUiState
import com.kingzcheung.xime.viewmodel.CellDictionaryViewModel

@Composable
fun CellDictionarySettingsContent(onBack: () -> Unit) {
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
        })
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
) {
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("全部") }
    var categoryMenu by remember { mutableStateOf(false) }
    val dictionaries = state.dictionaries
    val installed = dictionaries.installed.associateBy { it.id }
    val offers = CellDictionaryCatalog.offers
    val catalogIds = offers.map { it.id }.toSet()
    val categories = listOf("全部", "已下载") + offers.map { it.category }.distinct() + "本地导入"
    fun matches(name: String, group: String, id: String) =
        (category == "全部" || category == group || category == "已下载" && id in installed) &&
            (query.isBlank() || name.contains(query.trim(), ignoreCase = true) || group.contains(query.trim()))
    val visibleOffers = offers.filter { matches(it.name, it.category, it.id) }
    val locals = dictionaries.installed.filter { it.id !in catalogIds && matches(it.name, it.category, it.id) }
    Scaffold(topBar = {
        TopAppBar(title = { Text("分类词库") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
        })
    }, bottomBar = {
        Surface(tonalElevation = 3.dp) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text("已选 ${dictionaries.selected.size} 个 · 已应用 ${dictionaries.applied.size} 个",
                    style = MaterialTheme.typography.bodySmall)
                Button(onClick = onApply, enabled = !state.busy && dictionaries.pending,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(if (dictionaries.pending) "应用词库更改" else "词库已同步")
                }
            }
        }
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text("为中文拼音、九键和双拼补充候选词。按需下载，选好后统一应用。词库越多，首次应用耗时和占用空间越大。",
                    style = MaterialTheme.typography.bodyMedium)
            }
            item {
                Text("推荐词库来自搜狗细胞词库，也可导入本地 SCEL 或 CyIME JSON 词库。按原作者及来源页约定使用。关闭后不再加载该词库，已学入个人词库的词仍会保留。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("搜索词库或分类") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            item {
                Box {
                    OutlinedButton(onClick = { categoryMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("分类：$category")
                    }
                    DropdownMenu(expanded = categoryMenu, onDismissRequest = { categoryMenu = false }) {
                        categories.forEach { label -> DropdownMenuItem(text = { Text(label) },
                            onClick = { category = label; categoryMenu = false }) }
                    }
                }
            }
            item {
                OutlinedButton(onClick = onImport, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                    Text("导入词库（SCEL / JSON）")
                }
                TextButton(onClick = { onSource(CellDictionaryCatalog.SOURCE_URL) }, modifier = Modifier.fillMaxWidth()) {
                    Text("到搜狗官网查找更多词库")
                }
            }
            state.message?.let { message -> item {
                if (state.busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(message, color = if (state.error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            } }
            if (visibleOffers.isEmpty() && locals.isEmpty()) item { Text("没有符合条件的词库") }
            items(visibleOffers, key = { it.id }) { offer ->
                CellDictionaryRow(offer.name, offer.category, installed[offer.id],
                    offer.id in dictionaries.selected, offer.id in dictionaries.applied, state.busy,
                    { onSelect(offer.id, it) }, { onDownload(offer) }, { onSource(offer.sourceUrl) },
                    estimate = "约 ${offer.entryCount} 条 · ${offer.downloadBytes / 1024} KB")
            }
            items(locals, key = { it.id }) { entry ->
                CellDictionaryRow(entry.name, entry.category, entry,
                    entry.id in dictionaries.selected, entry.id in dictionaries.applied, state.busy,
                    { onSelect(entry.id, it) }, {}, null)
            }
        }
    }
}

@Composable
private fun CellDictionaryRow(
    name: String, category: String, installed: InstalledCellDictionary?, selected: Boolean,
    applied: Boolean, busy: Boolean, onSelect: (Boolean) -> Unit, onDownload: () -> Unit,
    onSource: (() -> Unit)?,
    estimate: String = "",
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(name, style = MaterialTheme.typography.titleMedium)
            Text(category, style = MaterialTheme.typography.labelMedium)
            if (installed != null) {
                Text("${installed.count} 条" + if (installed.skipped > 0) " · 跳过 ${installed.skipped} 条无效内容" else "",
                    style = MaterialTheme.typography.bodySmall)
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (selected != applied) { if (selected) "待开启" else "待关闭" }
                        else if (applied) "已开启" else "已关闭", modifier = Modifier.weight(1f))
                    Switch(checked = selected, onCheckedChange = onSelect, enabled = !busy,
                        modifier = Modifier.semantics { contentDescription = "启用$name" })
                }
            } else {
                if (estimate.isNotBlank()) Text(estimate, style = MaterialTheme.typography.bodySmall)
                Button(onClick = onDownload, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Text("下载并选用")
                }
            }
            if (onSource != null) TextButton(onClick = onSource) { Text("查看来源") }
        }
    }
}
