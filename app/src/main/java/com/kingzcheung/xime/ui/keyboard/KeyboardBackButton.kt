package com.kingzcheung.xime.ui.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** 固定40dp占位和32dp圆形底板，切换图标不会挤动工具栏。 */
@Composable
internal fun KeyboardToolbarButton(
    onClick: () -> Unit,
    background: Color,
    modifier: Modifier = Modifier,
    outline: Color = Color.Transparent,
    content: @Composable () -> Unit,
) {
    Box(modifier.size(40.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Box(Modifier.size(32.dp).clip(CircleShape).background(background)
            .border(1.dp, outline, CircleShape), contentAlignment = Alignment.Center) { content() }
    }
}

@Composable
internal fun KeyboardBackButton(
    onBack: () -> Unit,
    background: Color,
    foreground: Color,
    modifier: Modifier = Modifier,
    label: String = "返回",
) {
    KeyboardToolbarButton(onBack, background, modifier) {
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = label,
            tint = foreground, modifier = Modifier.size(24.dp))
    }
}
