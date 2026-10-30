package com.kingzcheung.xime.ui.settings

import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp

/**
 * 设置内容区最宽多少（2026-10-30 起）：按 dp 定，手机、平板、横竖屏都是同一个上限，屏幕本身更窄就铺满。
 * 以前是「窗口高 × 1.28」，平板横屏约 78%，手机横屏只剩约 55%，大小屏差得多。
 */
internal val SETTINGS_MAX_CONTENT_WIDTH = 840.dp

/** 内容区宽度：不超过 [SETTINGS_MAX_CONTENT_WIDTH]。 */
internal fun settingsContentWidth(maxWidth: Dp): Dp = minOf(maxWidth, SETTINGS_MAX_CONTENT_WIDTH)

internal val LocalSettingsGutterScroll = staticCompositionLocalOf<SettingsGutterScroll?> { null }

/** 宽屏时把设置界面限制在居中的内容区里，两侧用页面底色留空；两侧上下拖动照样滚当前页面。 */
@Composable
fun SettingsWidthLimit(content: @Composable () -> Unit) {
    val gutter = remember { SettingsGutterScroll() }
    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.TopCenter) {
        val width = settingsContentWidth(maxWidth)
        val side = (maxWidth - width) / 2
        if (side > 0.dp) {
            // 方向和 verticalScroll / LazyColumn 相同：手指往上拖，内容往上走。
            // 页面列表接了折叠标题栏的，两侧拖动也照样折叠。
            val drag = Modifier.fillMaxHeight().width(side).nestedScroll(gutter.nestedScroll)
                .scrollable(gutter, Orientation.Vertical, reverseDirection = true)
            Box(drag.align(Alignment.TopStart).testTag("settings-gutter-start"))
            Box(drag.align(Alignment.TopEnd).testTag("settings-gutter-end"))
        }
        CompositionLocalProvider(LocalSettingsGutterScroll provides gutter) {
            Box(Modifier.fillMaxHeight().width(width).testTag("settings-content")) {
                content()
            }
        }
    }
}

/**
 * 两侧留空处的拖动和甩动交给当前页面的纵向列表：页面列表用 [settingsGutterScroll] 登记，
 * 取看得见面积最大的那个，一样大取最后登记的（刚打开的页面）。没登记的页面（如手写采集画板）两侧不响应。
 */
internal class SettingsGutterScroll : ScrollableState {
    internal class Target(val state: ScrollableState, val nestedScroll: NestedScrollConnection?) {
        var bounds = Rect.Zero
    }

    internal val targets = mutableListOf<Target>()

    private fun current(): Target? = targets.asReversed()
        .filter { it.bounds.height > 0f && it.bounds.width > 0f }
        .maxByOrNull { it.bounds.width * it.bounds.height }

    internal fun target(): ScrollableState? = current()?.state

    val nestedScroll = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
            current()?.nestedScroll?.onPreScroll(available, source) ?: Offset.Zero

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
            current()?.nestedScroll?.onPostScroll(consumed, available, source) ?: Offset.Zero

        override suspend fun onPreFling(available: Velocity): Velocity =
            current()?.nestedScroll?.onPreFling(available) ?: Velocity.Zero

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity =
            current()?.nestedScroll?.onPostFling(consumed, available) ?: Velocity.Zero
    }

    override suspend fun scroll(scrollPriority: MutatePriority, block: suspend ScrollScope.() -> Unit) {
        target()?.scroll(scrollPriority, block)
    }

    override fun dispatchRawDelta(delta: Float): Float = target()?.dispatchRawDelta(delta) ?: 0f

    override val isScrollInProgress: Boolean get() = target()?.isScrollInProgress ?: false
    override val canScrollForward: Boolean get() = target()?.canScrollForward ?: false
    override val canScrollBackward: Boolean get() = target()?.canScrollBackward ?: false
}

/**
 * 把设置页的纵向列表登记给两侧留空处；放在 verticalScroll / LazyColumn 的 modifier 里，位置在滚动之前。
 * 列表接了折叠标题栏的，把同一个 [nestedScroll] 也传进来。
 */
internal fun Modifier.settingsGutterScroll(state: ScrollableState,
    nestedScroll: NestedScrollConnection? = null): Modifier = composed {
    val gutter = LocalSettingsGutterScroll.current ?: return@composed this
    val target = remember(state, nestedScroll) { SettingsGutterScroll.Target(state, nestedScroll) }
    DisposableEffect(gutter, target) {
        gutter.targets += target
        onDispose { gutter.targets -= target }
    }
    onGloballyPositioned { target.bounds = it.boundsInRoot() }
}

/** 设置页整页纵向滚动：同 verticalScroll，两侧留空处也能拖。 */
@Composable
internal fun Modifier.settingsVerticalScroll(state: ScrollState = rememberScrollState()): Modifier =
    settingsGutterScroll(state).verticalScroll(state)
