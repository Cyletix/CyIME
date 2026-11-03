package com.kingzcheung.xime.ui.settings

import android.content.Context
import com.kingzcheung.xime.plugin.core.model.PluginInfo
import com.kingzcheung.xime.plugin.core.model.PluginSource
import com.kingzcheung.xime.plugin.core.model.TrustLevel
import com.kingzcheung.xime.plugin.core.runtime.PluginManager
import com.kingzcheung.xime.settings.SettingsPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 插件页和插件设置页顶上的「入口：键盘菜单 › AI 改写」：下载了插件得知道去哪点（用户 2026-11-02）。
 * 只给在键盘上有按钮的插件；按钮在哪就写哪。
 */
internal fun pluginEntryHint(context: Context, info: PluginInfo?, enabled: Boolean): String? {
    val button = info?.toolbarButtons?.firstOrNull() ?: return null
    val place = if (enabled) SettingsPreferences.pluginButtonPlace(context, button.id) else null
    return pluginEntryHintText(button.label.ifBlank { info.name }, place)
}

/** [place]：toolbar / menu / unused；null = 插件没启用。 */
internal fun pluginEntryHintText(label: String, place: String?): String = when (place) {
    null -> "入口：打开开关后，键盘菜单 › $label"
    "toolbar" -> "入口：键盘工具栏 › $label"
    "menu" -> "入口：键盘菜单 › $label"
    else -> "入口：键盘菜单 › 排列，把「$label」拖进工具栏或菜单"
}

/**
 * 插件设置页顶上的「启用」能直接拨的插件：从市场装的和官方的。
 * 文件导入的非官方插件仍到「已安装」拨开关，那里会先确认来源。
 */
internal fun canEnableFromSettings(info: PluginInfo): Boolean =
    info.type == "tool" && (info.source == PluginSource.REMOTE || info.trustLevel == TrustLevel.TRUSTED)

/** 打开：加载插件，按钮放进键盘菜单；关掉：卸载。和「已安装」里的开关做同样的事。 */
internal suspend fun setToolPluginEnabled(context: Context, pluginId: String, enabled: Boolean) =
    withContext(Dispatchers.IO) {
        SettingsPreferences.setPluginEnabled(context, pluginId, enabled)
        if (enabled) {
            PluginManager.launchPlugin(pluginId)
            PluginManager.getAllInstallPlugins().find { it.id == pluginId }?.toolbarButtons
                ?.let { buttons -> SettingsPreferences.placeNewPluginButtons(context, buttons.map { it.id }) }
        } else {
            PluginManager.unloadPlugin(pluginId)
        }
    }

/** 卸载插件：停掉、删文件，再清掉它的开关、按钮和选用（「已安装」和插件详情页共用）。 */
internal suspend fun uninstallPluginCompletely(context: Context, pluginId: String) = withContext(Dispatchers.IO) {
    val buttons = PluginManager.getAllInstallPlugins().find { it.id == pluginId }?.toolbarButtons.orEmpty().map { it.id }
    PluginManager.unloadPlugin(pluginId)
    PluginManager.installerManager.uninstallPlugin(pluginId)
    SettingsPreferences.forgetUninstalledPlugin(context, pluginId, buttons)
}
