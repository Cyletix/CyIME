package com.kingzcheung.xime.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kingzcheung.xime.settings.DictEntry
import com.kingzcheung.xime.viewmodel.CustomPhraseUiState
import com.kingzcheung.xime.viewmodel.CustomPhraseViewModel

/**
 * 词库：我的短语（打编码出固定短语）、导入的词库（下载/导入、开关、删除、开启前的代价）、删掉的候选（长按删过的词）、
 * 查词（一个词在内置词库、导入词库、我的短语里有没有，编码是什么）。短语对所有键盘通用，不再按方案选择。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DictionarySettingsContent(
    onBack: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onCellDictionaries: () -> Unit = {},
) {
    var selectedDictTab by remember { mutableIntStateOf(0) }
    val customPhraseVM: CustomPhraseViewModel = viewModel(key = "dict_custom_phrase")
    val customPhraseState by customPhraseVM.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { customPhraseVM.loadShared() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("词库") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        floatingActionButton = {
            if (selectedDictTab == 0) {
                FloatingActionButton(
                    onClick = { customPhraseVM.showAddDialog() },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.Default.Add, contentDescription = "添加", tint = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                TabButton("我的短语", selected = selectedDictTab == 0, onClick = { selectedDictTab = 0 }, modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.width(6.dp))
                TabButton("导入的词库", selected = selectedDictTab == 1, onClick = { selectedDictTab = 1 }, modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.width(6.dp))
                TabButton("删掉的候选", selected = selectedDictTab == 2, onClick = { selectedDictTab = 2 }, modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.width(6.dp))
                TabButton("查词", selected = selectedDictTab == 3, onClick = { selectedDictTab = 3 }, modifier = Modifier.weight(1f))
            }
            when (selectedDictTab) {
                1 -> CellDictionarySettingsContent(onBack = onBack, embedded = true)
                2 -> Column {
                    Text("旧版本长按删除会把候选永久隐藏，这里可以放回去。现在长按删除只忘掉学习，不再隐藏。",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                    T9SuppressionPanel()
                }
                3 -> DictionaryLookupPanel()
                else -> CustomPhraseTabContent(viewModel = customPhraseVM, uiState = customPhraseState)
            }
        }
    }
}

@Composable
private fun TabButton(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
    ) {
        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
            Text(text, style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun UsageHint() {
    val uriHandler = LocalUriHandler.current
    Column(
        modifier = Modifier
            .clickable { uriHandler.openUri("https://github.com/Cyletix/CyIME/blob/main/docs/usage.md#词库") }
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.Info, contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(12.dp))
        Text("暂无词条", style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Text("点右下角 + 添加常用的短语和对应编码",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun CustomPhraseTabContent(
    viewModel: CustomPhraseViewModel,
    uiState: CustomPhraseUiState,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text("打编码，固定出你设定的短语，排在候选第一位（例如 dz → 你的地址）。所有键盘通用。",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            shape = RoundedCornerShape(28.dp),
            tonalElevation = 2.dp,
            color = MaterialTheme.colorScheme.surface
        ) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Search, contentDescription = null,
                    tint = if (uiState.searchQuery.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(12.dp))
                BasicTextField(value = uiState.searchQuery, onValueChange = { viewModel.setSearchQuery(it) },
                    modifier = Modifier.weight(1f), singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    decorationBox = { innerTextField ->
                        Box { if (uiState.searchQuery.isEmpty()) Text("搜索", color = MaterialTheme.colorScheme.onSurfaceVariant); innerTextField() }
                    })
                if (uiState.searchQuery.isNotEmpty()) {
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = { viewModel.clearSearch() }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Clear, contentDescription = "清除搜索", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        if (uiState.isLoading) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (uiState.entries.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                UsageHint()
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                itemsIndexed(uiState.filteredEntries, key = { i, _ -> "cp_$i" }) { i, entry ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(entry.word, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                                Spacer(Modifier.height(2.dp))
                                Text(entry.code, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                if (entry.weight != null) {
                                    Text("顺序 ${entry.weight}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                }
                            }
                            IconButton(onClick = {
                                viewModel.setEditing(i, entry)
                                viewModel.showEditDialog()
                            }, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Default.Edit, contentDescription = "编辑", modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = { viewModel.deleteEntry(i) }, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Default.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    if (uiState.showAddDialog) {
        PhraseEditDialog(
            title = "添加短语",
            word = uiState.editWord,
            code = uiState.editCode,
            weight = uiState.editWeight,
            onWordChange = viewModel::setEditWord,
            onCodeChange = viewModel::setEditCode,
            onWeightChange = viewModel::setEditWeight,
            onConfirm = { viewModel.addEntry(uiState.editWord, uiState.editCode, uiState.editWeight.toIntOrNull()); viewModel.hideAddDialog() },
            onDismiss = viewModel::hideAddDialog,
        )
    }
    if (uiState.showEditDialog) {
        PhraseEditDialog(
            title = "编辑短语",
            word = uiState.editWord,
            code = uiState.editCode,
            weight = uiState.editWeight,
            onWordChange = viewModel::setEditWord,
            onCodeChange = viewModel::setEditCode,
            onWeightChange = viewModel::setEditWeight,
            onConfirm = { viewModel.updateEntry(uiState.editIndex, uiState.editWord, uiState.editCode, uiState.editWeight.toIntOrNull()); viewModel.hideEditDialog() },
            onDismiss = viewModel::hideEditDialog,
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun PhraseEditDialog(
    title: String,
    word: String,
    code: String,
    weight: String,
    onWordChange: (String) -> Unit,
    onCodeChange: (String) -> Unit,
    onWeightChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 20.dp))
            OutlinedTextField(value = word, onValueChange = onWordChange,
                label = { Text("短语") }, shape = RoundedCornerShape(12.dp), singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = code, onValueChange = onCodeChange,
                label = { Text("编码") }, shape = RoundedCornerShape(12.dp), singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = weight, onValueChange = onWeightChange,
                label = { Text("顺序（可选：同一编码有几条短语时，数字大的在前）") }, shape = RoundedCornerShape(12.dp), singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(24.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("取消") }
                Spacer(Modifier.width(8.dp))
                Surface(
                    modifier = Modifier.clickable(enabled = word.isNotBlank() && code.isNotBlank(), onClick = onConfirm),
                    shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.primary
                ) {
                    Text("确定", modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimary)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
