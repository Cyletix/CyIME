package com.kingzcheung.xime.ui.keyboard

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.kingzcheung.xime.keyboard.ToolbarButtonItem

/**
 * 工具栏按钮图标统一渲染：内置按钮用 ImageVector 图标；
 * 插件按钮优先显示其资源图标（本地文件，coil 加载），无图标用 label 文字兜底。
 */
@Composable
fun ToolbarButtonIcon(
    item: ToolbarButtonItem,
    tint: Color,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    fontSize: TextUnit = 16.sp,
    /** 开关按钮当前是否开启；全角/半角按状态换图标。 */
    active: Boolean = false,
) {
    when (item) {
        is ToolbarButtonItem.Builtin -> Icon(
            imageVector = if (item.button == com.kingzcheung.xime.keyboard.ToolbarButton.FULL_SHAPE && active)
                com.kingzcheung.xime.keyboard.FullWidthIcon else item.button.icon,
            contentDescription = contentDescription ?: item.stateLabel(active),
            tint = tint,
            modifier = modifier,
        )
        is ToolbarButtonItem.Plugin -> {
            val context = LocalContext.current
            val icon = item.icon
            when {
                icon?.assetName != null -> AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(icon.assetName)
                        .crossfade(true)
                        .build(),
                    contentDescription = contentDescription ?: item.label,
                    modifier = modifier,
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                )
                else -> Box(modifier = modifier, contentAlignment = Alignment.Center) {
                    Text(
                        text = icon?.text ?: item.label,
                        color = tint,
                        fontSize = fontSize,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/** 键盘上显示的名称：全角/半角开关按当前状态显示，其余按钮用原名称。 */
internal fun ToolbarButtonItem.stateLabel(active: Boolean): String =
    if (this is ToolbarButtonItem.Builtin && button == com.kingzcheung.xime.keyboard.ToolbarButton.FULL_SHAPE)
        (if (active) "全角" else "半角") else label
