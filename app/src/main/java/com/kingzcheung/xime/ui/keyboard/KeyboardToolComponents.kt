package com.kingzcheung.xime.ui.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Shared tool typography and touch targets; selected colors always come as a semantic pair. */
@Composable
internal fun ToolTab(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Box(modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(22.dp))
        .background(if (selected) colors.secondaryContainer else colors.surfaceContainer)
        .semantics { this.selected = selected }.clickable(onClick = onClick)
        .padding(horizontal = 16.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
        Text(label, color = if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant,
            fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

@Composable
internal fun ToolIcon(icon: ImageVector, label: String, onClick: () -> Unit, tag: String = label) {
    IconButton(onClick, Modifier.size(44.dp).testTag(tag)) {
        Icon(icon, label, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurface)
    }
}
