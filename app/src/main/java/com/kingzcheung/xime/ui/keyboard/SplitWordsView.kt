package com.kingzcheung.xime.ui.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CancellationException
import com.kingzcheung.xime.settings.ClipboardWordSegmenter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kingzcheung.xime.viewmodel.KeyboardViewModel
import kotlin.math.max

@Composable
fun SplitWordsView(
    text: String,
    backgroundColor: Color,
    viewModel: KeyboardViewModel,
    onBack: () -> Unit,
    onNavigateToQuickSend: (() -> Unit)? = null,
    onConfirmText: (String) -> Unit,
    bottomPaddingDp: Int = 0,
    modifier: Modifier = Modifier
) {
    val textColor = MaterialTheme.colorScheme.onSurface
    val accentColor = MaterialTheme.colorScheme.primary
    val chipBgColor = MaterialTheme.colorScheme.surfaceContainerLow
    val context = LocalContext.current
    var splitParts by remember(text) { mutableStateOf<List<String>>(emptyList()) }
    var loading by remember(text) { mutableStateOf(true) }
    var error by remember(text) { mutableStateOf<String?>(null) }
    val selectedIndices = remember(text) { mutableStateListOf<Int>() }
    LaunchedEffect(text) {
        try { splitParts = ClipboardWordSegmenter.split(context, text) }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = "分词失败：${e.message}" }
        finally { loading = false }
    }
    val selectedText = selectedIndices.joinToString("") { splitParts[it] }

    fun addSelected(index: Int) {
        val pos = selectedIndices.binarySearch(index)
        if (pos < 0) selectedIndices.add(-(pos + 1), index)
    }

    BoxWithConstraints(modifier.fillMaxWidth().background(backgroundColor)) {
    val compactActions = maxWidth < 420.dp
    Column(Modifier.fillMaxSize()) {
        // 导航区
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 50.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "返回",
                    tint = accentColor,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = "拆词",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = textColor,
                modifier = Modifier
            )

            TextButton(
                modifier = Modifier.weight(1f),
                enabled = selectedText.isNotEmpty(),
                onClick = {
                    val text = selectedIndices.joinToString("") { splitParts[it] }
                    if (text.isNotEmpty()) {
                        viewModel.addQuickSendText(text)
                        onNavigateToQuickSend?.invoke()
                    }
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (compactActions) "快捷" else "添加到快捷发送", color = accentColor, fontSize = 13.sp, maxLines = 1)
            }
            TextButton(enabled = selectedText.isNotEmpty(), onClick = {
                onConfirmText(selectedText)
                onBack()
            }) { Text("确定", fontWeight = FontWeight.Bold) }
        }

        // 内容区（白色卡片样式）
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                ) {

                    if (loading) Text("正在按词库拆词…", color = textColor)
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    // 拆词结果（支持点击 + 滑动选词）
                    val chipBounds = remember(splitParts) { mutableMapOf<Int, Rect>() }
                    var containerRootPos by remember { mutableStateOf(Offset.Zero) }

                    fun findChipAt(pos: Offset): Int? {
                        val rootPos = pos + containerRootPos
                        return chipBounds.entries.firstOrNull { (_, bounds) ->
                            bounds.contains(rootPos)
                        }?.key
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .onGloballyPositioned { containerRootPos = it.positionInRoot() }
                            .pointerInput(splitParts) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    val firstChip = findChipAt(down.position)
                                    val dragStartIndex = firstChip
                                    val isRemoveMode = firstChip != null && firstChip in selectedIndices
                                    var didDrag = false

                                    // 处理首个词块
                                    if (firstChip != null) {
                                        if (isRemoveMode) {
                                            selectedIndices.remove(firstChip)
                                        } else {
                                            addSelected(firstChip)
                                        }

                                    }

                                    // 滑动过程只修改面板内的选择
                                    do {
                                        val event = awaitPointerEvent(PointerEventPass.Final)
                                        val change = event.changes.firstOrNull() ?: break
                                        if (change.pressed) {
                                            change.consume()
                                            val chipIndex = findChipAt(change.position)
                                            if (chipIndex != null && dragStartIndex != null) {
                                                didDrag = true
                                                val from = minOf(dragStartIndex, chipIndex)
                                                val to = maxOf(dragStartIndex, chipIndex)
                                                for (i in from..to) {
                                                    if (isRemoveMode) {
                                                        selectedIndices.remove(i)
                                                    } else if (i !in selectedIndices) {
                                                        addSelected(i)
                                                    }
                                                }

                                            }
                                        } else {
                                            break
                                        }
                                    } while (true)
                                }
                            }
                    ) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            splitParts.forEachIndexed { index, part ->
                                val isSelected = index in selectedIndices
                                Box(
                                    modifier = Modifier
                                        .onGloballyPositioned { chipBounds[index] = it.boundsInRoot() }
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) accentColor else chipBgColor)
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (part.isBlank()) if (part.contains('\n')) "换行" else "空格" else part,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else textColor,
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 底部留空
        Spacer(modifier = Modifier.height(bottomPaddingDp.dp))
    }
}
}
