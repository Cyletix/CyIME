package com.kingzcheung.xime.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import com.kingzcheung.xime.ui.keyboard.normalizeHandwritingPause
import com.kingzcheung.xime.ui.keyboard.normalizeCursorHoldSeconds
import com.kingzcheung.xime.ui.keyboard.normalizeLanguageMenuHoldSeconds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kingzcheung.xime.ui.keyboard.SpaceHoldAction
import com.kingzcheung.xime.ui.keyboard.FormulaDelimiter
import androidx.compose.foundation.clickable
import com.kingzcheung.xime.ui.keyboard.CursorGestureMode
import com.kingzcheung.xime.ui.keyboard.SymbolInputMode
import com.kingzcheung.xime.ui.keyboard.rememberKeyboardInputPreferences
import com.kingzcheung.xime.ui.keyboard.InputGestureLock
import com.kingzcheung.xime.ui.keyboard.InputGestureLocks

/** Groups of [InputExperienceSettings]; the 布局与显示 sub-pages each show some of them. */
internal enum class InputExperiencePart { SYMBOLS, FLICK_DISC, KANA, CURSOR, LANGUAGE_HOLD, HANDWRITING, KEY_TEXT }

@Composable
internal fun InputExperienceSettings(
    parts: Set<InputExperiencePart> = InputExperiencePart.entries.toSet(),
    title: String = "共通输入",
) {
    val context = LocalContext.current
    val saved = rememberKeyboardInputPreferences()
    var settings by remember(saved) { mutableStateOf(saved) }
    SettingsSection(title = title, content = {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            var first = true
            @Composable fun divider() { if (!first) HorizontalDivider(); first = false }
            if (InputExperiencePart.SYMBOLS in parts) {
            divider()
            Text("按键角标的符号和数字", style = MaterialTheme.typography.titleSmall)
            // 示意图随「反转上下滑方向」实时变化，一眼看出哪个方向输入哪个角标。
            Row(Modifier.fillMaxWidth().height(116.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SymbolInputMode.entries.forEach { mode ->
                    // 冲突的设置先设好的那一项锁住这里（见 InputGestureLocks）；选“长按”会顺带打开九键拂动。
                    val lock = InputGestureLocks.lockFor(settings, InputGestureLock.Option.SYMBOL_INPUT_MODE, mode)
                    IllustratedChoiceCard(mode.label, settings.symbolInputMode == mode,
                        onClick = { settings = InputGestureLocks.withSymbolInputMode(settings, mode); settings.save(context) },
                        modifier = Modifier.weight(1f), tag = "symbol-input:${mode.name}", enabled = lock == null) {
                        SymbolGestureIllustration(mode == SymbolInputMode.LONG_PRESS, settings.reverseSymbolSwipe)
                    }
                }
            }
            SymbolInputMode.entries.firstNotNullOfOrNull {
                InputGestureLocks.lockFor(settings, InputGestureLock.Option.SYMBOL_INPUT_MODE, it)
            }?.let { SettingsLockNote("「${it.lockedValue.let { v -> (v as SymbolInputMode).label }}」${it.message}") }
            Text(if (settings.symbolInputMode == SymbolInputMode.LONG_PRESS)
                "按住满 0.3 秒立即输入上方角标，持续按住和松手都不重复输入；此模式不弹出字母菜单。空格、删除和语言键保持原行为。"
                else "滑动时在原键内预览，松手输入；所有布局角标的方向均可在下方反转。",
                style = MaterialTheme.typography.bodySmall)
            // 角标“长按”时长按总是出角标，气泡出不来：照常显示，置灰并写明被谁锁。
            run {
                val lock = InputGestureLocks.lockFor(settings, InputGestureLock.Option.LETTER_LONG_PRESS_MENU, true)
                    ?.takeIf { !settings.letterLongPressMenu }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("字母键长按弹出大小写", style = MaterialTheme.typography.titleSmall)
                        Text("长按字母弹出小写、大写和带音调的字母；关掉后长按字母不弹，上下拖动输入角标不受打扰。",
                            style = MaterialTheme.typography.bodySmall)
                        if (lock != null) SettingsLockNote(lock.message)
                    }
                    Switch(checked = settings.letterLongPressMenu, enabled = lock == null,
                        modifier = Modifier.testTag("letter-long-press-menu"), onCheckedChange = {
                            settings = settings.copy(letterLongPressMenu = it); settings.save(context)
                        })
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("反转上下滑方向", style = MaterialTheme.typography.titleSmall)
                    Text(if (settings.symbolInputMode == SymbolInputMode.LONG_PRESS)
                        "所有布局：上方符号使用长按；下方符号${if (settings.reverseSymbolSwipe) "下拉" else "上拉"}输入。"
                        else if (settings.reverseSymbolSwipe)
                        "所有布局：上拉输入上方符号，下拉输入下方符号。"
                        else "所有布局：下拉输入上方符号，上拉输入下方符号。",
                        style = MaterialTheme.typography.bodySmall)
                    Text(if (settings.reverseSymbolSwipe) "删除键：上拉清空，下拉撤销。"
                        else "删除键：下拉清空，上拉撤销。", style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = settings.reverseSymbolSwipe,
                    modifier = Modifier.testTag("reverse-symbol-swipe"), onCheckedChange = {
                        settings = settings.copy(reverseSymbolSwipe = it); settings.save(context)
                    })
            }
            }
            if (InputExperiencePart.FLICK_DISC in parts) {
            divider()
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("拂动时显示方向圆盘", style = MaterialTheme.typography.titleSmall)
                    Text("默认开启。日语假名、九键拂动选字母和 14 键字母对：按住时在键上方显示四个方向的圆盘和箭头；关闭后与 26 键一样在键内渐变提示。",
                        style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = settings.flickDirectionDisc,
                    modifier = Modifier.testTag("flick-direction-disc"), onCheckedChange = {
                        settings = settings.copy(flickDirectionDisc = it); settings.save(context)
                    })
            }
            }
            if (InputExperiencePart.KANA in parts) {
            divider()
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("假名方向提示", style = MaterialTheme.typography.titleSmall)
                    Text("日语九键按键四周显示上下左右的灰色假名；按住时的方向圆盘由「拂动时显示方向圆盘」控制。",
                        style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = settings.kanaDirectionHints,
                    modifier = Modifier.testTag("kana-direction-hints"), onCheckedChange = {
                        settings = settings.copy(kanaDirectionHints = it); settings.save(context)
                    })
            }
            }
            if (InputExperiencePart.CURSOR in parts) {
            divider()
            Text("滑动移动光标", style = MaterialTheme.typography.titleSmall)
            Row(Modifier.fillMaxWidth().height(124.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CursorGestureMode.entries.forEach { mode ->
                    val lock = InputGestureLocks.lockFor(settings, InputGestureLock.Option.CURSOR_GESTURE, mode)
                    IllustratedChoiceCard(mode.label, settings.cursorGesture == mode,
                        onClick = { settings = settings.copy(cursorGesture = mode); settings.save(context) },
                        modifier = Modifier.weight(1f), tag = "cursor-gesture:${mode.name}", enabled = lock == null) {
                        CursorGestureIllustration(mode)
                    }
                }
            }
            CursorGestureMode.entries.firstNotNullOfOrNull {
                InputGestureLocks.lockFor(settings, InputGestureLock.Option.CURSOR_GESTURE, it)
            }?.let { SettingsLockNote("「${(it.lockedValue as CursorGestureMode).label}」${it.message}") }
            if (settings.cursorGesture == CursorGestureMode.KEYBOARD) {
                Text("整个键盘滑动移动光标时，英文 26 键的滑行输入暂停。", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // 只显示当前生效的项：长按空格移动光标时不列连续空格/语音；关闭滑动移动光标时不显示光标距离。
            // 触发时长跟着空格当前的长按功能放，标题写明它控制的是哪个功能。
            @Composable fun spaceHoldSlider() {
                val actionText = when (settings.effectiveSpaceHold) {
                    SpaceHoldAction.CURSOR -> "移动光标"; SpaceHoldAction.REPEAT -> "连续输入空格"; SpaceHoldAction.VOICE_TOGGLE -> "开关语音"
                }
                Text("长按空格多久开始$actionText：${"%.1f".format(settings.cursorHoldSeconds)} 秒")
                Slider(value = settings.cursorHoldSeconds, modifier = Modifier.testTag("cursor-hold-slider"),
                    onValueChange = { settings = settings.copy(cursorHoldSeconds = normalizeCursorHoldSeconds(it)) },
                    onValueChangeFinished = { settings.save(context) }, valueRange = 0.1f..1f, steps = 8)
                Text("默认 0.2 秒，每档 0.1 秒。语言键的长按单独设置。", style = MaterialTheme.typography.bodySmall)
            }
            if (settings.cursorGesture == CursorGestureMode.SPACE) {
                Text("长按空格后左右滑动移动光标；要让长按空格连续输入空格或开关语音，先改用其他移动方式。",
                    style = MaterialTheme.typography.bodySmall)
                spaceHoldSlider()
            }
            if (settings.cursorGesture != CursorGestureMode.NONE) {
                Text("光标每移动一字所需距离：${settings.cursorStepDp.toInt()} dp")
                Text("距离越小越灵敏", style = MaterialTheme.typography.bodySmall)
                Slider(value = settings.cursorStepDp, onValueChange = { settings = settings.copy(cursorStepDp = it) },
                    onValueChangeFinished = { settings.save(context) }, valueRange = 6f..24f, steps = 17)
            }
            if (settings.cursorGesture != CursorGestureMode.SPACE) {
                HorizontalDivider()
                Text("长按空格", style = MaterialTheme.typography.titleSmall)
                Text("语音再次长按结束，也可用工具栏麦克风结束。", style = MaterialTheme.typography.bodySmall)
                Row(Modifier.fillMaxWidth().height(104.dp).testTag("space-hold-options"),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(SpaceHoldAction.REPEAT, SpaceHoldAction.VOICE_TOGGLE).forEach { action ->
                        IllustratedChoiceCard(action.label, settings.effectiveSpaceHold == action,
                            onClick = { settings = settings.copy(spaceHold = action); settings.save(context) },
                            modifier = Modifier.weight(1f), tag = "space-hold:${action.name}") {
                            SpaceHoldIllustration(voice = action == SpaceHoldAction.VOICE_TOGGLE)
                        }
                    }
                }
                spaceHoldSlider()
            }
            }
            if (InputExperiencePart.LANGUAGE_HOLD in parts) {
            divider()
            Text("长按语言键弹出列表：${"%.2f".format(settings.languageMenuHoldSeconds)} 秒")
            Text("默认 0.15 秒，每档 0.05 秒；按住语言键达到时长后弹出语言列表，与空格长按分开设置。", style = MaterialTheme.typography.bodySmall)
            Slider(value = settings.languageMenuHoldSeconds, modifier = Modifier.testTag("language-menu-hold-slider"),
                onValueChange = { settings = settings.copy(languageMenuHoldSeconds = normalizeLanguageMenuHoldSeconds(it)) },
                onValueChangeFinished = { settings.save(context) }, valueRange = 0.05f..1f, steps = 18)
            }
            if (InputExperiencePart.HANDWRITING in parts) {
            divider()
            Text("手写停顿识别：${"%.1f".format(settings.handwritingPauseSeconds)} 秒")
            Text("默认 0.5 秒，每档 0.1 秒；继续书写会重新计时。", style = MaterialTheme.typography.bodySmall)
            Slider(value = settings.handwritingPauseSeconds, modifier = Modifier.testTag("handwriting-pause-slider"),
                onValueChange = { settings = settings.copy(handwritingPauseSeconds = normalizeHandwritingPause(it)) },
                onValueChangeFinished = { settings.save(context) }, valueRange = 0.1f..2.5f, steps = 23)
            Text("手写公式两边的标记", style = MaterialTheme.typography.titleSmall)
            Text("公式模式选中候选后上屏的样子，例如 x^{2} 上屏为 \$x^{2}\$ 或 \\[x^{2}\\]。", style = MaterialTheme.typography.bodySmall)
            FormulaDelimiter.entries.forEach { option ->
                Row(Modifier.fillMaxWidth().clickable { settings = settings.copy(formulaDelimiter = option); settings.save(context) }
                    .padding(vertical = 4.dp).testTag("formula-delimiter:${option.name}"),
                    verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = settings.formulaDelimiter == option,
                        onClick = { settings = settings.copy(formulaDelimiter = option); settings.save(context) })
                    Text(option.label)
                }
            }
            }
            if (InputExperiencePart.KEY_TEXT in parts) {
            divider()
            Text("按键字号：${(settings.keyTextScale * 100).toInt()}%")
            Text("a b c  A B C  拼音", fontSize = (16f * settings.keyTextScale).sp)
            Slider(value = settings.keyTextScale, onValueChange = { settings = settings.copy(keyTextScale = it) },
                onValueChangeFinished = { settings.save(context) }, valueRange = 0.8f..1.6f, steps = 15)
            }
        }
    })
}
