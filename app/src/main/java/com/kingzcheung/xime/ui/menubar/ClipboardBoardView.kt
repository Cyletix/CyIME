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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import com.kingzcheung.xime.ui.common.CompactActionMenu
import com.kingzcheung.xime.ui.common.CompactActionMenuEntry
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.focused
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.kingzcheung.xime.clipboard.*
import com.kingzcheung.xime.ui.keyboard.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private data class ClipboardMenuAnchor(val bounds: Rect = Rect.Zero, val occupied: List<Rect> = emptyList(), val preferSide: Boolean = true)
private data class ClipboardItemMenuAnchor(val card: ClipboardCard, val position: ClipboardMenuAnchor)

@Composable
internal fun ConnectedClipboardBoard(
    textItems: List<ClipboardItem>, initialImages: Boolean, expanded: Boolean,
    onBack: () -> Unit, onQuickSend: () -> Unit, onSelectText: (String) -> Unit,
    onRemoveText: (List<Long>) -> Unit, onAddQuick: (Long) -> Unit, onSplit: (String, Long) -> Unit,
    onImageSelect: (ClipboardImage) -> Unit, onImageShare: (ClipboardImage, String?) -> Unit,
    onSystemPaste: () -> Unit, onPullRemote: (() -> Unit)?, modifier: Modifier = Modifier,
    onAddPinned: (() -> Unit)? = null, onEditText: ((Long, String, String) -> Unit)? = null,
    onRecognizeDocument: (() -> Unit)? = null,
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
    val pins = remember(textItems, storedPins, pendingTextPins) {
        clipboardPinnedKeys(textItems, storedPins, pendingTextPins)
    }
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
            if (store.recentScreenshotsEnabled()) store.enableRecentScreenshots(false)
            else context.startActivity(Intent(context, ImageAccessActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        },
        onPick = { context.startActivity(Intent(context, ImageAccessActivity::class.java)
            .putExtra("pick_image", true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) },
        onSystemPaste = onSystemPaste, onShare = onImageShare, onPullRemote = onPullRemote, modifier = modifier,
        onAddPinned = onAddPinned, onEditText = onEditText, onDismissFailure = store::clearPasteFailure,
        onRecognizeDocument = onRecognizeDocument)
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
    onAddPinned: (() -> Unit)? = null, onEditText: ((Long, String, String) -> Unit)? = null,
    onDismissFailure: () -> Unit = {},
    onRecognizeDocument: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    var filter by remember(initialImages) { mutableStateOf(if (initialImages) ClipboardFilter.IMAGE else ClipboardFilter.ALL) }
    var menu by remember { mutableStateOf(false) }
    var moreAnchor by remember { mutableStateOf(ClipboardMenuAnchor()) }
    var deleteAnchor by remember { mutableStateOf(ClipboardMenuAnchor()) }
    val boardCoordinates = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val recordsCoordinates = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val moreBounds = remember { arrayOf(Rect.Zero) }
    val deleteBounds = remember { arrayOf(Rect.Zero) }
    var selected by remember { mutableStateOf(emptySet<String>()) }
    var selecting by remember { mutableStateOf(false) }
    var recentExpanded by remember(initialImages) { mutableStateOf(false) }
    var itemMenu by remember { mutableStateOf<ClipboardItemMenuAnchor?>(null) }
    // Physical-keyboard focus inside an open record menu or confirmation, and its entries.
    var menuFocus by remember { mutableStateOf(0) }
    val keyboardMenuEntries = remember { arrayOf<List<com.kingzcheung.xime.ui.common.CompactActionMenuEntry>>(emptyList()) }
    var deleting by remember { mutableStateOf<List<ClipboardCard>?>(null) }
    val all = remember(textItems, images, pins) { clipboardCards(textItems, images, ClipboardFilter.ALL, pins) }
    val cards = remember(all, filter) { all.filter { clipboardMatches(it, filter) } }
    val keyboard = LocalClipboardKeyboardNavigation.current
    var focusedKey by remember(keyboard?.active, filter) { mutableStateOf<String?>(null) }
    val gridState = rememberLazyStaggeredGridState()
    fun menuPosition(anchorInRoot: Rect, preferSide: Boolean): ClipboardMenuAnchor {
        val boardOrigin = boardCoordinates[0]?.boundsInRoot()?.topLeft ?: Offset.Zero
        val records = recordsCoordinates[0]?.boundsInRoot() ?: Rect.Zero
        val visible = gridState.layoutInfo.visibleItemsInfo.mapNotNull { cell ->
            if (all.none { it.key == cell.key }) return@mapNotNull null
            val rect = Rect(records.left + cell.offset.x, records.top + cell.offset.y,
                records.left + cell.offset.x + cell.size.width, records.top + cell.offset.y + cell.size.height)
                .intersect(records)
            rect.takeIf { it.width > 0 && it.height > 0 }?.translate(-boardOrigin)
        }
        return ClipboardMenuAnchor(anchorInRoot.translate(-boardOrigin), visible, preferSide)
    }
    LaunchedEffect(filter) { gridState.scrollToItem(0) }
    // Bumped when opening older copies should scroll to where they start, below the pins.
    var revealOlder by remember { mutableStateOf(0) }
    val foldScope = rememberCoroutineScope()
    // Folding first scrolls back so the 更早的记录 divider stays in view; set by the grid below.
    val olderDividerIndex = remember { intArrayOf(-1) }
    fun changeRecentExpansion(value: Boolean, reveal: Boolean = true) {
        if (value) {
            recentExpanded = true
            if (reveal) revealOlder++
        } else foldScope.launch {
            // Scroll before removing rows: reading deep in older copies, folding lands on the divider.
            val start = olderDividerIndex[0]
            if (start >= 0 && gridState.firstVisibleItemIndex > start) gridState.scrollToItem(start)
            recentExpanded = false
        }
    }
    LaunchedEffect(cards) {
        selected = selected.intersect(cards.mapTo(mutableSetOf()) { it.key })
    }
    LaunchedEffect(all, filter, recentExpanded, failure) {
        // Anchors capture the visible grid at opening time. A background copy/update
        // can move a surviving record too; do not keep an obsolete source rectangle.
        menu = false; itemMenu = null; deleting = null
    }
    DisposableEffect(keyboard) { onDispose { keyboard?.attach(null) } }
    BoxWithConstraints(modifier.fillMaxSize().keyboardPanelBackground(colors.surface).testTag("clipboard-board")
        .onGloballyPositioned { boardCoordinates[0] = it }) {
        val menuDensity = LocalDensity.current
        LaunchedEffect(constraints.maxWidth, constraints.maxHeight, menuDensity.density, menuDensity.fontScale) {
            // The source rectangles are opening-time snapshots. Resizing must not reuse stale anchors.
            menu = false; itemMenu = null; deleting = null
        }
        val columns = clipboardColumnCount(maxWidth.value)
        val compactControls = maxWidth.value / LocalDensity.current.fontScale < 360f
        val content = remember(cards, pins, columns, recentExpanded) {
            clipboardBoardContent(cards, pins, columns, recentExpanded)
        }
        val recentActuallyExpanded = recentExpanded && content.recentCollapsible
        val visibleCards = remember(content) { content.visible }
        val focused = focusedKey?.takeIf { key -> visibleCards.any { it.key == key } }
            ?: visibleCards.firstOrNull()?.key
        val gridKeys = remember(content, failure != null) {
            buildList {
                if (failure != null) add("failure")
                addAll(content.recent.map { it.key })
                if (content.recent.isNotEmpty() && content.pinned.isNotEmpty()) add("pins-end")
                addAll(content.pinned.map { it.key })
                if (content.recentCollapsible) add("older-start")
                addAll(content.older.map { it.key })
            }
        }
        LaunchedEffect(revealOlder) {
            val start = gridKeys.indexOf("older-start")
            if (revealOlder > 0 && start >= 0) gridState.animateScrollToItem(start)
        }
        SideEffect { olderDividerIndex[0] = gridKeys.indexOf("older-start") }
        LaunchedEffect(focused, keyboard?.active, gridKeys) {
            if (keyboard?.active == true) {
                val index = gridKeys.indexOf(focused)
                val visible = gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == focused }
                if (index >= 0 && (visible == null || visible.offset.y < gridState.layoutInfo.viewportStartOffset ||
                    visible.offset.y + visible.size.height > gridState.layoutInfo.viewportEndOffset)) gridState.scrollToItem(index)
            }
        }
        fun openItemMenu(card: ClipboardCard) {
            val cell = gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == card.key } ?: return
            val region = recordsCoordinates[0]?.boundsInRoot() ?: return
            val bounds = Rect(region.left + cell.offset.x, region.top + cell.offset.y,
                region.left + cell.offset.x + cell.size.width, region.top + cell.offset.y + cell.size.height)
                .intersect(region)
            menuFocus = 0
            itemMenu = ClipboardItemMenuAnchor(card, menuPosition(bounds, true))
        }
        SideEffect {
            keyboard?.attach { action ->
                val focusedCard = visibleCards.firstOrNull { it.key == (focusedKey?.takeIf { key -> key in gridKeys } ?: focused) }
                // Physical keys: Space opens the record menu, arrows move inside it, Enter runs the
                // highlighted action, Delete asks to delete the record, Esc closes the menu first.
                val openMenu = itemMenu
                if (openMenu != null || deleting != null) {
                    val entries = keyboardMenuEntries[0]
                    when (action) {
                        ClipboardNavigation.UP, ClipboardNavigation.LEFT ->
                            menuFocus = (menuFocus - 1).coerceAtLeast(0)
                        ClipboardNavigation.DOWN, ClipboardNavigation.RIGHT ->
                            menuFocus = (menuFocus + 1).coerceAtMost((entries.size - 1).coerceAtLeast(0))
                        ClipboardNavigation.CONFIRM -> entries.getOrNull(menuFocus)?.takeIf { it.enabled }?.let { entry ->
                            itemMenu = null; deleting = null; menuFocus = 0; entry.onClick()
                        }
                        ClipboardNavigation.CANCEL -> { itemMenu = null; deleting = null }
                        else -> Unit
                    }
                    return@attach true
                }
                if (menu && action == ClipboardNavigation.CANCEL) { menu = false; return@attach true }
                if (!menu && !selecting && action == ClipboardNavigation.MENU) {
                    focusedCard?.let(::openItemMenu)
                    return@attach true
                }
                if (!menu && !selecting && action == ClipboardNavigation.DELETE) {
                    focusedCard?.let { card ->
                        val cell = gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == card.key }
                        val region = recordsCoordinates[0]?.boundsInRoot()
                        if (cell != null && region != null) {
                            deleteAnchor = menuPosition(Rect(region.left + cell.offset.x, region.top + cell.offset.y,
                                region.left + cell.offset.x + cell.size.width, region.top + cell.offset.y + cell.size.height)
                                .intersect(region), true)
                            menuFocus = 0
                            deleting = listOf(card)
                        }
                    }
                    return@attach true
                }
                if (!menu && !selecting && itemMenu == null && deleting == null) {
                    if (action == ClipboardNavigation.CONFIRM) {
                        visibleCards.firstOrNull { it.key == (focusedKey?.takeIf { key -> key in gridKeys } ?: focused) }?.let { card ->
                            when (card) {
                                is ClipboardCard.Text -> onSelectText(card.item.text)
                                is ClipboardCard.Image -> onSelectImage(card.item)
                            }
                            keyboard.close()
                        }
                    } else {
                        focusedKey = nextClipboardKey(visibleCards.map { it.key }, focusedKey?.takeIf { it in gridKeys } ?: focused, action, columns,
                            gridState.layoutInfo.visibleItemsInfo.map { ClipboardCell(it.key.toString(),
                                it.offset.x, it.offset.y, it.size.width, it.size.height) })
                    }
                }
                false
            }
        }

        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().clipboardPanelExpandGesture()
                .testTag("clipboard-controls").padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically) {
                if (selecting) {
                    CompactClipboardAction(Icons.Default.Close, "退出多选", {
                        selecting = false
                        selected = emptySet()
                    }, "clipboard-exit-selection")
                    val allSelected = cards.isNotEmpty() && cards.all { it.key in selected }
                    CompactClipboardAction(
                        if (allSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        if (allSelected) "取消全选" else "全选当前分类", {
                            selected = if (allSelected) emptySet() else cards.mapTo(mutableSetOf()) { it.key }
                            // Do not operate on invisible folded pins without making the selection inspectable.
                            if (!allSelected) changeRecentExpansion(true, reveal = false)
                        }, "clipboard-select-all", enabled = cards.isNotEmpty(),
                        label = if (compactControls) null else "全选")
                    Text("已选 ${selected.size}", color = colors.onSurface,
                        fontSize = 13.sp, lineHeight = 18.sp, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(horizontal = 4.dp))
                    val unpin = selected.isNotEmpty() && selected.all { it in pins }
                    CompactClipboardAction(if (unpin) Icons.Default.LockOpen else Icons.Default.PushPin,
                        if (unpin) "取消固定所选" else "固定所选", {
                        onPinsChange(if (unpin) pins - selected else pins + selected)
                        selecting = false
                        selected = emptySet()
                    }, "clipboard-pin", enabled = selected.isNotEmpty(),
                        label = if (compactControls) null else if (unpin) "取消固定" else "固定")
                    CompactClipboardAction(Icons.Default.DeleteOutline, "删除所选",
                        { deleteAnchor = menuPosition(deleteBounds[0], false); deleting = cards.filter { it.key in selected } }, "clipboard-delete",
                        enabled = selected.isNotEmpty(), label = if (compactControls) null else "删除",
                        modifier = Modifier.onGloballyPositioned { deleteBounds[0] = it.boundsInRoot() })
                } else {
                    Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        ClipboardFilter.entries.forEach { item ->
                            CompactClipboardFilter(item.label, filter == item, { filter = item },
                                Modifier.testTag("clipboard-filter:${item.name}"))
                        }
                    }
                    if (content.recentCollapsible) {
                        RecentFoldToggle(recentActuallyExpanded, content.olderCount, compactControls) {
                            changeRecentExpansion(!recentExpanded)
                        }
                    }
                    onAddPinned?.let {
                        CompactClipboardAction(Icons.Default.Add, "新增固定内容", it, "clipboard-add-pinned")
                    }
                    CompactClipboardAction(Icons.Default.MoreHoriz, "更多操作", {
                        moreAnchor = menuPosition(moreBounds[0], false); menu = !menu
                    }, "clipboard-more", modifier = Modifier.onGloballyPositioned { moreBounds[0] = it.boundsInRoot() })
                }
            }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().testTag("clipboard-records-region")
                .onGloballyPositioned { recordsCoordinates[0] = it }
                .clipboardPanelCollapseGesture(!selecting && !menu && itemMenu == null && deleting == null) {
                    !gridState.canScrollBackward
                }
                .clipboardCategorySwipe(!selecting && !menu && itemMenu == null && deleting == null) { delta ->
                    filter = filter.afterSwipe(delta)
                }) {
                val expansion = LocalClipboardPanelExpansion.current
                val density = LocalDensity.current
                // Expanding exposes more records, not taller cards. Use the normal keyboard's
                // records area even at the expanded endpoint, so releasing cannot reflow the grid.
                val chromeHeight = remember(expansion, maxWidth, density.fontScale) {
                    expansion?.let { kotlin.math.round(it.height) - maxHeight.value } ?: 0f
                }
                val stableRecordsHeight = expansion?.let { (it.collapsedHeight - chromeHeight).coerceAtLeast(0f) }
                    ?: maxHeight.value
                val pinnedHeight = clipboardPinnedPreviewHeight(stableRecordsHeight).dp
                LazyVerticalStaggeredGrid(StaggeredGridCells.Fixed(columns), Modifier.fillMaxSize().testTag("clipboard-records"),
                    state = gridState,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp), verticalItemSpacing = 6.dp) {
                    if (failure != null) item(key = "failure", span = StaggeredGridItemSpan.FullLine) {
                        ClipboardImageFailureNotice(failure, onSystemPaste, onShare, onDismissFailure)
                    }
                    if (cards.isEmpty()) item(span = StaggeredGridItemSpan.FullLine) {
                        Text("暂无${if (filter == ClipboardFilter.ALL) "剪贴板记录" else filter.label}，复制后会显示在这里。",
                            Modifier.padding(vertical = 24.dp), color = colors.onSurfaceVariant, fontSize = 14.sp)
                    }
                    fun LazyStaggeredGridScope.records(records: List<ClipboardCard>, fixedRecords: Boolean) {
                        items(records, key = { it.key }) { card ->
                            val chosen = card.key in selected
                            ClipboardRecordCard(card = card, pinned = card.key in pins, chosen = chosen,
                                selecting = selecting, keyboardFocused = keyboard?.active == true && card.key == focused,
                                // Pictures share the pinned preview height: a tall screenshot must not fill the board.
                                previewHeight = if (fixedRecords || card is ClipboardCard.Image) pinnedHeight else null,
                                // Lazy layout retains removed keyed items through fade-out. Keep one
                                // grid so collapsing pins does not flash or replace recent history.
                                modifier = Modifier.animateItem(
                                    fadeInSpec = keyboardPanelMotion(true),
                                    placementSpec = keyboardPanelMotion(recentActuallyExpanded),
                                    fadeOutSpec = keyboardPanelMotion(false)),
                                onClick = {
                                    focusedKey = card.key
                                    if (selecting) selected = if (chosen) selected - card.key else selected + card.key
                                    else when (card) {
                                        is ClipboardCard.Text -> onSelectText(card.item.text)
                                        is ClipboardCard.Image -> onSelectImage(card.item)
                                    }
                                }, onLongClick = {
                                    if (selecting) {
                                        selected = if (chosen) selected - card.key else selected + card.key
                                    } else openItemMenu(card)
                                })
                        }
                    }
                    records(content.recent, fixedRecords = false)
                    if (content.recent.isNotEmpty() && content.pinned.isNotEmpty()) {
                        item(key = "pins-end", span = StaggeredGridItemSpan.FullLine) {
                            RecordsDivider(Icons.Default.Lock, "已固定 ${content.pinned.size} 条", colors.onSurface,
                                Modifier.animateItem().testTag("clipboard-pins-divider"))
                        }
                    }
                    records(content.pinned, fixedRecords = true)
                    if (content.recentCollapsible) {
                        item(key = "older-start", span = StaggeredGridItemSpan.FullLine) {
                            OlderRecordsDivider(recentActuallyExpanded, content.olderCount, colors.onSurface,
                                Modifier.animateItem()) { changeRecentExpansion(!recentExpanded) }
                        }
                    }
                    records(content.older, fixedRecords = false)
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
                    onEditText?.let { edit -> add(LongPressMenuEntry(Icons.Default.Edit, "编辑") {
                        edit(card.item.id, card.item.text, card.item.code)
                    }) }
                }
                add(LongPressMenuEntry(Icons.Default.DoneAll, "多选") {
                    selecting = true; selected = setOf(card.key)
                })
                add(LongPressMenuEntry(Icons.Default.DeleteOutline, "删除", colors.error) {
                    deleteAnchor = anchor.position; deleting = listOf(card)
                })
            }
            keyboardMenuEntries[0] = actions
            LongPressMenuOverlay(anchor.position.bounds, anchor.position.occupied,
                onDismiss = { itemMenu = null }, menuItems = actions,
                focusedIndex = menuFocus.takeIf { keyboard?.active == true })
        }
        if (menu) {
            val actions = buildList {
                add(CompactActionMenuEntry(Icons.Default.DoneAll, "多选", enabled = cards.isNotEmpty(), tag = "clipboard-select") {
                    selecting = true; selected = emptySet()
                })
                onAddPinned?.let { action -> add(CompactActionMenuEntry(Icons.Default.Edit, "新增固定内容", tag = "clipboard-add-text", onClick = action)) }
                add(CompactActionMenuEntry(Icons.Default.Image, "添加图片", tag = "clipboard-pick", onClick = onPick))
                onRecognizeDocument?.let { action -> add(CompactActionMenuEntry(Icons.Default.DocumentScanner,
                    "拍照识别", tag = "clipboard-ocr", onClick = action)) }
                add(CompactActionMenuEntry(Icons.Default.PhotoLibrary, if (photoAccess) "关闭最近截图" else "启用最近截图",
                    tag = "clipboard-photos", onClick = onPhotoAccess))
                onPullRemote?.let { action -> add(CompactActionMenuEntry(Icons.Default.Sync, "同步", tag = "clipboard-sync", onClick = action)) }
                add(CompactActionMenuEntry(Icons.Default.DeleteOutline, "清空未固定", colors.error,
                    enabled = all.any { it.key !in pins }, tag = "clipboard-clear") {
                    deleteAnchor = moreAnchor; deleting = all.filterNot { it.key in pins }
                })
                add(CompactActionMenuEntry(Icons.Default.Close, "关闭菜单", tag = "clipboard-close-menu") {})
            }
            CompactActionMenu(moreAnchor.bounds, actions, onDismiss = { menu = false },
                occupiedBounds = moreAnchor.occupied, preferSide = false, tag = "clipboard-more-menu",
                surfaceTag = "clipboard-board-action-card")
        }
        deleting?.let { records ->
            val imageCount = records.count { it is ClipboardCard.Image }
            val title = when (imageCount) {
                0 -> "删除 ${records.size} 条文本记录？"
                records.size -> "删除 ${records.size} 条图片记录？"
                else -> "删除 ${records.size - imageCount} 条文本和 $imageCount 条图片记录？"
            }
            val actions = listOf(
                CompactActionMenuEntry(Icons.Default.DeleteOutline, "删除", colors.error, tag = "clipboard-confirm-delete") {
                    onRemove(records); onPinsChange(pins - records.map { it.key }.toSet())
                    selected = emptySet(); selecting = false
                },
                CompactActionMenuEntry(Icons.Default.Close, "取消", tag = "clipboard-cancel-delete") {},
            )
            // Same anchored card as the record actions, in the same place, at full touch size.
            if (itemMenu == null) keyboardMenuEntries[0] = actions
            LongPressMenuOverlay(deleteAnchor.bounds, deleteAnchor.occupied, onDismiss = { deleting = null },
                menuItems = actions, title = title, focusedIndex = menuFocus.takeIf { keyboard?.active == true },
                message = if (imageCount > 0) "只移除剪贴板记录，保留原始图片。" else null,
                tag = "clipboard-delete-menu", surfaceTag = "clipboard-board-action-card")
        }
    }
}

