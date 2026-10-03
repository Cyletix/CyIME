package com.kingzcheung.xime.ui.menubar

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kingzcheung.xime.settings.*

/** The toolbar edits the active language's combination. Language switching belongs to the globe. */
@Composable
fun SchemaListView(
    schemas: List<SchemaInfo>, currentSchemaId: String,
    backgroundColor: Color, accentColor: Color, keyTextColor: Color, keyBgColor: Color,
    onSelectSchema: (String) -> Unit, onBack: (() -> Unit)? = null,
    onReorderSchemas: ((List<String>) -> Unit)? = null,
    modifier: Modifier = Modifier, orderableSchemas: List<SchemaInfo> = schemas,
) {
    val context = LocalContext.current
    val all = InputModes.available((schemas + orderableSchemas).distinctBy { it.schemaId })
        .filter { it.profile.mode == InputMode.KEYBOARD }
    // Never silently show the first entry as current while the live session is still loading.
    val current = all.firstOrNull { it.schemaId == currentSchemaId }
    val entries = all.filter { current != null && it.profile.language == current.profile.language }
    var layoutId by remember(currentSchemaId, current?.profile) { mutableStateOf(current?.profile?.layout?.id) }
    var draftId by remember(currentSchemaId, current?.profile) { mutableStateOf(current?.schemaId) }
    val selected = entries.firstOrNull { it.schemaId == draftId && it.profile.layout.id == layoutId }
    val layouts = entries.map { it.profile.layout }.distinctBy { it.id }
    val schemes = entries.filter { it.profile.layout.id == layoutId }
    var editingOrder by remember { mutableStateOf(false) }
    var more by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth().background(backgroundColor).testTag("input-profile-panel")) {
        Row(Modifier.fillMaxWidth().heightIn(min = 40.dp).padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(if (editingOrder) "组合顺序" else "输入方案", Modifier.weight(1f),
                color = keyTextColor, style = MaterialTheme.typography.titleSmall)
            if (editingOrder) TextButton(onClick = { editingOrder = false }) { Text("完成", color = keyTextColor) }
            else if ((onReorderSchemas != null && orderableSchemas.size > 1) || current?.profile?.language == InputLanguage.CHINESE) Box {
                IconButton(onClick = { more = true }, modifier = Modifier.testTag("profile-more")) {
                    Icon(Icons.Default.MoreVert, "更多选项", tint = keyTextColor)
                }
                DropdownMenu(more, { more = false }, containerColor = keyBgColor) {
                    if (onReorderSchemas != null && orderableSchemas.size > 1) DropdownMenuItem(
                        text = { Text("组合顺序", color = keyTextColor) }, onClick = { more = false; editingOrder = true })
                    if (current?.profile?.language == InputLanguage.CHINESE) DropdownMenuItem(
                        text = { Text("添加自定义布局", color = keyTextColor) }, modifier = Modifier.testTag("add-layout-tile"),
                        onClick = {
                            more = false
                            context.startActivity(Intent(context, com.kingzcheung.xime.CustomLayoutActivity::class.java)
                                .putExtra("create_layout", true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        })
                }
            }
        }
        if (editingOrder && onReorderSchemas != null) {
            InputModeOrderEditor(orderableSchemas, onReorderSchemas, keyBgColor, keyTextColor, accentColor,
                Modifier.fillMaxWidth().weight(1f))
        } else if (current != null) {
            Row(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(0.42f).fillMaxHeight().verticalScroll(rememberScrollState()).testTag("profile-layout-list")) {
                    Text("键盘布局", Modifier.padding(8.dp), color = keyTextColor, style = MaterialTheme.typography.labelMedium)
                    layouts.forEach { layout ->
                        ProfileListItem(layout.displayName, layout.id == layoutId, "panel-layout:${layout.id}",
                            keyBgColor, keyTextColor, accentColor) {
                            val previous = selected
                            layoutId = layout.id
                            // Preserve encoding if a compatible layout exists. Otherwise ask for a scheme;
                            // merely touching a layout must not silently switch encoding or language.
                            draftId = previous?.let { InputProfileSelection.changeLayout(entries, it, layout.id)?.schemaId }
                        }
                    }
                }
                VerticalDivider(Modifier.fillMaxHeight(), color = keyTextColor.copy(alpha = 0.15f))
                Column(Modifier.weight(0.58f).fillMaxHeight().verticalScroll(rememberScrollState()).testTag("profile-scheme-list")) {
                    Text("输入方案", Modifier.padding(8.dp), color = keyTextColor, style = MaterialTheme.typography.labelMedium)
                    schemes.forEach { entry ->
                        val hasVariants = schemes.count { it.profile.scheme == entry.profile.scheme } > 1
                        ProfileListItem(if (hasVariants) entry.name else entry.profile.scheme.displayName,
                            entry.schemaId == selected?.schemaId, "panel-scheme:${entry.schemaId}",
                            keyBgColor, keyTextColor, accentColor) { draftId = entry.schemaId }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { onBack?.invoke() }) { Text("取消", color = keyTextColor) }
                Spacer(Modifier.weight(1f))
                Button(onClick = { selected?.let { onSelectSchema(it.schemaId) } }, enabled = selected != null,
                    modifier = Modifier.testTag("apply-input-profile"),
                    colors = ButtonDefaults.buttonColors(containerColor = keyBgColor, contentColor = keyTextColor)) { Text("使用") }
            }
        } else {
            Text("正在读取当前方案…", Modifier.padding(16.dp), color = keyTextColor)
        }
    }
}

@Composable
private fun ProfileListItem(label: String, chosen: Boolean, tag: String, bg: Color, fg: Color, accent: Color, onClick: () -> Unit) {
    Surface(Modifier.fillMaxWidth().padding(bottom = 4.dp), shape = RoundedCornerShape(10.dp),
        color = if (chosen) bg else Color.Transparent) {
        Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).testTag(tag).semantics { selected = chosen }
            .clickable(role = Role.RadioButton, onClick = onClick).padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), color = fg, style = MaterialTheme.typography.bodyMedium)
            if (chosen) Icon(Icons.Default.Check, null, Modifier.padding(start = 4.dp).size(16.dp), tint = accent)
        }
    }
}
