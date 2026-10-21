package com.kingzcheung.xime.service

/**
 * 输入框再次请求弹出键盘（用户点了输入框）时，语音悬浮条是否换回完整键盘。
 * 旋转屏幕重建不算用户请求。
 */
internal fun voicePillExpandsOnShowRequest(voiceCollapsed: Boolean, configChange: Boolean): Boolean =
    voiceCollapsed && !configChange

/** 听写进行中、键盘界面要离开的各种情况。 */
internal enum class VoiceSessionEnd {
    /** 键盘里按返回键（或返回手势）。 */
    BACK_KEY,
    /** 键盘上的收起按钮。 */
    COLLAPSE_BUTTON,
    /** 点了 App 的发送等按钮后 App 自己收起键盘。 */
    APP_HIDES_KEYBOARD,
    /** 系统导航栏的收起键盘键。 */
    SYSTEM_HIDE_KEY,
    /** 输入框失去焦点。 */
    FOCUS_LEAVES,
    /** 焦点换到另一个输入框。 */
    FOCUS_TO_OTHER_FIELD,
    /** 换到密码等敏感输入框（无痕）。 */
    SENSITIVE_FIELD,
    HOME,
    APP_SWITCH,
    SCREEN_OFF,
    ROTATION,
    SWITCH_KEYBOARD,
    OPEN_CAPTIONS,
}

/**
 * 只有用户在键盘里主动收起（返回键、收起按钮）才收成悬浮条继续听写；
 * 其余情况本次听写都结束并立即释放麦克风，绝不自动收成悬浮条。
 */
internal fun voiceCollapsesInsteadOfEnding(end: VoiceSessionEnd): Boolean =
    end == VoiceSessionEnd.BACK_KEY || end == VoiceSessionEnd.COLLAPSE_BUTTON