@Composable
private fun CompactClipboardFilter(label: String, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val glass = LocalKeyboardInputPreferences.current.frostedGlass
    val bringIntoView = remember { BringIntoViewRequester() }
    LaunchedEffect(isSelected) { if (isSelected) bringIntoView.bringIntoView() }
    Box(modifier.bringIntoViewRequester(bringIntoView).heightIn(min = PanelCategoryHeight).clip(RoundedCornerShape(6.dp))
        .background(if (isSelected) frostedKeyColor(colors.secondaryContainer, colors.onSurface, glass) else Color.Transparent)
        .semantics { selected = isSelected }
        .clickable(role = Role.Tab, onClick = onClick)
        .padding(horizontal = 6.dp, vertical = PanelCategoryVerticalPadding), contentAlignment = Alignment.Center) {
        Text(label, color = if (isSelected) colors.onSecondaryContainer else colors.onSurfaceVariant,
            fontSize = 13.sp, lineHeight = 18.sp, fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
            maxLines = 1)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ClipboardRecordCard(
    card: ClipboardCard, pinned: Boolean, chosen: Boolean, selecting: Boolean,
    keyboardFocused: Boolean, previewHeight: Dp?,
    onClick: () -> Unit, onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val glass = LocalKeyboardInputPreferences.current.frostedGlass
    val highlighted = chosen || keyboardFocused
    val shape = RoundedCornerShape(12.dp)
    BoxWithConstraints(modifier.fillMaxWidth()
        .then(if (previewHeight != null) Modifier.height(previewHeight) else Modifier)
        .clip(shape)
        .background(frostedKeyColor(if (highlighted) colors.secondaryContainer else colors.surfaceContainerHigh,
            colors.onSurface, glass))
        .border(if (highlighted) 2.dp else 1.dp, if (highlighted) colors.primary else colors.outlineVariant, shape)
        .semantics { focused = keyboardFocused; if (selecting) selected = chosen }
        .testTag("clipboard-card:${card.key}")
        .combinedClickable(role = if (selecting) Role.Checkbox else Role.Button,
            onClick = onClick, onLongClick = onLongClick,
            onLongClickLabel = if (selecting) "切换选择" else "更多操作")) {
        when (card) {
            is ClipboardCard.Text -> Text(card.item.text,
                Modifier.fillMaxWidth().heightIn(min = if (previewHeight == null) 48.dp else 0.dp)
                    .padding(start = if (selecting) 28.dp else 8.dp,
                        end = if (pinned) 26.dp else 8.dp, top = 8.dp, bottom = 8.dp),
                color = if (highlighted) colors.onSecondaryContainer else colors.onSurface,
                fontSize = 16.sp, lineHeight = 23.sp,
                maxLines = if (previewHeight != null) 3 else 4,
                overflow = TextOverflow.Ellipsis)
            is ClipboardCard.Image -> {
                val context = LocalContext.current
                val density = LocalDensity.current
                val widthPx = constraints.maxWidth.coerceAtLeast(1)
                val heightPx = previewHeight?.let { with(density) { it.roundToPx() } }
                    ?: (widthPx / ClipboardThumbnailAspectRatio).roundToInt()
                val thumbnail = remember(context, card.item.uri, widthPx, heightPx) {
                    ImageRequest.Builder(context).data(card.item.uri)
                        .size(widthPx, heightPx.coerceAtLeast(1)).build()
                }
                AsyncImage(thumbnail, card.item.label, contentScale = ContentScale.Crop,
                    modifier = if (previewHeight == null) Modifier.fillMaxWidth().aspectRatio(ClipboardThumbnailAspectRatio)
                    else Modifier.fillMaxSize())
                Canvas(Modifier.matchParentSize().testTag("clipboard-image-hatch:${card.key}")) {
                    val spacing = 36.dp.toPx()
                    var x = -size.height
                    while (x < size.width) {
                        val start = Offset(x, size.height)
                        val end = Offset(x + size.height, 0f)
                        drawLine(Color.Black.copy(alpha = .14f), start, end, 1.5.dp.toPx())
                        drawLine(Color.White.copy(alpha = .16f), start, end, .5.dp.toPx())
                        x += spacing
                    }
                }
            }
        }
        if (pinned) ClipboardCardBadge(Icons.Default.Lock, "已固定",
            Modifier.align(Alignment.TopEnd).padding(4.dp), "clipboard-pin:${card.key}")
        if (selecting) {
            ClipboardCardBadge(if (chosen) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                if (chosen) "已选" else "未选", Modifier.align(Alignment.TopStart).padding(4.dp)
                    .semantics { selected = chosen; role = Role.Checkbox }, "clipboard-selection:${card.key}")
        }
    }
}

@Composable
private fun CompactClipboardAction(icon: ImageVector, description: String, onClick: () -> Unit, tag: String,
    enabled: Boolean = true, label: String? = null, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else .38f)
    Row(modifier.heightIn(min = PanelCategoryHeight).widthIn(min = 28.dp).clip(RoundedCornerShape(6.dp))
        .testTag(tag).clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .padding(horizontal = 4.dp), horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, description, Modifier.size(16.dp), tint = color)
        if (label != null) Text(label, color = color, fontSize = 12.sp, lineHeight = 18.sp,
            maxLines = 1, modifier = Modifier.padding(start = 2.dp))
    }
}

