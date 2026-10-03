package com.kingzcheung.xime.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kingzcheung.xime.settings.*

/** Real layout/encoding combinations only; changing a layout keeps the current encoding. */
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
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(modifier.fillMaxWidth().testTag("profile-settings-selectors")) {
        val stacked = maxWidth < (252f * fontScale).dp
        // Language settings provides unbounded vertical space. A bounded caller (for example
        // an embedded preview) scrolls the whole selector, while each list remains bounded.
        val contentModifier = if (constraints.hasBoundedHeight) {
            Modifier.verticalScroll(rememberScrollState())
        } else Modifier
        val layoutCard: @Composable (Modifier) -> Unit = { cardModifier ->
            ProfileSelectorCard("键盘布局", Icons.Default.Keyboard, "layout-choice:${profile.language.id}",
                layouts.map { it.id to it.displayName }, profile.layout.id, cardModifier,
                containerColor, contentColor, accentColor) { id ->
                InputProfileSelection.changeLayout(entries, selected, id)?.let(onSelect)
            }
        }
        val schemeCard: @Composable (Modifier) -> Unit = { cardModifier ->
            ProfileSelectorCard("输入方案", Icons.Default.Translate, "scheme-choice:${profile.language.id}",
                schemes.map { it.id to it.displayName }, profile.scheme.id, cardModifier,
                containerColor, contentColor, accentColor) { id ->
                schemes.firstOrNull { it.id == id }
                    ?.let { InputProfileSelection.changeScheme(entries, selected, it) }?.let(onSelect)
            }
        }
        Column(contentModifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (stacked) {
                layoutCard(Modifier.fillMaxWidth())
                schemeCard(Modifier.fillMaxWidth())
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    layoutCard(Modifier.weight(1f))
                    schemeCard(Modifier.weight(1f))
                }
            }
            if (variants.size > 1) {
                ProfileSelectorCard("方案版本", Icons.Default.Translate, "variant-choice:${profile.language.id}",
                    variants.map { it.schemaId to it.name }, selected.schemaId, Modifier.fillMaxWidth(),
                    containerColor, contentColor, accentColor) { id ->
                    variants.firstOrNull { it.schemaId == id }?.let(onSelect)
                }
            }
        }
    }
}

@Composable
private fun ProfileSelectorCard(
    label: String, icon: ImageVector, tag: String, choices: List<Pair<String, String>>, selectedId: String,
    modifier: Modifier, containerColor: Color, contentColor: Color, accentColor: Color,
    onSelect: (String) -> Unit,
) {
    val fontScale = LocalDensity.current.fontScale
    val rowHeight = (52f * fontScale.coerceAtLeast(1f)).dp
    val rowGap = 6.dp
    val viewportHeight = (rowHeight * 3 + rowGap * 2).coerceAtMost(240.dp)
    val centeringPadding = ((viewportHeight - rowHeight) / 2).coerceAtLeast(0.dp)
    val list = rememberLazyListState()
    LaunchedEffect(selectedId, choices) {
        val index = choices.indexOfFirst { it.first == selectedId }
        if (index >= 0) list.scrollToItem(index)
    }
    Surface(modifier.testTag(tag).semantics { if (choices.size <= 1) disabled() },
        color = containerColor, contentColor = contentColor,
        shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, contentColor.copy(alpha = .09f))) {
        Column(Modifier.fillMaxWidth().padding(8.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (fontScale <= 1.3f) Icon(icon, null, Modifier.size(18.dp), tint = contentColor.copy(alpha = .75f))
                Text(label, style = MaterialTheme.typography.labelMedium, color = contentColor.copy(alpha = .75f))
            }
            LazyColumn(Modifier.fillMaxWidth().height(viewportHeight).testTag("$tag:list"), state = list,
                contentPadding = PaddingValues(vertical = centeringPadding),
                verticalArrangement = Arrangement.spacedBy(rowGap)) {
                items(choices, key = { it.first }) { (id, name) ->
                    val chosen = id == selectedId
                    Surface(shape = RoundedCornerShape(12.dp),
                        color = if (chosen) accentColor.copy(alpha = .17f) else Color.Transparent,
                        border = if (chosen) BorderStroke(1.dp, accentColor.copy(alpha = .5f)) else null) {
                        Box(Modifier.fillMaxWidth().heightIn(min = rowHeight)
                            .testTag("$tag:option:$id").semantics { this.selected = chosen }
                            .clickable(enabled = choices.size > 1, role = Role.RadioButton) { onSelect(id) }
                            .padding(horizontal = 22.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                            Text(name, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
                                color = contentColor.copy(alpha = if (chosen) 1f else .7f),
                                fontWeight = if (chosen) FontWeight.Medium else FontWeight.Normal)
                            if (chosen) Icon(Icons.Default.Check, null,
                                Modifier.align(Alignment.CenterEnd).offset(x = 17.dp).size(14.dp), tint = accentColor)
                        }
                    }
                }
            }
        }
    }
}
