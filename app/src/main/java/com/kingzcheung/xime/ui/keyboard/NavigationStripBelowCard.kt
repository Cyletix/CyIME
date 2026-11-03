package com.kingzcheung.xime.ui.keyboard

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt

/**
 * 固定卡片底下系统按钮那条（导航区）。[card] 是卡片在窗口里的位置。
 * [followCard] 时这条只和卡片一样宽：卡片的底色一直接到屏幕底边，两侧照旧透出应用
 * （11-03 平板实测：居中变窄的卡片底下铺了整屏宽、颜色不同的一条，看着像键盘没贴底）。
 * 卡片还没量到或不跟卡片时整宽。[surface] 画这条的背景，[onBounds] 收到实际画出来的那块（窗口坐标），给毛玻璃用。
 */
@Composable
fun BoxScope.NavigationStripBelowCard(
    height: Dp,
    card: Rect?,
    followCard: Boolean,
    onBounds: (android.graphics.Rect) -> Unit,
    surface: Modifier,
) {
    val span = card?.takeIf { followCard && it.width > 0f }
    val hostX = remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    Box(
        Modifier.fillMaxWidth().height(height).align(Alignment.BottomCenter)
            .onGloballyPositioned { hostX.floatValue = it.positionInWindow().x }
    ) {
        Box(
            (if (span == null) Modifier.fillMaxSize() else Modifier
                .offset { IntOffset((span.left - hostX.floatValue).roundToInt(), 0) }
                .width(with(density) { span.width.toDp() })
                .fillMaxSize())
                .testTag("navigation-strip")
                .onGloballyPositioned { coordinates ->
                    val position = coordinates.positionInWindow()
                    onBounds(android.graphics.Rect(
                        position.x.roundToInt(), position.y.roundToInt(),
                        (position.x + coordinates.size.width).roundToInt(),
                        (position.y + coordinates.size.height).roundToInt()))
                }
                .then(surface)
        )
    }
}
