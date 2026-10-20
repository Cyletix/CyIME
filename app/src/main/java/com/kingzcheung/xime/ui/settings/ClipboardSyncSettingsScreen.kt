package com.kingzcheung.xime.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.RadioButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
    // 当前选中的插件实例（切换时在 IO 协程里 launch 完成后更新，驱动表单立即渲染新插件）；
    // 偏好未设置或不可用时显示第一个已启用的插件，与服务端实际使用的一致
    var activePlugin by remember {
        mutableStateOf(effectiveClipboardSyncPlugin(syncPlugins, SettingsPreferences.getClipboardSyncPluginId(context)))
    }
    var selectedPluginId by remember { mutableStateOf(activePlugin?.first.orEmpty()) }
    var installedPlugins by remember { mutableStateOf(ExtensionManager.getAllInstalledPlugins()) }
    val clipboardPlugins = remember(installedPlugins) { installedPlugins.filter { it.category == PluginCategory.CLIPBOARD_SYNC } }

    var selecting by remember { mutableStateOf(false) }
    var advanced by remember { mutableStateOf(false) }
    var selectionError by remember { mutableStateOf<String?>(null) }
    val pluginInstances by PluginManager.pluginInstancesFlow.collectAsState()
    val windowsPeer by com.kingzcheung.xime.clipboard.sync.WindowsDevices.paired.collectAsState()
    DisposableEffect(context) {
        val prefs = SettingsPreferences.getPrefsPublic(context)
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            enabled = SettingsPreferences.isClipboardSyncEnabled(context)
            val available = ExtensionManager.getEnabledClipboardSyncPlugins(context)
            activePlugin = effectiveClipboardSyncPlugin(available, SettingsPreferences.getClipboardSyncPluginId(context))
            selectedPluginId = activePlugin?.first.orEmpty()
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    LaunchedEffect(pluginInstances, windowsPeer) {
        enabled = SettingsPreferences.isClipboardSyncEnabled(context)
        installedPlugins = ExtensionManager.getAllInstalledPlugins()
        val available = ExtensionManager.getEnabledClipboardSyncPlugins(context)
        activePlugin = effectiveClipboardSyncPlugin(available, SettingsPreferences.getClipboardSyncPluginId(context))
        selectedPluginId = activePlugin?.first.orEmpty()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        installedPlugins = ExtensionManager.getAllInstalledPlugins()
        val available = ExtensionManager.getEnabledClipboardSyncPlugins(context)
        selectedPluginId = SettingsPreferences.getClipboardSyncPluginId(context)
        activePlugin = available.firstOrNull { it.first == selectedPluginId }
        enabled = SettingsPreferences.isClipboardSyncEnabled(context)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("剪贴板与同步") },
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
            WindowsDevicesPanel()
            androidx.compose.material3.TextButton(onClick = { advanced = !advanced }) { Text(if (advanced) "收起手动连接与其他服务" else "备用：手动连接与其他服务") }
            if (advanced) {
            Text("连接 Windows 优先使用上方的“扫码连接电脑”。下方手动填写仅供旧版兼容或其他同步服务使用。",
                style = MaterialTheme.typography.bodyMedium)
            SettingsSection(title = "同步服务", content = {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    if (clipboardPlugins.isEmpty()) {
                        Text("正在准备内置同步服务；若持续未出现，请重新打开应用并在插件管理中检查状态。")
                    }
                    clipboardPlugins.forEach { plugin ->
                        val isActive = plugin.id == selectedPluginId
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
            activePlugin?.takeUnless { it.first == com.kingzcheung.xime.clipboard.sync.WindowsDevices.ID }?.let { selected ->
                PluginConfigFormScreen(
                    pluginId = selected.first,
                    plugin = selected.second,
                    pluginName = installedPlugins.find { it.id == selected.first }?.name ?: selected.first,
                    onBack = {}, embedded = true
                )
            }
            Button(onClick = onNavigateToPlugins) { Text("管理其他同步服务") }
            if (activePlugin != null && activePlugin?.first != com.kingzcheung.xime.clipboard.sync.WindowsDevices.ID) SettingsSection(title = "同步开关", content = {
                SettingsToggleItem(
                    icon = Icons.TwoTone.Sync,
                    title = "启用所选兼容服务",
                    subtitle = "先选择服务并完成配置，再开启双向同步。开启后会向该服务传输剪贴板文本。",
                    checked = enabled,
                    onCheckedChange = { checked ->
                        if (checked && activePlugin == null) {
                            selectionError = "请先选择同步服务并完成连接配置。"
                        } else {
                            enabled = checked
                            SettingsPreferences.setClipboardSyncEnabled(context, checked)
                        }
                    }
                )
            })
            }
            // 验证码自动复制是首页这一项的主要用途之一，直接显示，不再折叠。
            VerificationCodeSettings()
            ClipboardCardLinesSetting()
        }
    }
}

/**
 * 实际在用的同步插件：与输入法服务相同，偏好的插件不可用（或没选）时用第一个已启用的。
 * 设置页按它显示，免得页面写“未选”而后台其实在用第一个。
 */
internal fun <T> effectiveClipboardSyncPlugin(available: List<Pair<String, T>>, preferredId: String): Pair<String, T>? =
    available.firstOrNull { it.first == preferredId } ?: available.firstOrNull()

/** 剪贴板面板里每张卡片最多显示几行文字（2 / 4 / 8，默认 4）；单选样式和本页「同步服务」一样。 */
@Composable
private fun ClipboardCardLinesSetting() {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("clipboard_board", android.content.Context.MODE_PRIVATE) }
    val key = com.kingzcheung.xime.ui.menubar.CLIPBOARD_CARD_MAX_LINES_KEY
    var lines by remember { mutableStateOf(prefs.getInt(key, 4)) }
    SettingsSection(title = "剪贴板面板", content = {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text("剪贴板卡片最多显示", style = MaterialTheme.typography.titleSmall)
            Text("每条记录最多显示几行文字", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            listOf(2, 4, 8).forEach { option ->
                Row(Modifier.fillMaxWidth().testTag("clipboard-card-lines:$option")
                    .selectable(lines == option, role = androidx.compose.ui.semantics.Role.RadioButton) {
                        lines = option
                        prefs.edit().putInt(key, option).apply()
                    }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = lines == option, onClick = null)
                    Spacer(Modifier.width(12.dp))
                    Text("$option 行", style = MaterialTheme.typography.titleSmall)
                }
            }
        }
    })
}
