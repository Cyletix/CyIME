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

/** 手势区上限（dp）：系统报的强制手势区比这还高，就不当手势条看。 */
internal const val GESTURE_ZONE_MAX_DP = 32

/**
 * 键盘自己从系统给的边衬区量出手势导航，不靠厂商开关（11-03「他能不能自己检测」）：
 * 三键导航时导航栏整条都是系统按钮，强制手势区和它一样高（平板实测 nav=90 mandatory=90）；
 * 手势导航时给输入法的是 48dp 按钮框，强制手势区只有底下手势条那一小段（≤[GESTURE_ZONE_MAX_DP] dp），
 * 而可点区域没占满整条，多出来的那段就是没有按钮的空白。
 */
internal fun insetsShowGestureNavigation(navPx: Int, mandatoryPx: Int, tappablePx: Int, density: Float): Boolean =
    mandatoryPx > 0 && mandatoryPx <= (GESTURE_ZONE_MAX_DP * density).roundToInt() &&
        navPx - mandatoryPx >= (4 * density).roundToInt() && tappablePx < navPx

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

/** The starting reserve and the lowest position the last row can actually reach, in this IME window. */
internal data class FixedBottomRange(val baselinePx: Int, val minimumPx: Int) {
    fun insetPx(adjustmentDp: Int, density: Float): Int =
        (baselinePx + adjustmentDp.coerceAtMost(0) * density).roundToInt().coerceAtLeast(minimumPx)
}

internal fun fixedKeyboardBottomRange(insets: WindowInsets, window: Window, normalPx: Int,
    edge: Boolean = SettingsPreferences.edgeToEdgeKeyboard(window.context)): FixedBottomRange {
    val cutout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) insets.displayCutout?.safeInsetBottom ?: 0 else 0
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        val bottom = maxOf(normalPx, cutout)
        return FixedBottomRange(bottom, bottom)
    }
    val context = window.context
    val resolver = context.contentResolver
    val config = context.resources.configuration
    val density = context.resources.displayMetrics.density
    @Suppress("DEPRECATION")
    val nav = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) insets.getInsets(WindowInsets.Type.navigationBars()).bottom
        else insets.systemWindowInsetBottom
    @Suppress("DEPRECATION")
    val tappable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) insets.getInsets(WindowInsets.Type.tappableElement()).bottom
        else insets.tappableElementInsets.bottom
    @Suppress("DEPRECATION")
    val mandatory = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) insets.getInsets(WindowInsets.Type.mandatorySystemGestures()).bottom
        else insets.mandatorySystemGestureInsets.bottom
    val mode = readSetting { android.provider.Settings.Secure.getInt(resolver, "navigation_mode") }
    val vendorGesture = vendorGestureNavigationOn(Build.MANUFACTURER) { global, key ->
        readSetting {
            if (global) android.provider.Settings.Global.getInt(resolver, key)
            else android.provider.Settings.Secure.getInt(resolver, key)
        }
    }
    val gesture = bottomGestureZoneHeightPx(mandatory, tappable, mode, vendorGesture, 0) != null ||
        (edge && (insetsShowGestureNavigation(nav, mandatory, tappable, density) ||
            navBarResourceShowsGestureBar(systemNavBarHeightPx(context), nav, density) && tappable < nav))
    val physical = maxOf(cutout, imeNavButtonHeightPx(window))
    val system = maxOf(normalPx, physical, tappable)
    val tabletBar = config.smallestScreenWidthDp >= 600 && nav > 0 &&
        (!gesture || tappable > 0 || nav > GESTURE_ZONE_MAX_DP * density)
    if (tabletBar || !gesture && (!edge || mode != null || vendorGesture == false)) {
        val protected = maxOf(system, nav)
        return FixedBottomRange(protected, protected)
    }
    // One UI can draw these buttons in SystemUI, outside the IME's NavigationBarFrame.
    // The gesture hint is a separate setting. A hidden or side navigation bar reserves no bottom space.
    val samsung = gestureStripVerifiedVendor(Build.MANUFACTURER)
    val hide = if (samsung) readSetting { android.provider.Settings.Global.getInt(resolver, "navigation_bar_button_to_hide_keyboard") } else null
    val picker = if (samsung) readSetting { android.provider.Settings.Secure.getInt(resolver, "show_keyboard_button") } else null
    val samsungButtons = nav > 0 && (hide == 1 || picker == 1) &&
        (config.orientation != android.content.res.Configuration.ORIENTATION_LANDSCAPE || tappable > 0)
    if (samsungButtons) {
        val protected = maxOf(physical, nav, tappable)
        return FixedBottomRange(maxOf(system, protected), protected)
    }
    if (!edge || !gesture) return FixedBottomRange(system, maxOf(physical, tappable))
    // Some gesture IMEs report the entire navigation frame as tappable even when empty.
    // Start above that ambiguous strip; explicit calibration may consume it. A smaller,
    // independently reported button area or a measured system control always stays protected.
    val minimum = if (tappable in 1 until nav) maxOf(physical, tappable) else physical
    val baseline = if (samsung && hide == 0 && picker == 0) minimum else maxOf(minimum, tappable)
    return FixedBottomRange(baseline, minimum)
}

