package com.kingzcheung.xime.ui.keyboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val MAX_UI_NODES = 64

/**
 * passive 纯展示面板（候选栏上方，替代 ToolPanel 的输入区）。
 *
 * 渲染插件通过 getPanelState.ui 声明的白名单节点树（声明式 UI）：
 *   section(title) / text(content, style) / metric(label, value, unit?) /
 *   divider / action(label, actionId)
 * 未知 type 降级为文本渲染；节点数截断 [MAX_UI_NODES]；不做嵌套渲染。
 */
@Composable
fun InfoPanel(
    title: String,
    nodes: List<Map<*, *>>,
    isLoading: Boolean = false,
    backgroundColor: Color,
    textColor: Color,
    accentColor: Color,
    cardBgColor: Color,
    onClose: () -> Unit,
    onAction: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val closeButtonBg = androidx.compose.ui.graphics.lerp(
        MaterialTheme.colorScheme.surface,
        MaterialTheme.colorScheme.primary,
        0.35f
    )

    CandidateBarOverlayPanel(
        heightDp = TOOL_PANEL_HEIGHT,
        backgroundColor = backgroundColor,
        cardBgColor = cardBgColor,
        closeButtonBg = closeButtonBg,
        closeButtonColor = accentColor,
        title = title,
        titleColor = textColor,
        modifier = modifier,
        onCloseClick = onClose,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            if (nodes.isEmpty()) {
                Text(
                    text = if (isLoading) "加载中..." else "暂无数据",
                    color = textColor.copy(alpha = 0.5f),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            nodes.take(MAX_UI_NODES).forEach { node ->
                val type = node["type"]?.toString() ?: ""
                when (type) {
                    "section" -> InfoSection(node["title"]?.toString() ?: "", accentColor)
                    "text" -> InfoText(node, textColor)
                    "metric" -> InfoMetric(node, textColor, accentColor)
                    "divider" -> HorizontalDivider(
                        modifier = Modifier.padding(vertical = 6.dp),
                        color = textColor.copy(alpha = 0.15f),
                    )
                    "action" -> InfoAction(node, onAction)
                    else -> Text(
                        // 未知节点类型降级：前向兼容，插件新节点跑旧宿主不崩溃
                        text = node["content"]?.toString() ?: node["title"]?.toString() ?: "",
                        color = textColor.copy(alpha = 0.7f),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoSection(title: String, accentColor: Color) {
    Text(
        text = title,
        color = accentColor,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
    )
}

@Composable
private fun InfoText(node: Map<*, *>, textColor: Color) {
    val style = node["style"]?.toString()
    val isCaption = style == "caption"
    Text(
        text = node["content"]?.toString() ?: "",
        color = textColor.copy(alpha = if (isCaption) 0.6f else 0.9f),
        fontSize = if (isCaption) 11.sp else 14.sp,
        modifier = Modifier.padding(vertical = 2.dp),
    )
}

@Composable
private fun InfoMetric(node: Map<*, *>, textColor: Color, accentColor: Color) {
    val value = node["value"]?.toString() ?: ""
    val label = node["label"]?.toString() ?: ""
    val unit = node["unit"]?.toString() ?: ""
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(text = label, color = textColor.copy(alpha = 0.6f), fontSize = 12.sp)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                color = accentColor,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )
            if (unit.isNotEmpty()) {
                Spacer(Modifier.height(0.dp))
                Text(
                    text = " $unit",
                    color = textColor.copy(alpha = 0.6f),
                    fontSize = 11.sp,
                )
            }
        }
    }
}

@Composable
private fun InfoAction(node: Map<*, *>, onAction: (String) -> Unit) {
    val actionId = node["actionId"]?.toString() ?: return
    val label = node["label"]?.toString() ?: return
    FilledTonalButton(
        onClick = { onAction(actionId) },
        modifier = Modifier
            .padding(top = 6.dp)
            .height(34.dp),
    ) {
        Text(text = label, fontSize = 12.sp)
    }
}
