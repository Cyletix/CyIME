package com.kingzcheung.xime.ui.menubar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kingzcheung.xime.clipboard.ClipboardImage
import com.kingzcheung.xime.clipboard.ImagePasteFailure
import com.kingzcheung.xime.ui.keyboard.keyboardPanelBackground

/** Compact, dismissible fallback; full-width grid slot does not mean a full-width card. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ClipboardImageFailureNotice(
    failure: ImagePasteFailure,
    onSystemPaste: () -> Unit,
    onShare: (ClipboardImage, String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Box(Modifier.fillMaxWidth()) {
        Row(Modifier.widthIn(max = 480.dp).clip(RoundedCornerShape(8.dp))
            .keyboardPanelBackground(colors.surfaceContainerHigh)
            .padding(start = 8.dp, end = 2.dp, top = 2.dp, bottom = 2.dp)
            .testTag("images-paste-notice"), verticalAlignment = Alignment.CenterVertically) {
            FlowRow(Modifier.weight(1f, fill = false), horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("未能粘贴图片", color = colors.onSurfaceVariant, fontSize = 12.sp,
                    lineHeight = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(vertical = 5.dp))
                NoticeAction("系统粘贴", "images-system-paste", onSystemPaste)
                NoticeAction("分享图片", "images-share") { onShare(failure.image, failure.packageName) }
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp).testTag("images-dismiss-failure")) {
                Icon(Icons.Default.Close, "关闭图片提示", tint = colors.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun NoticeAction(label: String, tag: String, onClick: () -> Unit) {
    Text(label, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, lineHeight = 18.sp,
        maxLines = 1, modifier = Modifier.clip(RoundedCornerShape(4.dp)).testTag(tag)
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 2.dp, vertical = 5.dp))
}
