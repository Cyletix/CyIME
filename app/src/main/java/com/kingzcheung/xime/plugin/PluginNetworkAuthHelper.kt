package com.kingzcheung.xime.plugin

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.kingzcheung.xime.MainActivity
import com.kingzcheung.xime.plugin.core.model.PluginInfo
import com.kingzcheung.xime.plugin.core.model.PluginSource
import com.kingzcheung.xime.plugin.core.model.TrustLevel
import com.kingzcheung.xime.plugin.core.runtime.PluginManager
import com.kingzcheung.xime.settings.SettingsPreferences

/**
 * 插件联网授权引导：
 * - [ensureAuthorized]：使用前检测——插件声明/配置了网络域名但未授权时，引导用户先授权；
 * - [onNetworkDenied]：运行时兜底——被拒时记录待授权域名、Toast 提示原因，
 *   首次被拒自动打开这个插件的设置页（「联网」在那页），让用户当场授权。
 */
object PluginNetworkAuthHelper {

    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * 市场装的和内置的插件、且声明接受自定义地址的：用户在它设置页里填的接口地址直接算已授权，
     * 填完就能测试、就能用，不用再点「保存」或找授权入口。文件导入的非官方插件仍要用户授权。
     */
    fun trustsConfiguredHosts(info: PluginInfo?): Boolean =
        info != null && info.allowCustomHosts &&
            (info.source == PluginSource.REMOTE || info.trustLevel == TrustLevel.TRUSTED)

    /** 联网时实际放行的「已授权」域名：用户授权过的，加上 [trustsConfiguredHosts] 时配置里填的。 */
    fun runtimeAuthorizedHosts(
        context: Context,
        pluginId: String,
        info: PluginInfo?,
        configuredHosts: Set<String>
    ): Set<String> {
        val authorized = SettingsPreferences.getPluginAuthorizedHosts(context, pluginId)
        return if (trustsConfiguredHosts(info)) authorized + configuredHosts else authorized
    }

    /** 使用前检测：返回 true 放行；false 表示存在未授权域名，已引导用户授权。 */
    fun ensureAuthorized(context: Context, pluginId: String): Boolean {
        val info = PluginManager.getAllInstallPlugins().firstOrNull { it.id == pluginId } ?: return true
        val configured = ExtensionManager.getConfiguredNetworkHosts(context, pluginId)
        val authorized = runtimeAuthorizedHosts(context, pluginId, info, configured.toSet())
        // 放行配置地址的插件不看以前被拦过的旧地址：改了接口地址后，旧地址不该再挡住使用。
        val pending = if (trustsConfiguredHosts(info)) emptySet()
            else SettingsPreferences.getPluginPendingHosts(context, pluginId)
        val candidates = (info.declaredHosts + configured + pending).distinct()
        val unauthorized = candidates.filter { it !in authorized }
        if (unauthorized.isEmpty()) return true

        val host = unauthorized.first()
        val firstTime = SettingsPreferences.addPluginPendingHost(context, pluginId, host)
        mainHandler.post {
            Toast.makeText(
                context,
                "插件「${info.name}」还不能连 $host\n已打开它的设置页，在「联网」里授权后再试",
                Toast.LENGTH_LONG
            ).show()
            if (firstTime) {
                runCatching {
                    context.startActivity(MainActivity.buildPluginAuthIntent(context, pluginId))
                }
            }
        }
        return false
    }

    fun onNetworkDenied(
        context: Context,
        pluginId: String,
        pluginName: String?,
        host: String?,
        reason: String
    ) {
        if (host == null) return
        val firstTime = SettingsPreferences.addPluginPendingHost(context, pluginId, host)
        mainHandler.post {
            val name = pluginName ?: pluginId
            Toast.makeText(
                context,
                "插件「$name」联网被拒绝：$reason\n在它设置页的「联网」里授权后再试",
                Toast.LENGTH_LONG
            ).show()
            if (firstTime) {
                runCatching {
                    context.startActivity(MainActivity.buildPluginAuthIntent(context, pluginId))
                }
            }
        }
    }
}