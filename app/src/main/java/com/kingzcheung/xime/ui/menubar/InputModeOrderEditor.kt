package com.kingzcheung.xime.ui.menubar

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kingzcheung.xime.settings.SchemaInfo
import kotlin.math.roundToInt

/** 在排序模式下拖动方案，松手即保存；排序不改变启用状态。 */
@Composable
internal fun InputModeOrderEditor(
    schemas: List<SchemaInfo>, onReorder: (List<String>) -> Unit,
    background: Color, foreground: Color, accent: Color, modifier: Modifier = Modifier,
) {
    var ordered by remember { mutableStateOf(schemas) }
    val latestSchemas by rememberUpdatedState(schemas)
    val save by rememberUpdatedState(onReorder)
    var dragging by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(schemas) { if (dragging == null) ordered = schemas }
    val haptic = LocalHapticFeedback.current
    val rowHeight = with(LocalDensity.current) { 48.dp.toPx() }
    fun move(id: String, index: Int) {
        val from = ordered.indexOfFirst { it.schemaId == id }
        if (from < 0) return
        val target = index.coerceIn(ordered.indices)
        if (from != target) ordered = ordered.toMutableList().apply { add(target, removeAt(from)) }
    }
    Column(modifier.verticalScroll(rememberScrollState()).padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ordered.forEach { schema -> key(schema.schemaId) {
            Row(Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(8.dp))
                .background(if (dragging == schema.schemaId) accent.copy(alpha = 0.3f) else background)
                .testTag("input-mode-order:${schema.schemaId}")
                .pointerInput(schema.schemaId) {
                    var start = 0
                    var distance = 0f
                    detectDragGesturesAfterLongPress(
                        onDragStart = {
                            start = ordered.indexOfFirst { it.schemaId == schema.schemaId }
                            distance = 0f; dragging = schema.schemaId
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        onDrag = { change, amount ->
                            change.consume(); distance += amount.y
                            move(schema.schemaId, start + (distance / rowHeight).roundToInt())
                        },
                        onDragEnd = { dragging = null; save(ordered.map { it.schemaId }) },
                        onDragCancel = { dragging = null; ordered = latestSchemas },
                    )
                }.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.DragHandle, contentDescription = "拖动排序${schema.name}", tint = foreground,
                    modifier = Modifier.size(20.dp))
                Text(schema.name, color = foreground, fontSize = 14.sp, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
                val index = ordered.indexOfFirst { it.schemaId == schema.schemaId }
                IconButton(enabled = index > 0, onClick = {
                    move(schema.schemaId, index - 1); save(ordered.map { it.schemaId })
                }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.KeyboardArrowUp, "上移${schema.name}", tint = foreground)
                }
                IconButton(enabled = index < ordered.lastIndex, onClick = {
                    move(schema.schemaId, index + 1); save(ordered.map { it.schemaId })
                }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.KeyboardArrowDown, "下移${schema.name}", tint = foreground)
                }
            }
        } }
    }
}
