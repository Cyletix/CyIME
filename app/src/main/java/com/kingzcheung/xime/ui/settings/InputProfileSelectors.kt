package com.kingzcheung.xime.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kingzcheung.xime.settings.*

/** Two independent choices; their intersection selects an installed backend profile. */
@Composable
internal fun InputProfileSelectors(entries: List<SchemaInfo>, selected: SchemaInfo,
    onSelect: (SchemaInfo) -> Unit) {
    val profile = selected.profile
    val schemes = entries.filter { it.profile.language == profile.language && it.profile.mode == InputMode.KEYBOARD }
        .map { it.profile.scheme }.distinct()
    val layouts = entries.filter { it.profile.language == profile.language && it.profile.scheme == profile.scheme &&
        it.profile.mode == InputMode.KEYBOARD }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
        ProfileChoice("输入方案", profile.scheme.displayName, "scheme-choice:${profile.language.id}",
            schemes.map { it.displayName }, schemes.indexOf(profile.scheme)) { index ->
            InputProfileSelection.changeScheme(entries, selected, schemes[index])?.let(onSelect)
        }
        // If two backend variants implement the same layout, keep both explicitly selectable.
        val names = layouts.map { entry ->
            val layout = entry.profile.layout
            if (layouts.count { it.profile.layout.id == layout.id } > 1) "${layout.displayName}（${entry.name}）"
            else layout.displayName
        }
        ProfileChoice("键盘布局", names.getOrNull(layouts.indexOfFirst { it.schemaId == selected.schemaId }).orEmpty(),
            "layout-choice:${profile.language.id}", names, layouts.indexOfFirst { it.schemaId == selected.schemaId }) { index ->
            onSelect(layouts[index])
        }
    }
}

@Composable
private fun ProfileChoice(label: String, value: String, tag: String,
    choices: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        TextButton(onClick = { expanded = true }, enabled = choices.size > 1,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(tag)) {
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(16.dp))
            Text(value, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
            if (choices.size > 1) Icon(Icons.Default.ArrowDropDown, null)
        }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            choices.forEachIndexed { index, name ->
                DropdownMenuItem(text = { Text(name,
                    color = if (index == selectedIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) },
                    onClick = { expanded = false; onSelect(index) })
            }
        }
    }
}
