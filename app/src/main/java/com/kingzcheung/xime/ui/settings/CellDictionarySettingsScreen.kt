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
import com.kingzcheung.xime.settings.CellDictionaryCost
import com.kingzcheung.xime.settings.CellDictionaryOffer
import com.kingzcheung.xime.settings.InstalledCellDictionary
import com.kingzcheung.xime.viewmodel.CellDictionaryUiState
import com.kingzcheung.xime.viewmodel.CellDictionaryViewModel
import com.kingzcheung.xime.ui.common.CompactDropdownMenu
import com.kingzcheung.xime.ui.common.CompactMenuRow

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
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("全部") }
    var categoryMenu by remember { mutableStateOf(false) }
    val dictionaries = state.dictionaries
    val installed = dictionaries.installed.associateBy { it.id }
    val offers = CellDictionaryCatalog.offers
    val builtinIds = CellDictionaryCatalog.builtins.map { it.id }.toSet()
    val catalogIds = offers.map { it.id }.toSet() + builtinIds
    val categories = listOf("全部", "已下载", CellDictionaryCatalog.BUILTIN_CATEGORY) +
        offers.map { it.category }.distinct() + "本地导入"
    fun matches(name: String, group: String, id: String) =
        (category == "全部" || category == group || category == "已下载" && id in installed ||
            category == CellDictionaryCatalog.BUILTIN_CATEGORY && id in builtinIds) &&
            (query.isBlank() || name.contains(query.trim(), ignoreCase = true) || group.contains(query.trim()))
    val builtins = dictionaries.installed.filter { it.id in builtinIds && matches(it.name, it.category, it.id) }
    val visibleOffers = offers.filter { matches(it.name, it.category, it.id) }
    val locals = dictionaries.installed.filter { it.id !in catalogIds && matches(it.name, it.category, it.id) }
    // The cost of what is switched on, shown before it is applied (CellDictionaryCost).
    val selectedEntries = dictionaries.installed.filter { it.id in dictionaries.selected }.sumOf { it.count }
    val large = selectedEntries > CellDictionaryCost.WARN_ENTRIES
    // Per row: the total cost with this dictionary on, so each switch shows what it adds.
    fun costWith(id: String, entries: Int): String {
        val total = if (id in dictionaries.selected) selectedEntries else selectedEntries + entries
        return (if (id in dictionaries.selected) "已计入 · " else "开启后 · ") + CellDictionaryCost.describe(total)
    }
    Scaffold(topBar = {
        if (!embedded) TopAppBar(title = { Text("导入的词库") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
        })
    }, bottomBar = {
        Surface(tonalElevation = 3.dp) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text("已选 ${dictionaries.selected.size} 个 · 已应用 ${dictionaries.applied.size} 个",
                    style = MaterialTheme.typography.bodySmall)
                Text(CellDictionaryCost.describe(selectedEntries), style = MaterialTheme.typography.bodySmall,
                    color = if (large) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = { if (large) confirmLarge = true else onApply() }, enabled = !state.busy && dictionaries.pending,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(if (dictionaries.pending) "应用词库更改" else "词库已同步")
                }
            }
        }
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text("为中文拼音、九键和双拼补充候选词。按需下载或导入，选好后统一应用。补充的词排在内置词后面，" +
                    "但词越多打字越慢、生僻词越容易出现在候选里：页面底部会先算出开启后的代价。",
                    style = MaterialTheme.typography.bodyMedium)
            }
            item {
                Text("推荐词库来自搜狗细胞词库，也可导入本地搜狗 SCEL、TXT（每行“词 拼音”或“拼音 词”）或 CyIME JSON 词库。按原作者及来源页约定使用。关闭后不再加载该词库，已学入个人词库的词仍会保留。",
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
                    CompactDropdownMenu(expanded = categoryMenu, onDismissRequest = { categoryMenu = false }) {
                        categories.forEach { label -> CompactMenuRow(label, selected = category == label,
                            onClick = { category = label; categoryMenu = false }) }
                    }
                }
            }
            item {
                OutlinedButton(onClick = onImport, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                    Text("导入词库（搜狗 SCEL / TXT / JSON）")
                }
                TextButton(onClick = { onSource(CellDictionaryCatalog.SOURCE_URL) }, modifier = Modifier.fillMaxWidth()) {
                    Text("到搜狗官网查找更多词库")
                }
            }
            state.message?.let { message -> item {
                if (state.busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(message, color = if (state.error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            } }
            if (builtins.isEmpty() && visibleOffers.isEmpty() && locals.isEmpty()) item { Text("没有符合条件的词库") }
            items(builtins, key = { it.id }) { entry ->
                CellDictionaryRow(entry.name, entry.category, entry,
                    entry.id in dictionaries.selected, entry.id in dictionaries.applied, state.busy,
                    { onSelect(entry.id, it) }, {}, entry.sourceUrl.takeIf { it.isNotBlank() }?.let { url -> { onSource(url) } },
                    cost = costWith(entry.id, entry.count))
            }
            items(visibleOffers, key = { it.id }) { offer ->
                CellDictionaryRow(offer.name, offer.category, installed[offer.id],
                    offer.id in dictionaries.selected, offer.id in dictionaries.applied, state.busy,
                    { onSelect(offer.id, it) }, { onDownload(offer) }, { onSource(offer.sourceUrl) },
                    estimate = "约 ${offer.entryCount} 条 · ${offer.downloadBytes / 1024} KB",
                    onRemove = { onRemove(offer.id) },
                    cost = costWith(offer.id, installed[offer.id]?.count ?: offer.entryCount))
            }
            items(locals, key = { it.id }) { entry ->
                CellDictionaryRow(entry.name, entry.category, entry,
                    entry.id in dictionaries.selected, entry.id in dictionaries.applied, state.busy,
                    { onSelect(entry.id, it) }, {}, null, onRemove = { onRemove(entry.id) },
                    cost = costWith(entry.id, entry.count))
            }
        }
    }
    if (confirmLarge) {
        AlertDialog(onDismissRequest = { confirmLarge = false },
            title = { Text("词库很大") },
            text = { Text(CellDictionaryCost.describe(selectedEntries) + "。这是全是生僻词时的最坏估计，真实词库通常更小；" +
                "生僻词也可能出现在候选的后几位。仍要应用吗？") },
            confirmButton = { TextButton(onClick = { confirmLarge = false; onApply() }) { Text("仍然应用") } },
            dismissButton = { TextButton(onClick = { confirmLarge = false }) { Text("取消") } })
    }
}

@Composable
private fun CellDictionaryRow(
    name: String, category: String, installed: InstalledCellDictionary?, selected: Boolean,
    applied: Boolean, busy: Boolean, onSelect: (Boolean) -> Unit, onDownload: () -> Unit,
    onSource: (() -> Unit)?,
    estimate: String = "",
    onRemove: (() -> Unit)? = null,
    cost: String = "",
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(name, style = MaterialTheme.typography.titleMedium)
            Text(category, style = MaterialTheme.typography.labelMedium)
            if (installed != null) {
                Text((if (installed.english > 0) "中文 ${installed.count} 条 · 英文 ${installed.english} 个" else "${installed.count} 条") + if (installed.skipped > 0) " · 跳过 ${installed.skipped} 条无效内容" else "",
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
            if (cost.isNotBlank()) Text(cost, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row {
                if (onSource != null) TextButton(onClick = onSource) { Text("查看来源") }
                // Only when not in use: switch it off and apply first.
                if (installed != null && onRemove != null && !applied && !selected)
                    TextButton(onClick = onRemove, enabled = !busy) { Text("删除", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}
