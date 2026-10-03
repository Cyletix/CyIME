package com.kingzcheung.xime.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kingzcheung.xime.settings.*

/** Shared by settings and keyboard panel; each layout appears once, regardless of backend variants. */
@Composable
internal fun InputProfileSelectors(
    entries: List<SchemaInfo>, selected: SchemaInfo,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    onSelect: (SchemaInfo) -> Unit,
) {
    val profile = selected.profile
    val schemes = InputProfileSelection.schemes(entries, profile.language)
    val layouts = InputProfileSelection.layouts(entries, selected)
    val variants = InputProfileSelection.variants(entries, selected)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ProfileChoice("输入方案", profile.scheme.displayName, "scheme-choice:${profile.language.id}",
            schemes.map { it.id to it.displayName }, profile.scheme.id, containerColor, contentColor, accentColor) { id ->
            schemes.firstOrNull { it.id == id }?.let { InputProfileSelection.changeScheme(entries, selected, it) }?.let(onSelect)
        }
        ProfileChoice("键盘布局", profile.layout.displayName, "layout-choice:${profile.language.id}",
            layouts.map { it.id to it.displayName }, profile.layout.id, containerColor, contentColor, accentColor) { id ->
            InputProfileSelection.changeLayout(entries, selected, id)?.let(onSelect)
        }
        if (variants.size > 1) ProfileChoice("方案版本", selected.name, "variant-choice:${profile.language.id}",
            variants.map { it.schemaId to it.name }, selected.schemaId, containerColor, contentColor, accentColor) { id ->
            variants.firstOrNull { it.schemaId == id }?.let(onSelect)
        }
    }
}

@Composable
internal fun ProfileChoice(
    label: String, value: String, tag: String, choices: List<Pair<String, String>>, selectedId: String,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    onSelect: (String) -> Unit,
) {
    var expanded by remember(tag, choices) { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        Surface(shape = RoundedCornerShape(12.dp), color = containerColor, contentColor = contentColor) {
            Row(Modifier.fillMaxWidth().heightIn(min = 52.dp)
                .clickable(enabled = choices.size > 1, role = Role.Button) { expanded = true }
                .testTag(tag).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(label, Modifier.width(80.dp), style = MaterialTheme.typography.labelMedium)
                Text(value, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                if (choices.size > 1) Icon(Icons.Default.ArrowDropDown, null, Modifier.size(24.dp))
            }
        }
        DropdownMenu(expanded, { expanded = false }, containerColor = containerColor) {
            choices.forEach { (id, name) ->
                DropdownMenuItem(text = { Text(name, color = contentColor) },
                    trailingIcon = { if (id == selectedId) Icon(Icons.Default.Check, null, tint = accentColor) },
                    modifier = Modifier.testTag("$tag:option:$id").semantics { selected = id == selectedId },
                    onClick = { expanded = false; onSelect(id) })
            }
        }
    }
}
