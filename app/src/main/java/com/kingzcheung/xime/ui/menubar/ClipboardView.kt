package com.kingzcheung.xime.ui.menubar

import com.kingzcheung.xime.ui.keyboard.keyboardPanelBackground
import com.kingzcheung.xime.ui.keyboard.LocalKeyboardInputPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Constraints
import com.kingzcheung.xime.clipboard.ClipboardItem
import com.kingzcheung.xime.viewmodel.KeyboardViewModel
import kotlin.math.max

@Composable
fun ClipboardView(
    clipboardItems: List<ClipboardItem>,
    quickSendItems: List<ClipboardItem>,
    selectedTab: Int,
    backgroundColor: Color,
    keyTextColor: Color,
    keyBgColor: Color,
    viewModel: KeyboardViewModel,
    onSelectItem: (String) -> Unit,
    onSplitWords: (String, Long) -> Unit,
    onBack: (() -> Unit)? = null,
    onClipboardTabChange: ((Int) -> Unit)? = null,
    bottomPaddingDp: Int = 0,
    modifier: Modifier = Modifier,
    onQuickSendAddClick: (() -> Unit)? = null,
    onQuickSendEditItem: ((Long, String, String) -> Unit)? = null,
    onPullRemote: (() -> Unit)? = null,
    pullRemoteAvailable: Boolean = false,
    onImageSelect: ((com.kingzcheung.xime.clipboard.ClipboardImage) -> Unit)? = null,
    onImageShare: ((com.kingzcheung.xime.clipboard.ClipboardImage, String?) -> Unit)? = null,
    onSystemImagePaste: (() -> Unit)? = null,
    imagesExpanded: Boolean = false,
    onExpandImages: (() -> Unit)? = null,
    onCollapseImages: (() -> Unit)? = null,
    onRecognizeDocument: (() -> Unit)? = null,
    onOpenSearch: (() -> Unit)? = null,
) {
    // Old tab 1 bookmarks now open the same pinned clipboard records.
    ConnectedClipboardBoard(clipboardItems, selectedTab == 2, imagesExpanded,
        onBack = { onBack?.invoke() }, onQuickSend = {},
        onSelectText = onSelectItem, onRemoveText = viewModel::removeClipboardItems,
        onAddQuick = {}, onSplit = onSplitWords,
        onImageSelect = { onImageSelect?.invoke(it) },
        onImageShare = { image, pkg -> onImageShare?.invoke(image, pkg) },
        onSystemPaste = { onSystemImagePaste?.invoke() },
        onPullRemote = if (pullRemoteAvailable) onPullRemote else null,
        onAddPinned = onQuickSendAddClick,
        onEditText = onQuickSendEditItem,
        onRecognizeDocument = onRecognizeDocument,
        onOpenSearch = onOpenSearch,
        modifier = modifier.padding(bottom = bottomPaddingDp.dp))
}

