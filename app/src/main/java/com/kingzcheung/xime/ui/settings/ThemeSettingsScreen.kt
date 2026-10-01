package com.kingzcheung.xime.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.twotone.Palette
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.activity.compose.BackHandler
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kingzcheung.xime.ui.theme.KeyboardColorScheme
import com.kingzcheung.xime.viewmodel.ThemeSettingsViewModel
import androidx.compose.ui.platform.LocalContext
import com.kingzcheung.xime.settings.SettingsPreferences
import com.kingzcheung.xime.ui.theme.VisualStyles

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeSettingsContent(
    onBack: () -> Unit,
    onThemeChanged: () -> Unit = {}
) {
    val viewModel: ThemeSettingsViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showIconSettings by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var showVisualSettings by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    BackHandler(showIconSettings || showVisualSettings) { showIconSettings = false; showVisualSettings = false }
    if (showVisualSettings) {
        Scaffold(topBar = { TopAppBar(title = { Text("视觉样式") }, navigationIcon = {
            IconButton(onClick = { showVisualSettings = false }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
        }) }) { padding ->
            LazyColumn(Modifier.fillMaxSize().padding(padding).padding(16.dp)) { item {
                VisualStylePicker(VisualStyles.current) { style ->
                    changeAppearance(context, onThemeChanged) { SettingsPreferences.setVisualStyle(context, style) }
                }
            } }
        }
        return
    }
    if (showIconSettings) {
        IconSettingsContent(onBack = { showIconSettings = false }, onChanged = onThemeChanged)
        return
    }
    
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("主题与定制") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { paddingValues ->
        var previewTheme by remember { mutableStateOf<KeyboardColorScheme?>(null) }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = "显示模式",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            
            item {
                val currentTheme = uiState.colorThemes.firstOrNull { it.id == uiState.colorTheme }
                    ?: uiState.colorThemes.first()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    KeyboardThemeCard(
                        theme = currentTheme,
                        previewAspectRatio = 1.3f,
                        isSelected = uiState.darkMode == 2,
                        onClick = {
                            changeAppearance(context, onThemeChanged) { viewModel.setDarkMode(2) }
                        },
                        modifier = Modifier.weight(1f),
                        title = "跟随系统"
                    )
                    KeyboardThemeCard(
                        theme = currentTheme,
                        previewAspectRatio = 1.3f,
                        title = "浅色",
                        isSelected = uiState.darkMode == 0,
                        previewDark = false,
                        onClick = {
                            changeAppearance(context, onThemeChanged) { viewModel.setDarkMode(0) }
                        },
                        modifier = Modifier.weight(1f)
                    )
                    KeyboardThemeCard(
                        theme = currentTheme,
                        previewAspectRatio = 1.3f,
                        title = "深色",
                        isSelected = uiState.darkMode == 1,
                        previewDark = true,
                        onClick = {
                            changeAppearance(context, onThemeChanged) { viewModel.setDarkMode(1) }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "配色方案",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }
            
            item {
                Text(
                    text = "柔和蓝在各设备使用相同配色；跟随系统动态配色会随壁纸和系统改变。点击可预览。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }
            
            uiState.colorThemes.chunked(3).forEach { rowThemes ->
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowThemes.forEach { theme ->
                            KeyboardThemeCard(
                                theme = theme,
                                isSelected = uiState.colorTheme == theme.id,
                                onClick = {
                                    previewTheme = theme
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        repeat(3 - rowThemes.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
                
                item {
                    Spacer(modifier = Modifier.height(1.dp))
                }
            }
            
            item {
                SettingsSection(title = "外观细节") {
                    SettingsItem(icon = Icons.TwoTone.Palette, title = "视觉样式",
                        subtitle = VisualStyles.current.title, onClick = { showVisualSettings = true }, showArrow = true)
                    SettingsItem(icon = Icons.TwoTone.Palette, title = "图标设置",
                        subtitle = if (com.kingzcheung.xime.ui.theme.IconAppearance.linked) "与视觉样式同步" else "独立选择",
                        onClick = { showIconSettings = true }, showArrow = true)
                }
            }
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "提示: 配色切换后设置页面立即生效，键盘需重启输入法",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
        }

        previewTheme?.let { theme ->
            ThemePreviewSheet(
                theme = theme,
                onApply = {
                    changeAppearance(context, onThemeChanged) {
                        viewModel.setColorTheme(theme.id)
                        previewTheme = null
                    }
                },
                onDismiss = { previewTheme = null },
            )
        }
    }
}
