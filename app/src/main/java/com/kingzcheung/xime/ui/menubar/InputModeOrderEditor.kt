package com.kingzcheung.xime.ui.menubar

import androidx.compose.foundation.lazy.items
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
    val order = rememberDragOrder(schemas.map { it.schemaId }, onReorder)
    val byId = schemas.associateBy { it.schemaId }
    androidx.compose.foundation.lazy.LazyColumn(modifier.padding(horizontal = 12.dp), state = order.list,
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items(order.order, key = { it }) { id ->
            val schema = byId.getValue(id)
            Row(Modifier.then(dragOrderItem(order, id)).fillMaxWidth().height(48.dp).clip(RoundedCornerShape(8.dp))
                .background(if (order.dragging == id) accent.copy(alpha = .3f) else background)
                .testTag("input-mode-order:$id").padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(schema.name, color = foreground, fontSize = 14.sp, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            }
        }
    }
}