@Composable
private fun ClipboardCardBadge(icon: ImageVector, description: String, modifier: Modifier, tag: String) {
    val colors = MaterialTheme.colorScheme
    Box(modifier.testTag(tag).clip(RoundedCornerShape(4.dp))
        .background(frostedKeyColor(colors.surfaceContainerHigh, colors.onSurface,
            LocalKeyboardInputPreferences.current.frostedGlass)).padding(2.dp)) {
        Icon(icon, description, Modifier.size(16.dp), tint = colors.onSurface)
    }
}

/** 记录分组之间的分隔线：中间一个小图标加标签，如“🔒 已固定”“更早的记录”。 */
@Composable
private fun RecordsDivider(icon: ImageVector, label: String, content: Color, modifier: Modifier) {
    val line = content.copy(alpha = .22f)
    val tint = content.copy(alpha = .78f)
    Row(modifier.fillMaxWidth().heightIn(min = 28.dp), verticalAlignment = Alignment.CenterVertically) {
        HorizontalDivider(Modifier.weight(1f), thickness = .5.dp, color = line)
        Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.padding(end = 4.dp).size(14.dp), tint = tint)
            Text(label, color = tint, style = MaterialTheme.typography.labelMedium)
        }
        HorizontalDivider(Modifier.weight(1f), thickness = .5.dp, color = line)
    }
}

