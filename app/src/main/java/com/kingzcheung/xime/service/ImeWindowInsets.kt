package com.kingzcheung.xime.service

import android.content.Context
import android.os.Build
import android.util.Log
import android.view.Window
import android.view.WindowInsets
import android.view.View
import androidx.core.view.OneShotPreDrawListener
import androidx.core.view.doOnPreDraw
import com.kingzcheung.xime.settings.SettingsPreferences
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
        imeNavButtons = taskbarScreen || unverifiedVendor || hideKeyboardButton || imeNavButtonHeightPx(imeWindow) > 0,
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

/** 系统资源里的导航栏高度（px），读不到为 0。 */
private fun systemNavBarHeightPx(context: Context): Int {
    val resId = context.resources.getIdentifier("navigation_bar_height", "dimen", "android")
    return if (resId > 0) runCatching { context.resources.getDimensionPixelSize(resId) }.getOrDefault(0) else 0
}

/**
 * 系统导航栏高度只有手势条那么高（1..[GESTURE_BAR_MAX_DP] dp），而给输入法的底部 inset 比它高：
 * 说明是手势导航，多出来的是给输入法按钮框留的。三键导航时两者一样高（约 48dp）。
 */
internal fun navBarResourceShowsGestureBar(resourcePx: Int, insetPx: Int, density: Float): Boolean =
    resourcePx > 0 && resourcePx <= (GESTURE_BAR_MAX_DP * density).roundToInt() && insetPx > resourcePx

/** 系统的手势条高度：手势导航时 navigation_bar_height 是手势条那一小条，限制在 [GESTURE_BAR_MAX_DP] 内。 */
private fun gestureBarHeightPx(context: Context, hintHidden: Boolean): Int {
    val max = ((if (hintHidden) GESTURE_HINT_HIDDEN_DP else GESTURE_BAR_MAX_DP) *
        context.resources.displayMetrics.density).roundToInt()
    val resId = context.resources.getIdentifier("navigation_bar_height", "dimen", "android")
    val px = if (resId > 0) runCatching { context.resources.getDimensionPixelSize(resId) }.getOrDefault(0) else 0
    return if (px in 1..max) px else max
}

/** Pixel 等机型手势导航时，系统在输入法窗口底部自己画返回/切换输入法按钮（NavigationBarFrame）。 */
private fun imeNavButtonHeightPx(imeWindow: Window): Int {
    val decor = imeWindow.decorView
    fun find(view: android.view.View): Int {
        val name = view.javaClass.name
        if ((name == "android.inputmethodservice.navigationbar.NavigationBarFrame" ||
                name == "android.inputmethodservice.NavigationBarFrame") && view.isShown && view.width >= view.height) {
            val frame = android.graphics.Rect()
            val windowBounds = android.graphics.Rect()
            if (view.getGlobalVisibleRect(frame) && decor.getGlobalVisibleRect(windowBounds)) {
                return (windowBounds.bottom - frame.top).coerceIn(0, windowBounds.height())
            }
        }
        if (view is android.view.ViewGroup) for (i in 0 until view.childCount) {
            val height = find(view.getChildAt(i))
            if (height > 0) return height
        }
        return 0
    }
    return runCatching { find(decor) }.getOrDefault(0)
}

/** 系统可以先派发 insets，再摆放输入法导航按钮。只在这一帧重查，取到真实位置后再修正。 */
internal fun View.observeKeyboardBottomInsets(window: Window?, update: (WindowInsets) -> Unit) {
    var pending: OneShotPreDrawListener? = null
    setOnApplyWindowInsetsListener { view, insets ->
        pending?.removeListener()
        val beforeLayout = window?.let(::imeNavButtonHeightPx)
        update(insets)
        if (window != null) pending = view.doOnPreDraw {
            pending = null
            if (imeNavButtonHeightPx(window) != beforeLayout) update(insets)
        }
        view.onApplyWindowInsets(insets)
    }
}

