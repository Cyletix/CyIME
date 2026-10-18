package com.kingzcheung.xime.ui.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val PanelCategoryHeight = 32.dp
internal val PanelCategoryVerticalPadding = 4.dp

/** One content bar, like the clipboard filters: modes, scrollable categories, delete. */
@Composable
internal fun ExpressionPanelBar(
    textColor: Color, onDelete: () -> Unit,
    leadingTabs: @Composable RowScope.() -> Unit,
    categories: @Composable RowScope.() -> Unit,
) {
    Row(Modifier.fillMaxWidth().testTag("expression-controls").padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        leadingTabs()
        Box(Modifier.width(1.dp).height(16.dp).background(textColor.copy(alpha = .16f)))
        categories()
        Box(Modifier.size(PanelCategoryHeight).testTag("expression-delete").clip(RoundedCornerShape(8.dp))
            .background(textColor.copy(alpha = .12f))
            .clickable(role = Role.Button, onClick = onDelete), contentAlignment = Alignment.Center) {
            Icon(Icons.AutoMirrored.Filled.Backspace, "删除", Modifier.size(20.dp), tint = textColor)
        }
    }
}

@Composable
internal fun ExpressionTab(label: String, chosen: Boolean, textColor: Color, accentColor: Color,
    modifier: Modifier = Modifier, description: String = label, onClick: () -> Unit) {
    Box(modifier.heightIn(min = PanelCategoryHeight).widthIn(min = 28.dp).clip(RoundedCornerShape(8.dp))
        .background(if (chosen) accentColor.copy(alpha = .22f) else Color.Transparent)
        .semantics { selected = chosen; contentDescription = description }
        .clickable(role = Role.Tab, onClick = onClick).padding(horizontal = 6.dp, vertical = PanelCategoryVerticalPadding),
        contentAlignment = Alignment.Center) {
        Text(label, color = textColor, fontSize = 13.sp, lineHeight = 18.sp, maxLines = 1)
    }
}
