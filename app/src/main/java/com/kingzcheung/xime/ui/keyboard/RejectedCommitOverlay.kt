package com.kingzcheung.xime.ui.keyboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/** An in-keyboard overlay keeps the host editor focused (no dialog window). */
@Composable
internal fun RejectedCommitOverlay(text: String, background: Color, foreground: Color, retry: () -> Unit, cancel: () -> Unit) {
    Box(Modifier.fillMaxSize().keyboardPanelBackground(background).clickable {}.testTag("rejected-commit"), contentAlignment = Alignment.Center) {
        Column(Modifier.padding(12.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("输入框未接受文字，内容已保留", color = foreground)
            Text(text, Modifier.padding(vertical = 8.dp), color = foreground)
            Row {
                TextButton(onClick = retry) { Text("重试", color = foreground) }
                TextButton(onClick = cancel) { Text("取消本次输入", color = foreground) }
            }
        }
    }
}
