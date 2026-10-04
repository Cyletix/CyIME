package com.kingzcheung.xime.ui.menubar

import com.kingzcheung.xime.ui.keyboard.keyboardPanelBackground
import com.kingzcheung.xime.ui.keyboard.LocalKeyboardInputPreferences
import com.kingzcheung.xime.ui.keyboard.frostedKeyColor
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

data class LongPressMenuEntry(
    val icon: ImageVector,
    val label: String,
    val tint: Color? = null,
    val onClick: () -> Unit
)

@Composable
fun LongPressMenuOverlay(
    text: String,
    isLeftColumn: Boolean,
    backgroundColor: Color,
    contentBgColor: Color,
    textColor: Color,
    onDismiss: () -> Unit,
    menuItems: List<LongPressMenuEntry>,
    preview: (@Composable () -> Unit)? = null,
) {
    val cardColor = frostedKeyColor(contentBgColor, textColor, LocalKeyboardInputPreferences.current.frostedGlass)
    BoxWithConstraints(modifier = Modifier.fillMaxSize().testTag("clipboard-item-menu")) {
        val menuWidth = (104.dp * LocalDensity.current.fontScale.coerceAtLeast(1f))
            .coerceAtLeast(maxWidth / 3).coerceAtMost(maxWidth / 2)
        Box(
            modifier = Modifier
                .matchParentSize()
                .keyboardPanelBackground(backgroundColor)
                .testTag("clipboard-item-menu-dismiss")
                .clickable(onClick = onDismiss)
        )

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .then(if (isLeftColumn) Modifier.weight(1f) else Modifier.width(menuWidth))
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                if (isLeftColumn) {
                    if (preview != null) preview() else ContentCard(
                        text = text,
                        bgColor = cardColor,
                        textColor = textColor
                    )
                } else {
                    MenuCard(menuItems = menuItems, cardBgColor = cardColor, onDismiss = onDismiss)
                }
            }

            Box(
                modifier = Modifier
                    .then(if (isLeftColumn) Modifier.width(menuWidth) else Modifier.weight(1f))
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                if (isLeftColumn) {
                    MenuCard(menuItems = menuItems, cardBgColor = cardColor, onDismiss = onDismiss)
                } else {
                    if (preview != null) preview() else ContentCard(
                        text = text,
                        bgColor = cardColor,
                        textColor = textColor
                    )
                }
            }
        }
    }
}

@Composable
private fun ContentCard(
    text: String,
    bgColor: Color,
    textColor: Color,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("clipboard-item-preview"),
        shape = RoundedCornerShape(8.dp),
        color = bgColor
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            maxLines = 8,
            overflow = TextOverflow.Ellipsis,
            // 卡片高度受列表区高度限制（短键盘/横屏）：超出部分改为可滚动，不再画到屏幕外
            modifier = Modifier
                .padding(horizontal = 10.dp, vertical = 10.dp)
                .verticalScroll(rememberScrollState())
        )
    }
}

@Composable
private fun MenuCard(
    menuItems: List<LongPressMenuEntry>,
    cardBgColor: Color,
    onDismiss: () -> Unit,
) {
    // 操作菜单：高度随内容自适应并被列表区高度约束，超出时整卡可滚动，
    // 保证「删除」等最后一项在任何键盘高度下都能点到
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("clipboard-item-actions"),
        shape = RoundedCornerShape(12.dp),
        color = cardBgColor
    ) {
        Column(
            modifier = Modifier
                .padding(vertical = 6.dp)
                .verticalScroll(rememberScrollState())
        ) {
            menuItems.forEachIndexed { index, entry ->
                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                    )
                }

                LongPressMenuItem(
                    icon = entry.icon,
                    label = entry.label,
                    tint = entry.tint ?: MaterialTheme.colorScheme.onSurface,
                    onClick = {
                        onDismiss()
                        entry.onClick()
                    }
                )
            }
        }
    }
}

@Composable
fun LongPressMenuItem(
    icon: ImageVector,
    label: String,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            color = tint,
            fontSize = 14.sp
        )
    }
}
