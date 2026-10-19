package com.kingzcheung.xime.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.LibraryBooks
import androidx.compose.material.icons.twotone.Gesture
import androidx.compose.material.icons.twotone.Speed
import androidx.compose.material.icons.twotone.AutoAwesome
import androidx.compose.material.icons.twotone.Backup
import androidx.compose.material.icons.twotone.Ballot

import androidx.compose.material.icons.twotone.Build
import androidx.compose.material.icons.twotone.Description
import androidx.compose.material.icons.twotone.Extension
import androidx.compose.material.icons.twotone.GraphicEq
import androidx.compose.material.icons.twotone.Info
import androidx.compose.material.icons.twotone.Keyboard
import androidx.compose.material.icons.twotone.KeyboardAlt
import androidx.compose.material.icons.twotone.Palette
import androidx.compose.material.icons.twotone.Storefront
import androidx.compose.material.icons.twotone.Straighten
import androidx.compose.material.icons.twotone.Sync
import androidx.compose.material.icons.twotone.TableChart
import androidx.compose.material.icons.twotone.ToggleOn
import androidx.compose.material.icons.twotone.TypeSpecimen
import androidx.compose.material.icons.twotone.Vibration
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kingzcheung.xime.settings.SettingsPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun SettingsMainContent(
    onNavigateToSchema: () -> Unit,
    onNavigateToMarket: () -> Unit = {},
    onNavigateToTheme: () -> Unit,
    onNavigateToKeyEffect: () -> Unit,
    onNavigateToLayoutDisplay: () -> Unit,
    onNavigateToDictionary: () -> Unit,
    onNavigateToPlugins: () -> Unit,
    onNavigateToModelLocal: () -> Unit = {},
    onNavigateToHandwritingTraining: () -> Unit = {},
    onNavigateToSmartPrediction: () -> Unit,
    onNavigateToSpeechToText: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateToClipboardSync: () -> Unit = {},
    onNavigateToBackup: () -> Unit = {},
    onNavigateToHardwareKeyboard: () -> Unit = {},
    onNavigateToLanguages: () -> Unit = {},
    onNavigateToRoute: (String) -> Unit = {},
) {
    val context = LocalContext.current
    var searchQuery by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    var entryStatus by remember { mutableStateOf(readMainEntryStatus(context)) }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        // 系统输入法选择框关闭、插拔键盘都不一定触发 resume，停留在本页时定时刷新。
        lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
            while (true) { entryStatus = readMainEntryStatus(context); kotlinx.coroutines.delay(500) }
        }
    }
    val density = LocalDensity.current
    val imeBottomPx = WindowInsets.ime.getBottom(density)
    val imeBottomDp = with(density) { imeBottomPx.toDp() }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            MediumTopAppBar(
                title = { Text("CyIME 设置") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                    navigationIconContentColor = Color.Unspecified,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = Color.Unspecified
                ),
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .consumeWindowInsets(innerPadding)
                .padding(horizontal = 16.dp),
            // Keep drawing behind the transparent IME gutters. IME space belongs to
            // scroll content, not a clipped full-width viewport below the toolbar.
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding(),
                bottom = maxOf(innerPadding.calculateBottomPadding(), imeBottomDp),
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { SettingsSearchField(searchQuery, { searchQuery = it }) }
            if (searchQuery.isNotBlank()) item {
                SettingsSearchResults(searchQuery) { entry ->
                    if (entry.target == SEARCH_TARGET_CUSTOM_LAYOUT) {
                        context.startActivity(Intent(context, com.kingzcheung.xime.CustomLayoutActivity::class.java))
                    } else onNavigateToRoute(entry.target)
                }
            }
            item {
                SettingsSection(title = "输入法", content = {
                    SettingsItem(
                        icon = Icons.TwoTone.Keyboard,
                        title = "启用输入法",
                        subtitle = "在系统设置中启用 CyIME",
                        status = if (entryStatus.imeEnabled) "已设置" else "未设置",
                        statusTone = if (entryStatus.imeEnabled) SettingsStatusTone.OK else SettingsStatusTone.WARNING,
                        onClick = {
                            val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
                            context.startActivity(intent)
                        }
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 56.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    SettingsItem(
                        icon = Icons.TwoTone.ToggleOn,
                        title = "选择输入法",
                        subtitle = "将 CyIME 设为当前输入法",
                        status = if (entryStatus.imeSelected) "已设置" else "未设置",
                        statusTone = if (entryStatus.imeSelected) SettingsStatusTone.OK else SettingsStatusTone.WARNING,
                        onClick = {
                            val imm = context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) 
                                as InputMethodManager
                            imm.showInputMethodPicker()
                        }
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 56.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    SettingsItem(icon = Icons.TwoTone.KeyboardAlt, title = "物理键盘",
                        subtitle = "语言切换快捷键、候选跟随与浮条", onClick = onNavigateToHardwareKeyboard,
                        status = if (entryStatus.keyboardConnected) "已连接" else "未连接",
                        statusTone = if (entryStatus.keyboardConnected) SettingsStatusTone.OK else SettingsStatusTone.NEUTRAL)
                    var testText by remember { mutableStateOf("") }
                    var isFocused by remember { mutableStateOf(false) }
                    val editorVisibility = remember { BringIntoViewRequester() }
                    var editorSize by remember { mutableStateOf(IntSize.Zero) }
                    // 清空（长按删除）会收起「清除」行、文字框变矮，列表随之回缩；等这一帧布局完成后
                    // 再把试打框连同键盘高度一起请求进视野，试打框始终露在键盘上方。
                    LaunchedEffect(isFocused, imeBottomPx, editorSize, testText.isEmpty()) {
                        if (isFocused && imeBottomPx > 0 && editorSize.height > 0) {
                            androidx.compose.runtime.withFrameNanos { }
                            editorVisibility.bringIntoView(Rect(0f, 0f, editorSize.width.toFloat(),
                                editorSize.height + imeBottomPx.toFloat()))
                        }
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusEvent { isFocused = it.hasFocus }
                                .bringIntoViewRequester(editorVisibility)
                                .onSizeChanged { editorSize = it }
                                .clip(RoundedCornerShape(28.dp))
                                .background(
                                    if (isFocused) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f)
                                )
                                .padding(horizontal = 16.dp, vertical = 14.dp)
                        ) {
                            BasicTextField(
                                value = testText,
                                onValueChange = { testText = it },
                                modifier = Modifier.fillMaxWidth(),
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                singleLine = false,
                                maxLines = 3,
                                decorationBox = { innerTextField ->
                                    Box {
                                        if (testText.isEmpty() && !isFocused) {
                                            Text(
                                                "点击此处开始输入测试...",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                            )
                                        }
                                        innerTextField()
                                    }
                                }
                            )
                        }
                        if (testText.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(onClick = { testText = "" }) {
                                    Text(
                                        "清除",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }
                })
            }

            item {
                SettingsSection(title = "外观与交互", content = {
                    SettingsItem(
                        icon = Icons.TwoTone.Palette,
                        title = "主题与定制",
                        subtitle = "自定义外观和样式",
                        status = entryStatus.themeName,
                        onClick = onNavigateToTheme,
                        showArrow = true
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 56.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    SettingsItem(
                        icon = Icons.TwoTone.Vibration,
                        title = "按键效果",
                        subtitle = "按键音效和振动反馈",
                        onClick = onNavigateToKeyEffect,
                        showArrow = true
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 56.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    SettingsItem(
                        icon = Icons.TwoTone.Speed,
                        title = "性能与省电",
                        subtitle = "高配、中配、低配一键切换，语音模型占用内存",
                        onClick = { onNavigateToRoute(SettingsRoutes.Performance) },
                        showArrow = true
                    )
                })
            }

            // 键盘布局相关的两个入口放在一起：各布局的手势与显示设置、按键布局编辑。
            item {
                SettingsSection(title = "键盘布局", content = {
                    SettingsItem(
                        icon = Icons.TwoTone.TableChart,
                        title = "布局与显示",
                        subtitle = "候选词显示、键盘布局等",
                        onClick = onNavigateToLayoutDisplay,
                        showArrow = true
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 56.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    SettingsItem(
                        icon = com.kingzcheung.xime.ui.keyboard.AddLayoutIcon,
                        title = "布局管理",
                        subtitle = "26键符号、自定义按键布局",
                        onClick = { context.startActivity(Intent(context, com.kingzcheung.xime.CustomLayoutActivity::class.java)) },
                        showArrow = true
                    )
                })
            }

            item {
                SettingsSection(title = "方案与词库", content = {
                    SettingsItem(icon = Icons.TwoTone.KeyboardAlt, title = "语言管理",
                        subtitle = "语言、输入方案、键盘布局、切换方式", onClick = onNavigateToLanguages, showArrow = true)
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 56.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    SettingsItem(
                        icon = Icons.TwoTone.KeyboardAlt,
                        title = "输入资源",
                        subtitle = "下载、导入和启停方案资源",
                        onClick = onNavigateToSchema,
                        showArrow = true
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 56.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    SettingsItem(
                        icon = Icons.TwoTone.Ballot,
                        title = "词库管理",
                        subtitle = "分类词库开关、个人词库和自定义短语",
                        onClick = onNavigateToDictionary,
                        showArrow = true
                    )
                })
            }

            item {
                SettingsSection(title = "扩展", content = {
                    SettingsItem(
                        icon = Icons.TwoTone.Storefront,
                        title = "扩展商店",
                        subtitle = "下载输入方案 / 模型 / 插件",
                        onClick = onNavigateToMarket,
                        showArrow = true
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 56.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    SettingsItem(
                        icon = Icons.TwoTone.Extension,
                        title = "插件管理",
                        subtitle = "管理已安装的插件",
                        onClick = onNavigateToPlugins,
                        showArrow = true
                    )
                })
            }

            item {
                SettingsSection(title = "智能", content = {
                    SettingsItem(
                        icon = Icons.TwoTone.AutoAwesome,
                        title = "智能联想",
                        subtitle = "基础联想、个人学习与可选模型增强",
                        onClick = onNavigateToSmartPrediction,
                        showArrow = true
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 56.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    var sttEnabled by remember { mutableStateOf(SettingsPreferences.isSttEnabled(context)) }
                    SettingsToggleItem(
                        icon = Icons.TwoTone.GraphicEq,
                        title = "语音转文本",
                        subtitle = "本地模型或在线服务，下载并选择识别方式",
                        checked = sttEnabled,
                        showArrow = true,
                        onClick = {
                            onNavigateToSpeechToText()
                        },
                        onCheckedChange = { enabled ->
                            sttEnabled = enabled
                            SettingsPreferences.setSttEnabled(context, enabled)
                        }
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 56.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    SettingsItem(
                        icon = Icons.TwoTone.Straighten,
                        title = "模型管理",
                        subtitle = "查看识字、语音、手写和联想的本地模型",
                        onClick = onNavigateToModelLocal,
                        showArrow = true
                    )
                    // Handwriting training is the developer's own tool: only 装到我的设备 and debug builds show it.
                    if (com.kingzcheung.xime.BuildConfig.DEV_TOOLS || com.kingzcheung.xime.BuildConfig.DEBUG) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 56.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                        SettingsItem(
                            icon = Icons.TwoTone.Gesture,
                            title = "手写训练模式",
                            subtitle = "按题写假名、字母、符号和公式，收集样本导出给电脑训练",
                            onClick = onNavigateToHandwritingTraining,
                            showArrow = true
                        )
                    }
                })
            }

            item {
                SettingsSection(                title = "同步与备份", content = {
                    SettingsItem(
                        icon = Icons.TwoTone.Backup,
                        title = "备份与恢复",
                        subtitle = "把设置导出为本地文件，或从备份文件恢复",
                        onClick = onNavigateToBackup,
                        showArrow = true
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 56.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    SettingsItem(
                        icon = Icons.TwoTone.Sync,
                        title = "剪贴板同步",
                        subtitle = "验证码提取、短信自动复制与远端设备同步",
                        onClick = onNavigateToClipboardSync,
                        showArrow = true
                    )
                })
            }

            item {
                SettingsSection(title = "关于", content = {
                    SettingsItem(
                        icon = Icons.TwoTone.Description,
                        title = "使用文档",
                        subtitle = "CyIME 使用说明",
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Cyletix/CyIME/blob/main/docs/usage.md"))
                            context.startActivity(intent)
                        },
                        showArrow = true
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 56.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    SettingsItem(
                        icon = Icons.TwoTone.Info,
                        title = "关于 CyIME",
                        subtitle = "版本信息、开发者、联系方式",
                        onClick = onNavigateToAbout,
                        showArrow = true
                    )
                })
            }
        }
    }
}

/** 首页入口右侧的状态：输入法启用/选中、实体键盘连接、当前主题。 */
internal data class MainEntryStatus(
    val imeEnabled: Boolean = false,
    val imeSelected: Boolean = false,
    val keyboardConnected: Boolean = false,
    val themeName: String = "",
)

internal fun readMainEntryStatus(context: android.content.Context): MainEntryStatus {
    val imm = context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? InputMethodManager
    val selected = android.content.ComponentName.unflattenFromString(
        Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD).orEmpty())
    val input = context.getSystemService(android.content.Context.INPUT_SERVICE) as? android.hardware.input.InputManager
    val devices: List<android.view.InputDevice> =
        input?.let { manager -> manager.inputDeviceIds.map { id -> manager.getInputDevice(id) }.filterNotNull() }.orEmpty()
    val theme = com.kingzcheung.xime.ui.theme.KeyboardThemes.getThemeById(SettingsPreferences.getKeyboardTheme(context)).name
    val glass = com.kingzcheung.xime.settings.FrostedGlassPreferences.read(context).enabled
    return MainEntryStatus(
        imeEnabled = imm?.enabledInputMethodList?.any { it.packageName == context.packageName } == true,
        imeSelected = selected?.packageName == context.packageName,
        keyboardConnected = com.kingzcheung.xime.service.hasPhysicalTextKeyboard(devices),
        themeName = if (glass) "$theme · 玻璃" else theme,
    )
}
