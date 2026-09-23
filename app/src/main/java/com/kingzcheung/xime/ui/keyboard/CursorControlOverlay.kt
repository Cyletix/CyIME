package com.kingzcheung.xime.ui.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Swipe
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 纯展示层：不消费触摸，长按中的空格继续接收移动和松手。 */
@Composable
fun CursorControlOverlay(modifier: Modifier = Modifier) {
    Box(modifier.background(Color.Black.copy(alpha = 0.78f)).testTag("cursor-control-overlay"),
        contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Default.Swipe, contentDescription = null, tint = Color.White, modifier = Modifier.size(36.dp))
            Text("光标调整", color = Color.White, fontSize = 18.sp)
        }
    }
}
