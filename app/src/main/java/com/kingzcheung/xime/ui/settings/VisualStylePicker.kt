package com.kingzcheung.xime.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kingzcheung.xime.ui.theme.VisualStyle
import com.kingzcheung.xime.ui.theme.VisualStyles
import com.kingzcheung.xime.ui.theme.visualMaterial
import com.kingzcheung.xime.ui.theme.CyimeGeneratedIcon
import com.kingzcheung.xime.ui.theme.visualEnvironment

@Composable
internal fun VisualStylePicker(selected: VisualStyle, onSelect: (VisualStyle) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("视觉样式", style = MaterialTheme.typography.titleMedium)
        Text("四组材质与配色预设，即点即用。原有主题配置保留，可随时恢复。",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        VisualStyle.entries.filter { it != VisualStyle.ORIGINAL }.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { style ->
                    val palette = requireNotNull(VisualStyles.palette(style))
                    val shape = RoundedCornerShape(16.dp)
                    Column(Modifier.weight(1f).testTag("visual-style-${style.id}")
                        .semantics { this.selected = selected == style }
                        .clip(shape).background(palette.surfaceLight).visualEnvironment(style)
                        .border(if (selected == style) 2.dp else 1.dp,
                            palette.accentLight.copy(alpha = if (selected == style) 1f else .24f), shape)
                        .clickable { onSelect(style) }.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        CyimeGeneratedIcon(style, Modifier.size(46.dp).align(Alignment.CenterHorizontally), background = true)
                        Text("你好   拼音", color = palette.candidateTextColorLight, fontSize = 13.sp,
                            modifier = Modifier.fillMaxWidth().visualMaterial(style, 6.dp, panel = true).padding(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            listOf("A", "S", "↵").forEach { label ->
                                Box(Modifier.weight(1f).height(42.dp).clip(RoundedCornerShape(8.dp))
                                    .background(if (label == "↵") palette.specialKeyLight else palette.keyBgLight)
                                    .visualMaterial(style, 8.dp), contentAlignment = Alignment.Center) {
                                    Text(label, color = palette.keyTextColorLight, fontSize = 16.sp)
                                }
                            }
                        }
                        Text(style.title + if (selected == style) " · 已选" else "",
                            color = palette.keyTextColorLight, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
        TextButton(onClick = { onSelect(VisualStyle.ORIGINAL) }, modifier = Modifier.testTag("visual-style-original")) {
            Text(if (selected == VisualStyle.ORIGINAL) "原有外观 · 已选" else "恢复原有外观")
        }
        if (selected != VisualStyle.ORIGINAL) Text("当前使用预设的配色和明暗。选择下面的配色方案或显示模式，将恢复原有外观。",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
