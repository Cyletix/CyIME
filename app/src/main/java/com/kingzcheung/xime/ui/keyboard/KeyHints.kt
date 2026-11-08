package com.kingzcheung.xime.ui.keyboard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView

/** 每毫米对应多少 dp；读不到屏幕物理尺寸时为 null，此时提示照常显示。 */
internal val LocalDpPerMillimeter = staticCompositionLocalOf<Float?> { null }

/** 键帽短边小于这个物理尺寸时，键面不再画灰色的上下滑提示；滑动输入本身不变。 */
internal const val MIN_HINT_KEY_MM = 4.2f

internal fun showKeyHints(capWidthDp: Float, capHeightDp: Float, dpPerMm: Float?): Boolean =
    dpPerMm == null || !dpPerMm.isFinite() || dpPerMm <= 0f ||
        minOf(capWidthDp, capHeightDp) / dpPerMm >= MIN_HINT_KEY_MM

/** 上下滑提示字号：手机比旧值小一档，大屏的放大幅度收敛，不和主字符抢视线。 */
internal fun swipeHintFontSp(baseSp: Float, contentScale: Float): Float =
    baseSp * 0.9f * adaptiveHintScale(contentScale).coerceAtMost(1.45f)

@Composable
internal fun rememberDpPerMillimeter(): Float? {
    val context = LocalContext.current
    val display = LocalView.current.display
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current.density
    return remember(context, display, configuration, density) {
        val dpPerMm = keyboardPhysicalMetrics(context, display, density)?.dpFromMillimeters(1.0)
        dpPerMm?.let { it * runCatching { resolutionOverrideScale(display ?: defaultDisplay(context)) }.getOrDefault(1f) }
    }
}

@Suppress("DEPRECATION")
private fun defaultDisplay(context: android.content.Context): android.view.Display? =
    context.getSystemService(android.view.WindowManager::class.java)?.defaultDisplay

/**
 * 系统把分辨率调低时（华为/荣耀「智能分辨率」、wm size），xdpi 还是面板原来的值，
 * 按它算出的键帽毫米数偏小，小屏手机上字母键的上下角标全被藏掉（#172：荣耀 V20 在 720p 下只剩数字行角标）。
 * 按当前像素和面板最高分辨率之比折回；分辨率没被调低时不变。只用于角标显示判断，不影响键盘尺寸。
 */
@Suppress("DEPRECATION")
private fun resolutionOverrideScale(display: android.view.Display?): Float {
    display ?: return 1f
    val real = android.util.DisplayMetrics().also { display.getRealMetrics(it) }
    val native = display.supportedModes.maxOfOrNull { minOf(it.physicalWidth, it.physicalHeight) } ?: return 1f
    return resolutionOverrideScale(minOf(real.widthPixels, real.heightPixels), native)
}

/** 当前短边像素比面板最高分辨率的短边小（差 2% 以上）时返回两者之比，否则 1。 */
internal fun resolutionOverrideScale(currentShortPx: Int, nativeShortPx: Int): Float =
    if (nativeShortPx <= 0 || currentShortPx <= 0 || currentShortPx >= nativeShortPx * 0.98f) 1f
    else currentShortPx.toFloat() / nativeShortPx
