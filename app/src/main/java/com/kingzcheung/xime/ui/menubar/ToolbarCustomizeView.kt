package com.kingzcheung.xime.ui.menubar

import com.kingzcheung.xime.ui.keyboard.keyboardPanelBackground
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kingzcheung.xime.keyboard.QuickActionLayout
import com.kingzcheung.xime.keyboard.ToolbarButton
import com.kingzcheung.xime.keyboard.ToolbarButtonItem
import com.kingzcheung.xime.ui.keyboard.ToolbarButtonIcon

/**
 * 工具栏与菜单的统一排列：一个列表，长按拖动即可在“工具栏 / 菜单 / 不使用”之间移动和排序。
 * 分隔条不可拖动；“设置”被拖到“不使用”时自动回到菜单末尾，保证始终能进入设置。
 */
@Composable
fun ToolbarCustomizeView(
    toolbarButtons: List<String>,
    menuButtons: List<String> = emptyList(),
    pluginButtons: List<ToolbarButtonItem.Plugin> = emptyList(),
    keyTextColor: Color,
    backgroundColor: Color,
    accentColor: Color,
    keyBgColor: Color,
    onUpdateQuickActions: ((QuickActionLayout) -> Unit)?,
    onDismiss: () -> Unit,
    bottomPaddingDp: Int = 0,
    modifier: Modifier = Modifier,
    /** 当前键盘工具栏／菜单里会出现的入口；其他键盘专用的不列出，保存时保留原位（见 [QuickActionLayout.restoreHidden]）。 */
    isShown: (String) -> Boolean = { true },
) {
    val allButtons = ToolbarButton.CUSTOMIZABLE.map { ToolbarButtonItem.Builtin(it) } + pluginButtons
    val itemById = allButtons.associateBy { it.id }
    val saved = QuickActionLayout.normalize(toolbarButtons.filter { it in itemById }, menuButtons.filter { it in itemById })
    val hidden = allButtons.map { it.id }.filterNot { it == QuickActionLayout.PINNED || isShown(it) }.toSet()
    val layout = QuickActionLayout(saved.toolbar - hidden, saved.menu - hidden)
    val dragOrder = rememberDragOrder(layout.editorOrder(allButtons.map { it.id }.filterNot { it in hidden })) { order ->
        onUpdateQuickActions?.invoke(QuickActionLayout.restoreHidden(QuickActionLayout.fromEditorOrder(order), saved, hidden))
    }
    val order = dragOrder.order
    val menuCount = (order.indexOf(QuickActionLayout.UNUSED_DIVIDER) - order.indexOf(QuickActionLayout.MENU_DIVIDER) - 1)
        .coerceAtLeast(0)
    val rowColor = lerp(keyBgColor, keyTextColor, .04f)

    Column(modifier.fillMaxWidth().keyboardPanelBackground(backgroundColor).padding(bottom = bottomPaddingDp.dp)) {
        Row(Modifier.fillMaxWidth().height(50.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("排列工具栏与菜单", color = keyTextColor, fontSize = 16.sp, modifier = Modifier.weight(1f))
            Box(Modifier.size(32.dp).clip(CircleShape).background(lerp(keyBgColor, accentColor, .25f))
                .clickable(onClick = onDismiss).testTag("quick-actions-done"), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Check, contentDescription = "完成", tint = keyTextColor, modifier = Modifier.size(22.dp))
            }
        }
        LazyColumn(Modifier.fillMaxWidth().weight(1f).testTag("quick-actions-list"), state = dragOrder.list,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            item(key = "#toolbar") {
                SectionHeader("工具栏", "长按拖动排序；拖过分隔线即移到菜单或不使用", keyTextColor)
            }
            items(order, key = { it }) { id ->
                when (id) {
                    QuickActionLayout.MENU_DIVIDER -> Box(dragOrderItem(dragOrder, id, enabled = false)) {
                        SectionHeader("菜单", if (menuCount > 8) "共 $menuCount 项，超过 8 项会分页" else "点开工具栏右侧菜单时显示",
                            keyTextColor)
                    }
                    QuickActionLayout.UNUSED_DIVIDER -> Box(dragOrderItem(dragOrder, id, enabled = false)) {
                        SectionHeader("不使用", "“设置”必须保留在工具栏或菜单中", keyTextColor)
                    }
                    else -> {
                        val item = itemById[id] ?: return@items
                        val dragging = dragOrder.dragging == id
                        Row(Modifier.fillMaxWidth().then(dragOrderItem(dragOrder, id))
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (dragging) lerp(rowColor, accentColor, .2f) else rowColor)
                            .border(if (dragging) 2.dp else 0.dp, if (dragging) accentColor else Color.Transparent, RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                            .testTag("quick-action:$id"),
                            verticalAlignment = Alignment.CenterVertically) {
                            ToolbarButtonIcon(item = item, tint = keyTextColor.copy(alpha = .8f), modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(item.label, color = keyTextColor, fontSize = 15.sp, modifier = Modifier.weight(1f))
                            Icon(Icons.Default.DragIndicator, contentDescription = "长按拖动${item.label}",
                                tint = keyTextColor.copy(alpha = .45f), modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, hint: String, textColor: Color) {
    Column(Modifier.fillMaxWidth().padding(start = 4.dp, top = 10.dp, bottom = 2.dp)) {
        Text(title, color = textColor, fontSize = 14.sp)
        Text(hint, color = textColor.copy(alpha = .6f), fontSize = 12.sp)
    }
}
