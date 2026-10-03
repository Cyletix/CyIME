package com.kingzcheung.xime.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.kingzcheung.xime.settings.*
import com.kingzcheung.xime.ui.menubar.rememberDragOrder
import com.kingzcheung.xime.ui.menubar.dragOrderItem
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import com.kingzcheung.xime.ui.theme.MaterialLevel
import com.kingzcheung.xime.ui.theme.VisualStyles
import com.kingzcheung.xime.ui.theme.visualMaterial
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageSettingsContent(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var languages by remember { mutableStateOf(LanguagePreferences.enabled(context)) }
    var orderedLanguages by remember { mutableStateOf(InputModes.languageOrder(context)) }
    var saving by remember { mutableStateOf(false) }
    var expandedLanguage by remember { mutableStateOf<InputLanguage?>(orderedLanguages.firstOrNull()) }
    var message by remember { mutableStateOf("") }
    val profilesModel: com.kingzcheung.xime.viewmodel.SchemaSettingsViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val profilesState by profilesModel.uiState.collectAsState()
    var remembered by remember { mutableStateOf(InputModes.rememberedModes(context)) }
    val availableProfiles = InputModes.ordered(context, profilesState.allSchemas
        .filter { it.schemaId in profilesState.enabledSchemas }.map { it.toSchemaInfo() })
    val dragOrder = rememberDragOrder(orderedLanguages.map { it.id }) { ids ->
        InputModes.saveLanguageOrder(context, ids)
        orderedLanguages = InputModes.languageOrder(context)
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        languages = LanguagePreferences.enabled(context)
        orderedLanguages = InputModes.languageOrder(context)
        profilesModel.refresh()
        remembered = InputModes.rememberedModes(context)
    }
    Scaffold(modifier = Modifier.testTag("language-settings"), containerColor = MaterialTheme.colorScheme.surface,
        topBar = { TopAppBar(title = { Text("语言管理") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
        }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)) }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().testTag("language-settings-list").padding(padding)
            .padding(horizontal = 16.dp, vertical = 8.dp),
            state = dragOrder.list, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { Text("点开语言，分别设置输入方案和键盘布局；下次切换到该语言时使用。长按可调整语言顺序。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(dragOrder.order + InputLanguage.supported.filterNot { it in languages }.map { it.id }, key = { it }) { id ->
                val language = InputLanguage.entries.first { it.id == id }
                val checked = language in languages
                val canChange = language != InputLanguage.ENGLISH && !saving
                val selected = InputProfileSelection.preferred(availableProfiles, language,
                    remembered[language], profilesState.currentSchema)
                val expanded = checked && expandedLanguage == language
                Surface(modifier = Modifier.fillMaxWidth().testTag("language-drag:${language.id}")
                    .then(dragOrderItem(dragOrder, id, checked && orderedLanguages.size > 1 && !saving)),
                    shape = RoundedCornerShape(12.dp),
                    border = if (dragOrder.dragging == id) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                    color = if (dragOrder.dragging == id) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerLow) {
                    Column(Modifier.fillMaxWidth().testTag("language-${language.id}")
                        .visualMaterial(VisualStyles.current, 12.dp, level = MaterialLevel.RAISED)) {
                        Row(Modifier.fillMaxWidth().heightIn(min = 72.dp)
                            .clickable(enabled = checked) { expandedLanguage = if (expanded) null else language }
                            .padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                                Text(language.displayName, style = MaterialTheme.typography.titleMedium)
                                Text(if (!checked) "未启用" else selected?.profile?.summary ?: "正在准备可用组合",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (checked) IconButton(onClick = { expandedLanguage = if (expanded) null else language },
                                modifier = Modifier.testTag("language-expand:${language.id}")) {
                                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    if (expanded) "收起${language.displayName}设置" else "展开${language.displayName}设置")
                            }
                            Switch(checked, enabled = canChange, modifier = Modifier.testTag("language-toggle:${language.id}"),
                                onCheckedChange = { enabled ->
                                scope.launch {
                                    saving = true
                                    try {
                                        withContext(Dispatchers.IO) { LanguagePreferences.save(context, language, enabled) }
                                        languages = LanguagePreferences.enabled(context)
                                        orderedLanguages = InputModes.languageOrder(context)
                                        profilesModel.refresh()
                                        if (enabled) expandedLanguage = language
                                        message = if (enabled) "已开启，输入方案会在后台自动准备" else "已关闭，已下载词库保留"
                                    } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                                    catch (error: Exception) { message = error.message ?: "保存失败，请稍后再试" }
                                    finally { saving = false }
                                }
                            })
                        }
                        if (expanded && selected != null) InputProfileSelectors(availableProfiles, selected,
                            Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) { entry ->
                            InputModes.selectProfile(context, entry)
                            remembered = remembered + (language to entry.schemaId)
                            message = "已保存：${language.displayName} · ${entry.profile.summary}，下次切换到该语言时使用"
                        }
                    }
                }
            }
            item(key = "language-key-switch") {
                LanguageKeySwitchSettings(availableProfiles, orderedLanguages, profilesState.currentSchema)
            }
            item { if (message.isNotEmpty()) Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}
