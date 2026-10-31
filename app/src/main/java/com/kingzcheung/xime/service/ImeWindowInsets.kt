package com.kingzcheung.xime.service

import android.content.Context
import android.os.Build
import android.util.Log
import android.view.Window
import android.view.WindowInsets
import kotlin.math.roundToInt

private const val INSETS_TAG = "ImeWindowInsets"

/** 获取导航栏高度（dp）。 */
internal fun tryGetNavBarHeightDp(context: Context, imeWindow: Window?): Int {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val decorView = imeWindow?.decorView
            if (decorView != null) {
                val insets = decorView.rootWindowInsets
                if (insets != null) {
                    val px = insets.getInsetsIgnoringVisibility(
                        WindowInsets.Type.navigationBars()
                    ).bottom
                    val dp = (px / context.resources.displayMetrics.density).roundToInt()
                    return dp
                }
            }
        }
        val resId = context.resources.getIdentifier("navigation_bar_height", "dimen", "android")
        val dp = if (resId > 0) (context.resources.getDimensionPixelSize(resId) / context.resources.displayMetrics.density).roundToInt() else 0
        return dp
    } catch (e: Exception) {
        Log.w(INSETS_TAG, "NavBar: error", e)
        0
    }
}

/** 获取可见导航栏高度（dp）；手势导航时只算手势条，与 [extractBottomInset] 同一规则。 */
internal fun tryGetVisibleNavBarHeightDp(context: Context, imeWindow: Window?): Int {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val decorView = imeWindow?.decorView
            if (decorView != null) {
                val insets = decorView.rootWindowInsets
                if (insets != null) {
                    val px = gestureAwareBottom(insets, imeWindow, insets.getInsets(
                        WindowInsets.Type.navigationBars()
                    ).bottom)
                    val dp = if (px > 0) (px / context.resources.displayMetrics.density).roundToInt() else 0
                    return dp
                }
            }
        }
        0
    } catch (e: Exception) {
        Log.w(INSETS_TAG, "NavBar: visibleOnly error", e)
        0
    }
}

/** 获取状态栏高度（dp）。 */
internal fun tryGetStatusBarHeightDp(context: Context, imeWindow: Window?): Int {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val decorView = imeWindow?.decorView
            if (decorView != null) {
                val insets = decorView.rootWindowInsets
                if (insets != null) {
                    val px = insets.getInsetsIgnoringVisibility(
                        WindowInsets.Type.statusBars()
                    ).top
                    if (px > 0) return (px / context.resources.displayMetrics.density).roundToInt()
                }
            }
        }
        val resId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resId > 0) (context.resources.getDimensionPixelSize(resId) / context.resources.displayMetrics.density).roundToInt() else 0
    } catch (e: Exception) { 0 }
}

/**
 * 多类型检测底部 inset。
 * 与主流输入法一致：navigationBars、mandatorySystemGestures 与 tappableElement 取最大，
 * 全部为空时再回退 systemGestures。
 * 手势导航下 navigationBars 通常为 0，但 mandatorySystemGestures / systemGestures
 * 会返回手势条高度（约 20~32dp），阈值不能过滤掉它们，否则底部手势条区域会露出窗口背景。
 * 手势导航时系统给输入法窗口的 navigationBars 是整条按钮框（48dp，给返回/切换输入法按钮留的），
 * 这里没有按钮时只留手势条高度，见 [imeBottomInsetPx]。
 */
