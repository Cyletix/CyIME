package com.kingzcheung.xime.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.twotone.Keyboard
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kingzcheung.xime.settings.SettingsPreferences
import com.kingzcheung.xime.ui.keyboard.normalizeLetterSwipeDistance
import com.kingzcheung.xime.ui.keyboard.rememberKeyboardInputPreferences

internal enum class LayoutInputCategory(val route: String, val title: String, val description: String) {
    Qwerty("layout_input_qwerty", "26 键", "邻键纠错、滑行输入、分体键盘、字母键符号"),
    Nine("layout_input_nine", "九键与 14 键", "拂动或滑动选字母、触发距离（含日语假名）、假名提示、侧栏符号"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LayoutInputSettingsContent(category: LayoutInputCategory, onBack: () -> Unit) {
    val context = LocalContext.current
    val saved = rememberKeyboardInputPreferences()
    var settings by remember(saved) { mutableStateOf(saved) }
    Scaffold(containerColor = MaterialTheme.colorScheme.surface, topBar = {
        TopAppBar(title = { Text(category.title) }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
        })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            when (category) {
                LayoutInputCategory.Qwerty -> SettingsSection(title = "26 键输入", content = {
                    SettingsToggleItem(Icons.TwoTone.Keyboard, "邻键纠错",
                        "仅用于中文全拼 26 键；按实际键位补充候选，保留原输入，不用于双拼。",
                        checked = settings.neighborCorrection, onCheckedChange = {
                            settings = settings.copy(neighborCorrection = it); settings.save(context)
                        })
                    HorizontalDivider()
                    // 「键盘滑动」移光标和滑行冲突：照常显示，被锁时置灰并写明被谁锁（见 InputGestureLocks）。
                    SettingsToggleItem(Icons.TwoTone.Keyboard, "滑行输入",
                        "英文 26 键手指划过字母出整词，候选栏可换成其他词；竖着滑一行仍输入角标符号。",
                        checked = settings.glideTyping, onCheckedChange = {
                            settings = settings.copy(glideTyping = it); settings.save(context)
                        },
                        lockMessage = com.kingzcheung.xime.ui.keyboard.InputGestureLocks.lockFor(settings,
                            com.kingzcheung.xime.ui.keyboard.InputGestureLock.Option.GLIDE_TYPING, true)
                            ?.takeIf { !settings.glideTyping }?.message)
                    HorizontalDivider()
                    QwertyStaggerSettings(settings) { settings = it; it.save(context) }
                    HorizontalDivider()
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text("空格两侧的标点", style = MaterialTheme.typography.titleSmall)
                        Text("只开句号：原逗号键移到空格右侧，点按句号、滑动逗号。两个都开：滑动输入另一种宽度，" +
                            "全角时点按全角、半角时点按半角。", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        SpacePunctuationIllustration(settings.qwertyCommaKey, settings.qwertyPeriodKey)
                    }
                    SettingsToggleItem(Icons.TwoTone.Keyboard, "空格左侧逗号键", "关闭后空格向左加宽。",
                        checked = settings.qwertyCommaKey, onCheckedChange = {
                            settings = settings.copy(qwertyCommaKey = it); settings.save(context)
                        })
                    SettingsToggleItem(Icons.TwoTone.Keyboard, "空格右侧句号键", "开启后空格让出一个键宽。",
                        checked = settings.qwertyPeriodKey, onCheckedChange = {
                            settings = settings.copy(qwertyPeriodKey = it); settings.save(context)
                        })
                })
                LayoutInputCategory.Nine -> Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    SettingsSection(title = "九键与笔画", content = {
                        SettingsToggleItem(Icons.TwoTone.Keyboard, "拂动选字母",
                            "左、上、右、下依次输入键上的第 1～4 个字母（7、9 用到下），点按仍是九键输入；" +
                                "开启后长按输入数字、不再弹出字母菜单，与「长按输入」角标的方式一致。触发距离在下方「滑动触发距离」设置。",
                            checked = settings.t9FlickLetters, onCheckedChange = {
                                settings = settings.copy(t9FlickLetters = it); settings.save(context)
                            },
                            // 角标“长按”时数字占了长按，拂动必须开着：锁成开，不能关。
                            lockMessage = com.kingzcheung.xime.ui.keyboard.InputGestureLocks.lockFor(settings,
                                com.kingzcheung.xime.ui.keyboard.InputGestureLock.Option.T9_FLICK_LETTERS, false)
                                ?.takeIf { settings.t9FlickLetters }?.message)
                    })
                    SettingsSection(title = "14 键双字母键", content = {
                        SettingsToggleItem(Icons.TwoTone.Keyboard, "左右滑动选字母",
                            "例如 qw：左滑输入 q，右滑输入 w，松手确认；点按仍使用合并键。适用于拼音和小鹤双拼 14 键。",
                            checked = settings.fourteenLetterSwipe, onCheckedChange = {
                                settings = settings.copy(fourteenLetterSwipe = it); settings.save(context)
                            })
                        Text("开启时，双字母键的横滑优先用于选字母；其他位置的光标手势仍遵循共通设置。",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                    })
                    SettingsSection(title = "滑动触发距离", content = {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Text("滑动触发距离：${settings.fourteenLetterSwipeDp.toInt()} dp", style = MaterialTheme.typography.titleSmall)
                            Slider(value = settings.fourteenLetterSwipeDp,
                                modifier = Modifier.testTag("fourteen-letter-distance"),
                                valueRange = 12f..72f, steps = 29,
                                onValueChange = { settings = settings.copy(fourteenLetterSwipeDp = normalizeLetterSwipeDistance(it)) },
                                onValueChangeFinished = { settings.save(context) })
                            Text("默认 24 dp，每档 2 dp；越大越不易误触。九键拂动选字母、14 键左右滑选字母和日语九键假名拂动共用这个距离。",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    })
                    InputExperienceSettings(setOf(InputExperiencePart.FLICK_DISC, InputExperiencePart.KANA), "拂动与假名提示")
                }
            }
            Text("分体键盘、按键符号在上一页「键盘布局」；角标、光标与空格在「按键与手势」；字号在「主题与外观」。各布局共用，无需逐个设置。",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}


@Composable
private fun QwertyStaggerSettings(
    settings: com.kingzcheung.xime.ui.keyboard.KeyboardInputPreferences,
    onChange: (com.kingzcheung.xime.ui.keyboard.KeyboardInputPreferences) -> Unit,
) {
    val modes = com.kingzcheung.xime.ui.keyboard.QwertyStaggerMode.entries
    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("三行字母错位", style = MaterialTheme.typography.titleSmall)
        Text("实体键盘：A 行右移 1/4 键、Z 行右移 3/4 键；手机常见：Z 与 S 上下对齐；按尺寸自动：手机用手机排布，" +
            "平板逐渐过渡到实体排布（原有行为）。分体与 14 键同样遵循。", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth().height(108.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            modes.forEach { mode ->
                IllustratedChoiceCard(mode.label, settings.qwertyStagger == mode,
                    onClick = { onChange(settings.copy(qwertyStagger = mode)) },
                    modifier = Modifier.weight(1f), tag = "qwerty-stagger:${mode.name}") {
                    QwertyStaggerIllustration(when (mode) {
                        com.kingzcheung.xime.ui.keyboard.QwertyStaggerMode.PHYSICAL -> 1f
                        com.kingzcheung.xime.ui.keyboard.QwertyStaggerMode.PHONE -> 0f
                        com.kingzcheung.xime.ui.keyboard.QwertyStaggerMode.CUSTOM -> settings.qwertyStaggerCustom
                        com.kingzcheung.xime.ui.keyboard.QwertyStaggerMode.AUTO -> 0.5f
                    })
                }
            }
        }
        if (settings.qwertyStagger == com.kingzcheung.xime.ui.keyboard.QwertyStaggerMode.CUSTOM) {
            var custom by remember(settings.qwertyStaggerCustom) { mutableStateOf(settings.qwertyStaggerCustom) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("手机", style = MaterialTheme.typography.bodySmall)
                Slider(value = custom, onValueChange = { custom = it },
                    onValueChangeFinished = { onChange(settings.copy(qwertyStaggerCustom = custom)) },
                    valueRange = 0f..1f, modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                        .testTag("qwerty-stagger-custom"))
                Text("实体", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