/**
 * 固定键盘的底边自检（11-03「他如果自己检测到自己没贴底的话…能不能做到」）：固定卡片的位置每变一次，
 * 量一次输入法窗口底边离屏幕底边多远。窗口没铺到屏幕底边时，底下那条是系统画的，键盘这边的留白调到 0 也去不掉；
 * 这时去掉窗口底边的系统栏适配，让窗口铺到屏幕底边，之后照常按导航方式留位。返回量到的缺口（px）。
 */
internal fun checkImeWindowReachesScreenBottom(window: Window): Int {
    val decor = window.decorView
    if (!decor.isAttachedToWindow || decor.height <= 0) return 0
    val location = IntArray(2)
    decor.getLocationOnScreen(location)
    val gap = imeWindowBottomGapPx(screenBottomPx(window), location[1] + decor.height)
    if (gap > 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val attributes = window.attributes
        val bottom = WindowInsets.Side.BOTTOM
        if (attributes.fitInsetsSides and bottom != 0) {
            attributes.fitInsetsSides = attributes.fitInsetsSides and bottom.inv()
            window.attributes = attributes
            Log.w(INSETS_TAG, "bottom-edge: window ends ${gap}px above the screen bottom, stop fitting the bottom bar")
        }
    }
    return gap
}

/** 窗口底边离屏幕底边的缺口（px）；1 px 以内是取整误差，不算。 */
internal fun imeWindowBottomGapPx(screenBottomPx: Int, windowBottomPx: Int): Int =
    (screenBottomPx - windowBottomPx).takeIf { it > 1 } ?: 0

private fun screenBottomPx(window: Window): Int =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) window.windowManager.currentWindowMetrics.bounds.bottom
    else android.graphics.Point().also {
        @Suppress("DEPRECATION") window.windowManager.defaultDisplay.getRealSize(it)
    }.y

internal fun fixedKeyboardBottomInsetPx(insets: WindowInsets, window: Window, normalPx: Int): Int {
    val range = fixedKeyboardBottomRange(insets, window, normalPx)
    return range.insetPx(SettingsPreferences.getKeyboardBottomAdjustmentDp(window.context),
        window.context.resources.displayMetrics.density)
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
 * 华为/荣耀 secure_gesture_navigation（Secure）、三星 navigation_bar_gesture_while_hidden（Global）；值非 0 即手势。
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
        // 三星「滑动手势」：One UI 的 navigation_bar_gesture_while_hidden（Global），三键导航时为 0。
        "samsung" -> true to "navigation_bar_gesture_while_hidden"
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
