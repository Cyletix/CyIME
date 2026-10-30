package com.kingzcheung.xime.keyboard

import android.os.SystemClock

/**
 * 设置页「键盘尺寸与位置」「工具栏与菜单排列」打开的是键盘菜单里同一个调节／排列面板：
 * 设置页记下要开哪个，本应用试打框弹出键盘时由输入法取走并打开；只开一次，过期作废。
 */
object KeyboardPanelLaunch {
    enum class Panel { RESIZE, ARRANGE }

    private const val EXPIRE_MS = 5_000L

    @Volatile private var pending: Pair<Panel, Long>? = null

    fun request(panel: Panel) { pending = panel to SystemClock.uptimeMillis() }

    fun cancel() { pending = null }

    fun take(): Panel? {
        val p = pending ?: return null
        pending = null
        return p.first.takeIf { SystemClock.uptimeMillis() - p.second < EXPIRE_MS }
    }
}
