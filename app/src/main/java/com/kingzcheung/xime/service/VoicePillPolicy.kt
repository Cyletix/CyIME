package com.kingzcheung.xime.service

/**
 * 输入框再次请求弹出键盘（用户点了输入框）时，语音悬浮条是否换回完整键盘。
 * 自动重新弹出悬浮条本身也会走这条请求，那次不能展开；旋转屏幕重建也不算用户请求。
 */
internal fun voicePillExpandsOnShowRequest(voiceCollapsed: Boolean, revivingPill: Boolean, configChange: Boolean): Boolean =
    voiceCollapsed && !revivingPill && !configChange
