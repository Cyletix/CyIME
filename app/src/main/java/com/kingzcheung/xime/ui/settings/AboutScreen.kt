package com.kingzcheung.xime.ui.settings

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.twotone.OpenInNew
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.twotone.BugReport
import androidx.compose.material.icons.twotone.Code
import androidx.compose.material.icons.twotone.Description
import androidx.compose.material.icons.twotone.PersonOutline
import androidx.compose.material.icons.twotone.PrivacyTip
import androidx.compose.material.icons.twotone.Storage
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kingzcheung.xime.BuildConfig
import com.kingzcheung.xime.settings.SettingsPreferences
import com.kingzcheung.xime.util.FileLogger

data class LicenseItem(
    val name: String,
    val license: String,
    val url: String
)

object AppInfo {
    val versionName: String = BuildConfig.VERSION_NAME
    val versionCode: Int = BuildConfig.VERSION_CODE
    val gitHash: String = BuildConfig.GIT_HASH
    
    val androidVersion: String = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
    val deviceModel: String = "${Build.MANUFACTURER} ${Build.MODEL}"
    
    val dependencies = listOf(
        LicenseItem(
            name = "Xime（上游，原名曦码）",
            license = "GPL-3.0",
            url = "https://github.com/ximeiorg/Xime"
        ),
        LicenseItem(
            name = "librime",
            license = "BSD-3-Clause",
            url = "https://github.com/rime/librime"
        ),
        LicenseItem(
            name = "AndroidX Core",
            license = "Apache-2.0",
            url = "https://developer.android.com/jetpack/androidx"
        ),
        LicenseItem(
            name = "Jetpack Compose",
            license = "Apache-2.0",
            url = "https://developer.android.com/jetpack/compose"
        ),
        LicenseItem(
            name = "Material Design Icons",
            license = "Apache-2.0",
            url = "https://fonts.google.com/icons"
        ),
        LicenseItem(
            name = "Kotlin",
            license = "Apache-2.0",
            url = "https://kotlinlang.org"
        ),
        LicenseItem(
            name = "Navigation Compose",
            license = "Apache-2.0",
            url = "https://developer.android.com/jetpack/compose/navigation"
        ),
        LicenseItem(
            name = "Lifecycle",
            license = "Apache-2.0",
            url = "https://developer.android.com/jetpack/androidx/releases/lifecycle"
        ),
        LicenseItem(
            name = "Boost",
            license = "BSL-1.0",
            url = "https://www.boost.org"
        ),
        LicenseItem(
            name = "OpenCC (Open Chinese Convert)",
            license = "Apache-2.0",
            url = "https://github.com/BYVoid/OpenCC"
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutContent(
    onBack: () -> Unit,
    onNavigateToPrivacy: () -> Unit,
    onNavigateToLicenses: () -> Unit,
    onNavigateToLogViewer: () -> Unit = {},
    onNavigateToHandwritingCapture: () -> Unit = {},
    onNavigateToStorageSpace: () -> Unit = {},
) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    var verboseLoggingEnabled by remember {
        mutableStateOf(SettingsPreferences.isVerboseLoggingEnabled(context))
    }
    // 彩蛋入口：1.5 秒内连点"设备信息"卡片 7 次解锁"手写数据采集"（平时隐藏）
    var captureTapCount by remember { mutableStateOf(0) }
    var lastCaptureTapMs by remember { mutableStateOf(0L) }
    var captureUnlocked by rememberSaveable { mutableStateOf(false) }
    fun onDeviceInfoTapped() {
        val now = System.currentTimeMillis()
        if (now - lastCaptureTapMs > 1500L) captureTapCount = 0
        lastCaptureTapMs = now
        captureTapCount++
        if (captureTapCount >= 7) {
            captureTapCount = 0
            captureUnlocked = true
            android.widget.Toast.makeText(context, "已解锁手写数据采集入口", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("关于") },
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
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // App 信息
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "CyIME",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "由 Cyletix 维护，基于 Xime / Rime 的 Android 输入法。遵循 GPL v3，保留上游及第三方版权声明。",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "版本 ${AppInfo.versionName} (${AppInfo.versionCode})",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "构建: ${AppInfo.gitHash}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { uriHandler.openUri("https://github.com/Cyletix/Xime/releases") },
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "查看最新版本",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
            
            // 作者信息
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "维护者与上游作者",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Cyletix · Fork 维护者",
                            modifier = Modifier.clickable { uriHandler.openUri("https://github.com/Cyletix") },
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 14.sp,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { uriHandler.openUri("https://github.com/kingzcheung") },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.TwoTone.PersonOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Kor1 (kingzcheung) · 上游作者",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "github.com/kingzcheung",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.TwoTone.OpenInNew,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
            
            // 源代码
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            uriHandler.openUri("https://github.com/Cyletix/Xime")
                        },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.TwoTone.Code,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "源代码",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "github.com/Cyletix/Xime",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            
            // 链接项
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column {
                        SettingsItem(
                            icon = Icons.TwoTone.PrivacyTip,
                            title = "隐私策略",
                            onClick = onNavigateToPrivacy
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 72.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                        SettingsItem(
                            icon = Icons.TwoTone.Description,
                            title = "开源许可证",
                            onClick = onNavigateToLicenses
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 72.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                        SettingsItem(
                            icon = Icons.TwoTone.BugReport,
                            title = "日志查看器",
                            onClick = onNavigateToLogViewer
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 72.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                        SettingsItem(
                            icon = Icons.TwoTone.Storage,
                            title = "存储空间",
                            onClick = onNavigateToStorageSpace
                        )
                        if (captureUnlocked) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 72.dp),
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                            SettingsItem(
                                icon = Icons.Default.Edit,
                                title = "手写数据采集",
                                onClick = onNavigateToHandwritingCapture
                            )
                        }
                        if (BuildConfig.DEBUG) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 72.dp),
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                            SettingsItem(
                                icon = Icons.TwoTone.BugReport,
                                title = "调试日志",
                                onClick = {},
                                trailing = {
                                    Switch(
                                        checked = verboseLoggingEnabled,
                                        onCheckedChange = { enabled ->
                                            verboseLoggingEnabled = enabled
                                            SettingsPreferences.setVerboseLoggingEnabled(context, enabled)
                                            FileLogger.setVerboseLoggingEnabled(enabled)
                                        }
                                    )
                                }
                            )
                        }
                    }
                }
            }
            
