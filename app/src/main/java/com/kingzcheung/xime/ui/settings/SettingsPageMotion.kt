package com.kingzcheung.xime.ui.settings

import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * 所有设置页共用的层级切换：进入下一级时新页从右推入、上一级往左让开；
 * 返回时当前页向右退出、上一级从左回来。系统关闭动画时直接切换。
 */
internal object SettingsPageMotion {
    private const val DURATION_MS = 300
    private fun <T> spec() = tween<T>(DURATION_MS, easing = FastOutSlowInEasing)

    // 上一级只让开四分之一宽度，和系统设置一样留出层次感，又不拖泥带水。
    val enter: EnterTransition = slideInHorizontally(spec()) { it }
    val exit: ExitTransition = slideOutHorizontally(spec()) { -it / 4 }
    val popEnter: EnterTransition = slideInHorizontally(spec()) { -it / 4 }
    val popExit: ExitTransition = slideOutHorizontally(spec()) { it }

    val push: ContentTransform get() = (enter togetherWith exit).apply { targetContentZIndex = 1f }
    val pop: ContentTransform get() = (popEnter togetherWith popExit).apply { targetContentZIndex = -1f }
    val none: ContentTransform get() = EnterTransition.None togetherWith ExitTransition.None
}

/** 系统“移除动画”/动画时长为 0 时返回 false。 */
@Composable
internal fun rememberSettingsMotionEnabled(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
    }
}

/**
 * 页内子页（同一路由里用状态切换的二级页）走与 NavHost 相同的推入/退出与预测性返回。
 * [page] 为 null 表示父页；[onBack] 负责把它置回 null。
 */
@Composable
internal fun <T> SettingsSubPages(
    page: T?,
    onBack: () -> Unit,
    content: @Composable (T?) -> Unit,
) {
    val motion = rememberSettingsMotionEnabled()
    val state = remember { SeekableTransitionState(page) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(page, motion) {
        if (state.currentState == page && state.targetState == page) return@LaunchedEffect
        if (motion) state.animateTo(page) else state.snapTo(page)
    }
    if (motion) {
        PredictiveBackHandler(enabled = page != null) { events ->
            val current = page
            try {
                events.collect { state.seekTo(it.progress, targetState = null) }
                onBack()
            } catch (e: CancellationException) {
                scope.launch { state.animateTo(current) }
                throw e
            }
        }
    } else {
        BackHandler(enabled = page != null, onBack = onBack)
    }
    rememberTransition(state).AnimatedContent(
        transitionSpec = { subPageTransform(motion) },
        contentKey = { it },
    ) { content(it) }
}

private fun <T> AnimatedContentTransitionScope<T?>.subPageTransform(motion: Boolean): ContentTransform = when {
    !motion -> SettingsPageMotion.none
    targetState == null -> SettingsPageMotion.pop
    else -> SettingsPageMotion.push
}
