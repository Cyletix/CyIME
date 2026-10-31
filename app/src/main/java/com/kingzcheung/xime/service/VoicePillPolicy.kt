package com.kingzcheung.xime.service

/**
 * 输入框再次请求弹出键盘（用户点了输入框）时，语音悬浮条是否换回完整键盘。
 * 输入法自己请求弹出悬浮条的那次、旋转屏幕重建都不算用户请求。
 */
internal fun voicePillExpandsOnShowRequest(voiceCollapsed: Boolean, selfShow: Boolean, configChange: Boolean): Boolean =
    voiceCollapsed && !selfShow && !configChange

/** 听写进行中、键盘界面要离开的各种情况。 */
internal enum class VoiceSessionEnd {
    /** 键盘里按返回键（或返回手势）：就是退出键盘。 */
    BACK_KEY,
    /** 键盘上的收起按钮。 */
    COLLAPSE_BUTTON,
    /**
     * 键盘被收起、输入框焦点还在：往上翻看时 App 收起键盘，系统导航栏的收起键也是这一种
     * （输入法分不出两者）。
     */
    APP_HIDES_KEYBOARD,
    /** 输入框从有字变成空：多半是点了发送。 */
    FIELD_CLEARED,
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
 * 听写在这些情况下继续（显示悬浮条）：收起按钮；键盘被收起但输入框焦点还在（翻看页面）。
 * 其余情况本次听写都结束并立即释放麦克风（用户 10-24 选定）。
 */
internal fun voiceKeepsListeningAfter(end: VoiceSessionEnd): Boolean =
    end == VoiceSessionEnd.COLLAPSE_BUTTON || end == VoiceSessionEnd.APP_HIDES_KEYBOARD

/** 听写中输入框从有字变成空（读不到时不算）。 */
internal fun voiceFieldCleared(hadText: Boolean, textNow: CharSequence?): Boolean =
    hadText && textNow != null && textNow.isEmpty()

/** 开车连续语音的记号：哪个应用里的悬浮条听写被打断、什么时候（elapsedRealtime）。 */
internal data class VoicePillResumeMark(val packageName: String, val atMs: Long)

/** 打断后多久内回来还自动续听：等对方回消息够用，又不至于隔很久突然开麦。 */
internal const val VOICE_PILL_RESUME_WINDOW_MS = 10 * 60_000L

/** 悬浮条听写结束时是否记下续听：只有用户自己收起的悬浮条、因发送或焦点离开而停下才算。 */
internal fun voicePillResumeArmsAfter(end: VoiceSessionEnd, pillByUser: Boolean, enabled: Boolean): Boolean =
    enabled && pillByUser && (end == VoiceSessionEnd.FIELD_CLEARED || end == VoiceSessionEnd.FOCUS_LEAVES ||
        end == VoiceSessionEnd.FOCUS_TO_OTHER_FIELD)

/** 回到输入框时是否自动续听：同一个应用、还在有效期内。 */
internal fun voicePillResumeApplies(mark: VoicePillResumeMark?, packageName: String?, nowMs: Long): Boolean =
    mark != null && packageName != null && mark.packageName == packageName &&
        nowMs - mark.atMs in 0..VOICE_PILL_RESUME_WINDOW_MS

/** 输入框本身是否允许自动开麦：密码等敏感框、不许语音的框、通话中都不开。 */
internal fun voicePillResumeFieldAllows(sensitive: Boolean, locksNativeInput: Boolean, inCall: Boolean, hasMicPermission: Boolean): Boolean =
    !sensitive && !locksNativeInput && !inCall && hasMicPermission
