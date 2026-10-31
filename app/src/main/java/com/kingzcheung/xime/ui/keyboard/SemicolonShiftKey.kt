package com.kingzcheung.xime.ui.keyboard

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp

/**
 * 双拼编码打了单数个键（声母或零声母的第一键已经打了，正等韵母）。分隔符 ' 和空格不算键。
 * 「ing 放在 Shift 键上」开着时，这时左下角的 Shift 键换成 ing。
 */
internal fun awaitsDoublePinyinFinal(input: String): Boolean =
    input.count { it != '\'' && it != ' ' } % 2 == 1

/**
 * 左下角那一格：[ingActive] 为真时是 ing 键（点按送分号，微软双拼的 ing），否则是平常的 [shift]。
 * 状态在这里读，打字时只重组这一格，不重组整个键盘。
 */
@Composable
internal fun SemicolonShiftKey(
    ingActive: State<Boolean>?,
    onKeyPress: (String) -> Unit,
    backgroundColor: Color,
    textColor: Color,
    modifier: Modifier,
    shadowEnabled: Boolean,
    shadowElevation: Dp,
    shadowShapeRadius: Dp,
    shift: @Composable () -> Unit,
) {
    if (ingActive?.value == true) {
        SwipeableKeyButton(
            text = "ing",
            onClick = { onKeyPress(";") },
            backgroundColor = backgroundColor,
            textColor = textColor,
            modifier = modifier.fillMaxHeight().testTag("shift-key-ing"),
            shadowEnabled = shadowEnabled,
            shadowElevation = shadowElevation,
            shadowShapeRadius = shadowShapeRadius,
        )
    } else shift()
}
