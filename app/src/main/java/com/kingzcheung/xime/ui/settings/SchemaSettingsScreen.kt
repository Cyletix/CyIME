package com.kingzcheung.xime.ui.settings

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.twotone.DriveFolderUpload
import androidx.compose.material.icons.twotone.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import com.kingzcheung.xime.ui.common.CompactDropdownMenu
import com.kingzcheung.xime.ui.common.CompactMenuRow
import com.kingzcheung.xime.ui.common.CompactMenuDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kingzcheung.xime.settings.SchemaManager
import com.kingzcheung.xime.settings.SchemaMeta
import com.kingzcheung.xime.settings.SettingsPreferences
import com.kingzcheung.xime.settings.InputLanguage
import com.kingzcheung.xime.settings.LanguagePreferences
import com.kingzcheung.xime.settings.InputModes
import com.kingzcheung.xime.settings.SchemaInfo
import com.kingzcheung.xime.viewmodel.LocalPackageItem
import com.kingzcheung.xime.viewmodel.SchemaLocalViewModel
import com.kingzcheung.xime.viewmodel.SchemaSettingsViewModel
import com.kingzcheung.xime.ui.menubar.rememberDragOrder
import com.kingzcheung.xime.ui.menubar.dragOrderItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchemaSettingsContent(
    onBack: () -> Unit,
    onNavigateToMarket: () -> Unit = {},
    onNavigateToRimeFileBrowser: () -> Unit = {},
) {
    val context = LocalContext.current
    val viewModel: SchemaSettingsViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val localViewModel: SchemaLocalViewModel = viewModel()
    val localUiState by localViewModel.uiState.collectAsStateWithLifecycle()
    var tabIndex by remember { mutableStateOf(0) }
    var showExtraSchemas by remember { mutableStateOf(false) }
    var englishEnabled by remember { mutableStateOf(InputLanguage.ENGLISH in LanguagePreferences.enabled(context)) }
    var modeOrderRevision by remember { mutableIntStateOf(0) }
    // F6: 从方案市场/导入返回时自动重扫描，新装方案立即出现
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refresh()
        localViewModel.loadLocalPackages()
        englishEnabled = InputLanguage.ENGLISH in LanguagePreferences.enabled(context)
        modeOrderRevision++
    }
    // 切 tab 时刷新对应列表，保持数据一致
    LaunchedEffect(tabIndex) {
        if (tabIndex == 0) viewModel.refresh() else localViewModel.loadLocalPackages()
    }
    // 导入完成后刷新本地包列表
    LaunchedEffect(Unit) {
        viewModel.importCompleted.collect { localViewModel.loadLocalPackages() }
    }
    var showMenu by remember { mutableStateOf(false) }
    var showImportWarning by remember { mutableStateOf(false) }
    var dontShowImportWarning by remember { mutableStateOf(false) }
    var pendingImportAction by remember { mutableStateOf<() -> Unit>({}) }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        for (uri in uris) {
            viewModel.importSchemaFile(uri)
        }
    }

    if (showImportWarning) {
        AlertDialog(
            onDismissRequest = {
                showImportWarning = false
            },
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Text("导入方案须知", fontWeight = FontWeight.SemiBold)
            },
            text = {
                Column {
                    Text(
                        "使用导入方案功能时，请先去了解 Rime 配置方案以及相关文档（github.com/rime/home/wiki），并确保你知道自己正在做什么。",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "如果你不知道自己在做什么，请使用默认方案。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { dontShowImportWarning = !dontShowImportWarning }
                    ) {
                        Checkbox(checked = dontShowImportWarning, onCheckedChange = { dontShowImportWarning = it })
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("不再显示", style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (dontShowImportWarning) SettingsPreferences.setSchemaImportWarningDismissed(context, true)
                        showImportWarning = false
                        pendingImportAction()
                    }
                ) { Text("继续导入") }
            },
            dismissButton = {
                TextButton(onClick = { showImportWarning = false }) {
                    Text("取消")
                }
            }
        )
    }

    fun requireImportWarning(action: () -> Unit) {
        if (SettingsPreferences.isSchemaImportWarningDismissed(context)) {
            action()
        } else {
            pendingImportAction = action
            showImportWarning = true
        }
    }

    if (uiState.showUninstallDialog) {
        val packages = uiState.marketPackages.filterNot { it.packageId == "builtin" }
        val selectedIds = remember { mutableStateListOf<String>() }
        AlertDialog(
            onDismissRequest = { viewModel.dismissUninstallDialog() },
            title = { Text("卸载方案") },
            text = {
                if (packages.isEmpty()) {
                    Text(
                        "没有可卸载的方案",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Column {
                        Text(
                            "选择要卸载的方案：",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        packages.forEach { pkg ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (pkg.packageId in selectedIds) {
                                            selectedIds.remove(pkg.packageId)
                                        } else {
                                            selectedIds.add(pkg.packageId)
                                        }
                                    }
                                    .padding(vertical = 4.dp),
                            ) {
                                Checkbox(
                                    checked = pkg.packageId in selectedIds,
                                    onCheckedChange = { checked ->
                                        if (checked) selectedIds.add(pkg.packageId)
                                        else selectedIds.remove(pkg.packageId)
                                    }
                                )
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(pkg.displayName, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        "${pkg.schemaCount} 个输入方案 · ${pkg.fileCount} 个文件" +
                                            if (pkg.version.isNotEmpty()) " · v${pkg.version}" else "",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline,
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.uninstallPackages(selectedIds.toList())
                    },
                    enabled = selectedIds.isNotEmpty(),
                ) {
                    Text(
                        "卸载 (${selectedIds.size})",
                        color = if (selectedIds.isNotEmpty()) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissUninstallDialog() }) {
                    Text("取消")
                }
            }
        )
    }

    LaunchedEffect(uiState.toastMessage) {
        uiState.toastMessage?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            viewModel.clearToast()
        }
    }

    LaunchedEffect(localUiState.toastMessage) {
        localUiState.toastMessage?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            localViewModel.clearToast()
        }
    }

    if (localUiState.conflictPackageId != null) {
        AlertDialog(
            onDismissRequest = { localViewModel.cancelConflictInstall() },
            title = { Text("更新共用文件") },
            text = {
                Text("以下 ${localUiState.conflictingFiles.size} 个同名文件将备份并更新：\n${localUiState.conflictingFiles.joinToString("、") { it.fileName }}\n不会卸载其他输入模式；卸载新方案时可恢复原文件。")
            },
            confirmButton = {
                TextButton(onClick = { localViewModel.confirmInstallWithReplace() }) {
                    Text("备份并更新")
                }
            },
            dismissButton = {
                TextButton(onClick = { localViewModel.cancelConflictInstall() }) {
                    Text("取消")
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("已安装的方案") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "更多")
                        }
                        CompactDropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            offset = DpOffset(0.dp, 4.dp)
                        ) {
                            CompactMenuRow(
                                label = "方案市场", icon = Icons.TwoTone.Storefront,
                                onClick = {
                                    showMenu = false
                                    onNavigateToMarket()
                                }
                            )
                            CompactMenuDivider()
                            CompactMenuRow(
                                label = "从文件选择", icon = Icons.TwoTone.DriveFolderUpload,
                                onClick = {
                                    showMenu = false
                                    requireImportWarning { importLauncher.launch(arrayOf("*/*")) }
                                }
                            )
                            CompactMenuDivider()
                            CompactMenuRow(
                                label = "文件管理器", icon = Icons.Default.FolderOpen,
                                onClick = {
                                    showMenu = false
                                    onNavigateToRimeFileBrowser()
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 和词库页同一种标签栏。
            PrimaryTabRow(selectedTabIndex = tabIndex, containerColor = MaterialTheme.colorScheme.surface) {
                Tab(
                    selected = tabIndex == 0,
                    onClick = { tabIndex = 0 },
                    text = { Text("已安装") },
                )
                Tab(
                    selected = tabIndex == 1,
                    onClick = { tabIndex = 1 },
                    text = { Text("已下载/导入") },
                )
            }

            when (tabIndex) {
                0 -> {
                    val installedIds = uiState.allSchemas.map { it.schemaId }.toSet()
                    val visibleSchemas = uiState.allSchemas.filter {
                        com.kingzcheung.xime.settings.CyimeInputDefaults.visibleSchema(it.schemaId, installedIds)
                    }
                    val schemaById = visibleSchemas.associateBy { it.schemaId }
                    val fullOrder = remember(uiState.allSchemas, modeOrderRevision) {
                        orderedInstalledModeIds(context, uiState.allSchemas)
                    }
                    val enabledIds = uiState.enabledSchemas.toSet()
                    val savedEnabledIds = fullOrder.filter { id ->
                        (id == InputModes.ENGLISH && englishEnabled) || (id in enabledIds && id in schemaById)
                    }
                    val dragOrder = rememberDragOrder(savedEnabledIds) { reordered ->
                        if (reorderEnabledModes(context, uiState.allSchemas, enabledIds, englishEnabled, reordered)) modeOrderRevision++
                    }
                    val orderedEnabledIds = dragOrder.order
                    // 和词库、布局管理同一套：组名 + 连成一张卡片的行 + 卡片下的小字说明（不再用红字）。
                    LazyColumn(
                        state = dragOrder.list,
                        modifier = Modifier.settingsGutterScroll(dragOrder.list)
                            .weight(1f)
                            .padding(horizontal = 16.dp),
                    ) {
                        item {
                            SettingsGroupTitle("已启用的输入组合", Modifier.padding(top = 8.dp))
                        }

                        if (orderedEnabledIds.isEmpty()) {
                            item {
                                Text(
                                    text = "暂未启用任何方案",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                )
                            }
                        }

                        itemsIndexed(orderedEnabledIds, key = { _, id -> id }) { row, id ->
                            val isEnglish = id == InputModes.ENGLISH
                            val schema = if (isEnglish) SchemaMeta(id, "英文") else schemaById.getValue(id)
                            SchemaToggleItem(
                                schema = schema,
                                enabled = true,
                                isCompiled = isEnglish || SchemaManager.isSchemaCompiled(context, id),
                                isCurrent = !isEnglish && id == uiState.currentSchema,
                                onToggle = { if (!isEnglish) viewModel.toggleSchema(schema) },
                                onSelect = { if (!isEnglish) viewModel.selectSchema(schema) },
                                isBuiltIn = isEnglish,
                                isDragging = dragOrder.dragging == id,
                                isDropTarget = false,
                                reorderModifier = dragOrderItem(dragOrder, id, orderedEnabledIds.size > 1),
                                groupIndex = row,
                                groupSize = orderedEnabledIds.size,
                            )
                        }

                        item {
                            SettingsNote("每项是语言、输入方案与布局的一个组合。长按可排序，开关控制资源准备；语言和默认组合在「语言与方案」选择。",
                                Modifier.padding(top = 4.dp))
                            SettingsNote("九键、14键、26键和小鹤双拼共享雾凇词库；两种日语布局共享日语词库。词库随应用内置。安装或启停其他方案后，点击「应用方案更改」生效。日常打字不需要定期部署。")
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        val disabledSchemas = visibleSchemas.filter { it.schemaId !in uiState.enabledSchemas &&
                            (showExtraSchemas || it.schemaId in com.kingzcheung.xime.settings.CyimeInputDefaults.recommended) }
                        item {
                            TextButton(onClick = { showExtraSchemas = !showExtraSchemas }) {
                                Text(if (showExtraSchemas) "收起其他已安装方案" else "其他已安装方案（高级）")
                            }
                        }

                        if (disabledSchemas.isEmpty()) {
                            item {
                                Text(
                                    text = "没有其他可用方案",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                )
                            }
                        }

                        itemsIndexed(disabledSchemas, key = { _, schema -> schema.schemaId }) { row, schema ->
                            SchemaToggleItem(
                                schema = schema,
                                enabled = false,
                                isCompiled = SchemaManager.isSchemaCompiled(context, schema.schemaId),
                                isCurrent = false,
                                onToggle = { viewModel.toggleSchema(schema) },
                                onSelect = { viewModel.selectSchema(schema) },
                                groupIndex = row,
                                groupSize = disabledSchemas.size,
                            )
                        }
                        item { Spacer(Modifier.height(8.dp)) }
                    }

                    Button(
                        onClick = { viewModel.deploySchema() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        if (uiState.isDeploying) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text("应用方案更改")
                    }
                }

                1 -> {
                    val packages = localUiState.packages
                    if (packages.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                "暂无本地方案",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        val listState = rememberLazyListState()
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.settingsGutterScroll(listState)
                                .weight(1f)
                                .padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            item { Spacer(Modifier.height(4.dp)) }
                            items(packages, key = { it.packageId }) { item ->
                                LocalPackageItemCard(
                                    item = item,
                                    installing = localUiState.installingId == item.packageId,
                                    onInstall = { localViewModel.installPackage(item) },
                                    onDelete = { localViewModel.deleteDownloaded(item) },
                                    onUninstall = { localViewModel.uninstall(item) },
                                )
                            }
                            item {
                                Spacer(Modifier.height(16.dp))
                                Text(
                                    "安装后需切到「已安装」tab 点击「应用方案更改」生效",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(bottom = 16.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

internal fun orderedInstalledModeIds(context: Context, schemas: List<SchemaMeta>): List<String> =
    InputModes.ordered(context, schemas.map { schema ->
        schema.toSchemaInfo()
    }).map { it.schemaId }

/** Keep inactive modes in their saved slots while reordering the visible cards. */
internal fun reorderEnabledModes(context: Context, schemas: List<SchemaMeta>, enabledIds: Set<String>,
    englishEnabled: Boolean, reordered: List<String>): Boolean {
    val installedIds = schemas.map { it.schemaId }.toSet()
    val visibleIds = schemas.filter {
        com.kingzcheung.xime.settings.CyimeInputDefaults.visibleSchema(it.schemaId, installedIds)
    }.mapTo(mutableSetOf()) { it.schemaId }
    val fullOrder = orderedInstalledModeIds(context, schemas)
    val enabledOrder = fullOrder.filter { id ->
        (id == InputModes.ENGLISH && englishEnabled) || (id in enabledIds && id in visibleIds)
    }
    return InputModes.saveReorderedModes(context, fullOrder, enabledOrder, reordered)
}

@Composable
internal fun SchemaToggleItem(
    schema: SchemaMeta,
    enabled: Boolean,
    isCompiled: Boolean,
    isCurrent: Boolean,
    onToggle: () -> Unit,
    onSelect: () -> Unit,
    isBuiltIn: Boolean = false,
    reorderModifier: Modifier = Modifier,
    isDragging: Boolean = false,
    isDropTarget: Boolean = false,
    /** 在同一张卡片里的第几行、共几行（见 SettingsGroupRow）；单独一行时四角都圆。正拖着的那行单独浮起、四角圆。 */
    groupIndex: Int = 0,
    groupSize: Int = 1,
) {
    val radius = 12.dp
    val top = if (isDragging || groupIndex == 0) radius else 0.dp
    val bottom = if (isDragging || groupIndex >= groupSize - 1) radius else 0.dp
    val groupColor = MaterialTheme.colorScheme.surfaceContainerLow
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("schema-card:${schema.schemaId}")
            .then(reorderModifier)
            .then(if (isBuiltIn) Modifier else Modifier.clickable { if (enabled && isCompiled) onSelect() }),
        shape = RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom),
        colors = CardDefaults.cardColors(
            containerColor = if (isDragging)
                MaterialTheme.colorScheme.secondaryContainer
            else if (isDropTarget)
                MaterialTheme.colorScheme.primaryContainer
            else if (isCurrent)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f).compositeOver(groupColor)
            else
                groupColor
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isDragging) 6.dp else 0.dp),
    ) {
        if (groupIndex > 0 && !isDragging) SettingsRowDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = schema.toSchemaInfo().resourceLabel,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (enabled) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.outline
                    )
                    if (isCurrent && isCompiled) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "当前方案",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                val profile = schema.toSchemaInfo().profile
                Text("${profile.language.displayName} · ${profile.summary}",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                com.kingzcheung.xime.settings.CyimeInputDefaults.description(schema.schemaId)?.let { description ->
                    Text(description, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (isBuiltIn) {
                    Text("内置英文模式 · 始终开启", style = MaterialTheme.typography.labelSmall,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.outline)
                } else Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (schema.version.isNotEmpty()) {
                        Text(
                            text = "${schema.version}",
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    if (schema.author.isNotEmpty()) {
                        Text(
                            text = schema.author,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    if (isCompiled) {
                        Text(
                            text = "已编译",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Switch(
                checked = enabled,
                enabled = !isBuiltIn,
                onCheckedChange = { if (!isBuiltIn) onToggle() },
                modifier = Modifier.testTag("schema-toggle:${schema.schemaId}")
            )
        }
    }
}

@Composable
private fun LocalPackageItemCard(
    item: LocalPackageItem,
    installing: Boolean,
    onInstall: () -> Unit,
    onDelete: () -> Unit,
    onUninstall: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(8.dp))
                val tagColor = when {
                    item.installed -> MaterialTheme.colorScheme.primary
                    item.downloaded -> MaterialTheme.colorScheme.outline
                    else -> MaterialTheme.colorScheme.error
                }
                LocalPackageTag(
                    if (item.installed) "已安装" else if (item.downloaded) "已下载" else "未知",
                    tagColor,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "ID: ${item.packageId}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
            if (item.version.isNotEmpty() || item.fileCount > 0) {
                Spacer(Modifier.height(2.dp))
                Text(
                    buildString {
                        if (item.version.isNotEmpty()) append("版本: ${item.version}")
                        if (item.fileCount > 0) {
                            if (isNotEmpty()) append(" · ")
                            append("${item.fileCount} 个文件")
                        }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                when {
                    installing -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(6.dp))
                            Text("安装中…", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    item.packageId == "builtin" -> Text("随应用提供", style = MaterialTheme.typography.labelMedium)
                    item.installed -> {
                        OutlinedButton(onClick = onUninstall) { Text("卸载") }
                    }
                    item.downloaded -> OutlinedButton(onClick = onInstall) { Text("安装") }
                }
                Spacer(Modifier.weight(1f))
                if (item.packageId != "builtin" && item.downloaded) {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "删除下载文件",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LocalPackageTag(text: String, color: androidx.compose.ui.graphics.Color) {
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
