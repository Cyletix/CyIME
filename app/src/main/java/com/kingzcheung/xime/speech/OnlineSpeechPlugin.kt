package com.kingzcheung.xime.speech

import android.content.Context
import com.kingzcheung.xime.plugin.ExtensionManager
import com.kingzcheung.xime.plugin.core.api.AsrPlugin
import com.kingzcheung.xime.settings.InputLanguage
import com.kingzcheung.xime.settings.SettingsPreferences

/**
 * 「优先使用在线语音」时用哪一路：默认是内置在线语音（[OnlineSpeechApi]，语音页直接填接口）；
 * 自己装了其他语音插件并选中它时走插件。这里只回答：认哪些语言、是不是整段上传。
 */
object OnlineSpeechPlugin {
    /** 选中并启用的语音插件；选的是内置在线语音、没选过或选的被停用了就取第一个启用的插件。 */
    fun selected(context: Context): Pair<String, AsrPlugin>? {
        val enabled = ExtensionManager.getEnabledAsrPlugins(context)
        val id = SettingsPreferences.getSttOnlinePluginId(context)
        return enabled.firstOrNull { it.first == id } ?: enabled.firstOrNull()
    }

    /** 能不能识别这种语言：中文都能；内置在线语音交给接口自己认；插件要声明 `languages: any`。 */
    fun supports(context: Context, language: InputLanguage): Boolean =
        language == InputLanguage.CHINESE || OnlineSpeechApi.isSelected(context) ||
            selected(context)?.second?.getCapabilities()?.anyLanguage == true

    /** 录完整段上传（收尾要等接口返回）：内置在线语音都是；插件看能力声明。 */
    fun isBatch(context: Context): Boolean = OnlineSpeechApi.isSelected(context) ||
        selected(context)?.second?.getCapabilities()?.isBatch == true
}
