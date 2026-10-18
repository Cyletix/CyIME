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
        keyboardPhysicalMetrics(context, display, density)?.dpFromMillimeters(1.0)
    }
}