/**
 * 三键、任务栏、系统绘制的 IME 按钮和底部切口决定下限，不能用手动校准覆盖。
 * [edge]（「手势导航时键盘贴底」）打开且识别到手势导航时忽略系统给输入法的底部边衬区（11-03 用户反馈
 * 「全面屏手势底下有一个高度去不掉」）：系统可能把可点区域（tappableElement）、三星「隐藏键盘」按钮都报成整条按钮框高，
 * 贴底时不再为它们留位，只留屏幕底部缺口和本窗口里系统真画出来的输入法导航按钮。三键导航和大屏任务栏照旧整条留着。
 */
internal fun fixedKeyboardMinimumInsetPx(insets: WindowInsets, window: Window, normalPx: Int,
    edge: Boolean = SettingsPreferences.edgeToEdgeKeyboard(window.context)): Int {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return maxOf(normalPx,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) insets.displayCutout?.safeInsetBottom ?: 0 else 0)
    val context = window.context
    val cutout = insets.displayCutout?.safeInsetBottom ?: 0
    val tappable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
        insets.getInsets(WindowInsets.Type.tappableElement()).bottom
    else @Suppress("DEPRECATION") insets.tappableElementInsets.bottom
    val navButtons = imeNavButtonHeightPx(window)
    val controls = maxOf(cutout, tappable, navButtons)
    val safe = maxOf(normalPx, controls)
    if (controls >= normalPx && !edge) return safe
    if (context.resources.configuration.smallestScreenWidthDp >= 600) {
        // 有些平板只把任务栏报为 navigationBars；大屏本身并不代表底部有任务栏。
        val nav = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) insets.getInsets(WindowInsets.Type.navigationBars()).bottom
            else @Suppress("DEPRECATION") insets.systemWindowInsetBottom
        if (nav > 0) return maxOf(nav, controls)
    }
    val resolver = context.contentResolver
    val mode = readSetting { android.provider.Settings.Secure.getInt(resolver, "navigation_mode") }
    val vendorGesture = vendorGestureNavigationOn(Build.MANUFACTURER) { global, key ->
        readSetting {
            if (global) android.provider.Settings.Global.getInt(resolver, key)
            else android.provider.Settings.Secure.getInt(resolver, key)
        }
    }
    val mandatory = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
        insets.getInsets(WindowInsets.Type.mandatorySystemGestures()).bottom
    else @Suppress("DEPRECATION") insets.mandatorySystemGestureInsets.bottom
    // 贴底时再看系统自己的导航栏高度：有的厂商手势导航时 navigation_mode 仍写 0、自家开关也读不到，
    // 但系统的 navigation_bar_height 已换成手势条那一小条（三键时是整条 48dp），这也算手势导航（11-03「调到最低还不行」）。
    val gesture = bottomGestureZoneHeightPx(mandatory, tappable, mode, vendorGesture, 0) != null ||
        (edge && navBarResourceShowsGestureBar(systemNavBarHeightPx(context), normalPx, context.resources.displayMetrics.density))
    if (!gesture) return safe
    if (edge) return maxOf(cutout, navButtons)
    if (gestureStripVerifiedVendor(Build.MANUFACTURER) &&
        readSetting { android.provider.Settings.Global.getInt(resolver, "navigation_bar_gesture_hint") } != 0 &&
        readSetting { android.provider.Settings.Global.getInt(resolver, "navigation_bar_button_to_hide_keyboard") } == 1
    ) return safe
    return controls
}

/** 默认采用系统建议值，手动下调只消耗建议值中可调的一段。 */
internal fun fixedKeyboardBottomInsetPx(insets: WindowInsets, window: Window, normalPx: Int,
    minimumPx: Int = fixedKeyboardMinimumInsetPx(insets, window, normalPx)): Int {
    val context = window.context
    val adjustment = SettingsPreferences.getKeyboardBottomAdjustmentDp(context).coerceAtMost(0)
    // 贴底时「调低」从贴底位置再往下调，可以一直到 0（识别不出手势导航的手机也能自己去掉那段）。
    if (SettingsPreferences.edgeToEdgeKeyboard(context))
        return (minimumPx + adjustment * context.resources.displayMetrics.density).roundToInt().coerceAtLeast(0)
    return (normalPx + adjustment * context.resources.displayMetrics.density).roundToInt().coerceAtLeast(minimumPx)
}

