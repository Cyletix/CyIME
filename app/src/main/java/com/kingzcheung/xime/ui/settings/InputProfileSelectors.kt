package com.kingzcheung.xime.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.style.TextAlign
import com.kingzcheung.xime.settings.*
import kotlin.math.roundToInt

/** Shared by language settings and IME; selection only changes real installed combinations. */
@Composable
internal fun InputProfileSelectors(
    entries: List<SchemaInfo>, selected: SchemaInfo,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    onSelect: (SchemaInfo) -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val profile = selected.profile
    val schemes = InputProfileSelection.schemeEntries(entries, selected)
    val layouts = InputProfileSelection.layouts(entries, selected)
    Column(modifier.fillMaxWidth().testTag("profile-settings-selectors")) {
        LayoutPreviewChoices(layouts, profile.language, profile.layout.id, "layout-choice:${profile.language.id}",
            containerColor = containerColor, contentColor = contentColor, accentColor = accentColor) { id ->
            InputProfileSelection.changeLayout(entries, selected, id)?.let { entry ->
                LetterLayouts.selectFor(context, entry, id)
                onSelect(entry)
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 4.dp), color = contentColor.copy(alpha = .15f))
        ProfileChoiceRow("方案", "scheme-choice:${profile.language.id}",
            schemes.map { it.schemaId to it.schemeLabel }, selected.schemaId,
            previews = schemes.associate { it.schemaId to InputProfiles.schemePreview(it.profile) },
            containerColor = containerColor, contentColor = contentColor, accentColor = accentColor) { id ->
            schemes.firstOrNull { it.schemaId == id }?.let(onSelect)
        }
    }
}

/** Two tight text lines, with management as the final item in the same row. */
internal fun profileCardWidths(available: Dp, count: Int, management: Boolean, fontScale: Float): Pair<Dp, Dp> {
    // One or two choices keep the size they would have among three, instead of filling the row.
    val slots = count.coerceAtLeast(3)
    val gaps = (slots + if (management) 1 else 0) - 1
    val normal = ((available - 6.dp * gaps.coerceAtLeast(0)) / (slots + if (management) .72f else 0f))
        .coerceAtLeast(minOf(112.dp * fontScale, available))
    return normal to (normal * .72f).coerceAtLeast(minOf(92.dp * fontScale, available))
}

@Composable
internal fun ProfileChoiceRow(
    title: String, tag: String, choices: List<Pair<String, String>>, selectedId: String?,
    modifier: Modifier = Modifier, itemTagPrefix: String = "$tag:option:",
    containerColor: Color, contentColor: Color, accentColor: Color,
    previews: Map<String, String> = emptyMap(), onManage: (() -> Unit)? = null,
    rowHeight: Dp? = null,
    onSelect: (String) -> Unit,
) {
    val list = rememberLazyListState()
    LaunchedEffect(selectedId, choices) {
        val index = choices.indexOfFirst { it.first == selectedId }
        if (index >= 0) list.animateScrollToItem(index)
    }
    Row(modifier.fillMaxWidth().testTag(tag), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(if (rowHeight != null) title.toList().joinToString("\n") else title,
            Modifier.then(if (rowHeight != null) Modifier.align(Alignment.CenterVertically) else Modifier.padding(top = 3.dp)),
            color = contentColor.copy(alpha = .75f), textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelMedium, maxLines = if (rowHeight != null) 2 else 1)
        BoxWithConstraints(Modifier.weight(1f)) {
        val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
        val widths = profileCardWidths(maxWidth, choices.size, onManage != null, fontScale)
        val cardWidth = if (rowHeight != null) (widths.first.value * .75f).roundToInt().dp
            .coerceAtLeast(minOf(112.dp * fontScale, maxWidth)) else null
        val managementWidth = if (rowHeight != null) (widths.second.value * .75f).roundToInt().dp
            .coerceAtLeast(minOf(88.dp * fontScale, maxWidth)) else null
        LazyRow(Modifier.fillMaxWidth().testTag("$tag:list"), state = list,
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(choices, key = { it.first }) { (id, label) ->
                SchemeTextCard(label, previews[id].orEmpty(), id == selectedId, "$itemTagPrefix$id",
                    containerColor, contentColor, accentColor, width = cardWidth, height = rowHeight, onClick = { onSelect(id) })
            }
            if (onManage != null) item(key = "scheme-management") {
                SchemeTextCard("方案管理", "", false, "profile-scheme-management",
                    containerColor, contentColor, accentColor, isAction = true,
                    width = managementWidth, height = rowHeight, onClick = onManage)
            }
        }
        }
    }
}

@Composable
private fun SchemeTextCard(label: String, preview: String, chosen: Boolean, tag: String,
    containerColor: Color, contentColor: Color, accentColor: Color, isAction: Boolean = false,
    width: Dp? = null, height: Dp? = null, onClick: () -> Unit) {
    Surface(shape = RoundedCornerShape(14.dp),
        border = BorderStroke(if (chosen) 1.5.dp else 1.dp, if (chosen) accentColor else contentColor.copy(alpha = idleBorderAlpha(containerColor))),
        color = if (chosen) chosenFill(containerColor, accentColor) else containerColor) {
        Box(Modifier.then(if (width != null) Modifier.width(width) else Modifier)
            .then(if (height != null) Modifier.height(height) else Modifier.heightIn(min = 48.dp)).testTag(tag)
            .then(if (isAction) Modifier.clickable(role = Role.Button, onClick = onClick)
                else Modifier.selectable(chosen, role = Role.RadioButton, onClick = onClick))
            .padding(horizontal = 10.dp, vertical = 3.dp), contentAlignment = Alignment.Center) {
            Column(Modifier.padding(horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (isAction) Icon(Icons.Outlined.Settings, null, Modifier.size(16.dp), tint = contentColor.copy(alpha = .75f))
                else Text(preview, style = MaterialTheme.typography.labelSmall, color = contentColor.copy(alpha = .75f),
                    textAlign = TextAlign.Center, maxLines = 1)
                Text(label, style = MaterialTheme.typography.bodyMedium, color = contentColor, textAlign = TextAlign.Center, maxLines = 1)
            }
            if (chosen) Icon(Icons.Default.Check, "已选中", Modifier.align(Alignment.TopEnd).size(14.dp), tint = accentColor)
        }
    }
}
