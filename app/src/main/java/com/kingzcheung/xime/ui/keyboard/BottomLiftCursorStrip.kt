package com.kingzcheung.xime.ui.keyboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs

internal const val BOTTOM_LIFT_ACTION_BUTTON_MIN_HEIGHT_DP = 28

/**
 * 固定键盘的底部留白（「调节键盘」里往上拖出来的那一条）。
 * - 高度达到 [BOTTOM_LIFT_ACTION_BUTTON_MIN_HEIGHT_DP]（28dp）时，左右对称显示「切换中英文」与「剪贴板」轻按钮，
 *   直接沿用键盘底色、不加额外背景色块。
 * - 打开「底部留白滑动移动光标」后，在这一条上左右滑动移动光标，按住 Shift 滑是扩选（同长按空格）；
 *   点一下或竖着滑什么都不做，和以前一样。
 * 每一格走的是和空格同一条移光标路径（拖动中只读一次输入框，排着的格子合并执行）。
 */
@Composable
internal fun BottomLiftSpacer(
    heightDp: Int,
    cursorEnabled: Boolean,
    onCursorMove: ((Int) -> Unit)?,
    isAsciiMode: Boolean = false,
    iconColor: Color = Color.Unspecified,
    actionsEnabled: Boolean = true,
    onSwitchLanguage: (() -> Unit)? = null,
    onOpenClipboard: (() -> Unit)? = null,
) {
    if (heightDp <= 0) return
    val base = Modifier.fillMaxWidth().height(heightDp.dp)
    val showButtons = heightDp >= BOTTOM_LIFT_ACTION_BUTTON_MIN_HEIGHT_DP &&
        onSwitchLanguage != null &&
        onOpenClipboard != null
    val cursorModifier = rememberBottomLiftCursorModifier(cursorEnabled, onCursorMove)

    if (!showButtons) {
        Spacer(base.then(cursorModifier))
        return
    }

    val resolvedIconColor = if (iconColor.isSpecified) iconColor else LocalContentColor.current.copy(alpha = 0.78f)
    val iconSize = minOf(20f, heightDp * 0.62f).dp

    Box(base.then(cursorModifier)) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BottomLiftActionIcon(
                icon = Icons.Default.Language,
                contentDescription = if (isAsciiMode) "切换为中文" else "切换中英文",
                tint = resolvedIconColor,
                iconSize = iconSize,
                enabled = actionsEnabled,
                testTag = "bottom-lift-language-switch",
                onClick = onSwitchLanguage,
            )
            BottomLiftActionIcon(
                icon = Icons.Default.ContentPaste,
                contentDescription = "打开剪贴板",
                tint = resolvedIconColor,
                iconSize = iconSize,
                enabled = actionsEnabled,
                testTag = "bottom-lift-clipboard",
                onClick = onOpenClipboard,
            )
        }
    }
}

@Composable
private fun BottomLiftActionIcon(
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    iconSize: Dp,
    enabled: Boolean,
    testTag: String,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .testTag(testTag)
            .width(44.dp)
            .fillMaxHeight()
            .clip(RoundedCornerShape(10.dp))
            .clickable(
                enabled = enabled,
                role = Role.Button,
                interactionSource = interactionSource,
                indication = ripple(bounded = false, radius = 20.dp),
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(iconSize),
        )
    }
}

@Composable
private fun rememberBottomLiftCursorModifier(
    cursorEnabled: Boolean,
    onCursorMove: ((Int) -> Unit)?,
): Modifier {
    if (!cursorEnabled || onCursorMove == null) return Modifier
    val prefs = LocalKeyboardInputPreferences.current
    val currentMove by rememberUpdatedState(onCursorMove)
    val cursorUnitPx = rememberCursorUnitPx()
    val rootView = LocalView.current.rootView
    return Modifier.testTag("bottom-lift-cursor").pointerInput(prefs.cursorStepDp, prefs.cursorSwipeLines, cursorUnitPx) {
        val fallbackStepPx = prefs.cursorStepDp * cursorUnitPx
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val slop = viewConfiguration.touchSlop
            var active = false
            val steps = CursorStepAccumulator(fallbackStepPx)
            var lastX = down.position.x
            try {
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) {
                        if (active) {
                            change.consume()
                            SpaceAfterCursorGuard.mark(change.uptimeMillis)
                        }
                        break
                    }
                    val dx = change.position.x - down.position.x
                    val dy = change.position.y - down.position.y
                    if (!active) {
                        if (change.isConsumed) break
                        // 先竖着走的不是移光标（比如从这里往上划），放手不管。
                        if (abs(dy) > slop && abs(dy) > abs(dx)) break
                        if (abs(dx) <= slop) continue
                        active = true
                        lastX = change.position.x
                        SpaceCursorShift.begin()
                        CursorLineMetrics.request()
                    }
                    change.consume()
                    steps.stepPx = lineCursorStepPx(rootView.width / 2f, prefs.cursorSwipeLines,
                        CursorLineMetrics.charsPerLine, fallbackStepPx, cursorUnitPx)
                    val n = steps.move(change.position.x - lastX)
                    lastX = change.position.x
                    if (n != 0) {
                        currentMove(n)
                        SpaceAfterCursorGuard.mark(change.uptimeMillis)
                    }
                }
            } finally {
                if (active) SpaceCursorShift.end()
            }
        }
    }
}
