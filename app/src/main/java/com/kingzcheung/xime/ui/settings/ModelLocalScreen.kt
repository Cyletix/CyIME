package com.kingzcheung.xime.ui.settings

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.RadioButton
import androidx.compose.ui.semantics.Role
import com.kingzcheung.xime.model.DownloadRegion
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kingzcheung.xime.viewmodel.ModelItemState
import com.kingzcheung.xime.viewmodel.ModelManagementViewModel

/** 本地模型管理：列出已下载模型并提供删除入口（对齐本地方案页）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelLocalContent(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val viewModel: ModelManagementViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.toastMessage) {
        uiState.toastMessage?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            viewModel.clearToast()
        }
    }

    var pendingDelete by remember { mutableStateOf<ModelItemState?>(null) }

    pendingDelete?.let { model ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除模型") },
            text = { Text("确定删除「${model.model.name}」？模型文件将被删除，此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    viewModel.deleteModel(model.model.id)
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("取消")
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("模型管理") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { padding ->
        val localModels = uiState.models.filter { it.isDownloaded }
        // 三段都用设置页统一的分组样式：标题 + 一个圆角面板，行样式一致，说明文字只放在所属分组里。
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .testTag("model-local-list"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "models") {
                SettingsSection(title = "已安装模型（${localModels.size + 1}）") {
                    BuiltinOcrModelRow()
                    localModels.forEach { item ->
                        ModelRowDivider()
                        ModelLocalRow(item = item, onDelete = { pendingDelete = item })
                    }
                    if (localModels.isEmpty() && uiState.isLoading) {
                        ModelRowDivider()
                        ModelSectionNote("正在检查已下载的模型…")
                    }
                    ModelRowDivider()
                    ModelSectionNote("本地模型在手机上运行，会占用 CPU、增加耗电，可能让手机发热。")
                }
            }
            item(key = "model-source") {
                DownloadRegionSection(
                    region = uiState.downloadRegion,
                    detectedChina = uiState.detectedChina,
                    onRegion = viewModel::setDownloadRegion,
                )
            }
            item(key = "model-import") {
                ModelImportSection(
                    isImporting = uiState.isImporting,
                    onImportFiles = viewModel::importDocuments,
                    onImportFolder = viewModel::importTree,
                )
            }
        }
    }
}

/** 下载线路：网络慢或无法访问时换线路。 */
@Composable
private fun DownloadRegionSection(
    region: DownloadRegion,
    detectedChina: Boolean,
    onRegion: (DownloadRegion) -> Unit,
) {
    SettingsSection(title = "下载线路") {
        Column(Modifier.selectableGroup().testTag("model-source")) {
            DownloadRegion.entries.forEachIndexed { index, option ->
                if (index > 0) ModelRowDivider()
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp)
                    .testTag("model-source:${option.name}")
                    .selectable(region == option, role = Role.RadioButton) { onRegion(option) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (option == DownloadRegion.AUTO)
                            "${option.label}（当前判断：${if (detectedChina) "中国大陆" else "海外"}）" else option.label,
                            style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        Text(option.description, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.width(8.dp))
                    RadioButton(selected = region == option, onClick = null)
                }
            }
            ModelRowDivider()
            ModelSectionNote("每条线路下载后都会核对文件，一条失败自动换下一条。")
        }
    }
}

/** 从本机导入：把自己下好的文件导进来。 */
@Composable
private fun ModelImportSection(
    isImporting: Boolean,
    onImportFiles: (List<Uri>) -> Unit,
    onImportFolder: (Uri) -> Unit,
) {
    val filesLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) onImportFiles(uris)
    }
    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let(onImportFolder)
    }
    SettingsSection(title = "从本机导入") {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("可选模型文件、官方压缩包、整合包（.zip），或一个存放模型的文件夹；按文件内容自动认出是哪个模型，认不出的不会安装。",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { filesLauncher.launch(arrayOf("*/*")) }, enabled = !isImporting,
                    modifier = Modifier.testTag("model-import-files")) { Text("选择文件") }
                OutlinedButton(onClick = { folderLauncher.launch(null) }, enabled = !isImporting,
                    modifier = Modifier.testTag("model-import-folder")) { Text("选择文件夹") }
                if (isImporting) Text("正在导入…", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** 内置 OCR 与下载的模型用同一种行样式，只是没有删除按钮。 */
@Composable
private fun BuiltinOcrModelRow() {
    ModelRow(
        name = "文字识别（OCR）",
        tag = "文字识别",
        detail = "中文、英文 · 随应用内置，无需下载",
        modifier = Modifier.testTag("builtin-ocr-model"),
    )
}

@Composable
private fun ModelLocalRow(
    item: ModelItemState,
    onDelete: () -> Unit,
) {
    val detail = buildString {
        item.installedVersion?.let { append("版本 $it") }
        if (item.diskSize > 0) {
            if (isNotEmpty()) append(" · ")
            append(modelFormatSize(item.diskSize))
        }
        if (item.hasUpdate) append(" · 旧版，可删除后装新版")
    }
    ModelRow(
        name = item.model.name,
        tag = modelCategoryLabel(item.model.category),
        detail = detail,
    ) {
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "删除${item.model.name}",
                tint = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun ModelRow(
    name: String,
    tag: String,
    detail: String,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = 64.dp)
            .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(8.dp))
                TagText(tag, MaterialTheme.colorScheme.primary)
            }
            if (detail.isNotEmpty()) {
                Text(detail, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { trailing() }
    }
}

@Composable
private fun ModelSectionNote(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
}

@Composable
private fun ModelRowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 16.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    )
}


@Composable
private fun TagText(text: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.12f),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}