/**
 * 手势导航时系统底部手势区的上沿（px，[view] 所在窗口的坐标）：从这里往下按住上滑会被系统当成回桌面。
 * 三键导航、Android 9 或读不到时为 null。
 * 小米、vivo、OPPO、华为等的全面屏手势不一定写系统的 navigation_mode，也不一定给输入法窗口报手势区，
 * 所以同时读各家自己的开关（[vendorGestureNavigationOn]），手势区报得太小时按 [GESTURE_ZONE_MIN_DP] 算。
 */
internal fun bottomGestureZoneTopInWindowPx(view: android.view.View): Int? {
    val insets = view.rootWindowInsets ?: return null
    return bottomGestureZonePx(view.context, insets)?.let { view.rootView.height - it }
}

/** 悬浮拖条需要可以接收整段拖动。优先用系统报告的强制手势区，缺报才用厂商回退。 */
internal fun floatingMinimumInsetPx(window: Window, insets: WindowInsets, minimumPx: Int): Int =
    maxOf(minimumPx, bottomGestureZonePx(window.context, insets, preferReported = true) ?: 0)

private fun bottomGestureZonePx(context: Context, insets: WindowInsets, preferReported: Boolean = false): Int? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
    return try {
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
        if (preferReported && zone > 0) return zone
        val resolver = context.contentResolver
        val navigationMode = readSetting { android.provider.Settings.Secure.getInt(resolver, "navigation_mode") }
        val vendorGesture = vendorGestureNavigationOn(Build.MANUFACTURER) { global, key ->
            readSetting {
                if (global) android.provider.Settings.Global.getInt(resolver, key)
                else android.provider.Settings.Secure.getInt(resolver, key)
            }
        }
        val minZonePx = (GESTURE_ZONE_MIN_DP * context.resources.displayMetrics.density).roundToInt()
        bottomGestureZoneHeightPx(zone, tappable, navigationMode, vendorGesture, minZonePx)
    } catch (e: Exception) { null }
}

/** 手势导航时底部手势区至少按这么高（dp）算；系统没报或报得比这小时用它。 */
internal const val GESTURE_ZONE_MIN_DP = 32f

/**
 * 底部手势区高度（px），不是手势导航时为 null。
 * 手势导航：系统 navigation_mode 为 2、厂商自己的全面屏手势开着，或两者都读不到且底部没有可点的系统按钮。
 */
internal fun bottomGestureZoneHeightPx(
    zonePx: Int,
    tappablePx: Int,
    navigationMode: Int?,
    vendorGesture: Boolean?,
    minZonePx: Int,
): Int? {
    val gesture = navigationMode == 2 || vendorGesture == true ||
        (navigationMode == null && vendorGesture == null && tappablePx <= 0 && zonePx > 0)
    return if (gesture) maxOf(zonePx, minZonePx) else null
}

/**
 * 厂商自己的全面屏手势开关：开着为 true，关着为 false，不是这些厂商或读不到为 null。
 * 小米 force_fsg_nav_bar（Global）、vivo navigation_gesture_on、OPPO/一加/realme hide_navigationbar_enable、
 * 华为/荣耀 secure_gesture_navigation（Secure）；值非 0 即手势。
 */
internal fun vendorGestureNavigationOn(
    manufacturer: String?,
    read: (global: Boolean, key: String) -> Int?,
): Boolean? {
    val (global, key) = when (manufacturer?.lowercase()) {
        "xiaomi", "redmi", "poco" -> true to "force_fsg_nav_bar"
        "vivo", "iqoo" -> false to "navigation_gesture_on"
        "oppo", "oneplus", "realme" -> false to "hide_navigationbar_enable"
        "huawei", "honor" -> false to "secure_gesture_navigation"
        else -> return null
    }
    return read(global, key)?.let { it != 0 }
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
