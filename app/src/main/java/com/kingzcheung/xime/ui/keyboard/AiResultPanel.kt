package com.kingzcheung.xime.ui.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kingzcheung.xime.plugin.core.api.PluginResultItem

/**
 * AI 插件多条结果全屏页面（与表情/符号页面同级，Overlay 全屏承载）。
 *
 * 顶部导航（返回 + 插件名）+ 结果列表（一行一个候选，点击上屏）+ 底部重新生成。
 * 生成中显示 loading。全部颜色由主题参数注入，适配深色与各主题。
 * 输入与生成仍在候选栏上方面板（ToolPanel）完成，本页面只承载结果选择。
 */
@Composable
fun AiResultPanel(
    title: String,
    items: List<PluginResultItem>,
    isLoading: Boolean = false,
    backgroundColor: Color,
    textColor: Color,
    accentColor: Color,
    cardBgColor: Color,
    bottomPaddingDp: Int = 0,
    onBack: () -> Unit,
    onItemClick: (PluginResultItem) -> Unit,
    onRegenerate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val iconButtonContainer = androidx.compose.ui.graphics.lerp(
        MaterialTheme.colorScheme.surface,
        MaterialTheme.colorScheme.primary,
        0.15f
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxSize()
            .background(backgroundColor)
            .padding(bottom = bottomPaddingDp.dp)
    ) {
        // 导航区：返回按钮 + 插件名
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .padding(start = 8.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(iconButtonContainer)
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowLeft,
                    contentDescription = "返回",
                    tint = textColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                color = textColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }

        // 结果列表：一行一个候选，点击上屏
        if (isLoading && items.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "生成中...",
                    color = textColor.copy(alpha = 0.6f),
                    fontSize = 13.sp,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(
                    start = 10.dp, end = 10.dp, top = 2.dp, bottom = 8.dp
                ),
            ) {
                items(items, key = { it.id }) { item ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(cardBgColor)
                            .clickable { onItemClick(item) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        Text(
                            text = item.text,
                            color = textColor,
                            fontSize = 15.sp,
                            maxLines = 6,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        // 底部：状态 + 重新生成
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = when {
                    isLoading -> "生成中..."
                    items.isEmpty() -> "暂无结果，返回面板重新生成"
                    else -> "${items.size} 条候选，点击上屏"
                },
                color = textColor.copy(alpha = 0.5f),
                fontSize = 11.sp,
            )
            if (items.isNotEmpty() && !isLoading) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(accentColor.copy(alpha = 0.15f))
                        .clickable { onRegenerate() }
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "重新生成",
                        tint = accentColor,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("重新生成", color = accentColor, fontSize = 12.sp)
                }
            }
        }
    }
}
