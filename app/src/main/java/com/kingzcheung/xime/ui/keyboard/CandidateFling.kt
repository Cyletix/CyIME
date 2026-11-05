package com.kingzcheung.xime.ui.keyboard

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.spring
import androidx.compose.animation.splineBasedDecay
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.snapping.SnapLayoutInfoProvider
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.snapFlingBehavior
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import kotlin.math.abs

/**
 * 候选滑动的减速倍数（用户 11-05：松手后惯性太大，要等一会儿才停，不好选）。
 * 系统默认的滑动摩擦乘这个倍数：同样的甩动，停下用的时间和滑过的距离都只剩约 36%
 * （4^-0.736，系统样条减速的距离、时长都跟摩擦的 -0.736 次方成正比）。
 * 只用于候选栏和展开候选列表，别的滚动区不受影响。
 */
internal const val CANDIDATE_FLING_FRICTION = 4f

/**
 * 系统样条减速里摩擦只以「摩擦 × 屏幕密度」的乘积出现，把密度乘 [CANDIDATE_FLING_FRICTION]
 * 就等于把摩擦乘这个倍数，曲线形状照旧，只是更快停住。
 */
internal fun candidateFlingDecay(density: Float): DecayAnimationSpec<Float> =
    splineBasedDecay(Density(density * CANDIDATE_FLING_FRICTION))

@Composable
private fun rememberCandidateFlingDecay(): DecayAnimationSpec<Float> {
    val density = LocalDensity.current.density
    return remember(density) { candidateFlingDecay(density) }
}

/** 不对齐的候选滑动（展开候选列表、露出长候选时的候选栏）。 */
@Composable
internal fun rememberCandidateFlingBehavior(): FlingBehavior {
    val decay = rememberCandidateFlingDecay()
    return remember(decay) { CandidateFlingBehavior(decay) }
}

/** 松手后对齐候选开头的候选栏滑动；减速同上，最后对齐也收得更快，不再慢慢晃到位。 */
@Composable
internal fun rememberCandidateSnapFlingBehavior(state: LazyListState, snapPosition: SnapPosition): FlingBehavior {
    val decay = rememberCandidateFlingDecay()
    val layoutInfo = remember(state, snapPosition) { SnapLayoutInfoProvider(state, snapPosition) }
    return remember(layoutInfo, decay) {
        snapFlingBehavior(layoutInfo, decay, spring(stiffness = Spring.StiffnessMedium))
    }
}

private class CandidateFlingBehavior(private val decay: DecayAnimationSpec<Float>) : FlingBehavior {
    override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
        if (abs(initialVelocity) <= 1f) return initialVelocity
        var velocityLeft = initialVelocity
        var lastValue = 0f
        AnimationState(initialValue = 0f, initialVelocity = initialVelocity).animateDecay(decay) {
            val delta = value - lastValue
            val consumed = scrollBy(delta)
            lastValue = value
            velocityLeft = velocity
            // 滑到头了，剩下的速度交还给外层（和系统默认滑动一样）。
            if (abs(delta - consumed) > 0.5f) cancelAnimation()
        }
        return velocityLeft
    }
}
