package com.kingzcheung.xime.ui.menubar

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.focused
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import coil.compose.AsyncImage
import com.kingzcheung.xime.clipboard.*
import com.kingzcheung.xime.ui.keyboard.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private data class ClipboardItemMenuAnchor(val card: ClipboardCard, val isLeftColumn: Boolean)

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
    var storedPins by remember { mutableStateOf(prefs.getStringSet("pins", emptySet()).orEmpty().toSet()) }
    var pendingTextPins by remember { mutableStateOf(emptyMap<Long, Boolean>()) }
    var pendingPinJobs by remember { mutableStateOf(emptyMap<Long, Job>()) }
    val scope = rememberCoroutineScope()
    val pins = clipboardPinnedKeys(textItems, storedPins, pendingTextPins)
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { store.refresh() }
    LaunchedEffect(Unit) { store.refresh() }
    LaunchedEffect(textItems, pendingPinJobs) {
        val inFlight = pendingTextPins.filterKeys { it in pendingPinJobs }
        val completed = pendingTextPins.filterKeys { it !in pendingPinJobs }
        pendingTextPins = inFlight + pendingClipboardTextPins(textItems, completed)
    }
    ClipboardBoardView(textItems, images, initialImages, expanded, pins, photoAccess, failure,
        onBack, onQuickSend, onSelectText, onImageSelect, onSplit, onAddQuick,
        onPinsChange = { updated ->
            val changedTextPins = ((pins - updated) + (updated - pins)).mapNotNull { key ->
                key.takeIf { it.startsWith("text:") }?.removePrefix("text:")?.toLongOrNull()
                    ?.let { id -> id to (key in updated) }
            }.toMap()
            pendingTextPins = pendingTextPins + changedTextPins
            storedPins = updated.filterTo(mutableSetOf()) { it.startsWith("image:") }
            synchronized(prefs) {
                val unmigratedTextPins = prefs.getStringSet("pins", emptySet()).orEmpty()
                    .filterTo(mutableSetOf()) { it.startsWith("text:") }
                prefs.edit().putStringSet("pins", storedPins + unmigratedTextPins).apply()
            }
            val manager = ClipboardManager.getInstance(context)
            changedTextPins.forEach { (id, pinned) ->
                val job = manager.setClipboardPinned(id, pinned)
                pendingPinJobs = pendingPinJobs + (id to job)
                scope.launch {
                    job.join()
                    if (pendingPinJobs[id] === job) {
                        if (job.isCancelled) {
                            pendingTextPins = pendingTextPins - id
                            Toast.makeText(context, "无法保存固定状态，请重试", Toast.LENGTH_SHORT).show()
                        }
                        pendingPinJobs = pendingPinJobs - id
                    }
                }
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
    var itemMenu by remember { mutableStateOf<ClipboardItemMenuAnchor?>(null) }
    var deleting by remember { mutableStateOf<List<ClipboardCard>?>(null) }
    val all = remember(textItems, images, pins) { clipboardCards(textItems, images, ClipboardFilter.ALL, pins) }
    val cards = remember(all, filter) { all.filter { clipboardMatches(it, filter) } }
    val expansion = LocalClipboardPanelExpansion.current
    val keyboard = LocalClipboardKeyboardNavigation.current
    var focusedKey by remember(keyboard?.active, filter) { mutableStateOf<String?>(null) }
    val focused = focusedKey?.takeIf { key -> cards.any { it.key == key } } ?: cards.firstOrNull()?.key
    val gridState = rememberLazyStaggeredGridState()
    LaunchedEffect(filter) { gridState.scrollToItem(0) }
    DisposableEffect(keyboard) { onDispose { keyboard?.attach(null) } }
    BoxWithConstraints(modifier.fillMaxSize().keyboardPanelBackground(colors.surface).testTag("clipboard-board")) {
        val boardWidthPx = constraints.maxWidth
        val columns = clipboardColumnCount(maxWidth.value)
        val gridKeys = buildList {
            if (failure != null) add("failure")
            addAll(cards.map { it.key })
        }
        LaunchedEffect(focused, keyboard?.active, gridKeys) {
            if (keyboard?.active == true) {
                val index = gridKeys.indexOf(focused)
                val visible = gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == focused }
                if (index >= 0 && (visible == null || visible.offset.y < gridState.layoutInfo.viewportStartOffset ||
                    visible.offset.y + visible.size.height > gridState.layoutInfo.viewportEndOffset)) gridState.scrollToItem(index)
            }
        }
        SideEffect {
            keyboard?.attach { action ->
                if (!menu && !selecting && itemMenu == null && deleting == null) {
                    if (action == ClipboardNavigation.CONFIRM) {
                        cards.firstOrNull { it.key == (focusedKey ?: focused) }?.let { card ->
                            when (card) {
                                is ClipboardCard.Text -> onSelectText(card.item.text)
                                is ClipboardCard.Image -> onSelectImage(card.item)
                            }
                            keyboard.close()
                        }
                    } else {
                        focusedKey = nextClipboardKey(cards.map { it.key }, focusedKey ?: focused, action, columns,
                            gridState.layoutInfo.visibleItemsInfo.map { ClipboardCell(it.key.toString(),
                                it.offset.x, it.offset.y, it.size.width, it.size.height) })
                    }
                }
            }
        }

        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().heightIn(min = 24.dp).clipboardPanelExpandGesture()
                .testTag("clipboard-controls").padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically) {
                if (selecting) {
                    CompactClipboardAction(Icons.Default.Close, "退出多选", {
                        selecting = false
                        selected = emptySet()
                    }, "clipboard-exit-selection")
                    Text("已选 ${selected.size}", color = colors.onSurface,
                        fontSize = 13.sp, lineHeight = 18.sp, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    CompactClipboardAction(Icons.Default.PushPin, "固定所选", {
                        onPinsChange(pins + selected)
                        selecting = false
                        selected = emptySet()
                    }, "clipboard-pin")
                    CompactClipboardAction(Icons.Default.DeleteOutline, "删除所选",
                        { deleting = all.filter { it.key in selected } }, "clipboard-delete")
                } else {
                    Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        ClipboardFilter.entries.forEach { item ->
                            CompactClipboardFilter(item.label, filter == item, { filter = item },
                                Modifier.testTag("clipboard-filter:${item.name}"))
                        }
                    }
                    CompactClipboardAction(if (expanded) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                        if (expanded) "收起" else "展开", { expansion?.setExpanded(!expanded) }, "clipboard-expand")
                    CompactClipboardAction(Icons.Default.MoreHoriz, "更多操作", { menu = !menu }, "clipboard-more")
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth().testTag("clipboard-records-region")
                .clipboardCategorySwipe(!selecting && !menu && itemMenu == null && deleting == null) { delta ->
                    filter = filter.afterSwipe(delta)
                }) {
                LazyVerticalStaggeredGrid(StaggeredGridCells.Fixed(columns), Modifier.fillMaxSize().testTag("clipboard-records"),
                    state = gridState,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp), verticalItemSpacing = 6.dp) {
                    if (failure != null) item(key = "failure", span = StaggeredGridItemSpan.FullLine) {
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
                    items(cards, key = { it.key }) { card ->
                        val chosen = card.key in selected
                        val pinned = card.key in pins
                        val keyboardFocused = keyboard?.active == true && card.key == focused
                        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .background(if (chosen || keyboardFocused) colors.secondaryContainer else colors.surfaceContainerHigh)
                            .border(if (chosen || keyboardFocused) 2.dp else 1.dp, if (chosen || keyboardFocused) colors.primary else colors.outlineVariant, RoundedCornerShape(12.dp))
                            .semantics { this.focused = keyboardFocused }
                            .testTag("clipboard-card:${card.key}").combinedClickable(
                                onClick = {
                                    focusedKey = card.key
                                    if (selecting) selected = if (chosen) selected - card.key else selected + card.key
                                    else when (card) {
                                        is ClipboardCard.Text -> onSelectText(card.item.text)
                                        is ClipboardCard.Image -> onSelectImage(card.item)
                                    }
                                }, onLongClick = {
                                    val cell = gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == card.key }
                                    val isLeft = cell == null || cell.offset.x + cell.size.width / 2 <= boardWidthPx / 2
                                    itemMenu = ClipboardItemMenuAnchor(card, isLeft)
                                }, onLongClickLabel = "更多操作")) {
                            when (card) {
                                is ClipboardCard.Text -> Text(card.item.text,
                                    Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(
                                        start = if (chosen) 26.dp else 8.dp,
                                        end = if (pinned) 26.dp else 8.dp,
                                        top = 8.dp, bottom = 8.dp),
                                    color = if (chosen || keyboardFocused) colors.onSecondaryContainer else colors.onSurface,
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
                            if (pinned) {
                                ClipboardCardBadge(Icons.Default.Lock, "已固定",
                                    Modifier.align(Alignment.TopEnd).padding(4.dp), "clipboard-pin:${card.key}")
                            }
                            if (chosen) {
                                ClipboardCardBadge(Icons.Default.Check, "已选",
                                    Modifier.align(Alignment.TopStart).padding(4.dp), "clipboard-selected:${card.key}")
                            }
                        }
                    }
                }
                itemMenu?.let { anchor ->
                    val card = anchor.card
                    val actions = buildList {
                        add(LongPressMenuEntry(Icons.Default.PushPin, if (card.key in pins) "取消固定" else "固定") {
                            onPinsChange(if (card.key in pins) pins - card.key else pins + card.key)
                        })
                        if (card is ClipboardCard.Text) {
                            add(LongPressMenuEntry(Icons.Default.ContentCut, "分词") { onSplit(card.item.text, card.item.id) })
                            add(LongPressMenuEntry(Icons.Outlined.StarBorder, "快捷") { onAddQuick(card.item.id) })
                        }
                        add(LongPressMenuEntry(Icons.Default.DoneAll, "多选") {
                            selecting = true
                            selected = setOf(card.key)
                        })
                        add(LongPressMenuEntry(Icons.Default.DeleteOutline, "删除", colors.error) { deleting = listOf(card) })
                    }
                    LongPressMenuOverlay(
                        text = (card as? ClipboardCard.Text)?.item?.text.orEmpty(),
                        isLeftColumn = anchor.isLeftColumn,
                        backgroundColor = colors.surface,
                        contentBgColor = colors.surfaceContainerHigh,
                        textColor = colors.onSurface,
                        onDismiss = { itemMenu = null },
                        menuItems = actions,
                        preview = if (card is ClipboardCard.Image) ({
                            Surface(
                                Modifier.fillMaxWidth().heightIn(max = 240.dp).testTag("clipboard-item-preview"),
                                shape = RoundedCornerShape(8.dp),
                                color = frostedKeyColor(colors.surfaceContainerHigh, colors.onSurface,
                                    LocalKeyboardInputPreferences.current.frostedGlass),
                            ) {
                                AsyncImage(card.item.uri, card.item.label, contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp, max = 240.dp).padding(10.dp))
                            }
                        }) else null,
                    )
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
private fun CompactClipboardFilter(label: String, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val glass = LocalKeyboardInputPreferences.current.frostedGlass
    val bringIntoView = remember { BringIntoViewRequester() }
    LaunchedEffect(isSelected) { if (isSelected) bringIntoView.bringIntoView() }
    Box(modifier.bringIntoViewRequester(bringIntoView).heightIn(min = 20.dp).clip(RoundedCornerShape(6.dp))
        .background(if (isSelected) frostedKeyColor(colors.secondaryContainer, colors.onSurface, glass) else Color.Transparent)
        .semantics { selected = isSelected }
        .clickable(role = Role.Tab, onClick = onClick)
        .padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
        Text(label, color = if (isSelected) colors.onSecondaryContainer else colors.onSurfaceVariant,
            fontSize = 13.sp, lineHeight = 18.sp, fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
            maxLines = 1)
    }
}

@Composable
private fun CompactClipboardAction(icon: ImageVector, description: String, onClick: () -> Unit, tag: String) {
    Box(Modifier.size(width = 28.dp, height = 20.dp).clip(RoundedCornerShape(6.dp))
        .testTag(tag).clickable(role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, description, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ClipboardCardBadge(icon: ImageVector, description: String, modifier: Modifier, tag: String) {
    val colors = MaterialTheme.colorScheme
    Box(modifier.clip(RoundedCornerShape(4.dp))
        .background(frostedKeyColor(colors.surfaceContainerHigh, colors.onSurface,
            LocalKeyboardInputPreferences.current.frostedGlass)).padding(2.dp)) {
        Icon(icon, description, Modifier.size(16.dp).testTag(tag), tint = colors.onSurface)
    }
}

@Composable
private fun BoxScope.BoardActionOverlay(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val colors = MaterialTheme.colorScheme
    val glass = LocalKeyboardInputPreferences.current.frostedGlass
    Box(Modifier.matchParentSize().background(colors.scrim.copy(alpha = .4f)).clickable(onClick = onDismiss))
    Surface(Modifier.align(Alignment.Center).padding(12.dp).widthIn(max = 360.dp).fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = if (glass.enabled) Color.Transparent else colors.surfaceContainerHigh,
        tonalElevation = 0.dp) {
        Column(Modifier
            .then(if (glass.enabled) Modifier.keyboardPanelBackground(colors.surface, glass) else Modifier)
            .verticalScroll(rememberScrollState()).padding(16.dp), content = content)
    }
}