internal fun extractBottomInset(
    insets: WindowInsets,
    imeWindow: Window? = null,
): Int {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
        @Suppress("DEPRECATION")
        return insets.systemWindowInsetBottom.coerceAtLeast(0)
    }
    val sys = insets.getInsets(WindowInsets.Type.systemBars()).bottom
    val nav = insets.getInsets(WindowInsets.Type.navigationBars()).bottom
    val tappable = insets.getInsets(WindowInsets.Type.tappableElement()).bottom
    val mandatory = insets.getInsets(WindowInsets.Type.mandatorySystemGestures()).bottom
    val gestures = insets.getInsets(WindowInsets.Type.systemGestures()).bottom
    val primary = maxOf(nav, mandatory, tappable)
    val fallback = gestures
    val full = if (primary > 0) primary else fallback
    val pick = gestureAwareBottom(insets, imeWindow, full)
    Log.d(INSETS_TAG, "bottom: sys=$sys nav=$nav tappable=$tappable mandatory=$mandatory gestures=$gestures pick=$pick")
    return pick
}

/** 底部系统栏能不能缩到手势条：读系统导航模式、本窗口有没有系统画的输入法导航按钮。 */
private fun gestureAwareBottom(insets: WindowInsets, imeWindow: Window?, fullPx: Int): Int {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || imeWindow == null || fullPx <= 0) return fullPx
    val context = imeWindow.context
    val tappable = insets.getInsets(WindowInsets.Type.tappableElement()).bottom
    val navigationMode = readSetting { android.provider.Settings.Secure.getInt(context.contentResolver, "navigation_mode") }
    // 三星手势导航：「手势提示」关掉时底部既没有手势条也没有「隐藏键盘」按钮；
    // 提示开着且「显示隐藏键盘按钮」开着时，按钮画在键盘下面这条里，整条都要留。
    val gestureHint = readSetting {
        android.provider.Settings.Global.getInt(context.contentResolver, "navigation_bar_gesture_hint")
    }
    val hideKeyboardButton = gestureHint != 0 && readSetting {
        android.provider.Settings.Global.getInt(context.contentResolver, "navigation_bar_button_to_hide_keyboard")
    } == 1
    // 平板手势导航时任务栏在键盘下面画返回/切换输入法按钮，整条留着（未在平板上核对前不缩）。
    val taskbarScreen = context.resources.configuration.smallestScreenWidthDp >= 600
    // 只有核对过的厂商才缩：vivo 等手势导航时也在键盘下面画「切换输入法」按钮，缩了会压在按钮上。
    val unverifiedVendor = !gestureStripVerifiedVendor(Build.MANUFACTURER)
    val pick = imeBottomInsetPx(fullPx, tappable, navigationMode,
        imeNavButtons = taskbarScreen || unverifiedVendor || hideKeyboardButton || imeWindowDrawsNavButtons(imeWindow),
        gestureBarPx = gestureBarHeightPx(context, hintHidden = gestureHint == 0))
    if (pick != fullPx) {
        Log.d(INSETS_TAG, "gesture nav: full=$fullPx mode=$navigationMode tappable=$tappable hint=$gestureHint hideButton=$hideKeyboardButton pick=$pick")
    }
    return pick
}

/**
 * 手势导航时能不能把底部缩到手势条：只认核对过底部有没有系统按钮的厂商（三星，按它自己的
 * 「手势提示」「隐藏键盘按钮」设置判断）。其他厂商（如 vivo 在键盘下面画「切换输入法」按钮）
 * 照 1.15.17 以前整条留着，不让最下一排压住系统按钮。
 */
internal fun gestureStripVerifiedVendor(manufacturer: String?): Boolean =
    manufacturer.equals("samsung", ignoreCase = true)

/** 手势导航时键盘下面最多留多高（dp）：手势条本身。 */
internal const val GESTURE_BAR_MAX_DP = 24

/** 手势提示（手势条）隐藏时只留一小截，让最下一排避开屏幕圆角。 */
internal const val GESTURE_HINT_HIDDEN_DP = 12

/**
 * 输入法底部要让出的系统栏高度（px）。
 * 三键导航（导航模式 0/1；读不到模式时底部有可点的系统按钮）整条留着；手势导航时系统按 48dp 按钮框给输入法 inset，
 * 但那里没有按钮（系统没在输入法窗口里画返回/切换键）就只让出手势条 [gestureBarPx]，键盘贴着手势条。
 * [navigationMode] 读不到时为 null，这时底部没有可点的系统元素才算手势导航。
 */
