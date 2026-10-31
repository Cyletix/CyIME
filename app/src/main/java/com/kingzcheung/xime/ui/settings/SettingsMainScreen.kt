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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.automirrored.twotone.MenuBook
import androidx.compose.material.icons.twotone.Draw
import androidx.compose.material.icons.twotone.Swipe
import androidx.compose.material.icons.twotone.Language
import androidx.compose.material.icons.twotone.AppSettingsAlt
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Speed
import androidx.compose.material.icons.twotone.AutoAwesome
import androidx.compose.material.icons.twotone.Backup

import androidx.compose.material.icons.twotone.Build
import androidx.compose.material.icons.twotone.CloudDownload
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
import androidx.compose.material.icons.twotone.Tune
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
import androidx.compose.runtime.collectAsState
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
    val waitingModelBytes by com.kingzcheung.xime.model.DefaultModelInstaller.waitingForWifiBytes.collectAsState()
    val updateState by com.kingzcheung.xime.update.AppUpdater.state.collectAsState()
    LaunchedEffect(Unit) { com.kingzcheung.xime.update.AppUpdater.checkIfDue(context) }
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
        val listState = rememberLazyListState()
        LazyColumn(
            state = listState,
            modifier = Modifier.settingsGutterScroll(listState, scrollBehavior.nestedScrollConnection)
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
            if (waitingModelBytes > 0) item {
                SettingsSection(title = "模型下载", content = {
                    SettingsItem(
                        icon = Icons.TwoTone.CloudDownload,
                        title = "等连上 Wi-Fi 再下载模型",
                        subtitle = "语音、联想、手写等模型共约 ${formatModelMegabytes(waitingModelBytes)}；现在用的是移动数据，点这里用流量下载",
                        status = "等 Wi-Fi",
                        statusTone = SettingsStatusTone.WARNING,
                        onClick = { com.kingzcheung.xime.model.DefaultModelInstaller.allowMeteredDownload(context) }
                    )
                })
            }
            pendingUpdate(updateState)?.let { release -> item { AppUpdateHomeNotice(release, onNavigateToAbout) } }
            if (searchQuery.isNotBlank()) item {
                SettingsSearchResults(searchQuery) { entry ->
                    if (entry.target == SEARCH_TARGET_CUSTOM_LAYOUT) {
                        context.startActivity(Intent(context, com.kingzcheung.xime.CustomLayoutActivity::class.java))
                    } else onNavigateToRoute(entry.target)
                }
            }
            item {
                SettingsSection(title = "配置选择", content = {
                    SettingsItem(
                        icon = Icons.TwoTone.AppSettingsAlt,
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
                    SettingsItem(
                        icon = Icons.TwoTone.Tune,
                        title = "按设备推荐配置",
                        subtitle = "问打字习惯、看设备性能和空间，设好全拼、双拼或五笔，推荐语音、九键、联想用哪些模型，删掉用不到的",
                        onClick = { onNavigateToRoute(SettingsRoutes.SetupGuide) },
                        showArrow = true
                    )
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

            // 首页五组按用户 10-21 的分法：配置选择 / 输入 / 外观 / 智能 / 系统。
            item {
                SettingsSection(title = "输入", content = {
                    SettingsItem(icon = Icons.TwoTone.Language, title = "语言与方案",
                        subtitle = "语言、每种语言的布局和输入方案、语言键切换方式", onClick = onNavigateToLanguages, showArrow = true)
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 56.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    SettingsItem(
                        icon = Icons.TwoTone.Keyboard,
                        title = "键盘布局",
                        subtitle = "26 键、九键与 14 键、自定义布局、按键符号、分体",
                        onClick = onNavigateToLayoutDisplay,
                        showArrow = true
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 56.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    SettingsItem(
                        icon = Icons.TwoTone.Swipe,
                        title = "按键与手势",
                        subtitle = "角标输入、长按语言键、滑动移动光标、长按空格",
                        onClick = { onNavigateToRoute("${SettingsRoutes.LayoutDisplay}?page=gestures") },
                        showArrow = true
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
                })
            }

            item {
                SettingsSection(title = "外观", content = {
                    SettingsItem(
                        icon = Icons.TwoTone.Palette,
                        title = "主题与外观",
                        subtitle = "配色、玻璃、视觉样式、按键字号与角标提示",
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
                        icon = Icons.TwoTone.TableChart,
                        title = "候选词",
                        subtitle = "字号、候选注释、每页候选数",
                        onClick = { onNavigateToRoute("${SettingsRoutes.LayoutDisplay}?page=candidates") },
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
                })
            }

            item {
                SettingsSection(title = "智能", content = {
                    SettingsItem(
                        icon = Icons.AutoMirrored.TwoTone.MenuBook,
                        title = "词库",
                        subtitle = "我的短语、导入的词库、删掉的候选、查词",
                        onClick = onNavigateToDictionary,
                        showArrow = true
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 56.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
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
                    // 首页这一行右边就是语音开关，点开关直接开关语音，点行进语音页（用户 10-20 要求恢复）。
                    var sttEnabled by remember(entryStatus.sttEnabled) { mutableStateOf(entryStatus.sttEnabled) }
                    SettingsToggleItem(
                        icon = Icons.TwoTone.GraphicEq,
                        title = "语音转文本",
                        subtitle = "本地模型或在线服务，下载并选择识别方式",
                        checked = sttEnabled,
                        showArrow = true,
                        onClick = onNavigateToSpeechToText,
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
                        icon = Icons.TwoTone.Draw,
                        title = "手写",
                        subtitle = "停顿识别、公式标记、手写模型",
                        onClick = { onNavigateToRoute(SettingsRoutes.Handwriting) },
                        showArrow = true
                    )
                })
            }

            item {
                SettingsSection(title = "系统", content = {
                    SettingsItem(
                        icon = Icons.TwoTone.Sync,
                        title = "剪贴板与同步",
                        subtitle = "验证码自动复制、与电脑等设备同步剪贴板",
                        onClick = onNavigateToClipboardSync,
                        showArrow = true
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 56.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    SettingsItem(
                        icon = Icons.TwoTone.Storefront,
                        title = "扩展",
                        subtitle = "输入方案、模型和插件：下载、已安装、导入",
                        onClick = onNavigateToMarket,
                        showArrow = true
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 56.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
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
                        icon = Icons.TwoTone.Speed,
                        title = "性能与省电",
                        subtitle = "高配、中配、低配一键切换，语音模型占用内存",
                        onClick = { onNavigateToRoute(SettingsRoutes.Performance) },
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
    val sttEnabled: Boolean = false,
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
        sttEnabled = SettingsPreferences.isSttEnabled(context),
    )
}

/** 下载提示里的大小：和模型管理里写的 MB 一样按 1024 × 1024 字节算，取整。 */
internal fun formatModelMegabytes(bytes: Long): String = "${(bytes + 524_288) / 1_048_576} MB"
