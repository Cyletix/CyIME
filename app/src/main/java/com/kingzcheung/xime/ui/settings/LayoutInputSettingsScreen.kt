package com.kingzcheung.xime.ui.settings

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.twotone.Keyboard
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kingzcheung.xime.settings.SettingsPreferences
import com.kingzcheung.xime.ui.keyboard.normalizeLetterSwipeDistance
import com.kingzcheung.xime.ui.keyboard.rememberKeyboardInputPreferences

internal enum class LayoutInputCategory(val route: String, val title: String, val description: String) {
    Qwerty("layout_input_qwerty", "26 键", "邻键纠错、分体键盘、字母键符号"),
    Fourteen("layout_input_fourteen", "14 键", "左右滑动选字母、触发距离"),
    Nine("layout_input_nine", "九键与笔画", "侧栏快捷符号"),
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
                    SettingsToggleItem(Icons.TwoTone.Keyboard, "分体键盘", "适用于 26 键；间距与尺寸仍可在键盘调整面板中设置。",
                        checked = settings.splitKeyboardEnabled,
                        onCheckedChange = { SettingsPreferences.setSplitKeyboardEnabled(context, it) })
                    HorizontalDivider()
                    SettingsItem(Icons.TwoTone.Keyboard, "字母键滑动符号", "在自定义布局中调整上下角标符号并预览。",
                        onClick = { context.startActivity(Intent(context, com.kingzcheung.xime.CustomLayoutActivity::class.java)) },
                        showArrow = true)
                })
                LayoutInputCategory.Fourteen -> SettingsSection(title = "双字母键", content = {
                    SettingsToggleItem(Icons.TwoTone.Keyboard, "左右滑动选字母",
                        "例如 qw：左滑输入 q，右滑输入 w，松手确认；点按仍使用合并键。适用于拼音和小鹤双拼 14 键。",
                        checked = settings.fourteenLetterSwipe, onCheckedChange = {
                            settings = settings.copy(fourteenLetterSwipe = it); settings.save(context)
                        })
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text("滑动触发距离：${settings.fourteenLetterSwipeDp.toInt()} dp", style = MaterialTheme.typography.titleSmall)
                        Slider(value = settings.fourteenLetterSwipeDp, enabled = settings.fourteenLetterSwipe,
                            modifier = Modifier.testTag("fourteen-letter-distance"),
                            valueRange = 12f..72f, steps = 29,
                            onValueChange = { settings = settings.copy(fourteenLetterSwipeDp = normalizeLetterSwipeDistance(it)) },
                            onValueChangeFinished = { settings.save(context) })
                        Text("默认 24 dp，每档 2 dp；越大越不易误触。开启时，双字母键的横滑优先用于选字母；其他位置的光标手势仍遵循共通设置。",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                })
                LayoutInputCategory.Nine -> FixedSymbolsSettings()
            }
            Text("空格、光标、字号和按键角标等共通设置保留在「布局与显示」，无需逐个布局重复设置。",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun FixedSymbolsSettings() {
    val context = LocalContext.current
    val saved = rememberKeyboardInputPreferences()
    var settings by remember(saved) { mutableStateOf(saved) }
    SettingsSection(title = "固定栏快捷符号", content = {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("每行一个符号或短语。九键、笔画及常用符号键盘共用这份列表；留空使用方案默认值。",
                style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(value = settings.fixedSymbols,
                onValueChange = { settings = settings.copy(fixedSymbols = it) },
                modifier = Modifier.fillMaxWidth(), minLines = 3, maxLines = 6, label = { Text("快捷符号") })
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
