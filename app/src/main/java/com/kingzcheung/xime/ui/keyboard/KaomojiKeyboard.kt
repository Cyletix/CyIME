package com.kingzcheung.xime.ui.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kingzcheung.xime.data.KaomojiData
import com.kingzcheung.xime.data.RecentUsageStore

@Composable
internal fun KaomojiKeyboard(onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var category by remember { mutableIntStateOf(0) }
    var recent by remember { mutableStateOf(RecentUsageStore.get(context, RecentUsageStore.KEY_RECENT_KAOMOJI)) }
    val faces = if (category == -1) recent else KaomojiData.categories[category].faces
    val colors = MaterialTheme.colorScheme
    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ToolTab("最近", category == -1, { category = -1 }, Modifier.testTag("kaomoji-recent"))
            KaomojiData.categories.forEachIndexed { index, item ->
                ToolTab(item.name, category == index, { category = index }, Modifier.testTag("kaomoji-category:$index"))
            }
        }
        if (faces.isEmpty()) Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text("まだ履歴がありません", color = colors.onSurfaceVariant, fontSize = 14.sp)
        } else LazyVerticalGrid(GridCells.Adaptive(144.dp), Modifier.weight(1f).testTag("kaomoji-grid"),
            contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(faces, key = { it }) { face ->
                Box(Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(16.dp))
                    .background(colors.surfaceContainerHigh).testTag("kaomoji:$face").clickable {
                        recent = RecentUsageStore.record(context, RecentUsageStore.KEY_RECENT_KAOMOJI, face)
                        onSelect(face)
                    }.padding(12.dp), contentAlignment = Alignment.Center) {
                    Text(face, color = colors.onSurface, fontSize = 16.sp, lineHeight = 22.sp, textAlign = TextAlign.Center)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.End) {
            ToolIcon(Icons.AutoMirrored.Filled.Backspace, "删除", { onSelect("delete") })
        }
    }
}
