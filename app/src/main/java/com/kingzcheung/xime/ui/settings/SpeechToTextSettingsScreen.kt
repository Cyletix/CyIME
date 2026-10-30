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
import androidx.compose.material.icons.twotone.GraphicEq
import androidx.compose.material.icons.twotone.Tune
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import com.kingzcheung.xime.ui.caption.CaptionLauncherIcon
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
    onNavigateToSetupGuide: () -> Unit = {},
) {
    val context = LocalContext.current

    var activeAsrPluginId by remember {
        mutableStateOf(SettingsPreferences.getSttOnlinePluginId(context))
    }

    var useLocal by remember {
        mutableStateOf(OfflineAsrSettings.isSupported() && SettingsPreferences.isSttUseLocal(context))
    }

    var saveTuningAudio by remember {
        mutableStateOf(SettingsPreferences.isSttDebugRecord(context))
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

            // 语音总开关放在页内最上面（以前挂在首页那一行上，进来反而找不到）。
            var sttEnabled by remember { mutableStateOf(SettingsPreferences.isSttEnabled(context)) }
            SettingsSection(title = "语音输入") {
                SettingsToggleItem(icon = Icons.TwoTone.GraphicEq, title = "语音转文本",
                    subtitle = "长按空格或点工具栏麦克风说话，转成文字上屏",
                    checked = sttEnabled, onCheckedChange = { sttEnabled = it; SettingsPreferences.setSttEnabled(context, it) })
            }

            // 模型名（Paraformer、SenseVoice…）多数人看不懂：先给一个按手机和用法自动选好的入口。
            SettingsSection(title = "不知道怎么选") {
                SettingsItem(icon = Icons.TwoTone.Tune, title = "按设备推荐配置",
                    subtitle = "回答几个问题，自动选好语音模型，删掉用不到的",
                    onClick = onNavigateToSetupGuide, showArrow = true)
            }

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
                    // 模型常驻与空闲释放统一在「性能与省电」设置，这里不再重复（两处会互相覆盖）。
                    SettingsSection(title = "本地识别") {
                        Text(
                            text = "语音模型是否常驻、空闲多久释放，在「性能与省电」中设置。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)
                        )
                        Column(Modifier.settingsSearchAnchor("保存语音调优录音").fillMaxWidth().padding(16.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "保存语音调优录音",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.weight(1f)
                                )
                                Switch(
                                    checked = saveTuningAudio,
                                    onCheckedChange = {
                                        saveTuningAudio = it
                                        SettingsPreferences.setSttDebugRecord(context, it)
                                    },
                                )
                            }
                            Text(
                                text = "每次本地语音的录音和识别过程只存在本机应用目录，用于连接电脑后回放分析、调整断句和模型；最多保留 300 段或 500 MB，不会上传。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // 本地模型下载与管理
                    OfflineAsrSettings.ModelSection()
                }
            }

            SettingsSection(title = "字幕") {
                var captionIcon by remember { mutableStateOf(CaptionLauncherIcon.isEnabled(context)) }
                Column(Modifier.settingsSearchAnchor("桌面显示“字幕”图标").fillMaxWidth().padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "桌面显示“字幕”图标",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = captionIcon,
                            onCheckedChange = {
                                captionIcon = it
                                CaptionLauncherIcon.setEnabled(context, it)
                            },
                        )
                    }
                    Text(
                        text = "打开后确认添加到桌面，点一下直接进字幕页；图标颜色跟随键盘主题。关闭后桌面图标变灰，可长按移除；也可从下拉快捷开关或键盘菜单的“字幕”进入。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                var captionAutoSave by remember { mutableStateOf(com.kingzcheung.xime.ui.caption.CaptionAutoSave.isOn(context)) }
                Column(Modifier.settingsSearchAnchor("自动保存字幕").fillMaxWidth().padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "自动保存字幕",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = captionAutoSave,
                            onCheckedChange = {
                                captionAutoSave = it
                                com.kingzcheung.xime.ui.caption.CaptionAutoSave.setOn(context, it)
                            },
                        )
                    }
                    Text(
                        text = "从打开字幕到按“退出”算一次，每次存成一个文件，边听边更新，在“下载/CyIME字幕”里。关闭后只在点“保存”时存。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                var keepThreeDays by remember { mutableStateOf(com.kingzcheung.xime.ui.caption.CaptionAutoSave.keepsThreeDays(context)) }
                Column(Modifier.settingsSearchAnchor("字幕只保留 3 天").fillMaxWidth().padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "字幕只保留 3 天",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = keepThreeDays,
                            onCheckedChange = {
                                keepThreeDays = it
                                com.kingzcheung.xime.ui.caption.CaptionAutoSave.setKeepsThreeDays(context, it)
                            },
                        )
                    }
                    Text(
                        text = "和 Google 实时转写一样，“下载/CyIME字幕”里超过 3 天的字幕记录在下次打开字幕时自动删掉。关闭后一直保留。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                var nameAlert by remember { mutableStateOf(com.kingzcheung.xime.ui.caption.CaptionNameAlert.isOn(context)) }
                var alertNames by remember { mutableStateOf(com.kingzcheung.xime.ui.caption.CaptionNameAlert.names(context)) }
                Column(Modifier.settingsSearchAnchor("有人叫我名字时振动").fillMaxWidth().padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "有人叫我名字时振动",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = nameAlert,
                            onCheckedChange = {
                                nameAlert = it
                                com.kingzcheung.xime.ui.caption.CaptionNameAlert.setOn(context, it)
                            },
                        )
                    }
                    Text(
                        text = "字幕里别人说的话出现下面的名字时，手机振动三下。几个叫法用空格隔开，比如“老王 王叔 爷爷”。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (nameAlert) androidx.compose.material3.OutlinedTextField(
                        value = alertNames,
                        onValueChange = {
                            alertNames = it
                            com.kingzcheung.xime.ui.caption.CaptionNameAlert.setNames(context, it)
                        },
                        label = { Text("我的名字") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
                var captionFloat by remember { mutableStateOf(com.kingzcheung.xime.ui.caption.CaptionFloat.isOn(context)) }
                val callCaptionOn = remember(captionFloat) { com.kingzcheung.xime.ui.caption.CallCaptionService.isEnabled(context) }
                Column(Modifier.settingsSearchAnchor("后台浮窗").fillMaxWidth().padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "后台浮窗",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = captionFloat,
                            onCheckedChange = {
                                captionFloat = it
                                com.kingzcheung.xime.ui.caption.CaptionFloat.setOn(context, it)
                                // The bar lives in the call caption service; without it there is nothing to float in.
                                if (it && !com.kingzcheung.xime.ui.caption.CallCaptionService.isEnabled(context)) runCatching {
                                    context.startActivity(android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                                }
                            },
                        )
                    }
                    Text(
                        text = "离开字幕页（回桌面或切到别的应用）后，字幕继续在屏幕底部显示，点一下回到字幕页；按“退出”才停止。打电话时只显示通话字幕，挂断后接着显示。" +
                            if (callCaptionOn) "" else "需要先在系统无障碍里打开“通话字幕”。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
    onSettings: (String) -> Unit = {}
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
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
