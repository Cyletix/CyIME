package com.kingzcheung.xime.settings

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * 设置页等界面文字用哪种语言（不是输入语言，输入语言见 [InputLanguage]）。
 *
 * 只用 Android 13 起系统自带的「应用语言」（LocaleManager）：系统自己保存、对本进程所有界面和输入法服务都生效，
 * 系统设置 › 应用 › CyIME › 语言 里改的也是同一个值。Android 12 及更早没有这个机制，界面跟随系统语言；
 * 不自己包 Context 换语言——那样包出来的 Resources 配置是死的，旋转后键盘还拿旧的屏幕方向。
 *
 * 文字在 res/values（简体，默认）、values-b+zh+Hant、values-en、values-ja；系统语言不是这四种时显示简体中文。
 */
enum class AppLanguage(val tag: String, val nativeName: String) {
    SYSTEM("", ""),
    SIMPLIFIED_CHINESE("zh-CN", "简体中文"),
    TRADITIONAL_CHINESE("zh-TW", "繁體中文"),
    ENGLISH("en", "English"),
    JAPANESE("ja", "日本語");

    companion object {
        /** 系统或别处设的语言标签归到这四种里；认不出的当作跟随系统。 */
        fun fromTag(tag: String?): AppLanguage {
            if (tag.isNullOrBlank()) return SYSTEM
            val locale = Locale.forLanguageTag(tag)
            return when (locale.language) {
                "zh" -> if (locale.script == "Hant" || locale.script.isEmpty() && locale.country in TRADITIONAL_REGIONS)
                    TRADITIONAL_CHINESE else SIMPLIFIED_CHINESE
                "en" -> ENGLISH
                "ja" -> JAPANESE
                else -> SYSTEM
            }
        }

        private val TRADITIONAL_REGIONS = setOf("TW", "HK", "MO")
    }
}

object AppLanguages {
    /** 这台设备能不能在应用里单独选语言（Android 13 起）。 */
    val supported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    fun current(context: Context): AppLanguage {
        if (!supported) return AppLanguage.SYSTEM
        val locales = context.getSystemService(LocaleManager::class.java)?.applicationLocales ?: return AppLanguage.SYSTEM
        return if (locales.isEmpty) AppLanguage.SYSTEM else AppLanguage.fromTag(locales[0].toLanguageTag())
    }

    /** 选了就由系统换语言、重建界面；选「跟随系统」清掉应用自己的语言。 */
    fun set(context: Context, language: AppLanguage) {
        if (!supported) return
        val manager = context.getSystemService(LocaleManager::class.java) ?: return
        manager.applicationLocales = if (language == AppLanguage.SYSTEM) LocaleList.getEmptyLocaleList()
            else LocaleList.forLanguageTags(language.tag)
    }
}
