package com.kingzcheung.xime.ui.menubar

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import coil.compose.AsyncImage
import com.kingzcheung.xime.clipboard.*
import com.kingzcheung.xime.ui.keyboard.*

@Composable
internal fun ConnectedClipboardBoard(
    textItems: List<ClipboardItem>, initialImages: Boolean, expanded: Boolean,
    onBack: () -> Unit, onQuickSend: () -> Unit, onSelectText: (String) -> Unit,
    onRemoveText: (List<Long>) -> Unit, onAddQuick: (Long) -> Unit, onSplit: (String, Long) -> Unit,
    onImageSelect: (ClipboardImage) -> Unit, onImageShare: (ClipboardImage, String?) -> Unit,
    onSystemPaste: () -> Unit, onPullRemote: (() -> Unit)?, modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val store = remember(context) { ClipboardImages.getInstance(context) }
    val prefs = remember(context) { context.getSharedPreferences("clipboard_board", Context.MODE_PRIVATE) }
    val images by store.images.collectAsState()
    val failure by store.pasteFailure.collectAsState()
    val photoAccess by store.photoAccess.collectAsState()
    var pins by remember { mutableStateOf(prefs.getStringSet("pins", emptySet()).orEmpty().toSet()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { store.refresh() }
    LaunchedEffect(Unit) { store.refresh() }
    ClipboardBoardView(textItems, images, initialImages, expanded, pins, photoAccess, failure,
        onBack, onQuickSend, onSelectText, onImageSelect, onSplit, onAddQuick,
        onPinsChange = { updated ->
            pins = updated
            prefs.edit().putStringSet("pins", updated).apply()
            val manager = ClipboardManager.getInstance(context)
            textItems.forEach { item ->
                val pinned = "text:${item.id}" in updated
                if (item.isPinned != pinned) manager.setClipboardPinned(item.id, pinned)
            }
        },
        onRemove = { cards ->
            onRemoveText(cards.filterIsInstance<ClipboardCard.Text>().map { it.item.id })
            store.removeImages(cards.filterIsInstance<ClipboardCard.Image>().map { it.item.uri }.toSet())
        },
        onPhotoAccess = {
            if (store.recentPhotosEnabled()) store.enableRecentPhotos(false)
            else context.startActivity(Intent(context, ImageAccessActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        },
        onPick = { context.startActivity(Intent(context, ImageAccessActivity::class.java)
            .putExtra("pick_image", true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) },
        onSystemPaste = onSystemPaste, onShare = onImageShare, onPullRemote = onPullRemote, modifier = modifier)
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
internal fun ClipboardBoardView(
    textItems: List<ClipboardItem>, images: List<ClipboardImage>, initialImages: Boolean, expanded: Boolean,
    pins: Set<String>, photoAccess: Boolean, failure: ImagePasteFailure?,
    onBack: () -> Unit, onQuickSend: () -> Unit, onSelectText: (String) -> Unit,
    onSelectImage: (ClipboardImage) -> Unit, onSplit: (String, Long) -> Unit, onAddQuick: (Long) -> Unit,
    onPinsChange: (Set<String>) -> Unit, onRemove: (List<ClipboardCard>) -> Unit,
    onPhotoAccess: () -> Unit, onPick: () -> Unit, onSystemPaste: () -> Unit,
    onShare: (ClipboardImage, String?) -> Unit, onPullRemote: (() -> Unit)?, modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    var filter by remember(initialImages) { mutableStateOf(if (initialImages) ClipboardFilter.IMAGE else ClipboardFilter.ALL) }
    var menu by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(emptySet<String>()) }
    var selecting by remember { mutableStateOf(false) }
    var itemMenu by remember { mutableStateOf<ClipboardCard?>(null) }
    var deleting by remember { mutableStateOf<List<ClipboardCard>?>(null) }
    val all = remember(textItems, images, pins) { clipboardCards(textItems, images, ClipboardFilter.ALL, pins) }
    val cards = remember(all, filter) { all.filter { clipboardMatches(it, filter) } }
    val expansion = LocalClipboardPanelExpansion.current
    BoxWithConstraints(modifier.fillMaxSize().background(colors.surface).testTag("clipboard-board")) {
        val columns = clipboardColumnCount(maxWidth.value)
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().clipboardPanelExpandGesture().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                ToolIcon(if (selecting) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowBack,
                    if (selecting) "退出多选" else "返回键盘", { if (selecting) { selecting = false; selected = emptySet() } else onBack() })
                Text(if (selecting) "已选 ${selected.size}" else "剪贴板", color = colors.onSurface,
                    fontSize = 18.sp, fontWeight = FontWeight.Medium, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (selecting) {
                    ToolIcon(Icons.Default.PushPin, "固定所选", { onPinsChange(pins + selected); selecting = false; selected = emptySet() }, "clipboard-pin")
                    ToolIcon(Icons.Default.DeleteOutline, "删除所选", { deleting = all.filter { it.key in selected } }, "clipboard-delete")
                } else {
                    ToolIcon(if (expanded) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                        if (expanded) "收起" else "展开", { expansion?.setExpanded(!expanded) }, "clipboard-expand")
                    ToolIcon(Icons.Default.MoreHoriz, "更多操作", { menu = !menu }, "clipboard-more")
                }
            }
            HorizontalDivider(color = colors.outlineVariant)
            Row(Modifier.fillMaxWidth().clipboardPanelExpandGesture().horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ClipboardFilter.entries.forEach { item ->
                    ToolTab(item.label, filter == item, { filter = item }, Modifier.testTag("clipboard-filter:${item.name}"))
                }
            }
            LazyVerticalStaggeredGrid(StaggeredGridCells.Fixed(columns), Modifier.weight(1f).fillMaxWidth().testTag("clipboard-records"),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp), verticalItemSpacing = 10.dp) {
                if (failure != null) item(span = StaggeredGridItemSpan.FullLine) {
                    Column(Modifier.clip(RoundedCornerShape(16.dp)).background(colors.surfaceContainerHigh).padding(12.dp)) {
                        Text("此输入框未接受图片，可尝试系统粘贴或分享。", color = colors.onSurface, fontSize = 14.sp)
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ToolTab("系统粘贴", false, onSystemPaste, Modifier.testTag("images-system-paste"))
                            ToolTab("分享图片", false, { onShare(failure.image, failure.packageName) }, Modifier.testTag("images-share"))
                        }
                    }
                }
                if (cards.isEmpty()) item(span = StaggeredGridItemSpan.FullLine) {
                    Text("暂无${if (filter == ClipboardFilter.ALL) "剪贴板记录" else filter.label}，复制后会显示在这里。",
                        Modifier.padding(vertical = 24.dp), color = colors.onSurfaceVariant, fontSize = 14.sp)
                }
                val groups = listOf("固定" to cards.filter { it.key in pins }, "最近记录" to cards.filterNot { it.key in pins })
                groups.forEach { (title, group) ->
                    if (group.isNotEmpty()) item(key = "section:$title", span = StaggeredGridItemSpan.FullLine) {
                        Text(title, color = colors.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                    items(group, key = { it.key }) { card ->
                        val chosen = card.key in selected
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                            .background(if (chosen) colors.secondaryContainer else colors.surfaceContainerHigh)
                            .border(if (chosen) 2.dp else 1.dp, if (chosen) colors.primary else colors.outlineVariant, RoundedCornerShape(18.dp))
                            .testTag("clipboard-card:${card.key}").combinedClickable(
                                onClick = {
                                    if (selecting) selected = if (chosen) selected - card.key else selected + card.key
                                    else when (card) {
                                        is ClipboardCard.Text -> onSelectText(card.item.text)
                                        is ClipboardCard.Image -> onSelectImage(card.item)
                                    }
                                }, onLongClick = { itemMenu = card }, onLongClickLabel = "更多操作")) {
                            when (card) {
                                is ClipboardCard.Text -> Text(card.item.text,
                                    Modifier.fillMaxWidth().heightIn(min = 80.dp).padding(16.dp),
                                    color = if (chosen) colors.onSecondaryContainer else colors.onSurface,
                                    fontSize = 16.sp, lineHeight = 23.sp, maxLines = if (expanded) 8 else 4, overflow = TextOverflow.Ellipsis)
                                is ClipboardCard.Image -> {
                                    var aspect by remember(card.item.uri) { mutableFloatStateOf(.8f) }
                                    AsyncImage(card.item.uri, card.item.label, contentScale = ContentScale.Crop,
                                        onSuccess = { result ->
                                            val drawable = result.result.drawable
                                            if (drawable.intrinsicHeight > 0) aspect = (drawable.intrinsicWidth.toFloat() / drawable.intrinsicHeight).coerceIn(.65f, 1.6f)
                                        }, modifier = Modifier.fillMaxWidth().aspectRatio(aspect))
                                }
                            }
                            if (chosen || card.key in pins) Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                                Icon(if (chosen) Icons.Default.Check else Icons.Default.PushPin, if (chosen) "已选" else "已固定",
                                    Modifier.size(16.dp), tint = colors.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
        if (menu) BoardActionOverlay({ menu = false }) {
            Text("剪贴板", color = colors.onSurface, fontSize = 18.sp)
            TextButton({ selecting = true; menu = false }, Modifier.testTag("clipboard-select")) { Text("选择记录") }
            TextButton({ menu = false; onQuickSend() }) { Text("快捷发送") }
            TextButton({ menu = false; onPick() }, Modifier.testTag("clipboard-pick")) { Text("添加图片") }
            TextButton({ menu = false; onPhotoAccess() }) { Text(if (photoAccess) "关闭最近照片" else "启用最近照片") }
            onPullRemote?.let { action -> TextButton({ menu = false; action() }) { Text("同步") } }
            TextButton({ deleting = all.filterNot { it.key in pins }; menu = false }, Modifier.testTag("clipboard-clear")) { Text("清空未固定") }
            TextButton({ menu = false }) { Text("取消") }
        }
        itemMenu?.let { card ->
            BoardActionOverlay({ itemMenu = null }) {
                Text("记录操作", color = colors.onSurface, fontSize = 18.sp)
                TextButton({ onPinsChange(if (card.key in pins) pins - card.key else pins + card.key); itemMenu = null }) {
                    Text(if (card.key in pins) "取消固定" else "固定")
                }
                if (card is ClipboardCard.Text) {
                    TextButton({ itemMenu = null; onSplit(card.item.text, card.item.id) }) { Text("分词") }
                    TextButton({ onAddQuick(card.item.id); itemMenu = null }) { Text("加入快捷发送") }
                }
                TextButton({ selecting = true; selected = setOf(card.key); itemMenu = null }) { Text("多选") }
                TextButton({ deleting = listOf(card); itemMenu = null }) { Text("删除", color = colors.error) }
                TextButton({ itemMenu = null }) { Text("取消") }
            }
        }
        deleting?.let { records ->
            BoardActionOverlay({ deleting = null }) {
                Text("删除 ${records.size} 条记录？", color = colors.onSurface, fontSize = 18.sp)
                Text("只移除剪贴板记录，不删除相册原图。", color = colors.onSurfaceVariant, fontSize = 14.sp)
                TextButton({ onRemove(records); onPinsChange(pins - records.map { it.key }.toSet()); deleting = null; selected = emptySet(); selecting = false },
                    Modifier.testTag("clipboard-confirm-delete")) { Text("删除", color = colors.error) }
                TextButton({ deleting = null }) { Text("取消") }
            }
        }
    }
}

@Composable
private fun BoxScope.BoardActionOverlay(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.matchParentSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = .4f)).clickable(onClick = onDismiss))
    Surface(Modifier.align(Alignment.Center).padding(12.dp).widthIn(max = 360.dp).fillMaxWidth(),
        shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), content = content)
    }
}
