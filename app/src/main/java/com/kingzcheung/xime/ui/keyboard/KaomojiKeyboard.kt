package com.kingzcheung.xime.ui.keyboard

import com.kingzcheung.xime.ui.theme.KeyShapes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kingzcheung.xime.data.KaomojiData
import com.kingzcheung.xime.data.RecentUsageStore
import kotlin.math.ceil

@Composable
internal fun KaomojiKeyboard(onSelect: (String) -> Unit, modifier: Modifier = Modifier,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    leadingTabs: @Composable RowScope.() -> Unit = {},
    onCategoryFeedback: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    var category by remember { mutableIntStateOf(0) }
    var recent by remember { mutableStateOf(RecentUsageStore.get(context, RecentUsageStore.KEY_RECENT_KAOMOJI)) }
    val faces = if (category == -1) recent else KaomojiData.categories[category].faces
    Column(modifier.fillMaxSize()) {
        ExpressionPanelBar(textColor, { onSelect("delete") }, leadingTabs) {
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                ExpressionTab("最近", category == -1, textColor, accentColor, Modifier.testTag("kaomoji-recent")) {
                    onCategoryFeedback?.invoke(); category = -1
                }
                KaomojiData.categories.forEachIndexed { index, item ->
                    ExpressionTab(item.name, category == index, textColor, accentColor, Modifier.testTag("kaomoji-category:$index")) {
                        onCategoryFeedback?.invoke(); category = index
                    }
                }
            }
        }
        if (faces.isEmpty()) PanelEmptyHint("暂无颜文字记录", textColor, Modifier.weight(1f).fillMaxWidth())
        else BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            // Long faces span several cells instead of wrapping onto a second line.
            val density = LocalDensity.current
            val measurer = rememberTextMeasurer()
            val style = LocalTextStyle.current.merge(TextStyle(fontSize = 16.sp))
            val widths = remember(faces, density, style) {
                faces.associateWith { measurer.measure(it, style, softWrap = false).size.width }
            }
            val gridWidth = with(density) { (maxWidth - 8.dp).toPx() }
            val gap = with(density) { 4.dp.toPx() }
            val padding = with(density) { 14.dp.toPx() }
            LazyVerticalGrid(GridCells.Adaptive(112.dp), Modifier.fillMaxSize().testTag("kaomoji-grid"),
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp), verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            items(faces, key = { it }, span = { face ->
                val cell = (gridWidth - gap * (maxLineSpan - 1)) / maxLineSpan
                val needed = (widths[face] ?: 0) + padding
                GridItemSpan(ceil((needed + gap) / (cell + gap)).toInt().coerceIn(1, maxLineSpan))
            }) { face ->
                Box(Modifier.fillMaxWidth().heightIn(min = 40.dp).clip(RoundedCornerShape(KeyShapes.current.radius(8.dp)))
                    .background(textColor.copy(alpha = .08f)).testTag("kaomoji:$face").clickable {
                        recent = RecentUsageStore.record(context, RecentUsageStore.KEY_RECENT_KAOMOJI, face)
                        onSelect(face)
                    }.padding(horizontal = 6.dp, vertical = 4.dp), contentAlignment = Alignment.Center) {
                    Text(face, color = textColor, fontSize = 16.sp, lineHeight = 22.sp, textAlign = TextAlign.Center)
                }
            }
            }
        }
    }
}
