package com.kingzcheung.xime.ui.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kingzcheung.xime.keyboard.KeyboardPanelLaunch

internal fun keyboardPanelTitle(panel: KeyboardPanelLaunch.Panel) = when (panel) {
    KeyboardPanelLaunch.Panel.RESIZE -> "键盘尺寸与位置"
    KeyboardPanelLaunch.Panel.ARRANGE -> "工具栏与菜单排列"
}

@Composable
internal fun KeyboardPanelSettingsContent(panel: KeyboardPanelLaunch.Panel, onBack: () -> Unit) {
    KeyboardSettingsScaffold(keyboardPanelTitle(panel), onBack, preview = true, editPanel = panel) { modifier ->
        Text(
            when (panel) {
                KeyboardPanelLaunch.Panel.RESIZE -> "点「调整尺寸」后，拖动边框或移动手柄。\n调好大小和透明度，点「确认」查看效果。"
                KeyboardPanelLaunch.Panel.ARRANGE -> "点「编辑排列」后，可以拖动工具栏和菜单里的按钮。点勾号回到键盘预览。"
            },
            modifier.padding(horizontal = 24.dp, vertical = 8.dp), style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
