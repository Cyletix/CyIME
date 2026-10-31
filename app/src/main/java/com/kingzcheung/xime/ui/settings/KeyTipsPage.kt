package com.kingzcheung.xime.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kingzcheung.xime.ui.keyboard.KeyboardInputPreferences
import com.kingzcheung.xime.ui.keyboard.SymbolInputMode

/** 一条技巧：[how] 怎么按，[what] 会怎样。 */
internal data class KeyTip(val how: String, val what: String)

/** 一组技巧，对应「按键技巧」页里的一个分组。 */
internal data class KeyTipGroup(val title: String, val tips: List<KeyTip>)

/**
 * 「按键技巧」页的内容（用户 10-31：Shift、Ctrl 的高级操作很多人不知道，要有地方说明）。
 * 方向、时长跟着实际设置写（角标长按/反转、符号键时限、Z X C V 开关），不写死默认值。
 */
internal fun keyTipGroups(settings: KeyboardInputPreferences): List<KeyTipGroup> {
    val upper = when {
        settings.symbolInputMode == SymbolInputMode.LONG_PRESS -> "长按"
        settings.reverseSymbolSwipe -> "上拉"
        else -> "下拉"
    }
    val lower = if (settings.reverseSymbolSwipe) "下拉" else "上拉"
    val clearSwipe = if (settings.reverseSymbolSwipe) "上拉" else "下拉"
    val restoreSwipe = if (settings.reverseSymbolSwipe) "下拉" else "上拉"
    val hold = "%.1f".format(com.kingzcheung.xime.ui.keyboard.normalizeSymbolKeyHoldSeconds(settings.symbolKeyHoldSeconds))
    return listOf(
        KeyTipGroup("Shift", listOf(
            KeyTip("点一下 Shift", "下一个字母大写，打完自动回到小写。"),
            KeyTip("连点两下 Shift", "大写锁定，再点一下解除。"),
            KeyTip("按住 Shift 再点字母", "按住期间一直是大写。"),
            KeyTip("从 Shift 拖到字母上松手", "只把这一个字母打成大写。14 键拖动时双字母键拆成单字母，停在哪个出哪个。"),
            KeyTip("按住 Shift 看候选", "有英文的候选中英对调，这时点候选或按空格上屏英文。"),
            KeyTip("从 Shift 拖到候选上松手", "这条候选有英文就上屏英文。"),
            KeyTip("长按空格移光标时按住 Shift", "选中文字，和电脑上 Shift 加方向键一样。"),
        )),
        KeyTipGroup("Ctrl（26 键左下角 !@# 键）", buildList {
            add(KeyTip("按住 !@#", "字母键改成显示快捷键：全选、复制、粘贴、剪切、撤销、重做、保存、查找等。"))
            add(KeyTip("按住 !@# 拖到字母上松手", "发送 Ctrl 加这个字母，和实体键盘一样；也可以另一根手指直接点字母。"))
            add(KeyTip("点一下 !@#", "$hold 秒内、没拖动就松手才打开符号面板；时限在「按键与手势」里调。"))
            add(if (settings.zxcvEditShortcuts) KeyTip("${upper} Z X C V", "撤销、剪切、复制、粘贴（「Z X C V 角标做撤销、剪切、复制、粘贴」已打开）。")
                else KeyTip("想${upper} Z X C V 直接撤销、剪切、复制、粘贴", "在「按键与手势」打开「Z X C V 角标做撤销、剪切、复制、粘贴」，默认关。"))
        }),
        KeyTipGroup("九键", listOf(
            KeyTip("长按「下一项」", "等于按住 Shift：候选中英对调，松手恢复。"),
            KeyTip("从「下一项」拖到候选上松手", "这条候选有英文就上屏英文。"),
        )),
        KeyTipGroup("其他按键", listOf(
            KeyTip("${upper}字母键", "输入键上方的角标；${lower}输入下方的角标。"),
            KeyTip("长按空格左右拖", "移动光标。"),
            KeyTip("删除键${clearSwipe}", "清空输入框；随后${restoreSwipe}删除键撤回这次清空。"),
            KeyTip("长按语言键", "弹出语言列表。"),
            KeyTip("长按候选", "确认后删掉这个词的学习记录。"),
        )),
    )
}

/** 「按键技巧」页：按 [keyTipGroups] 分组列出，每条一行「怎么按」加一行「会怎样」。 */
@Composable
internal fun KeyTipsSettings() {
    val settings = com.kingzcheung.xime.ui.keyboard.rememberKeyboardInputPreferences()
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        keyTipGroups(settings).forEach { group ->
            SettingsSection(title = group.title, content = {
                group.tips.forEachIndexed { index, tip ->
                    if (index > 0) HorizontalDivider(Modifier.padding(start = 16.dp), thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Column(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Text(tip.how, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface)
                        Text(tip.what, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            })
        }
    }
}
