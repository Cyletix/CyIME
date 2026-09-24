package com.kingzcheung.xime.ui.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.DarkMode
import androidx.compose.material.icons.twotone.Keyboard
import androidx.compose.material.icons.twotone.LightMode
import androidx.compose.material.icons.twotone.Padding
import androidx.compose.material.icons.twotone.Quickreply
import androidx.compose.material.icons.twotone.Rotate90DegreesCcw
import androidx.compose.material.icons.twotone.Settings
import androidx.compose.material.icons.twotone.SettingsOverscan
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kingzcheung.xime.viewmodel.SchemaSwitchUiState

data class MenuItem(
    val icon: Painter? = null,
    val label: String,
    val action: () -> Unit,
    val textIcon: String? = null,
    val currentState: String? = null,
)

data class MenuBarState(
    val isVisible: Boolean,
    val isDarkTheme: Boolean,
    val darkMode: Int = 2,
    val backgroundColor: Color,
    val keyBgColor: Color = Color.White,
    val keyTextColor: Color = Color(0xFF202124),
    val isFloatingMode: Boolean = false,
    val schemaSwitches: List<SchemaSwitchUiState> = emptyList(),
)

data class MenuBarCallbacks(
    val onDismiss: () -> Unit,
    val onClipboard: () -> Unit,
    val onQuickSend: () -> Unit,
    val onKeyboardResize: () -> Unit,
    val onEmoji: () -> Unit,
    val onReloadConfig: () -> Unit,
    val onSettings: () -> Unit,
    val onSchemaList: () -> Unit,
    val onToggleDarkMode: () -> Unit,
    val onFloatingModeToggle: (() -> Unit)? = null,
    val onToolbarCustomize: () -> Unit = {},
    val onToggleSchemaSwitch: ((SchemaSwitchUiState) -> Unit)? = null,
)

@Composable
fun MenuBar(
    state: MenuBarState,
    callbacks: MenuBarCallbacks,
    modifier: Modifier = Modifier
) {
    if (!state.isVisible) return
    
    val textColor = state.keyTextColor
    // 功能 item 背景：与键盘按键背景一致（keyBgColor，浅色纯白、深色跟随 keyboard.colors）
    val itemBgColor = state.keyBgColor
    val configuration = LocalConfiguration.current
    val isLandscape = !state.isFloatingMode && configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    
    val quickSendIcon = rememberVectorPainter(Icons.TwoTone.Quickreply)
    val keyboardResizeIcon = rememberVectorPainter(Icons.TwoTone.SettingsOverscan)
    val darkModeIcon = when (state.darkMode) {
        0 -> rememberVectorPainter(Icons.TwoTone.DarkMode)
        1 -> rememberVectorPainter(Icons.TwoTone.LightMode)
        else -> rememberVectorPainter(if (state.isDarkTheme) Icons.TwoTone.LightMode else Icons.TwoTone.DarkMode)
    }
    val deployIcon = rememberVectorPainter(Icons.TwoTone.Rotate90DegreesCcw)
    val customizeIcon = rememberVectorPainter(Icons.TwoTone.Padding)
    val schemaIcon = rememberVectorPainter(Icons.TwoTone.Keyboard)
    val settingsIcon = rememberVectorPainter(Icons.TwoTone.Settings)

    val darkModeLabel = when (state.darkMode) {
        0 -> "深色模式"
        1 -> "浅色模式"
        else -> "跟随系统"
    }

    var showOptions by remember { mutableStateOf(false) }
    val widthSwitch = state.schemaSwitches.firstOrNull { it.name == "full_shape" }
    val options = state.schemaSwitches.filter { it.name != "ascii_mode" && it.name != "full_shape" }
    if (showOptions) {
        Column(modifier.fillMaxSize().background(state.backgroundColor)
            .padding(horizontal = 12.dp).verticalScroll(rememberScrollState())) {
            Row(Modifier.fillMaxWidth().height(44.dp).clickable { showOptions = false },
                verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回菜单", tint = textColor, modifier = Modifier.size(22.dp))
                Text("输入选项", color = textColor, fontSize = 16.sp, modifier = Modifier.padding(start = 12.dp))
            }
            options.forEach { sw ->
                val current = sw.states.getOrNull(sw.currentIndex) ?: "未知"
                val title = when (sw.name) {
                    "ascii_punct" -> "标点样式"
                    "simplification" -> "简繁转换"
                    else -> sw.states.joinToString(" / ")
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(12.dp))
                    .background(itemBgColor).semantics { stateDescription = current }
                    .clickable { callbacks.onToggleSchemaSwitch?.invoke(sw) }
                    .padding(horizontal = 16.dp, vertical = 12.dp).testTag("input-option:${sw.name}"),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(title, color = textColor, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Text("$current  ›", color = textColor, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
        return
    }
    val menuItems = listOf(
        MenuItem(keyboardResizeIcon, "键盘调节", callbacks.onKeyboardResize),
        MenuItem(settingsIcon, "设置", callbacks.onSettings),
    ) + listOfNotNull(widthSwitch?.let { sw ->
        val current = sw.states.getOrNull(sw.currentIndex) ?: "未知"
        MenuItem(label = "全角／半角", action = { callbacks.onToggleSchemaSwitch?.invoke(sw) },
            textIcon = current, currentState = current)
    }) + listOf(
        MenuItem(quickSendIcon, "快捷发送", callbacks.onQuickSend),
        MenuItem(customizeIcon, "定制工具栏", callbacks.onToolbarCustomize),
        MenuItem(darkModeIcon, darkModeLabel, callbacks.onToggleDarkMode),
    ) + (if (options.isNotEmpty()) listOf(MenuItem(schemaIcon, "输入选项", { showOptions = true })) else emptyList()) +
        listOf(MenuItem(deployIcon, "部署方案", callbacks.onReloadConfig))
    KeyboardPanelGrid(menuItems, isLandscape, textColor, "menu-pages",
        modifier.fillMaxWidth().background(state.backgroundColor)) { item, cellModifier ->
        MenuItemButton(item, itemBgColor, textColor,
            cellModifier.testTag("menu-item:${item.label}"), isLandscape)
    }
}

@Composable
fun MenuItemButton(
    item: MenuItem,
    bgColor: Color,
    textColor: Color,
    modifier: Modifier = Modifier,
    isLandscape: Boolean = false
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .clickable { item.action() }
            .semantics { item.currentState?.let { stateDescription = it } }
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (item.textIcon != null) {
            Text(
                text = item.textIcon,
                color = textColor.copy(alpha = 0.7f),
                fontSize = if (isLandscape) 18.sp else 24.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        } else if (item.icon != null) {
            Icon(
                painter = item.icon,
                contentDescription = item.label,
                tint = textColor.copy(alpha = 0.7f),
                modifier = Modifier.size(if (isLandscape) 18.dp else 24.dp)
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = item.label,
            color = textColor,
            fontSize = if (isLandscape) 11.sp else 12.sp,
            lineHeight = if (isLandscape) 13.sp else 14.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
