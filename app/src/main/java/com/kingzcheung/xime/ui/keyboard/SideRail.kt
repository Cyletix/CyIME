package com.kingzcheung.xime.ui.keyboard

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background

/**
 * 侧栏内容超出时在右缘画一条细滚动条，告诉人这一列还能上下滑（用户 10-30 反馈：九键左栏只看到四个标点、不知道能滑）。
 * 条目等高时按条目数算位置；放不满时不画。
 */
internal fun Modifier.railScrollIndicator(state: LazyListState, color: Color): Modifier = drawWithContent {
    drawContent()
    val info = state.layoutInfo
    val total = info.totalItemsCount
    val first = info.visibleItemsInfo.firstOrNull() ?: return@drawWithContent
    val itemHeight = first.size.toFloat().takeIf { it > 0f } ?: return@drawWithContent
    val contentHeight = itemHeight * total
    val viewport = (info.viewportEndOffset - info.viewportStartOffset).toFloat()
    if (contentHeight <= viewport + 1f) return@drawWithContent
    val inset = 4.dp.toPx()
    val track = size.height - inset * 2
    val thumb = (track * viewport / contentHeight).coerceAtLeast(12.dp.toPx()).coerceAtMost(track)
    val scrolled = (state.firstVisibleItemIndex * itemHeight + state.firstVisibleItemScrollOffset)
        .coerceIn(0f, contentHeight - viewport)
    val top = inset + (track - thumb) * scrolled / (contentHeight - viewport)
    val width = 2.5.dp.toPx()
    drawRoundRect(color, topLeft = Offset(size.width - width - 2.dp.toPx(), top), size = Size(width, thumb),
        cornerRadius = CornerRadius(width / 2))
}

/** 侧栏末尾的「编辑」格：点一下打开「按键符号」里这一栏的编辑页（用户 10-30：能调整符号但别人找不到）。 */
@Composable
internal fun SideRailEditItem(target: SideSymbolTarget, textColor: Color, backgroundColor: Color, iconSize: Dp,
    onPress: (() -> Unit)?, onOpen: (Context) -> Unit = { openSideSymbolEditor(it, target) },
    modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var pressed by remember { mutableStateOf(false) }
    val currentOnPress by rememberUpdatedState(onPress)
    val frosted = LocalKeyboardInputPreferences.current.frostedGlass
    Box(modifier
        .background(if (pressed) frostedKeyColor(backgroundColor, textColor, frosted,
            legacyStateColor = backgroundColor.copy(alpha = 0.7f), pressed = true) else Color.Transparent)
        .pointerInput(target) {
            detectTapGestures(onPress = {
                pressed = true
                currentOnPress?.invoke()
                tryAwaitRelease()
                pressed = false
            }, onTap = { onOpen(context) })
        }, contentAlignment = Alignment.Center) {
        Icon(Icons.Filled.Edit, contentDescription = "编辑${target.label}", tint = textColor.copy(alpha = 0.6f),
            modifier = Modifier.size(iconSize))
    }
}

internal fun openSideSymbolEditor(context: Context, target: SideSymbolTarget) {
    runCatching {
        context.startActivity(Intent(context, com.kingzcheung.xime.CustomLayoutActivity::class.java)
            .putExtra("edit_symbols", true).putExtra("symbol_target", target.key)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
