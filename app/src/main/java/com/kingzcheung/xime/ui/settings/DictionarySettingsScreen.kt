package com.kingzcheung.xime.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kingzcheung.xime.viewmodel.CustomPhraseUiState
import com.kingzcheung.xime.viewmodel.CustomPhraseViewModel

/**
 * 词库：我的短语（打编码出固定短语）、导入词库（下载/导入、开关、删除）、删掉的候选（旧版本藏起来的词）、
 * 查词（一个词在内置词库、导入词库、我的短语里有没有，编码是什么）。四个标签同一种排法：顶部一个输入框，
 * 下面一行一条，不放说明段落。短语对所有键盘通用。
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
                    Icon(Icons.Default.Add, contentDescription = "添加短语", tint = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            PrimaryTabRow(selectedTabIndex = selectedDictTab, containerColor = MaterialTheme.colorScheme.surface) {
                listOf("我的短语", "导入词库", "删掉的候选", "查词").forEachIndexed { index, title ->
                    Tab(selected = selectedDictTab == index, onClick = { selectedDictTab = index },
                        text = { Text(title, maxLines = 1) })
                }
            }
            when (selectedDictTab) {
                1 -> CellDictionarySettingsContent(onBack = onBack, embedded = true)
                2 -> T9SuppressionPanel()
                3 -> DictionaryLookupPanel()
                else -> CustomPhraseTabContent(viewModel = customPhraseVM, uiState = customPhraseState)
            }
        }
    }
}

/** The search box every tab starts with. */
@Composable
internal fun DictionarySearchField(value: String, onValueChange: (String) -> Unit, placeholder: String,
                                   modifier: Modifier = Modifier) {
    OutlinedTextField(value = value, onValueChange = onValueChange, placeholder = { Text(placeholder) },
        singleLine = true, modifier = modifier)
}

/** One line of a tab's list: a title, a grey second line, and its controls on the right. */
@Composable
internal fun DictionaryListRow(title: String, detail: String, modifier: Modifier = Modifier,
                               trailing: @Composable () -> Unit = {}) {
    Row(modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (detail.isNotEmpty()) Text(detail, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing()
    }
}

/** A tab with nothing to list: one plain line in the middle. */
@Composable
internal fun DictionaryEmptyLine(text: String) {
    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CustomPhraseTabContent(
    viewModel: CustomPhraseViewModel,
    uiState: CustomPhraseUiState,
) {
    val listState = rememberLazyListState()
    LazyColumn(state = listState, modifier = Modifier.settingsGutterScroll(listState).fillMaxSize(), contentPadding = PaddingValues(top = 8.dp, bottom = 88.dp)) {
        item {
            DictionarySearchField(uiState.searchQuery, viewModel::setSearchQuery, "搜索短语或编码",
                Modifier.fillMaxWidth().padding(horizontal = 16.dp))
        }
        when {
            uiState.isLoading -> item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
            uiState.entries.isEmpty() -> item { DictionaryEmptyLine("还没有短语，点 + 添加") }
            uiState.filteredEntries.isEmpty() -> item { DictionaryEmptyLine("没有找到") }
            else -> itemsIndexed(uiState.filteredEntries, key = { i, _ -> "cp_$i" }) { _, entry ->
                // filteredEntries is a view of entries; edit and delete act on the entry's own position.
                val index = uiState.entries.indexOf(entry)
                DictionaryListRow(entry.word, entry.code,
                    Modifier.clickable { viewModel.setEditing(index, entry); viewModel.showEditDialog() }) {
                    IconButton(onClick = { viewModel.deleteEntry(index) }) {
                        Icon(Icons.Default.Delete, contentDescription = "删除${entry.word}",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(value = word, onValueChange = onWordChange,
                label = { Text("短语") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = code, onValueChange = onCodeChange,
                label = { Text("编码，例如 dz") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = weight, onValueChange = onWeightChange,
                label = { Text("排序（可不填，数字大的在前）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("取消") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = onConfirm, enabled = word.isNotBlank() && code.isNotBlank()) { Text("保存") }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
