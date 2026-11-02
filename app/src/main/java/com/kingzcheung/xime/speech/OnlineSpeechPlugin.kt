package com.kingzcheung.xime.speech

import android.app.Application
import android.content.Context
import com.kingzcheung.xime.plugin.ExtensionManager
import com.kingzcheung.xime.plugin.PluginConfigStoreImpl
import com.kingzcheung.xime.plugin.core.api.AsrPlugin
import com.kingzcheung.xime.plugin.core.lua.ws.NetworkPolicy
import com.kingzcheung.xime.settings.InputLanguage
import com.kingzcheung.xime.settings.SettingsPreferences

/**
 * 在线语音是插件（plugins/online-asr，插件市场「在线语音」，默认不装），和 AI 改写一样。
 * 宿主这边只管：用哪个语音插件、它认哪些语言、是不是整段上传；以及把 1.15.33 起内置的
 * 「在线语音接口」填过的地址、密钥、模型搬进插件（装上插件后第一次用到时搬一次，搬完删掉旧文件）。
 */
object OnlineSpeechPlugin {
    const val PLUGIN_ID = "com.kingzcheung.xime.plugin.online_asr"
    private const val OLD_PREFS = "online_speech_api"
    private const val OLD_DEFAULT_BASE_URL = "https://api.openai.com/v1"

    /** 选中并启用的语音插件；没选过或选的被停用了就取第一个启用的。 */
    fun selected(context: Context): Pair<String, AsrPlugin>? {
        val enabled = ExtensionManager.getEnabledAsrPlugins(context)
        val id = SettingsPreferences.getSttOnlinePluginId(context)
        return enabled.firstOrNull { it.first == id } ?: enabled.firstOrNull()
    }

    /** 选中的插件能不能识别这种语言：中文都能；其他语言要插件声明 `languages: any`。 */
    fun supports(context: Context, language: InputLanguage): Boolean =
        language == InputLanguage.CHINESE || selected(context)?.second?.getCapabilities()?.anyLanguage == true

    /** 选中的插件录完整段上传（收尾要等接口返回）。 */
    fun isBatch(context: Context): Boolean = selected(context)?.second?.getCapabilities()?.isBatch == true

    /** 旧版内置接口填过密钥、还没搬进插件。 */
    fun hasOldApiConfig(context: Context): Boolean =
        !context.getSharedPreferences(OLD_PREFS, Context.MODE_PRIVATE).getString("api_key", null).isNullOrBlank()

    /**
     * 旧内置接口 → 插件：插件已装、插件里还没填密钥时，把地址、密钥、模型写进插件设置（加密存本机），
     * 给接口域名联网授权，并选中这个插件（原来选了「优先使用在线接口」的人照旧优先在线）。搬完删掉旧文件。
     */
    fun migrateOldApi(context: Context) {
        val old = context.getSharedPreferences(OLD_PREFS, Context.MODE_PRIVATE)
        val apiKey = old.getString("api_key", null)?.trim().orEmpty()
        if (apiKey.isEmpty()) return
        if (ExtensionManager.getAllInstalledPlugins().none { it.id == PLUGIN_ID }) return
        val app = context.applicationContext as? Application ?: return
        runCatching {
            val store = PluginConfigStoreImpl(app, PLUGIN_ID)
            if (store.get("apiKey").isNullOrBlank()) {
                val baseUrl = old.getString("base_url", null)?.trim()?.takeIf { it.isNotEmpty() } ?: OLD_DEFAULT_BASE_URL
                store.set("baseUrl", baseUrl)
                store.set("apiKey", apiKey)
                old.getString("model", null)?.trim()?.takeIf { it.isNotEmpty() }?.let { store.set("model", it) }
                NetworkPolicy.extractHttpHost(baseUrl)?.let { SettingsPreferences.authorizePluginHost(context, PLUGIN_ID, it) }
                if (SettingsPreferences.getSttOnlinePluginId(context).isBlank()) {
                    SettingsPreferences.setSttOnlinePluginId(context, PLUGIN_ID)
                }
            }
            old.edit().clear().commit()
        }
    }
}
