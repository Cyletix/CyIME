package com.kingzcheung.xime.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.kingzcheung.xime.ui.keyboard.normalizeHandwritingPause
import com.kingzcheung.xime.ui.keyboard.normalizeCursorHoldSeconds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kingzcheung.xime.ui.keyboard.SpaceHoldAction
import com.kingzcheung.xime.ui.keyboard.CursorGestureMode
import com.kingzcheung.xime.ui.keyboard.SymbolInputMode
import com.kingzcheung.xime.ui.keyboard.rememberKeyboardInputPreferences

@Composable
internal fun InputExperienceSettings() {
    val context = LocalContext.current
    val saved = rememberKeyboardInputPreferences()
    var settings by remember(saved) { mutableStateOf(saved) }
    SettingsSection(title = "基础输入", content = {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("26 键邻键纠错", style = MaterialTheme.typography.titleSmall)
                Switch(checked = settings.neighborCorrection, onCheckedChange = {
                    settings = settings.copy(neighborCorrection = it); settings.save(context)
                })
            }
            Text("中文全拼按当前键位补充相邻字母误触的候选，保留原输入；不用于九键、双拼或合并键。", style = MaterialTheme.typography.bodySmall)
            HorizontalDivider()
            Text("按键角标的符号和数字", style = MaterialTheme.typography.titleSmall)
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SymbolInputMode.entries.forEach { mode ->
                    FilterChip(selected = settings.symbolInputMode == mode,
                        modifier = Modifier.testTag("symbol-input:${mode.name}"),
                        onClick = { settings = settings.copy(symbolInputMode = mode); settings.save(context) },
                        label = { Text(mode.label) })
                }
            }
            Text(if (settings.symbolInputMode == SymbolInputMode.LONG_PRESS)
                "按住满 0.3 秒立即输入角标，持续按住和松手都不重复输入；此模式不弹出字母菜单。空格、删除和语言键保持原行为。"
                else "上滑后松手输入角标，原有长按菜单保持不变。",
                style = MaterialTheme.typography.bodySmall)
            HorizontalDivider()
            Text("滑动移动光标", style = MaterialTheme.typography.titleSmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CursorGestureMode.entries.forEach { mode ->
                    FilterChip(selected = settings.cursorGesture == mode,
                        modifier = Modifier.weight(1f),
                        onClick = { settings = settings.copy(cursorGesture = mode); settings.save(context) },
                        label = { Text(mode.label) })
                }
            }
            Text("空格长按触发：${"%.1f".format(settings.cursorHoldSeconds)} 秒")
            Slider(value = settings.cursorHoldSeconds, modifier = Modifier.testTag("cursor-hold-slider"),
                enabled = settings.cursorGesture == CursorGestureMode.SPACE,
                onValueChange = { settings = settings.copy(cursorHoldSeconds = normalizeCursorHoldSeconds(it)) },
                onValueChangeFinished = { settings.save(context) }, valueRange = 0.1f..1f, steps = 8)
            Text("默认 0.2 秒，每档 0.1 秒；按住达到时长后滑动，松手不输入空格。仅用于长按空格移动光标。",
                style = MaterialTheme.typography.bodySmall)
            Text("空格键其他长按行为", style = MaterialTheme.typography.titleSmall)
            Text(if (settings.cursorGesture == CursorGestureMode.SPACE) "当前用于移动光标，以下功能暂停，选择其他移动方式后恢复。"
                else "长按 0.3 秒触发；语音再次长按结束，也可用工具栏麦克风结束。", style = MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth().testTag("space-hold-options"), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(SpaceHoldAction.REPEAT, SpaceHoldAction.VOICE_TOGGLE).forEach { action ->
                    FilterChip(selected = (settings.spaceHold.takeUnless { it == SpaceHoldAction.CURSOR } ?: SpaceHoldAction.REPEAT) == action,
                        enabled = settings.cursorGesture != CursorGestureMode.SPACE,
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
