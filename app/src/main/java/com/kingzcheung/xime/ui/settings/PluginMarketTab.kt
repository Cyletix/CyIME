package com.kingzcheung.xime.ui.settings

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kingzcheung.xime.settings.MarketPluginItem
import com.kingzcheung.xime.viewmodel.PluginMarketViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PluginsMarketTab(onInstalledDetail: (String) -> Unit) {
    val model: PluginMarketViewModel = viewModel()
    val state by model.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { model.refreshInstalled() }
    LaunchedEffect(state.message) {
        state.message?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            model.clearMessage()
        }
    }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var version by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    PullToRefreshBox(isRefreshing = state.refreshing && state.plugins.isEmpty(), onRefresh = model::refresh) {
        LazyColumn(
            state = listState, modifier = Modifier.fillMaxSize().settingsGutterScroll(listState),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { MarketSectionLabel("插件市场") }
            if (state.plugins.isEmpty() && !state.refreshing) item {
                TextButton(onClick = model::refresh) { Text("目录暂不可用，点击重试") }
            }
            items(state.plugins, key = { it.plugin.id }) { item ->
                val plugin = item.plugin
                val current = plugin.resolvedVersion()
                val colors = pluginCategoryColors(plugin.pluginType)
                val open = {
                    selected = plugin.id
                    version = current?.version.orEmpty()
                }
                MarketStoreCard(
                    icon = pluginCategoryIcon(plugin.pluginType),
                    iconContainerColor = colors.first, iconContentColor = colors.second,
                    title = plugin.name.ifBlank { plugin.id }, subtitle = plugin.description,
                    metaLine = listOfNotNull(pluginCategoryLabel(plugin.pluginType), current?.version).joinToString(" · "),
                    versions = emptyList(), selectedVersion = "", onSelectVersion = {},
                    onCardClick = open,
                ) {
                    TextButton(onClick = open, enabled = state.installing != plugin.id) {
                        Text(when {
                            state.installing == plugin.id -> "安装中"
                            !item.compatible -> "不兼容"
                            item.hasUpdate -> "更新"
                            item.installed -> "已安装"
                            else -> "获取"
                        })
                    }
                }
            }
            if (state.source.isNotBlank()) item {
                Text("目录来源 · ${state.source}", style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(8.dp))
            }
        }
    }
    state.plugins.find { it.plugin.id == selected }?.let { item ->
        PluginMarketDialog(item, version, state.installing != null, { version = it }, { selected = null },
            onInstall = {
                item.plugin.versions.find { it.version == version }?.let { model.install(item, it) }
                selected = null
            },
            onManage = {
                selected = null
                onInstalledDetail(item.plugin.id)
            })
    }
}

@Composable
private fun PluginMarketDialog(
    item: MarketPluginItem, version: String, busy: Boolean, select: (String) -> Unit,
    close: () -> Unit, onInstall: () -> Unit, onManage: () -> Unit,
) {
    val plugin = item.plugin
    val installed = item.installed && item.installedVersion == version
    AlertDialog(
        onDismissRequest = close, title = { Text(plugin.name.ifBlank { plugin.id }) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(plugin.description)
                MarketVersionSelector(plugin.versions.map { it.version }, version, select)
                if (plugin.warning.isNotBlank()) Text(plugin.warning)
                if (!item.compatible) Text("需要 CyIME ${item.minAppVersion} 或更高版本")
                else if (!item.installed && plugin.pluginType == "tool") Text("装好后在键盘菜单里；接口和密钥在「已安装」› 设置。")
                else if (!item.installed) Text("安装后到「已安装」启用和配置，可在插件详情查看联网授权。")
            }
        },
        confirmButton = {
            TextButton(
                onClick = if (installed) onManage else onInstall,
                enabled = installed || (!busy && item.compatible && plugin.versions.any { it.version == version }),
            ) { Text(if (installed) "查看已安装插件" else "安装此版本") }
        },
        dismissButton = { TextButton(onClick = close) { Text("关闭") } },
    )
}
