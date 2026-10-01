package com.kingzcheung.xime.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kingzcheung.xime.ui.theme.MaterialLevel
import com.kingzcheung.xime.ui.theme.VisualStyle
import com.kingzcheung.xime.ui.theme.visualMaterial
import com.kingzcheung.xime.ui.theme.visualEnvironment
import com.kingzcheung.xime.ui.theme.LocalKeyboardPalette
import com.kingzcheung.xime.ui.theme.LocalMaterialPalette
import com.kingzcheung.xime.ui.theme.MaterialPalette

@Composable
internal fun VisualStylePicker(selected: VisualStyle, onSelect: (VisualStyle) -> Unit) {
    val keyboard = LocalKeyboardPalette.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("视觉样式", style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary)
        Text("只改变材质。明暗、配色和背景始终跟随主题设置。",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        VisualStyle.entries.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { style ->
                    val palette = MaterialTheme.colorScheme
                    val surface = keyboard?.background ?: palette.surface
                    val accent = keyboard?.accent ?: palette.primary
                    val key = keyboard?.key ?: palette.surfaceContainerHigh
                    val ink = keyboard?.text ?: palette.onSurface
                    val shape = RoundedCornerShape(16.dp)
                    CompositionLocalProvider(LocalMaterialPalette provides MaterialPalette(surface, accent)) {
                    Column(Modifier.weight(1f).testTag("visual-style-${style.id}")
                        .semantics { this.selected = selected == style }
                        .clip(shape).background(surface).visualEnvironment(style)
                        .border(if (selected == style) 2.dp else 1.dp,
                            accent.copy(alpha = if (selected == style) 1f else .24f), shape)
                        .clickable { onSelect(style) }.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("拼音    你好", color = keyboard?.candidateText ?: ink, fontSize = 13.sp,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 36.dp)
                                .visualMaterial(style, 6.dp, level = MaterialLevel.BASE)
                                .padding(horizontal = 7.dp, vertical = 6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            listOf("A", "S", "D").forEach { label ->
                                Box(Modifier.weight(1f).height(42.dp).clip(RoundedCornerShape(8.dp))
                                    .background(key)
                                    .visualMaterial(style, 8.dp), contentAlignment = Alignment.Center) {
                                    Text(label, color = ink, fontSize = 16.sp)
                                }
                            }
                        }
                        Text(style.title + if (selected == style) " · 已选" else "",
                            color = ink, style = MaterialTheme.typography.labelLarge)
                    }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        if (selected != VisualStyle.ORIGINAL) Text("切换明暗或配色不会关闭当前材质效果。",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
