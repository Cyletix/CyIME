package com.kingzcheung.xime.ui.keyboard

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Swipe
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 纯展示层：不消费触摸，长按中的空格继续接收移动和松手。
 * 不铺底色：下面就是键盘自己的背景（纯色、渐变、图片、毛玻璃照旧），按键由 [hiddenDuringCursorControl] 压淡。
 * 以前铺一整块不透明的主题底色，深色主题（薰衣草紫、黑白等）整个键盘变成一块黑（2026-10-31 用户反馈）。
 * [background] 保留给调用方，不再用来铺底。
 */
@Composable
fun CursorControlOverlay(@Suppress("UNUSED_PARAMETER") background: Color, foreground: Color, modifier: Modifier = Modifier) {
    Box(modifier.testTag("cursor-control-overlay"), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Default.Swipe, contentDescription = null, tint = foreground, modifier = Modifier.size(36.dp))
            Text("光标调整", color = foreground, fontSize = 18.sp)
        }
    }
}

/** 光标调整时按键和候选留下的不透明度：看得出还是这块键盘，又不和「光标调整」叠字。 */
internal const val CURSOR_CONTROL_KEY_ALPHA = 0.22f

/**
 * 光标调整时下面的按键和候选压淡，仍在原处接收长按空格的拖动和松手。
 * 只压淡不藏：键盘背景不在这一层，照常显示，整块键盘不会变成一块纯色。
 */
internal fun Modifier.hiddenDuringCursorControl(active: Boolean): Modifier =
    if (active) alpha(CURSOR_CONTROL_KEY_ALPHA) else this
