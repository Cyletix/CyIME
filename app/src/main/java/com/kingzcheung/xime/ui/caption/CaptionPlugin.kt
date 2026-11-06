package com.kingzcheung.xime.ui.caption

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.kingzcheung.xime.keyboard.ToolbarButton
import com.kingzcheung.xime.plugin.core.runtime.PluginManager
import com.kingzcheung.xime.settings.SettingsPreferences
import com.kingzcheung.xime.settings.XimeIndexSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 「字幕」插件（用户 11-05「把字幕做成插件…加一个插件补一个菜单」）。
 *
 * 字幕要用手机上的识别模型、麦克风、全屏页面、下拉快捷开关和桌面图标，脚本插件做不了，
 * 所以和通讯录插件一样：插件包只声明 `capabilities.caption`，字幕本身还在宿主里。
 * 插件装着并启用时才放出字幕的入口（键盘菜单「字幕」、下拉快捷开关、通话字幕、桌面图标、设置里的字幕），
 * 停用或卸载就全部收起，不留空入口。插件包随安装包带在插件市场目录里，装它不用联网。
 */
object CaptionPlugin {
    const val ID = "com.kingzcheung.xime.plugin.caption"

    /** 键盘菜单里的字幕按钮沿用内置的 caption：老用户放在工具栏或菜单里的位置不变。 */
    val BUTTON_ID: String = ToolbarButton.CAPTION.id

    private const val TAG = "CaptionPlugin"
    private const val KEY_ACTIVE = "caption_plugin_active"
    private const val KEY_MIGRATED = "caption_plugin_migrated"
    private const val CAPTION_PREFS = "caption_mode"

    /**
     * 字幕插件装着并启用。键盘菜单每次排按钮都会问，所以只读一个偏好（由 [sync] 维护），不碰插件管理器。
     */
    fun isActive(context: Context): Boolean =
        SettingsPreferences.getPrefsPublic(context).getBoolean(KEY_ACTIVE, false)

    private fun installed(): Boolean = runCatching {
        PluginManager.isInitialized && PluginManager.getAllInstallPlugins().any { it.id == ID && it.capabilities?.caption == true }
    }.getOrDefault(false)

    /**
     * 按插件现在装没装、开没开，放出或收起字幕的入口。启动时、拨插件开关、卸载插件后调用。
     * 收起时桌面图标一起关掉（钉在桌面上的会变灰，可长按移除）；字幕记录和字幕设置都留着，再装上照旧。
     */
    fun sync(context: Context) {
        val app = context.applicationContext ?: context
        val active = installed() && SettingsPreferences.isPluginEnabled(app, ID)
        val prefs = SettingsPreferences.getPrefsPublic(app)
        val was = prefs.getBoolean(KEY_ACTIVE, false)
        if (was != active || !prefs.contains(KEY_ACTIVE)) prefs.edit().putBoolean(KEY_ACTIVE, active).apply()
        setComponents(app, active)
        if (active) SettingsPreferences.placeNewPluginButtons(app, listOf(BUTTON_ID))
        else if (was) runCatching { CaptionLauncherIcon.setEnabled(app, false) }
    }

    /** 下拉快捷开关和通话字幕（无障碍服务）跟着插件出现、消失。 */
    private fun setComponents(context: Context, on: Boolean) {
        val state = if (on) PackageManager.COMPONENT_ENABLED_STATE_DEFAULT else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        val pm = context.packageManager
        for (component in listOf(CaptionTileService::class.java, CallCaptionService::class.java)) {
            val name = ComponentName(context, component)
            runCatching {
                if (pm.getComponentEnabledSetting(name) != state) pm.setComponentEnabledSetting(name, state, PackageManager.DONT_KILL_APP)
            }.onFailure { Log.w(TAG, "切换 ${component.simpleName} 失败", it) }
        }
    }

    /**
     * 从随包的插件市场目录装上字幕插件并启用（不联网）。按设备推荐配置选「用字幕」、老用户升级时用。
     * 已经装着的不重装。返回装好没有。
     */
    suspend fun install(context: Context): Boolean = withContext(Dispatchers.IO) {
        val app = context.applicationContext ?: context
        if (installed()) {
            if (!SettingsPreferences.isPluginEnabled(app, ID)) SettingsPreferences.setPluginEnabled(app, ID, true)
            return@withContext true
        }
        runCatching {
            val version = XimeIndexSource.bundledPlugins(app).plugins.first { it.id == ID }.resolvedVersion()
                ?: error("随包目录里没有字幕插件的版本")
            XimeIndexSource.installMarketPlugin(app, ID, version)
            // 市场第一次装会直接启用；以前装过又停用的，这里是用户自己要用，打开。
            if (!SettingsPreferences.isPluginEnabled(app, ID)) SettingsPreferences.setPluginEnabled(app, ID, true)
            sync(app)
        }.onFailure { Log.e(TAG, "装字幕插件失败", it) }.isSuccess
    }

    /**
     * 用过字幕：打开过字幕页或改过字幕设置、桌面字幕图标开着、系统无障碍里开着「通话字幕」、
     * 自己把「字幕」放到了工具栏，或者是老人包。菜单里默认放进去的「字幕」不算。
     */
    internal fun usedCaption(context: Context): Boolean =
        com.kingzcheung.xime.BuildConfig.ELDER ||
            context.getSharedPreferences(CAPTION_PREFS, Context.MODE_PRIVATE).all.isNotEmpty() ||
            runCatching { CaptionLauncherIcon.isEnabled(context) }.getOrDefault(false) ||
            runCatching { CallCaptionService.isEnabled(context) }.getOrDefault(false) ||
            BUTTON_ID in SettingsPreferences.getToolbarButtons(context)

    /**
     * 启动时调用（插件管理器装好内置插件之后）：升级到插件版时只做一次，用过字幕的人自动装上，入口和设置照旧；
     * 没用过的不装，菜单里默认放进去的「字幕」随之不显示。之后每次启动对一遍入口。
     */
    suspend fun migrateOnce(context: Context) {
        val app = context.applicationContext ?: context
        val prefs = SettingsPreferences.getPrefsPublic(app)
        if (!prefs.getBoolean(KEY_MIGRATED, false)) {
            if (!installed() && usedCaption(app)) install(app)
            prefs.edit().putBoolean(KEY_MIGRATED, true).apply()
        }
        sync(app)
    }
}
