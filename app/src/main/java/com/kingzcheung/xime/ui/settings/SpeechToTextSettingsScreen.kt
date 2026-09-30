package com.kingzcheung.xime.ui.settings

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kingzcheung.xime.plugin.ExtensionManager
import com.kingzcheung.xime.plugin.core.api.PluginIcon
import com.kingzcheung.xime.plugin.core.model.PluginCategory
import com.kingzcheung.xime.plugin.core.api.AsrPlugin
import com.kingzcheung.xime.plugin.core.runtime.PluginManager
import com.kingzcheung.xime.speech.AsrBackendFactory
import com.kingzcheung.xime.settings.SettingsPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AsrProvider(
    val id: String,
    val name: String,
    val description: String,
    val iconRes: Int? = null,
    val icon: ImageVector? = null,
    val pluginIcon: PluginIcon? = null,
    val isOnline: Boolean,
    val isConfigured: Boolean,
    val isActive: Boolean = false,
    val features: List<String> = emptyList()
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeechToTextSettingsContent(
    onBack: () -> Unit,
    onNavigateToPluginSettings: (String) -> Unit = {},
    onNavigateToPlugins: () -> Unit = {},
    onNavigateToPluginMarket: () -> Unit = {}
) {
    val context = LocalContext.current

    var activeAsrPluginId by remember {
        mutableStateOf(SettingsPreferences.getSttOnlinePluginId(context))
    }

    var useLocal by remember {
        mutableStateOf(OfflineAsrSettings.isSupported() && SettingsPreferences.isSttUseLocal(context))
    }

    var keepEngineAlive by remember {
        mutableStateOf(SettingsPreferences.isSttKeepEngineAlive(context))
    }

    var providersRevision by remember { mutableStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        activeAsrPluginId = SettingsPreferences.getSttOnlinePluginId(context)
        providersRevision++
    }
    val onlineProviders = remember(activeAsrPluginId, providersRevision) {
        val installedAsr = ExtensionManager.getAllInstalledPlugins()
            .filter { it.category == PluginCategory.ASR }
        mutableStateListOf<AsrProvider>().apply {
            installedAsr.forEach { info ->
                val instance = PluginManager.getPluginInstance(info.id) as? AsrPlugin
                if (instance != null) {
                    val caps = instance.getCapabilities()
                    add(
                        AsrProvider(
                            id = info.id,
                            name = info.name,
                            description = "在线语音识别插件",
                            isOnline = true,
                            isConfigured = instance.isConfigured(),
                            isActive = info.id == activeAsrPluginId,
                            pluginIcon = ExtensionManager.extractPluginIcon(context, info.id, instance, info),
                            features = buildList {
                                add(if (caps.inputMode == "streaming") "实时流式" else "文件识别")
                                if (caps.supportsPartialResults) add("中间结果")
                                if (caps.requiresNetwork) add("在线")
                            }
                        )
                    )
                } else {
                    add(
                        AsrProvider(
                            id = info.id,
                            name = info.name,
                            description = info.description.ifBlank { "在线语音识别插件" },
                            isOnline = true,
                            isConfigured = false,
                            isActive = info.id == activeAsrPluginId,
                            features = listOf("在线")
                        )
                    )
                }
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("语音转文本", style = MaterialTheme.typography.titleMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis) },
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            val scope = rememberCoroutineScope()

            if (OfflineAsrSettings.isSupported()) {
                // 本地/在线引擎切换开关
                OfflineAsrSettings.EngineSelector(
                    useLocal = useLocal,
                    onUseLocalChange = {
                        useLocal = it
                        SettingsPreferences.setSttUseLocal(context, it)
                        if (it) {
                            // 打开本地识别：预热模型并常驻，避免语音时加载延迟丢开头音频
                            scope.launch(Dispatchers.IO) {
                                AsrBackendFactory.warmup(context)
                            }
                        } else {
                            // 关闭本地识别：卸载常驻模型
                            scope.launch(Dispatchers.IO) {
                                AsrBackendFactory.releaseModel()
                            }
                        }
                    }
                )

                if (useLocal) {
                    // 引擎常驻开关（仅本地模式显示/生效）：语音结束后保留引擎，闲置后再用免重建。
                    // 在线插件常驻需保持 WebSocket 长连接（耗电、占用服务端资源），不提供该选项。
                    // 保持引擎设置排在模型选择之前。
                    SettingsSection(title = "本地识别") {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "保持引擎常驻",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.weight(1f)
                                )
                                Switch(
                                    checked = keepEngineAlive,
                                    onCheckedChange = {
                                        keepEngineAlive = it
                                        SettingsPreferences.setSttKeepEngineAlive(context, it)
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = MaterialTheme.colorScheme.primary,
                                        checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                                    )
                                )
                            }
                            Text(
                                text = "语音结束后保留所选模型，减少下次加载等待；内存占用因模型而异，不保留麦克风录音。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // 本地模型下载与管理
                    OfflineAsrSettings.ModelSection()
                }
            }

            if (!useLocal) {
                OnlineAsrTab(
                    providers = onlineProviders,
                    onProviderClick = { provider ->
                        if (provider.isActive) {
                            onNavigateToPluginSettings(provider.id)
                            return@OnlineAsrTab
                        }
                        val wasConfigured = provider.isConfigured
                        scope.launch(Dispatchers.IO) {
                            // 单选激活：同一时间只能使用 1 个在线 ASR 插件
                            ExtensionManager.getAllInstalledPlugins()
                                .filter { it.category == PluginCategory.ASR && it.id != provider.id }
                                .forEach { SettingsPreferences.setPluginEnabled(context, it.id, false) }
                            SettingsPreferences.setSttOnlinePluginId(context, provider.id)
                            SettingsPreferences.setPluginEnabled(context, provider.id, true)
                            PluginManager.launchPlugin(provider.id)
                            activeAsrPluginId = provider.id
                            if (!wasConfigured) {
                                withContext(Dispatchers.Main) {
                                    onNavigateToPluginSettings(provider.id)
                                }
                            }
                        }
                    },
                    onManagePlugins = onNavigateToPlugins,
                    onInstallPlugins = onNavigateToPluginMarket,
                    onSettings = onNavigateToPluginSettings
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
fun OnlineAsrTab(
    providers: List<AsrProvider>,
    onProviderClick: (AsrProvider) -> Unit,
    onManagePlugins: () -> Unit = {},
    onInstallPlugins: () -> Unit = {},
    onSettings: (String) -> Unit = {}
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        PluginSetupCard("在线语音", onInstallPlugins, onManagePlugins)

        SettingsSection(title = "在线服务") {
            if (providers.isEmpty()) {
                Text(
                    text = "尚未安装在线语音插件；也可以返回上方选择本地模型。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            } else {
                providers.forEachIndexed { index, provider ->
                    if (index > 0) {
                        androidx.compose.material3.HorizontalDivider(
                            modifier = Modifier.padding(start = 72.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    }
                    AsrProviderCardModern(
                        provider = provider,
                        onClick = { onProviderClick(provider) },
                        onSettings = { onSettings(provider.id) }
                    )
                }
            }
        }

        Text(
            text = "在线服务需要网络连接，可能需要账号或 API 密钥；费用和数据处理方式以服务提供方为准。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun AsrProviderCardModern(
    provider: AsrProvider,
    onClick: () -> Unit,
    onSettings: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                if (provider.iconRes != null) {
                    Icon(
                        painter = painterResource(provider.iconRes),
                        contentDescription = null,
                        modifier = Modifier.size(32.dp)
                    )
                } else if (provider.icon != null) {
                    Icon(
                        imageVector = provider.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    PluginIconView(
                        icon = provider.pluginIcon,
                        category = PluginCategory.ASR,
                        modifier = Modifier.size(36.dp),
                        showBackground = false
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = provider.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = provider.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = when {
                        provider.isActive -> "当前使用中"
                        provider.isConfigured -> "已配置"
                        else -> "未配置"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (provider.isActive) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (provider.isActive || provider.isConfigured) {
                IconButton(onClick = onSettings) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "设置",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        if (provider.features.isNotEmpty()) {
            Text(
                text = provider.features.joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 56.dp, top = 4.dp)
            )
        }
    }
}
