package com.kingzcheung.xime.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.twotone.Image
import androidx.compose.material.icons.twotone.Palette
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kingzcheung.xime.ui.theme.KeyboardColorScheme
import com.kingzcheung.xime.viewmodel.ThemeSettingsViewModel
import androidx.compose.ui.platform.LocalContext
import com.kingzcheung.xime.settings.SettingsPreferences
import com.kingzcheung.xime.settings.FrostedGlassPreferences
import com.kingzcheung.xime.settings.FrostedGlassProfiles
import com.kingzcheung.xime.ui.keyboard.rememberKeyboardInputPreferences
import com.kingzcheung.xime.ui.theme.VisualStyles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeSettingsContent(
    onBack: () -> Unit,
    onThemeChanged: () -> Unit = {}
) {
    var showIconSettings by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    val subPage = if (showIconSettings) "icons" else null
    SettingsSubPages(subPage, onBack = { showIconSettings = false }) { shown ->
        ThemeSettingsPage(shown, onBack, onThemeChanged,
            openIcons = { showIconSettings = true }, closeSubPage = { showIconSettings = false })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeSettingsPage(
    subPage: String?,
    onBack: () -> Unit,
    onThemeChanged: () -> Unit,
    openIcons: () -> Unit,
    closeSubPage: () -> Unit,
) {
    val viewModel: ThemeSettingsViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val glass = FrostedGlassProfiles(
        rememberKeyboardInputPreferences(false).frostedGlass,
        rememberKeyboardInputPreferences(true).frostedGlass,
    )
    if (subPage == "icons") {
        IconSettingsContent(onBack = closeSubPage, onChanged = onThemeChanged)
        return
    }
    
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("主题与外观") },
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
        var showGlassSettings by remember { mutableStateOf(false) }

        val listState = rememberSettingsSearchListState()
        LazyColumn(
            state = listState,
            modifier = Modifier.settingsGutterScroll(listState)
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
                    ?: com.kingzcheung.xime.ui.theme.KeyboardThemes.getThemeById(uiState.colorTheme)
                Row(
                    modifier = Modifier.settingsSearchAnchor("显示模式", bleed = 6.dp, bleedVertical = 6.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    KeyboardThemeCard(
                        theme = currentTheme,
                        glass = glass,
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
                        glass = glass,
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
                        glass = glass,
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
                SettingsSection(title = "材质效果") {
                    SettingsToggleItem(icon = Icons.TwoTone.Palette, title = "玻璃效果",
                        subtitle = "沿用当前配色，切换主题时保持", checked = glass.dark.enabled,
                        onCheckedChange = { enabled ->
                            changeAppearance(context, onThemeChanged) {
                                FrostedGlassPreferences.saveProfiles(context, glass.withEnabled(enabled))
                            }
                        })
                    if (glass.dark.enabled) {
                        SettingsItem(icon = Icons.TwoTone.Palette, title = "透明度与模糊",
                            subtitle = "切换深浅时，背景与按键透明度互换", showArrow = true,
                            onClick = { showGlassSettings = true })
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "配色方案",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.settingsSearchAnchor("配色方案", bleed = 6.dp, bleedVertical = 4.dp).fillMaxWidth().padding(bottom = 6.dp)
                )
            }
            
            item {
                Text(
                    text = "${(uiState.colorThemes.firstOrNull { it.id == uiState.colorTheme }
                        ?: com.kingzcheung.xime.ui.theme.KeyboardThemes.getThemeById(uiState.colorTheme)).name}在各设备使用相同配色。点击可预览。",
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
                                glass = glass,
                                isSelected = com.kingzcheung.xime.ui.theme.KeyboardThemes.getThemeById(uiState.colorTheme).id == theme.id,
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
                CustomImageThemeSection(currentThemeId = uiState.colorTheme) {
                    viewModel.refreshThemes()
                    changeAppearance(context, onThemeChanged) {
                        viewModel.setColorTheme(com.kingzcheung.xime.ui.theme.CustomImageTheme.ID)
                    }
                }
            }

            // 视觉样式只有一组卡片，直接放在本页；按键显示（字号、角标、气泡、底部）也属于外观。
            item {
                VisualStylePicker(VisualStyles.current) { style ->
                    changeAppearance(context, onThemeChanged) { SettingsPreferences.setVisualStyle(context, style) }
                }
            }
            item {
                KeyShapePicker(com.kingzcheung.xime.ui.theme.KeyShapes.current) { shape ->
                    changeAppearance(context, onThemeChanged) { SettingsPreferences.setKeyShape(context, shape) }
                }
            }
            item { KeyboardAppearanceSettings() }
            item {
                SettingsSection(title = "桌面图标") {
                    SettingsItem(icon = Icons.TwoTone.Palette, title = "图标设置",
                        subtitle = if (com.kingzcheung.xime.ui.theme.IconAppearance.linked) "与视觉样式同步" else "独立选择",
                        onClick = openIcons, showArrow = true)
                }
            }
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "提示: 配色切换后设置页面和键盘都会立即生效",
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
        if (showGlassSettings) {
            FrostedGlassSettingsSheet(
                theme = uiState.colorThemes.firstOrNull { it.id == uiState.colorTheme }
                    ?: com.kingzcheung.xime.ui.theme.KeyboardThemes.getThemeById(uiState.colorTheme),
                onApply = { draftGlass ->
                    changeAppearance(context, onThemeChanged) {
                        FrostedGlassPreferences.saveProfiles(context, draftGlass)
                        showGlassSettings = false
                    }
                },
                onDismiss = { showGlassSettings = false },
            )
        }
    }
}

/** 「用自己的图片」：从相册选一张图做键盘背景，存好后直接换成「我的图片」主题。 */
@Composable
private fun CustomImageThemeSection(currentThemeId: String, onSaved: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var hasImage by remember { mutableStateOf(com.kingzcheung.xime.ui.theme.CustomImageTheme.hasImage(context)) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val base = com.kingzcheung.xime.ui.theme.CustomImageTheme.baseFor(context, currentThemeId)
        scope.launch {
            val saved = withContext(Dispatchers.IO) {
                runCatching { com.kingzcheung.xime.ui.theme.CustomImageTheme.save(context, uri, base) }
            }
            saved.onSuccess {
                com.kingzcheung.xime.ui.theme.KeyboardThemes.reload(context)
                hasImage = true
                onSaved()
            }.onFailure {
                com.kingzcheung.xime.util.FileLogger.e("Appearance", "Custom background failed", it)
                android.widget.Toast.makeText(context, "这张图片读不出来，换一张试试", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }
    SettingsSection(title = "背景图片") {
        SettingsItem(icon = Icons.TwoTone.Image, title = "用自己的图片",
            subtitle = if (hasImage) "已设置，在上面的配色里选「我的图片」使用；点这里换一张"
                else "从相册选一张图片做键盘背景，按键和文字的颜色沿用当前主题",
            onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            showArrow = true)
    }
}