/**
 * 固定内容之后的“🕘 更早的记录 · N 条”分隔线，右侧的主色胶囊写明动作：
 * “展开更早的 N 条 ⌄”/“收起更早的记录 ⌃”。整条线都可点，只折叠更早的记录，最近一行和固定内容不受影响。
 */
@Composable
private fun OlderRecordsDivider(expanded: Boolean, count: Int, content: Color, modifier: Modifier, onToggle: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val line = content.copy(alpha = .22f)
    val tint = content.copy(alpha = .78f)
    Row(modifier.fillMaxWidth().heightIn(min = 36.dp).testTag("clipboard-older-divider")
        .semantics { stateDescription = if (expanded) "已展开" else "已收起" }
        .clickable(role = Role.Button, onClickLabel = if (expanded) "收起更早的记录" else "展开更早的 $count 条记录",
            onClick = onToggle),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.History, null, Modifier.padding(start = 4.dp, end = 4.dp).size(14.dp), tint = tint)
        Text("更早的记录 · $count 条", color = tint, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        HorizontalDivider(Modifier.weight(1f).padding(horizontal = 8.dp), thickness = .5.dp, color = line)
        Row(Modifier.clip(RoundedCornerShape(50))
            .background(colors.primary) // Solid even under glass: the action must stay legible.
            .testTag("clipboard-older-toggle")
            .padding(start = 10.dp, end = 6.dp, top = 3.dp, bottom = 3.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(if (expanded) "收起更早的记录" else "展开更早的 $count 条", color = colors.onPrimary,
                fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            Icon(if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, null,
                Modifier.size(18.dp), tint = colors.onPrimary)
        }
    }
}

/**
 * 工具栏右上角的同一个开关：“更早 N 条 ⌄ / 收起更早 ⌃”，固定在工具栏里不随列表滚动，
 * 展开很多更早的记录后不用翻回去也能收起。只作用于更早的记录。
 */
@Composable
private fun RecentFoldToggle(expanded: Boolean, hidden: Int, compact: Boolean, onToggle: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.padding(horizontal = 2.dp).heightIn(min = PanelCategoryHeight)
        .clip(RoundedCornerShape(50))
        .background(colors.primary) // Solid even under glass: the action must stay legible.
        .semantics { stateDescription = if (expanded) "已展开" else "已收起" }
        .clickable(role = Role.Button,
            onClickLabel = if (expanded) "收起更早的记录" else "显示更早的 $hidden 条记录", onClick = onToggle)
        .testTag("clipboard-recent-toggle")
        .padding(start = 10.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(if (expanded) (if (compact) "收起更早" else "收起更早的记录") else if (compact) "更早 $hidden" else "更早 $hidden 条",
            color = colors.onPrimary, fontSize = 13.sp, lineHeight = 18.sp,
            fontWeight = FontWeight.Medium, maxLines = 1)
        Icon(if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, null,
            Modifier.size(18.dp), tint = colors.onPrimary)
    }
}
