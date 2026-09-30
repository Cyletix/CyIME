package com.kingzcheung.xime.ui.menubar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
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
    val rowHeight = with(LocalDensity.current) { 52.dp.toPx() }
    fun move(id: String, index: Int) {
        val from = ordered.indexOfFirst { it.schemaId == id }
        if (from < 0) return
        val target = index.coerceIn(ordered.indices)
        if (from != target) ordered = ordered.toMutableList().apply { add(target, removeAt(from)) }
    }
    Column(modifier.verticalScroll(rememberScrollState()).padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ordered.forEach { schema -> key(schema.schemaId) {
            var start by remember { mutableIntStateOf(0) }
            var distance by remember { mutableFloatStateOf(0f) }
            Row(Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(8.dp))
                .background(if (dragging == schema.schemaId) accent.copy(alpha = 0.3f) else background)
                .testTag("input-mode-order:${schema.schemaId}")
                .reorderOnLongPress(
                    onStart = {
                        start = ordered.indexOfFirst { it.schemaId == schema.schemaId }
                        distance = 0f
                        dragging = schema.schemaId
                    },
                    onDrag = { delta ->
                        distance += delta
                        move(schema.schemaId, start + (distance / rowHeight).roundToInt())
                    },
                    onEnd = { dragging = null; save(ordered.map { it.schemaId }) },
                    onCancel = { dragging = null; ordered = latestSchemas },
                ).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(schema.name, color = foreground, fontSize = 14.sp, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            }
        } }
    }
}
