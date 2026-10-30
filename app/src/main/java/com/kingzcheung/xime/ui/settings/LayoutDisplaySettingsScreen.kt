package com.kingzcheung.xime.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.twotone.Straighten
import androidx.compose.material.icons.twotone.Padding
import androidx.compose.material.icons.twotone.Tune
import androidx.compose.material.icons.twotone.Keyboard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kingzcheung.xime.settings.SettingsPreferences

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LayoutDisplaySettingsContent(
    onBack: () -> Unit,
    onOpenLayout: (String) -> Unit = {},
    /** 首页或设置搜索直接打开某个子页；返回回到进来的地方。 */
    initialPage: String? = null,
) {
    // One index page; each group opens as its own sub-page. Settings themselves are unchanged.
    var openPage by androidx.compose.runtime.saveable.rememberSaveable {
        mutableStateOf(initialPage?.takeIf { p -> LayoutDisplayPages.any { it.first == p } })
    }
    // 首页直接进入的子页（候选词、按键与手势）返回首页，不经过「键盘布局」。
    val direct = initialPage != null
    SettingsSubPages(openPage, onBack = { if (direct) onBack() else openPage = null }) { page ->
        LayoutDisplayPage(page, onBack, onOpenLayout, openPage = { openPage = it },
            closePage = { if (direct) onBack() else openPage = null })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LayoutDisplayPage(
    page: String?,
    onBack: () -> Unit,
    onOpenLayout: (String) -> Unit,
    openPage: (String) -> Unit,
    closePage: () -> Unit,
) {
    val context = LocalContext.current
    val pageTitle = LayoutDisplayPages.firstOrNull { it.first == page }?.second

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text(pageTitle ?: "键盘布局") },
                navigationIcon = {
                    IconButton(onClick = { if (page != null) closePage() else onBack() }) {
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
        val listState = rememberSettingsSearchListState()
        LazyColumn(
            state = listState,
            modifier = Modifier.settingsGutterScroll(listState)
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (page == null) item {
                SettingsSection(title = "各布局", content = {
                    LayoutInputCategory.entries.forEachIndexed { index, category ->
                        if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                        SettingsItem(Icons.TwoTone.Keyboard, category.title, category.description,
                            onClick = { onOpenLayout(category.route) }, showArrow = true)
                    }
                })
            }
            if (page == null) item {
                SettingsSection(title = "布局与符号", content = {
                    SettingsItem(com.kingzcheung.xime.ui.keyboard.AddLayoutIcon, "自定义布局", "新建、复制和编辑按键排列",
                        onClick = { context.startActivity(android.content.Intent(context, com.kingzcheung.xime.CustomLayoutActivity::class.java)) },
                        showArrow = true)
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    SettingsItem(Icons.TwoTone.Keyboard, "按键符号", "26 键上下角标、九键与笔画侧栏、手写和常用符号栏，一处按位置编辑",
                        onClick = { context.startActivity(android.content.Intent(context, com.kingzcheung.xime.CustomLayoutActivity::class.java)
                            .putExtra("edit_symbols", true)) },
                        showArrow = true)
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    var pairedSymbols by remember { mutableStateOf(SettingsPreferences.isPairedSymbolsEnabled(context)) }
                    SettingsToggleItem(Icons.TwoTone.Keyboard, "输入成对符号",
                        "点 “ （ 《 【 等开头的一半时连结尾一起输入，光标停在中间；接着点结尾会跳过已有的那个，" +
                            "退格删掉空的一对。所有屏幕键盘、符号面板和侧栏都一样，实体键盘不变。",
                        checked = pairedSymbols, onCheckedChange = {
                            pairedSymbols = it; SettingsPreferences.setPairedSymbolsEnabled(context, it)
                        })
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    val split = com.kingzcheung.xime.ui.keyboard.rememberKeyboardInputPreferences().splitKeyboardEnabled
                    SettingsToggleItem(Icons.TwoTone.Keyboard, "分体键盘",
                        "适用于 26 键和 14 键；14 键跨中线的双字母键分体时拆成两个单字母键。间距与尺寸在键盘调整面板中设置。",
                        checked = split, onCheckedChange = { SettingsPreferences.setSplitKeyboardEnabled(context, it) })
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    val filled = com.kingzcheung.xime.ui.keyboard.rememberKeyboardInputPreferences().fillBlankAreas
                    SettingsToggleItem(Icons.TwoTone.Keyboard, "填满留空处",
                        "分体中缝和固定键盘两侧的空白用键盘背景铺满，遮住后面的应用；关闭时镂空，透出后面的内容。",
                        checked = filled, onCheckedChange = { SettingsPreferences.setKeyboardBlankFilled(context, it) })
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    SettingsItem(Icons.TwoTone.Tune, keyboardPanelTitle(com.kingzcheung.xime.keyboard.KeyboardPanelLaunch.Panel.RESIZE),
                        "高度、宽度、悬浮与分体、透明度，打开键盘菜单里同一个调节面板",
                        onClick = { onOpenLayout(SettingsRoutes.KeyboardResize) }, showArrow = true)
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    SettingsItem(Icons.TwoTone.Padding, keyboardPanelTitle(com.kingzcheung.xime.keyboard.KeyboardPanelLaunch.Panel.ARRANGE),
                        "哪些入口放工具栏、哪些放菜单，以及先后顺序",
                        onClick = { onOpenLayout(SettingsRoutes.ToolbarArrange) }, showArrow = true)
                })
            }
            if (page == null) item {
                SettingsSection(title = "数字键盘", content = {
                    var autoNumberKeyboard by remember {
                        mutableStateOf(SettingsPreferences.isAutoNumberKeyboardEnabled(context))
                    }
                    Row(
                        modifier = Modifier.settingsSearchAnchor("数字框自动数字键盘")
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "数字框自动数字键盘",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "进入号码、验证码等数字输入框时自动弹出数字键盘",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = autoNumberKeyboard,
                            onCheckedChange = { newValue ->
                                autoNumberKeyboard = newValue
                                SettingsPreferences.setAutoNumberKeyboardEnabled(context, newValue)
                            }
                        )
                    }
                })
            }
            if (page == "candidates") item {
                SettingsSection(title = "候选词", content = {
                    val candidateTextSizePref = SettingsPreferences.getCandidateTextSize(context)
                    var candidateTextSize by remember(candidateTextSizePref) {
                        mutableStateOf(candidateTextSizePref.toFloat())
                    }

                    Column(
                        modifier = Modifier.settingsSearchAnchor("候选字大小").padding(16.dp)
                    ) {
                        Text(
                            text = "候选字大小",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        CandidateTextSizeCard(
                            candidateTextSize = candidateTextSize,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Slider(
                            value = candidateTextSize,
                            onValueChange = { candidateTextSize = it },
                            onValueChangeFinished = {
                                SettingsPreferences.setCandidateTextSize(context, candidateTextSize.toInt())
                            },
                            valueRange = 12f..22f,
                            steps = 9
                        )
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(start = 16.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    var showCandidateCancel by remember {
                        mutableStateOf(SettingsPreferences.shouldShowCandidateCancelButton(context))
                    }
                    Row(
                        modifier = Modifier.settingsSearchAnchor("候选栏取消按钮")
                            .fillMaxWidth()
                            .testTag("candidate-cancel-setting")
                            .toggleable(
                                value = showCandidateCancel,
                                role = Role.Switch,
                                onValueChange = {
                                    showCandidateCancel = it
                                    SettingsPreferences.setShowCandidateCancelButton(context, it)
                                },
                            )
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("候选栏取消按钮", style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium)
                            Text("默认关闭，候选词从左侧开始；开启后可点左箭头取消当前输入",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.width(12.dp))
                        Switch(checked = showCandidateCancel, onCheckedChange = null)
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 16.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    )

                    var commentMode by remember { mutableStateOf(SettingsPreferences.candidateCommentMode(context)) }
                    Column(Modifier.settingsSearchAnchor("候选注释")) {
                        Text("候选注释", style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp))
                        Text("26／14／九键共用。显示：始终显示；自动：宽时显示、窄时隐藏；隐藏：始终不显示。",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp))
                        Row(Modifier.fillMaxWidth().height(136.dp).padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            com.kingzcheung.xime.settings.CandidateCommentMode.entries.forEach { mode ->
                                IllustratedChoiceCard(when (mode) {
                                    com.kingzcheung.xime.settings.CandidateCommentMode.AUTO -> "自动"
                                    com.kingzcheung.xime.settings.CandidateCommentMode.SHOW -> "显示"
                                    com.kingzcheung.xime.settings.CandidateCommentMode.HIDE -> "隐藏"
                                }, commentMode == mode,
                                    onClick = {
                                        commentMode = mode
                                        SettingsPreferences.setCandidateCommentMode(context, mode)
                                        com.kingzcheung.xime.gloss.CandidateGlossary.prepare(context)
                                    },
                                    modifier = Modifier.weight(1f), tag = "candidate-comments:${mode.name}") {
                                    CandidateCommentIllustration(mode)
                                }
                            }
                        }
                    }

                    // 注释隐藏时内容选项无意义：整段不显示，也不会触发释义词典下载。
                    if (commentMode != com.kingzcheung.xime.settings.CandidateCommentMode.HIDE) {
                        var commentContent by remember { mutableStateOf(SettingsPreferences.candidateCommentContent(context)) }
                        Column(Modifier.settingsSearchAnchor("候选注释内容")) {
                            Text("候选注释内容", style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp))
                            Text("拼音：显示拼音或编码。释义：中文候选显示英文，中文键盘里混出的英文候选显示中文，九键长按「下一项」或按住 Shift 再选候选就上屏英文单词；选释义时会自动下载释义词典（也在扩展商店的模型“其他”里），查不到的候选仍显示拼音。",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp))
                            var glossDownloadRequest by remember { mutableStateOf(0) }
                            if (commentContent == com.kingzcheung.xime.settings.CandidateCommentContent.GLOSS) {
                                GlossDictionaryStatus(glossDownloadRequest)
                            }
                            Row(Modifier.fillMaxWidth().height(136.dp).padding(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                com.kingzcheung.xime.settings.CandidateCommentContent.entries.forEach { content ->
                                    IllustratedChoiceCard(when (content) {
                                        com.kingzcheung.xime.settings.CandidateCommentContent.PINYIN -> "拼音"
                                        com.kingzcheung.xime.settings.CandidateCommentContent.GLOSS -> "释义"
                                    }, commentContent == content,
                                        onClick = {
                                            // 刚选释义且词典没下载：直接开始下载，不让用户去商店找。
                                            if (content == com.kingzcheung.xime.settings.CandidateCommentContent.GLOSS &&
                                                commentContent != content) glossDownloadRequest++
                                            commentContent = content
                                            SettingsPreferences.setCandidateCommentContent(context, content)
                                            com.kingzcheung.xime.gloss.CandidateGlossary.prepare(context)
                                        },
                                        modifier = Modifier.weight(1f), tag = "candidate-comment-content:${content.name}") {
                                        CandidateCommentContentIllustration(content)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    HorizontalDivider(
                        modifier = Modifier.padding(start = 16.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    var inputTextLocation by remember {
                        mutableStateOf(SettingsPreferences.getInputTextLocation(context))
                    }

                    Column(Modifier.settingsSearchAnchor("编码显示")) {
                        Text(
                            text = "编码显示",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
                        )
                        Text(
                            text = "选择输入编码的显示位置",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 16.dp, bottom = 12.dp)
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp)
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CodeDisplayCard(
                                title = "显示在输入框",
                                isSelected = inputTextLocation == SettingsPreferences.INPUT_TEXT_INPUT_BOX,
                                showCodeInInputBox = true,
                                onClick = {
                                    inputTextLocation = SettingsPreferences.INPUT_TEXT_INPUT_BOX
                                    SettingsPreferences.setInputTextLocation(context, SettingsPreferences.INPUT_TEXT_INPUT_BOX)
                                },
                                modifier = Modifier.weight(1f)
                            )
                            CodeDisplayCard(
                                title = "显示在候选栏",
                                isSelected = inputTextLocation == SettingsPreferences.INPUT_TEXT_CANDIDATE_BAR,
                                showCodeInInputBox = false,
                                onClick = {
                                    inputTextLocation = SettingsPreferences.INPUT_TEXT_CANDIDATE_BAR
                                    SettingsPreferences.setInputTextLocation(context, SettingsPreferences.INPUT_TEXT_CANDIDATE_BAR)
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    HorizontalDivider(
                        modifier = Modifier.padding(start = 16.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    val pageSizePref = SettingsPreferences.getPageSize(context)
                    val effectiveValue = if (pageSizePref == 0) 20f else pageSizePref.toFloat()
                    var pageSizeSlider by remember(effectiveValue) {
                        mutableStateOf(effectiveValue)
                    }

                    Column(
                        modifier = Modifier.settingsSearchAnchor("每页候选词数").padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "每页候选词数",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${pageSizeSlider.toInt()} 个",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Slider(
                            value = pageSizeSlider,
                            onValueChange = { pageSizeSlider = it },
                            onValueChangeFinished = {
                                val intValue = pageSizeSlider.toInt()
                                SettingsPreferences.setPageSize(context, intValue)
                            },
                            valueRange = 20f..50f,
                            steps = 29
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "下次开始输入时生效，无需重新部署",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                })
            }

            if (page == "gestures") item { GesturePresetSettings() }
            if (page == "gestures") item {
                InputExperienceSettings(setOf(InputExperiencePart.SYMBOLS, InputExperiencePart.LANGUAGE_HOLD), "角标与长按")
            }
            if (page == "gestures") item { InputExperienceSettings(setOf(InputExperiencePart.CURSOR), "光标与空格") }
        }
    }
}

/** 「键盘布局」之外直接从首页进入的两页：route, title, one-line description. */
internal val LayoutDisplayPages = listOf(
    Triple("candidates", "候选词", "字号、候选注释（拼音或中英释义）、取消按钮、输入框或候选栏显示"),
    Triple("gestures", "按键与手势", "角标输入方式、上下滑方向、长按语言键、滑动移动光标、长按空格"),
)

/** 按键显示：字号、角标提示、点按气泡、底部圆角；放在「主题与外观」。 */
@Composable
internal fun KeyboardAppearanceSettings() {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        InputExperienceSettings(setOf(InputExperiencePart.KEY_TEXT), "按键文字")
                SettingsSection(title = "角标与气泡", content = {
                    var swipeUpEnabled by remember {
                        mutableStateOf(SettingsPreferences.isSwipeUpHintsEnabled(context))
                    }
                    Row(
                        modifier = Modifier.settingsSearchAnchor("上方角标提示")
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "上方角标提示",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "显示按键上方的符号提示，输入方式和滑动方向在「按键与手势」设置",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = swipeUpEnabled,
                            onCheckedChange = { newValue ->
                                swipeUpEnabled = newValue
                                SettingsPreferences.setSwipeUpHintsEnabled(context, newValue)
                            }
                        )
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 16.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    var swipeDownEnabled by remember {
                        mutableStateOf(SettingsPreferences.isSwipeDownHintsEnabled(context))
                    }
                    Row(
                        modifier = Modifier.settingsSearchAnchor("下方角标提示")
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "下方角标提示",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "显示按键下方的提示内容，输入方式和滑动方向在「按键与手势」设置",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = swipeDownEnabled,
                            onCheckedChange = { newValue ->
                                swipeDownEnabled = newValue
                                SettingsPreferences.setSwipeDownHintsEnabled(context, newValue)
                            }
                        )
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 16.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    var showPressBubble by remember {
                        mutableStateOf(SettingsPreferences.shouldShowPressBubble(context))
                    }
                    Row(
                        modifier = Modifier.settingsSearchAnchor("点按弹出气泡")
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "点按弹出气泡",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "默认关闭以减少动画开销；开启后点按会显示字符气泡",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = showPressBubble,
                            onCheckedChange = { newValue ->
                                showPressBubble = newValue
                                SettingsPreferences.setShowPressBubble(context, newValue)
                            }
                        )
                    }
                })
                SettingsSection(title = "键盘底部样式", content = {
                    var rounded by remember { mutableStateOf(SettingsPreferences.roundedKeyboardBottom(context)) }
                    Row(
                        Modifier.settingsSearchAnchor("底部圆角与透明导航栏").fillMaxWidth().testTag("rounded-keyboard-bottom-setting")
                            .toggleable(value = rounded, role = Role.Switch, onValueChange = {
                                rounded = it
                                SettingsPreferences.setRoundedKeyboardBottom(context, it)
                            }).padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("底部圆角与透明导航栏", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                if (rounded) "底部保留圆角，导航栏透明；图标跟随系统明暗，系统可能添加对比度底色"
                                else "底部直角，导航栏跟随键盘背景；图标深浅跟随按键文字",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Switch(checked = rounded, onCheckedChange = null)
                    }
                })
    }
}

/** 手写：停顿识别、公式标记；放在「语音与手写」。 */
@Composable
internal fun HandwritingSettings() {
    InputExperienceSettings(setOf(InputExperiencePart.HANDWRITING), "手写")
}

/** 「按键与手势」最上面的整套预设：滑动 / 长按 / 英文滑行；单独改过下面某项就显示为自定义（与性能页档位同一写法）。 */
@Composable
private fun GesturePresetSettings() {
    val context = LocalContext.current
    val settings = com.kingzcheung.xime.ui.keyboard.rememberKeyboardInputPreferences()
    val current = com.kingzcheung.xime.ui.keyboard.InputGesturePreset.of(settings)
    SettingsSection(title = "手势预设", content = {
        com.kingzcheung.xime.ui.keyboard.InputGesturePreset.presets.forEachIndexed { index, preset ->
            if (index > 0) RowDivider()
            TierRow(preset.label, preset.summary(settings.reverseSymbolSwipe), current == preset) { preset.applyTo(settings).save(context) }
        }
        if (current == com.kingzcheung.xime.ui.keyboard.InputGesturePreset.CUSTOM) {
            RowDivider()
            TierRow(current.label, current.summary(settings.reverseSymbolSwipe), true, enabled = false) {}
        }
    })
}
