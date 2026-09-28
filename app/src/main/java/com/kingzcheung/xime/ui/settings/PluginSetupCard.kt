package com.kingzcheung.xime.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/** The same install / manage entry points are available at each plugin-backed feature. */
@Composable
internal fun PluginSetupCard(feature: String, onInstall: () -> Unit, onManage: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("安装${feature}插件 → 选择服务 → 配置连接", style = MaterialTheme.typography.bodyMedium)
        Text("扩展商店用于安装，插件管理用于更新或卸载；服务选择和配置在本页完成。",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onInstall, modifier = Modifier.weight(1f)) { Text("安装插件") }
            OutlinedButton(onClick = onManage, modifier = Modifier.weight(1f)) { Text("已安装插件") }
        }
    }
}

@Composable
internal fun PluginServiceChoice(name: String, description: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
        .padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, enabled = enabled, onClick = null)
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleSmall)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
