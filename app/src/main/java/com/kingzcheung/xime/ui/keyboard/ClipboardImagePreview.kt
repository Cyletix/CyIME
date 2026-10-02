package com.kingzcheung.xime.ui.keyboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.kingzcheung.xime.clipboard.ClipboardImage

@Composable
internal fun ClipboardImagePreview(image: ClipboardImage, modifier: Modifier = Modifier, onPaste: () -> Unit) {
    Box(modifier.height(36.dp).testTag("clipboard-image-paste")
        .clickable(role = Role.Button, onClickLabel = "粘贴图片", onClick = onPaste)
        .padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
        // 与文字共用胶囊外观，只替换中间内容；长截图完整缩放，不裁切或撑高候选栏。
        AsyncImage(image.uri, contentDescription = "最近图片预览", contentScale = ContentScale.Fit,
            alignment = Alignment.Center,
            modifier = Modifier.fillMaxSize().testTag("clipboard-image-thumbnail"))
    }
}