            // 设备信息
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onDeviceInfoTapped() }
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "设备信息",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        InfoRow(label = "系统版本", value = AppInfo.androidVersion)
                        InfoRow(label = "设备型号", value = AppInfo.deviceModel)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = title,
            fontSize = 16.sp,
            modifier = Modifier.weight(1f)
        )
        if (trailing != null) {
            trailing()
        } else {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyContent(
    onBack: () -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("隐私策略") },
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
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "CyIME隐私策略",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        PrivacySection(
                            title = "数据收集",
                            content = "CyIME 默认使用本地输入与识别。主动启用在线语音或其他联网服务时，相应音频或文本会发送给您选择的服务商。"
                        )
                        
                        PrivacySection(
                            title = "本地存储",
                            content = "CyIME 默认在本地存储设置、词库和剪贴板历史。主动启用云备份或剪贴板同步插件时，所选数据会按插件配置传输。您可以随时清除本地数据。"
                        )
                        
                        PrivacySection(
                            title = "网络权限",
                            content = "网络权限用于下载输入方案和模型，以及您主动启用的在线服务。本地 Rime、手写和离线语音模型在设备上运行。"
                        )
                        
                        PrivacySection(
                            title = "输入内容",
                            content = "本地输入由开源 Rime 引擎处理。在线语音等可选服务的数据处理规则取决于所选服务商，请查看其隐私政策。"
                        )
                        
                        PrivacySection(
                            title = "剪贴板",
                            content = "剪贴板历史默认保存在本地；同步功能由您启用的插件提供。您可以随时查看、删除或清除本地记录。"
                        )
                        
                        PrivacySection(
                            title = "开源",
                            content = "CyIME是开源软件，源代码公开可审计。您可以在 GitHub 上查看完整源代码。"
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "更新日期：2026年9月23日",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PrivacySection(title: String, content: String) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(
            text = title,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = content,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface,
            lineHeight = 20.sp
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicensesContent(
    onBack: () -> Unit
) {
    val uriHandler = LocalUriHandler.current
    
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("开源许可证") },
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
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(AppInfo.dependencies) { item ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            uriHandler.openUri(item.url)
                        },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = item.name,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = item.license,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}