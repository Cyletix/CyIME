package com.kingzcheung.xime.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.twotone.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kingzcheung.xime.plugin.ExtensionManager
import com.kingzcheung.xime.plugin.core.model.PluginCategory
import com.kingzcheung.xime.settings.SettingsPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.kingzcheung.xime.plugin.core.runtime.PluginManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClipboardSyncSettingsContent(
    onBack: () -> Unit,
    onNavigateToPlugins: () -> Unit,
    onNavigateToPluginMarket: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var enabled by remember {
        mutableStateOf(SettingsPreferences.isClipboardSyncEnabled(context))
    }
    val syncPlugins = remember { ExtensionManager.getEnabledClipboardSyncPlugins(context) }
    // 偏好未设置时，自动选中第一个已启用的插件（与服务端 fallback 一致）
    var selectedPluginId by remember {
        mutableStateOf(
            SettingsPreferences.getClipboardSyncPluginId(context).ifEmpty {
                syncPlugins.firstOrNull()?.first ?: ""
            }
        )
    }
    // 当前选中的插件实例（切换时在 IO 协程里 launch 完成后更新，驱动表单立即渲染新插件）
    var activePlugin by remember {
        mutableStateOf(
            syncPlugins.firstOrNull { it.first == selectedPluginId } ?: syncPlugins.firstOrNull()
        )
    }
    var installedPlugins by remember { mutableStateOf(ExtensionManager.getAllInstalledPlugins()) }
    val clipboardPlugins = remember(installedPlugins) { installedPlugins.filter { it.category == PluginCategory.CLIPBOARD_SYNC } }

    var selecting by remember { mutableStateOf(false) }
    var selectionError by remember { mutableStateOf<String?>(null) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        installedPlugins = ExtensionManager.getAllInstalledPlugins()
        val available = ExtensionManager.getEnabledClipboardSyncPlugins(context)
        selectedPluginId = SettingsPreferences.getClipboardSyncPluginId(context)
        activePlugin = available.firstOrNull { it.first == selectedPluginId } ?: available.firstOrNull()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("剪贴板同步") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .imePadding()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            PluginSetupCard("剪贴板同步", onNavigateToPluginMarket, onNavigateToPlugins)
            SettingsSection(title = "同步服务", content = {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    if (clipboardPlugins.isEmpty()) {
                        Text("尚未安装剪贴板同步插件，请从上方安装。")
                    }
                    clipboardPlugins.forEach { plugin ->
                        val isActive = plugin.id == activePlugin?.first
                        PluginServiceChoice(plugin.name, plugin.description, isActive, !selecting) {
                            if (!isActive) {
                                selecting = true
                                selectionError = null
                                scope.launch {
                                    try {
                                        val instance = withContext(Dispatchers.IO) {
                                            clipboardPlugins.filter { it.id != plugin.id }.forEach {
                                                SettingsPreferences.setPluginEnabled(context, it.id, false)
                                                PluginManager.unloadPlugin(it.id)
                                            }
                                            SettingsPreferences.setClipboardSyncPluginId(context, plugin.id)
                                            SettingsPreferences.setPluginEnabled(context, plugin.id, true)
                                            PluginManager.launchPlugin(plugin.id)
                                            ExtensionManager.getEnabledClipboardSyncPlugins(context).firstOrNull { it.first == plugin.id }
                                        }
                                        selectedPluginId = plugin.id
                                        activePlugin = instance
                                        if (instance == null) selectionError = "插件未能启动，请在已安装插件中检查状态。"
                                    } catch (error: Exception) {
                                        activePlugin = null
                                        selectionError = "无法启用服务：${error.message}"
                                    } finally { selecting = false }
                                }
                            }
                        }
                    }
                    selectionError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            })
            activePlugin?.let { selected ->
                PluginConfigFormScreen(
                    pluginId = selected.first,
                    plugin = selected.second,
                    pluginName = installedPlugins.find { it.id == selected.first }?.name ?: selected.first,
                    onBack = {}, embedded = true
                )
            }
            SettingsSection(title = "同步开关", content = {
                SettingsToggleItem(
                    icon = Icons.TwoTone.Sync,
                    title = "启用剪贴板同步",
                    subtitle = "先选择服务并完成配置，再开启双向同步。开启后会向该服务传输剪贴板文本。",
                    checked = enabled,
                    onCheckedChange = { checked ->
                        if (checked && activePlugin == null) {
                            selectionError = "请先安装并选择同步服务，再完成连接配置。"
                        } else {
                            enabled = checked
                            SettingsPreferences.setClipboardSyncEnabled(context, checked)
                        }
                    }
                )
            })
        }
    }
}
