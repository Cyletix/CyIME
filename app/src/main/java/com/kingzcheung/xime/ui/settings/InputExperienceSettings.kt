package com.kingzcheung.xime.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.kingzcheung.xime.ui.keyboard.normalizeHandwritingPause
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kingzcheung.xime.ui.keyboard.SpaceHoldAction
import com.kingzcheung.xime.ui.keyboard.rememberKeyboardInputPreferences

@Composable
internal fun InputExperienceSettings() {
    val context = LocalContext.current
    val saved = rememberKeyboardInputPreferences()
    var settings by remember(saved) { mutableStateOf(saved) }
    SettingsSection(title = "基础输入", content = {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("空格键长按行为", style = MaterialTheme.typography.titleSmall)
            Text("长按 0.3 秒后触发。光标模式松手不会输入空格。", style = MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth().testTag("space-hold-options"), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SpaceHoldAction.entries.forEach { action ->
                    FilterChip(selected = settings.spaceHold == action,
                        modifier = Modifier.weight(1f),
                        onClick = { settings = settings.copy(spaceHold = action); settings.save(context) },
                        label = { Text(action.label) })
                }
            }
            Text("光标每移动一字所需距离：${settings.cursorStepDp.toInt()} dp")
            Text("距离越小越灵敏", style = MaterialTheme.typography.bodySmall)
            Slider(value = settings.cursorStepDp, onValueChange = { settings = settings.copy(cursorStepDp = it) },
                onValueChangeFinished = { settings.save(context) }, valueRange = 6f..24f, steps = 17)
            HorizontalDivider()
            Text("手写停顿识别：${"%.1f".format(settings.handwritingPauseSeconds)} 秒")
            Text("默认 0.5 秒，每档 0.1 秒；继续书写会重新计时。", style = MaterialTheme.typography.bodySmall)
            Slider(value = settings.handwritingPauseSeconds, modifier = Modifier.testTag("handwriting-pause-slider"),
                onValueChange = { settings = settings.copy(handwritingPauseSeconds = normalizeHandwritingPause(it)) },
                onValueChangeFinished = { settings.save(context) }, valueRange = 0.1f..2.5f, steps = 23)
            HorizontalDivider()
            Text("按键字号：${(settings.keyTextScale * 100).toInt()}%")
            Text("a b c  A B C  拼音", fontSize = (16f * settings.keyTextScale).sp)
            Slider(value = settings.keyTextScale, onValueChange = { settings = settings.copy(keyTextScale = it) },
                onValueChangeFinished = { settings.save(context) }, valueRange = 0.8f..1.6f, steps = 15)
            HorizontalDivider()
            Text("固定栏快捷符号", style = MaterialTheme.typography.titleSmall)
            Text("每行一个符号或短语，用于九键、笔画及常用符号键盘。留空使用方案默认值。", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(value = settings.fixedSymbols,
                onValueChange = { settings = settings.copy(fixedSymbols = it) },
                modifier = Modifier.fillMaxWidth(), minLines = 3, maxLines = 6,
                label = { Text("快捷符号") })
            TextButton(onClick = {
                settings = settings.copy(fixedSymbols = listOf("，", "。", "？", "！", "、", "：", "；", "…", "（", "）", "@", "#", "/", "-").joinToString("\n"))
            }) { Text("填入常用符号") }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { settings.save(context) }) { Text("保存符号") }
                TextButton(onClick = { settings = settings.copy(fixedSymbols = ""); settings.save(context) }) { Text("恢复默认") }
            }
        }
    })
}
