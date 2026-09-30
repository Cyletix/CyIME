package com.kingzcheung.xime.ui.settings

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kingzcheung.xime.ui.theme.*
import com.kingzcheung.xime.util.FileLogger

internal fun changeAppearance(context: Context, onChanged: () -> Unit, change: () -> Unit) {
    try { change(); onChanged() } catch (e: RuntimeException) {
        FileLogger.e("Appearance", "Appearance change failed", e)
        Toast.makeText(context, "图标切换失败，未保存此次选择，请重试", Toast.LENGTH_LONG).show()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun IconSettingsContent(onBack: () -> Unit, onChanged: () -> Unit) {
    val context = LocalContext.current
    BackHandler(onBack = onBack)
    Scaffold(containerColor = MaterialTheme.colorScheme.surface, topBar = {
        TopAppBar(title = { Text("图标设置") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
        }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface))
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SettingsSection(title = "图标联动") {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("同步图标与视觉样式", style = MaterialTheme.typography.bodyLarge)
                            Text(if (IconAppearance.linked) "任一处选择，键盘和图标一起变化" else "图标与键盘分别设置",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = IconAppearance.linked, onCheckedChange = {
                            changeAppearance(context, onChanged) { IconAppearance.setLinked(context, it) }
                        }, modifier = Modifier.testTag("icon-link"))
                    }
                    Text("桌面图标使用所选样式；键盘左上角使用简洁线稿，颜色跟随键盘主题。桌面可能需要片刻刷新。",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            SettingsSection(title = "图标样式") {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    VisualStyle.entries.filter { it != VisualStyle.ORIGINAL }.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            row.forEach { style ->
                                val selected = IconAppearance.effective == style
                                val palette = MaterialTheme.colorScheme
                                val shape = RoundedCornerShape(12.dp)
                                Column(Modifier.weight(1f).testTag("icon-style-${style.id}").semantics { this.selected = selected }
                                    .background(palette.surfaceContainerHigh, shape)
                                    .border(if (selected) 2.dp else 1.dp, palette.primary.copy(alpha = if (selected) 1f else .25f), shape)
                                    .clickable { changeAppearance(context, onChanged) { IconAppearance.setIconStyle(context, style) } }
                                    .padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    CyimeGeneratedIcon(style, Modifier.fillMaxWidth().aspectRatio(1f), background = true)
                                    Text(style.title + if (selected) " · 已选" else "", color = palette.onSurface,
                                        style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                    }
                    TextButton(onClick = { changeAppearance(context, onChanged) { IconAppearance.setIconStyle(context, VisualStyle.ORIGINAL) } },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("icon-style-original")) {
                        Text(if (IconAppearance.effective == VisualStyle.ORIGINAL) "原有图标 · 已选" else "恢复原有图标")
                    }
                    Text("绑定开启时，恢复原有图标也会恢复原有外观。关闭绑定后可单独恢复图标。",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
