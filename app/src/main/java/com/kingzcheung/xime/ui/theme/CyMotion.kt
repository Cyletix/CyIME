package com.kingzcheung.xime.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer

/**
 * CyIME 统一动效规范。新动画只从这里取时长和曲线，不再在各处写数字。
 *
 * - 按下的反馈立即出现（[INSTANT]），只有松手和出现/切换才有过渡。
 * - 只改 graphicsLayer 的透明度/缩放：每帧只重画，不重组、不重新布局，打字路径不加成本。
 * - 全部基于 Compose tween：系统“移除动画”时 Compose 的动画时长缩放为 0，自动直接到位。
 */
object CyMotion {
    const val INSTANT = 0
    /** 松手恢复、气泡/弹出菜单出现、键盘页切换淡入。 */
    const val SHORT = 120
    /** 面板内切页、对话框。 */
    const val MEDIUM = 200
    /** 整块面板展开（沿用原 240 ms）。 */
    const val LONG = 240
    /** 整块面板收起，比展开快约 25%（沿用原 180 ms）。 */
    const val LONG_EXIT = 180

    /** 位移和尺寸。 */
    val Standard = FastOutSlowInEasing
    /** 出现、淡入：减速进入。 */
    val Enter = LinearOutSlowInEasing
    /** 消失、淡出：加速离开。 */
    val Exit = FastOutLinearInEasing

    fun <T> enter(duration: Int = SHORT): TweenSpec<T> = tween(duration, easing = Enter)
    fun <T> exit(duration: Int = SHORT): TweenSpec<T> = tween(duration, easing = Exit)
    fun <T> panel(expanding: Boolean): TweenSpec<T> =
        tween(if (expanding) LONG else LONG_EXIT, easing = Standard)

    /** 弹出层出现时的起始透明度和缩放。 */
    const val POPUP_FROM_ALPHA = 0f
    const val POPUP_FROM_SCALE = 0.92f
    /** 键盘页切换淡入的起始透明度：不从全透明开始，避免一闪。 */
    const val SWAP_FROM_ALPHA = 0.35f
}

/**
 * 弹出层（长按气泡、语言菜单等）出现时淡入并从 [origin] 轻微放大。
 * 只在首次组合时播放一次；内容从第一帧起就能接收触摸，不吞点击。
 */
@Composable
fun Modifier.cyPopupEnter(
    origin: TransformOrigin = TransformOrigin.Center,
    scale: Boolean = true,
): Modifier {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, CyMotion.enter()) }
    return graphicsLayer {
        val p = progress.value
        alpha = CyMotion.POPUP_FROM_ALPHA + (1f - CyMotion.POPUP_FROM_ALPHA) * p
        if (scale) {
            val s = CyMotion.POPUP_FROM_SCALE + (1f - CyMotion.POPUP_FROM_SCALE) * p
            scaleX = s
            scaleY = s
            transformOrigin = origin
        }
    }
}

/**
 * [key] 变化时（换布局、进数字页等）新内容快速淡入。新内容立即可点，不做淡出、
 * 不叠两层内容，因此不会像揭幕遮罩那样在动画期间吞掉连点。首次出现不播放。
 */
@Composable
fun Modifier.cySwapFadeIn(key: Any?): Modifier {
    // 新 key 的第一帧就用起始透明度：若等 LaunchedEffect 再降透明度，会先整帧闪现一次。
    val first = remember { booleanArrayOf(true) }
    val alpha = remember(key) {
        Animatable(if (first[0]) 1f else CyMotion.SWAP_FROM_ALPHA).also { first[0] = false }
    }
    LaunchedEffect(alpha) { alpha.animateTo(1f, CyMotion.enter()) }
    return graphicsLayer { this.alpha = alpha.value }
}
