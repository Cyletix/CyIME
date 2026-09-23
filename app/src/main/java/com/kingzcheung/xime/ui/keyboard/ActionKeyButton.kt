package com.kingzcheung.xime.ui.keyboard

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.KeyboardReturn
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.NavigateNext
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp

data class KeyboardKeyColors(val background: Color, val foreground: Color)
val LocalEnterKeyColors = androidx.compose.runtime.staticCompositionLocalOf<KeyboardKeyColors?> { null }
val LocalFunctionKeyColors = androidx.compose.runtime.staticCompositionLocalOf<KeyboardKeyColors?> { null }

/** 主键盘与编辑页使用同一组动作图标，文字保留为无障碍说明。 */
@Composable
fun ActionKeyButton(
    text: String,
    onClick: () -> Unit,
    backgroundColor: Color,
    textColor: Color,
    modifier: Modifier = Modifier,
    onPress: (() -> Unit)? = null,
    onRelease: (() -> Unit)? = null,
    fontSize: TextUnit = TextUnit.Unspecified,
    shadowEnabled: Boolean = true,
    shadowElevation: Dp = 1.dp,
    shadowShapeRadius: Dp = 8.dp,
) {
    val icon = when (text) {
        "删除" -> Icons.AutoMirrored.Filled.Backspace
        "搜索" -> Icons.Default.Search
        "发送" -> Icons.AutoMirrored.Filled.Send
        "下一步", "下一个" -> Icons.Default.NavigateNext
        "确定", "完成", "前往" -> Icons.Default.Check
        else -> Icons.AutoMirrored.Filled.KeyboardReturn
    }
    val enter = if (text == "删除") LocalFunctionKeyColors.current else LocalEnterKeyColors.current
    IconKeyButton(icon = rememberVectorPainter(icon), onClick = onClick,
        backgroundColor = enter?.background ?: backgroundColor, iconColor = enter?.foreground ?: textColor, modifier = modifier,
        onPress = onPress, onRelease = onRelease, contentDescription = text,
        shadowEnabled = shadowEnabled, shadowElevation = shadowElevation,
        shadowShapeRadius = shadowShapeRadius)
}
