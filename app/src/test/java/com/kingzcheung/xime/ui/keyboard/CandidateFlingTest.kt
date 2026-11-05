package com.kingzcheung.xime.ui.keyboard

import android.app.Application
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.core.calculateTargetValue
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.VectorizedDecayAnimationSpec
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.splineBasedDecay
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** 用户 11-05：候选滑动惯性太大。同一下甩动，候选的减速要明显比系统默认更快停住。 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class CandidateFlingTest {
    private val density = 2.75f

    private fun durationMs(spec: DecayAnimationSpec<Float>, velocity: Float): Long {
        val vectorized: VectorizedDecayAnimationSpec<AnimationVector1D> = spec.vectorize(Float.VectorConverter)
        return vectorized.getDurationNanos(AnimationVector1D(0f), AnimationVector1D(velocity)) / 1_000_000
    }

    @Test fun candidateFlingStopsInAboutAThirdOfTheDefaultTimeAndDistance() {
        val default = splineBasedDecay<Float>(Density(density))
        val candidate = candidateFlingDecay(density)
        for (velocity in listOf(3000f, 6000f, 12000f)) {
            val defaultDistance = default.calculateTargetValue(0f, velocity)
            val candidateDistance = candidate.calculateTargetValue(0f, velocity)
            assertEquals(0.36f, candidateDistance / defaultDistance, 0.02f)
            val defaultMs = durationMs(default, velocity)
            val candidateMs = durationMs(candidate, velocity)
            assertTrue("$velocity px/s: $candidateMs ms vs $defaultMs ms", candidateMs <= defaultMs * 0.4f)
        }
        // 普通一甩（6000 px/s）不到半秒就停，不再要一秒左右。
        assertTrue(durationMs(candidate, 6000f) < 450)
    }

    @Test fun flingDirectionIsKept() {
        val candidate = candidateFlingDecay(density)
        assertTrue(candidate.calculateTargetValue(0f, -6000f) < 0f)
        assertTrue(candidate.calculateTargetValue(0f, 6000f) > 0f)
    }
}
