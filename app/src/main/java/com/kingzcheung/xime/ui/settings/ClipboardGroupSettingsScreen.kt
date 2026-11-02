package com.kingzcheung.xime.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.twotone.CreateNewFolder
import androidx.compose.material.icons.twotone.Email
import androidx.compose.material.icons.twotone.Folder
import androidx.compose.material.icons.twotone.Image
import androidx.compose.material.icons.twotone.Link
import androidx.compose.material.icons.twotone.Phone
import androidx.compose.material.icons.twotone.Place
import androidx.compose.material.icons.twotone.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kingzcheung.xime.clipboard.ClipboardFilter
import com.kingzcheung.xime.clipboard.ClipboardGroup
import com.kingzcheung.xime.clipboard.ClipboardGroupStore
import com.kingzcheung.xime.clipboard.clipboardGroupNameError

/**
 * 剪贴板分组：自带分类（按内容自动归类）和自己建的分组（常用语、工作……）在同一张卡片里，顺序和剪贴板分类栏一样：
 * 自带分类在前，自己建的分组接在后面，最后是「新建分组」。放进分组的记录不固定，不会排到最前面，也不会被清空。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClipboardGroupSettingsContent(onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember(context) { ClipboardGroupStore.getInstance(context) }
    val state by store.state.collectAsState()
    // Name dialog: null closed; a group being renamed, or a new one (id "").
    var naming by remember { mutableStateOf<ClipboardGroup?>(null) }
    var deleting by remember { mutableStateOf<ClipboardGroup?>(null) }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("剪贴板分组") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp).settingsVerticalScroll(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column {
                // One list in tab order: the built-in categories, the user's groups, then 新建分组.
                SettingsSection(title = "分组", content = {
                    ClipboardFilter.entries.filter { it != ClipboardFilter.ALL }.forEach { filter ->
                        SettingsToggleItem(
                            icon = filter.icon(),
                            title = filter.label,
                            subtitle = filter.description(),
                            checked = filter !in state.hidden,
                            onCheckedChange = { store.setHidden(filter, !it) },
                        )
                    }
                    state.groups.forEach { group ->
                        ClipboardGroupRow(group.name, "${state.count(group.id)} 条",
                            Modifier.testTag("clipboard-group-row:${group.id}"),
                            onRename = { naming = group }, onDelete = { deleting = group })
                    }
                    SettingsItem(
                        icon = Icons.TwoTone.CreateNewFolder,
                        title = "新建分组",
                        subtitle = "比如常用语、工作、家人",
                        onClick = { naming = ClipboardGroup("", "") },
                    )
                })
                SettingsNote("长按剪贴板记录，选「分组」放入。关掉的分类不在分类栏显示，记录仍在「全部」里。")
            }
            Spacer(Modifier.height(8.dp))
        }
    }
    naming?.let { group ->
        ClipboardGroupNameDialog(group, state.groups, onDismiss = { naming = null }) { name ->
            val error = if (group.id.isEmpty()) store.add(name) else store.rename(group.id, name)
            if (error == null) naming = null
            error
        }
    }
    deleting?.let { group ->
        val count = state.count(group.id)
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("删除分组「${group.name}」？") },
            text = { Text(if (count == 0) "分组里没有记录。" else "里面的 $count 条记录不会删除，仍在「全部」里。") },
            confirmButton = {
                TextButton(onClick = { store.delete(group.id); deleting = null }, Modifier.testTag("clipboard-group-delete-confirm")) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } },
        )
    }
}

/**
 * One of the user's groups, laid out like the category rows above it (icon tile, name, a grey line),
 * with rename and delete where the categories have their switch. Tapping the row renames.
 */
@Composable
private fun ClipboardGroupRow(name: String, detail: String, modifier: Modifier = Modifier,
                              onRename: () -> Unit, onDelete: () -> Unit) {
    Row(modifier.fillMaxWidth().clickable(onClick = onRename).padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
            Icon(Icons.TwoTone.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        IconButton(onClick = onRename) {
            Icon(Icons.Default.Edit, contentDescription = "改名", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.DeleteOutline, contentDescription = "删除", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** [save] returns null when saved, otherwise why the name cannot be used (shown under the field). */
@Composable
private fun ClipboardGroupNameDialog(group: ClipboardGroup, groups: List<ClipboardGroup>, onDismiss: () -> Unit,
                                     save: (String) -> String?) {
    var name by remember(group.id) { mutableStateOf(group.name) }
    var error by remember(group.id) { mutableStateOf<String?>(null) }
    // A name that only repeats the current one, or is still empty, is not an error worth showing yet.
    val problem = if (name.isBlank() || name.trim() == group.name) null else clipboardGroupNameError(name, groups, group.id.ifEmpty { null })
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (group.id.isEmpty()) "新建分组" else "改名") },
        text = {
            OutlinedTextField(value = name, onValueChange = { name = it; error = null },
                label = { Text("分组名称") }, singleLine = true, isError = (error ?: problem) != null,
                supportingText = (error ?: problem)?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth().testTag("clipboard-group-name"))
        },
        confirmButton = {
            TextButton(onClick = { error = save(name) }, enabled = name.isNotBlank() && problem == null,
                modifier = Modifier.testTag("clipboard-group-name-save")) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

private fun ClipboardFilter.icon(): ImageVector = when (this) {
    ClipboardFilter.LINK -> Icons.TwoTone.Link
    ClipboardFilter.ADDRESS -> Icons.TwoTone.Place
    ClipboardFilter.PHONE -> Icons.TwoTone.Phone
    ClipboardFilter.EMAIL -> Icons.TwoTone.Email
    ClipboardFilter.IMAGE -> Icons.TwoTone.Image
    else -> Icons.TwoTone.TextFields
}

private fun ClipboardFilter.description(): String = when (this) {
    ClipboardFilter.TEXT -> "所有文字记录"
    ClipboardFilter.LINK -> "含网址的记录"
    ClipboardFilter.ADDRESS -> "含地址的记录"
    ClipboardFilter.PHONE -> "含电话号码的记录"
    ClipboardFilter.EMAIL -> "含邮箱的记录"
    ClipboardFilter.IMAGE -> "复制的图片和最近截图"
    else -> ""
}