@Composable
internal fun ClearClipboardConfirmOverlay(
    itemCount: Int,
    backgroundColor: Color,
    cardBgColor: Color,
    textColor: Color,
    subTextColor: Color,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    BoxWithConstraints(
        Modifier.fillMaxSize().testTag("clipboard-clear-overlay")
            .background(backgroundColor).clickable(onClick = onCancel).padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        val density = LocalDensity.current
        val measurer = rememberTextMeasurer()
        val inheritedStyle = LocalTextStyle.current
        val cardWidth = with(density) { maxWidth.coerceAtMost(360.dp).roundToPx() }
        val contentWidth = (cardWidth - with(density) { 24.dp.roundToPx() }).coerceAtLeast(1)
        fun textHeight(text: String, style: TextStyle, width: Int = contentWidth): Int = measurer.measure(
            text, style = inheritedStyle.merge(style), constraints = Constraints(maxWidth = width)
        ).size.height
        val titleHeight = textHeight("清空剪贴板", TextStyle(fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium))
        val messageHeight = textHeight("将删除全部 $itemCount 条剪贴板记录", TextStyle(fontSize = 13.sp, lineHeight = 18.sp))
        val buttonTextHeight = textHeight("清空", TextStyle(fontSize = 13.sp, lineHeight = 16.sp),
            ((contentWidth - with(density) { 12.dp.roundToPx() }) / 2 - with(density) { 16.dp.roundToPx() }).coerceAtLeast(1))
        val buttonHeight = maxOf(with(density) { 48.dp.roundToPx() }, buttonTextHeight + with(density) { 16.dp.roundToPx() })
        // A short panel uses a complete, concise question instead of a half-visible
        // paragraph. Keep the count available to accessibility services in both forms.
        val compact = constraints.maxHeight < titleHeight + messageHeight + buttonHeight + with(density) { 40.dp.roundToPx() }
        val compactTitleHeight = textHeight("清空剪贴板？", TextStyle(fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
            (cardWidth - with(density) { 16.dp.roundToPx() }).coerceAtLeast(1))
        val scrollWholeCard = constraints.maxHeight < compactTitleHeight + buttonHeight + with(density) { 4.dp.roundToPx() }
        Surface(
            modifier = Modifier.widthIn(max = 360.dp).fillMaxWidth().testTag("clipboard-clear-card"),
            shape = RoundedCornerShape(16.dp), color = cardBgColor,
            onClick = {},
        ) {
            Column(Modifier.then(if (scrollWholeCard) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .padding(horizontal = if (compact) 8.dp else 12.dp,
                    vertical = if (compact) 2.dp else 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Column(Modifier.then(if (scrollWholeCard) Modifier else
                    Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()))
                    .fillMaxWidth().padding(bottom = if (compact) 0.dp else 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (compact) "清空剪贴板？" else "清空剪贴板", color = textColor,
                        modifier = Modifier.semantics { contentDescription = "将删除全部 $itemCount 条剪贴板记录" },
                        fontSize = if (compact) 13.sp else 16.sp,
                        lineHeight = if (compact) 16.sp else 20.sp,
                        fontWeight = FontWeight.Medium)
                    if (!compact) {
                        Spacer(Modifier.height(8.dp))
                        Text("将删除全部 $itemCount 条剪贴板记录", color = subTextColor,
                            fontSize = 13.sp, lineHeight = 18.sp)
                    }
                }
                Row(Modifier.fillMaxWidth().testTag("clipboard-clear-actions"),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.weight(1f).heightIn(min = 48.dp).testTag("clipboard-clear-cancel")
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable(onClick = onCancel).padding(horizontal = 8.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center) {
                        Text("取消", color = textColor, fontSize = 13.sp, lineHeight = 16.sp)
                    }
                    Box(Modifier.weight(1f).heightIn(min = 48.dp).testTag("clipboard-clear-confirm")
                        .clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.error)
                        .clickable(onClick = onConfirm).padding(horizontal = 8.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center) {
                        Text("清空", color = MaterialTheme.colorScheme.onError, fontSize = 13.sp, lineHeight = 16.sp)
                    }
                }
            }
        }
    }
}

internal typealias LongPressMenuEntry = com.kingzcheung.xime.ui.common.CompactActionMenuEntry

/**
 * Long-press actions anchored beside the pressed record, as in XIME and the earliest CyIME: the
 * rest of the board is dimmed, the record stays bright, and the card opens on whichever side of
 * the record has room. Delete confirmations reuse the same card in the same place.
 */
@Composable
internal fun LongPressMenuOverlay(
    anchor: androidx.compose.ui.geometry.Rect,
    @Suppress("UNUSED_PARAMETER") occupiedBounds: List<androidx.compose.ui.geometry.Rect>,
    onDismiss: () -> Unit,
    menuItems: List<LongPressMenuEntry>,
    title: String? = null,
    message: String? = null,
    tag: String = "clipboard-item-menu",
    surfaceTag: String = "clipboard-item-actions",
    /** Row highlighted for physical-keyboard navigation; null when navigating by touch. */
    focusedIndex: Int? = null,
    /** Corner of the bright area left around [anchor]; the pressed card's own corner. */
    highlightCorner: androidx.compose.ui.unit.Dp = 8.dp,
) {
    val colors = MaterialTheme.colorScheme
    // Keyboard palette, not the Material surface: the card looks like the keys of every theme
    // (a white Material surface showed on dark iOS). It covers other records, so it stays opaque
    // even on glass, like CompactActionMenu; a translucent keycap fill let their text show through.
    val palette = com.kingzcheung.xime.ui.theme.LocalKeyboardPalette.current
    val glass = LocalKeyboardInputPreferences.current.frostedGlass
    val cardText = palette?.text ?: colors.onSurface
    val cardColor = palette?.key?.copy(alpha = 1f)
        ?: if (glass.enabled) colors.surface.copy(alpha = 1f) else colors.surfaceContainerHigh
    // 输入法窗口没有返回键分发器；无条件调用 BackHandler 会直接崩溃。
    if (androidx.activity.compose.LocalOnBackPressedDispatcherOwner.current != null)
        androidx.activity.compose.BackHandler(onBack = onDismiss)
    val density = LocalDensity.current
    val scrim = colors.scrim.copy(alpha = .32f)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Dim everything except the pressed record, which stays visible beside its actions.
        androidx.compose.foundation.Canvas(Modifier.matchParentSize().testTag("$tag-dismiss")
            .clickable(onClick = onDismiss)) {
            val hole = androidx.compose.ui.graphics.Path().apply {
                addRoundRect(androidx.compose.ui.geometry.RoundRect(anchor,
                    androidx.compose.ui.geometry.CornerRadius(highlightCorner.toPx())))
            }
            clipPath(hole, clipOp = androidx.compose.ui.graphics.ClipOp.Difference) { drawRect(scrim) }
        }
        // Draws nothing; marks the bright area so tests can compare it with the pressed card.
        Box(Modifier.offset { androidx.compose.ui.unit.IntOffset(anchor.left.toInt(), anchor.top.toInt()) }
            .size(with(density) { anchor.width.toDp() }, with(density) { anchor.height.toDp() })
            .testTag("$tag-highlight"))
        val margin = with(density) { 6.dp.toPx() }
        val gap = with(density) { 6.dp.toPx() }
        val minWidth = with(density) { (if (title != null) 220.dp else 150.dp).toPx() }
        val widthCap = with(density) { 260.dp.toPx() }
        androidx.compose.ui.layout.Layout(content = {
            Surface(Modifier.testTag(tag), shape = RoundedCornerShape(12.dp), color = cardColor, shadowElevation = 4.dp) {
                Column(Modifier.testTag(surfaceTag).verticalScroll(rememberScrollState()).padding(vertical = 6.dp)) {
                    title?.let {
                        Text(it, color = cardText, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
                    }
                    message?.let {
                        Text(it, color = cardText.copy(alpha = .7f), fontSize = 13.sp, lineHeight = 18.sp,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp))
                    }
                    menuItems.forEachIndexed { index, entry ->
                        if (index > 0 || title != null) HorizontalDivider(Modifier.padding(horizontal = 12.dp),
                            color = cardText.copy(alpha = .12f))
                        val tint = entry.tint ?: cardText
                        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .then(entry.tag?.let { Modifier.testTag(it) } ?: Modifier)
                            .background(if (index == focusedIndex) cardText.copy(alpha = .12f) else Color.Transparent)
                            .clickable(enabled = entry.enabled) { onDismiss(); entry.onClick() }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            entry.icon?.let {
                                Icon(it, null, tint = tint, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(12.dp))
                            }
                            Text(entry.label, color = tint, fontSize = 15.sp, maxLines = 1)
                        }
                    }
                }
            }
        }, modifier = Modifier.matchParentSize()) { measurables, constraints ->
            val boardWidth = constraints.maxWidth.toFloat()
            val boardHeight = constraints.maxHeight.toFloat()
            val cap = minOf(widthCap, boardWidth - 2 * margin).coerceAtLeast(1f)
            val width = measurables.single().maxIntrinsicWidth(constraints.maxHeight).toFloat()
                .coerceIn(minOf(minWidth, cap), cap)
            val card = measurables.single().measure(Constraints(
                minWidth = width.toInt(), maxWidth = width.toInt(),
                maxHeight = (boardHeight - 2 * margin).toInt().coerceAtLeast(1)))
            val right = anchor.right + gap
            val left = anchor.left - gap - card.width
            // Beside the record on the side with room; on a single narrow column, over its edge.
            val x = when {
                right + card.width <= boardWidth - margin -> right
                left >= margin -> left
                boardWidth - anchor.right >= anchor.left -> boardWidth - margin - card.width
                else -> margin
            }.coerceIn(margin, (boardWidth - margin - card.width).coerceAtLeast(margin))
            val y = anchor.top.coerceIn(margin, (boardHeight - margin - card.height).coerceAtLeast(margin))
            layout(constraints.maxWidth, constraints.maxHeight) { card.place(x.toInt(), y.toInt()) }
        }
    }
}