internal fun imeBottomInsetPx(
    fullPx: Int,
    tappablePx: Int,
    navigationMode: Int?,
    imeNavButtons: Boolean,
    gestureBarPx: Int,
): Int {
    if (fullPx <= 0) return 0
    // 系统的输入法 inset 可能把 tappableElement 也覆盖成按钮框高，读得到导航模式时以它为准。
    val gesture = if (navigationMode != null) navigationMode == 2 else tappablePx <= 0
    if (!gesture || imeNavButtons || gestureBarPx <= 0) return fullPx
    return minOf(fullPx, gestureBarPx)
}

/** 系统的手势条高度：手势导航时 navigation_bar_height 是手势条那一小条，限制在 [GESTURE_BAR_MAX_DP] 内。 */
private fun gestureBarHeightPx(context: Context, hintHidden: Boolean): Int {
    val max = ((if (hintHidden) GESTURE_HINT_HIDDEN_DP else GESTURE_BAR_MAX_DP) *
        context.resources.displayMetrics.density).roundToInt()
    val resId = context.resources.getIdentifier("navigation_bar_height", "dimen", "android")
    val px = if (resId > 0) runCatching { context.resources.getDimensionPixelSize(resId) }.getOrDefault(0) else 0
    return if (px in 1..max) px else max
}

/** Pixel 等机型手势导航时，系统在输入法窗口底部自己画返回/切换输入法按钮（NavigationBarFrame）。 */
private fun imeWindowDrawsNavButtons(imeWindow: Window): Boolean {
    fun find(view: android.view.View): Boolean {
        if (view.javaClass.name == "android.inputmethodservice.NavigationBarFrame") return view.isShown && view.height > 0
        if (view is android.view.ViewGroup) for (i in 0 until view.childCount) if (find(view.getChildAt(i))) return true
        return false
    }
    return runCatching { find(imeWindow.decorView) }.getOrDefault(false)
}

/**
 * 手势导航时系统底部手势区的上沿（px，[view] 所在窗口的坐标）：从这里往下按住上滑会被系统当成回桌面。
 * 三键导航、Android 9 或读不到时为 null。判断手势导航的规则与 [imeBottomInsetPx] 相同。
 */
internal fun bottomGestureZoneTopInWindowPx(view: android.view.View): Int? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
    return try {
        val insets = view.rootWindowInsets ?: return null
        val zone: Int
        val tappable: Int
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            zone = insets.getInsets(WindowInsets.Type.mandatorySystemGestures()).bottom
            tappable = insets.getInsets(WindowInsets.Type.tappableElement()).bottom
        } else {
            @Suppress("DEPRECATION")
            zone = insets.mandatorySystemGestureInsets.bottom
            @Suppress("DEPRECATION")
            tappable = insets.tappableElementInsets.bottom
        }
        if (zone <= 0) return null
        val navigationMode = readSetting {
            android.provider.Settings.Secure.getInt(view.context.contentResolver, "navigation_mode")
        }
        val gesture = if (navigationMode != null) navigationMode == 2 else tappable <= 0
        if (!gesture) null else view.rootView.height - zone
    } catch (e: Exception) { null }
}

private inline fun readSetting(read: () -> Int): Int? = try { read() } catch (_: Exception) { null }

/** 获取当前活跃的底部 inset（px）。 */
internal fun getActiveBottomInsetPx(imeWindow: Window?): Int {
    return try {
        val decorView = imeWindow?.decorView ?: return 0
        val insets = decorView.rootWindowInsets ?: return 0
        val px = extractBottomInset(insets, imeWindow)
        Log.d(INSETS_TAG, "getActiveBottomInsetPx=$px (rootWindowInsets)")
        px
    } catch (e: Exception) { 0 }
}
