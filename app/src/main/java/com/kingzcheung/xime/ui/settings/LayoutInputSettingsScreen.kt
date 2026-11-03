package com.kingzcheung.xime.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.twotone.SwapHoriz
import androidx.compose.material.icons.twotone.OpenWith
import androidx.compose.material.icons.twotone.SpaceBar
import androidx.compose.material.icons.twotone.ContentCut
import androidx.compose.material.icons.twotone.Gesture
import androidx.compose.material.icons.twotone.Abc
import androidx.compose.material.icons.twotone.Spellcheck
import androidx.compose.material.icons.twotone.Hearing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kingzcheung.xime.settings.SettingsPreferences
import com.kingzcheung.xime.settings.SpellingPatches
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.kingzcheung.xime.ui.keyboard.normalizeLetterSwipeDistance
import com.kingzcheung.xime.ui.keyboard.normalizeCandidateDragPercent
import com.kingzcheung.xime.ui.keyboard.rememberKeyboardInputPreferences

internal enum class LayoutInputCategory(val route: String, val title: String, val description: String) {
    Qwerty("layout_input_qwerty", "26 键", "中英混输、滑行输入、字母错位、空格两侧标点、分词键"),
    Nine("layout_input_nine", "九键与 14 键", "九键滑行、拂动选字母、14 键双字母键、触发距离、方向圆盘与假名提示"),
}

/** 日语 26 键的长音 ー 放在哪：三张带图例的卡片，默认 L 键（见 JapaneseLongVowelKey）。 */
@Composable
internal fun JapaneseLongVowelKeySettings(
    settings: com.kingzcheung.xime.ui.keyboard.KeyboardInputPreferences,
    onChange: (com.kingzcheung.xime.ui.keyboard.KeyboardInputPreferences) -> Unit,
) {
    Column(Modifier.settingsSearchAnchor("长音键").padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("长音键", style = MaterialTheme.typography.titleSmall)
        Text("日语 26 键打 ー 的键。选单独键或 Shift 时 L 是字母 l，代替 Shift 时按住仍是 Shift。",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth().choiceCardRowHeight(108.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            com.kingzcheung.xime.ui.keyboard.JapaneseLongVowelKey.entries.forEach { mode ->
                IllustratedChoiceCard(mode.label, settings.japaneseLongVowelKey == mode,
                    onClick = { onChange(settings.copy(japaneseLongVowelKey = mode)) },
                    modifier = Modifier.weight(1f), tag = "long-vowel-key:${mode.name}") {
                    JapaneseLongVowelIllustration(mode)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LayoutInputSettingsContent(category: LayoutInputCategory, onBack: () -> Unit) {
    val context = LocalContext.current
    val saved = rememberKeyboardInputPreferences()
    var settings by remember(saved) { mutableStateOf(saved) }
    KeyboardSettingsScaffold(category.title, onBack, preview = true, example = when (category) {
        LayoutInputCategory.Qwerty -> com.kingzcheung.xime.keyboard.KeyboardPanelLaunch.Example.Letters
        LayoutInputCategory.Nine -> com.kingzcheung.xime.keyboard.KeyboardPanelLaunch.Example.Keypad
    }) { modifier ->
        Column(modifier.settingsVerticalScroll().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            when (category) {
                LayoutInputCategory.Qwerty -> Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // 邻键纠错、模糊音 11-01 搬到「语言与方案」（跟着方案走，九键和 26 键页不再各放一份）。
                SettingsSection(title = "混输", content = {
                    SettingsToggleItem(Icons.TwoTone.Abc, "中英混输",
                        "仅用于中文全拼 26 键；拼音里夹着英文词（nihaohello）时首选“你好hello”。本身像拼音的英文词（game、nice）认不出。",
                        checked = settings.mixedInput, onCheckedChange = {
                            settings = settings.copy(mixedInput = it); settings.save(context)
                        }, costItem = com.kingzcheung.xime.settings.PerformanceCosts.Item.MIXED_INPUT)
                })
                SettingsSection(title = "滑行", content = {
                    // 「键盘滑动」移光标和滑行冲突：照常显示，被锁时置灰并写明被谁锁（见 InputGestureLocks）。
                    SettingsToggleItem(Icons.TwoTone.Gesture, "滑行输入",
                        "英文 26 键手指划过字母出整词，候选栏可换成其他词；竖着滑一行仍输入角标符号。",
                        checked = settings.glideTyping, onCheckedChange = {
                            settings = settings.copy(glideTyping = it); settings.save(context)
                        },
                        lockMessage = com.kingzcheung.xime.ui.keyboard.InputGestureLocks.lockFor(settings,
                            com.kingzcheung.xime.ui.keyboard.InputGestureLock.Option.GLIDE_TYPING, true)
                            ?.takeIf { !settings.glideTyping }?.message,
                        costItem = com.kingzcheung.xime.settings.PerformanceCosts.Item.GLIDE_TYPING)
                    HorizontalDivider()
                    SettingsToggleItem(Icons.TwoTone.Gesture, "中文滑行输入",
                        "中文全拼、双拼 26 键划一个词的拼音，接着划或点按都接在后面，照常选字；竖着滑一行仍输入角标符号。",
                        checked = settings.chineseGlideTyping, onCheckedChange = {
                            settings = settings.copy(chineseGlideTyping = it); settings.save(context)
                        },
                        lockMessage = com.kingzcheung.xime.ui.keyboard.InputGestureLocks.lockFor(settings,
                            com.kingzcheung.xime.ui.keyboard.InputGestureLock.Option.GLIDE_TYPING, true)
                            ?.takeIf { !settings.chineseGlideTyping }?.message,
                        costItem = com.kingzcheung.xime.settings.PerformanceCosts.Item.CHINESE_GLIDE)
                    HorizontalDivider()
                    // 别的用户 11-02 提的（用惯 Gboard）：默认关；两种滑行都没开时没有作用，关着就置灰。
                    SettingsToggleItem(Icons.TwoTone.Gesture, "滑行时实时显示词",
                        "划的过程中在手指上方显示现在抬手会出的词，中文显示拼音。滑动中每 60 毫秒重新识别一次，多占一点 CPU、略耗电，不影响抬手后出词的速度。",
                        checked = settings.glidePreview, onCheckedChange = {
                            settings = settings.copy(glidePreview = it); settings.save(context)
                        },
                        lockMessage = "需先打开「滑行输入」或「中文滑行输入」"
                            .takeIf { !settings.glidePreview && !settings.glideTyping && !settings.chineseGlideTyping },
                        costItem = com.kingzcheung.xime.settings.PerformanceCosts.Item.GLIDE_PREVIEW)
                })
                SettingsSection(title = "按键排列与分词", content = {
                    QwertyStaggerSettings(settings) { settings = it; it.save(context) }
                    HorizontalDivider()
                    Column(Modifier.settingsSearchAnchor("空格两侧的标点").padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text("空格两侧的标点", style = MaterialTheme.typography.titleSmall)
                        Text("只开句号：原逗号键移到空格右侧，点按句号、滑动逗号。两个都开：滑动输入另一种宽度，" +
                            "全角时点按全角、半角时点按半角。", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        SpacePunctuationIllustration(settings.qwertyCommaKey, settings.qwertyPeriodKey)
                    }
                    SettingsToggleItem(Icons.TwoTone.SpaceBar, "空格左侧逗号键", "关闭后空格向左加宽。",
                        checked = settings.qwertyCommaKey, onCheckedChange = {
                            settings = settings.copy(qwertyCommaKey = it); settings.save(context)
                        })
                    SettingsToggleItem(Icons.TwoTone.SpaceBar, "空格右侧句号键", "开启后空格让出一个键宽。",
                        checked = settings.qwertyPeriodKey, onCheckedChange = {
                            settings = settings.copy(qwertyPeriodKey = it); settings.save(context)
                        })
                    HorizontalDivider()
                    // 逗号键分词（默认关）：下角标的方向跟「反转上下滑方向」走，说明照实际方向写。
                    SettingsToggleItem(Icons.TwoTone.ContentCut, "逗号键分词",
                        "中文全拼、双拼的 26 键和 14 键：逗号键左下角加 '（只开句号键时在句号键上），" +
                            "打拼音时${if (settings.reverseSymbolSwipe) "下滑" else "上滑"}插入分词（xi'an），F 键角标的 ' 也一样；" +
                            "没在打拼音时输入 '。九键照旧用 1 键分词。",
                        checked = settings.syllableSplitKey, onCheckedChange = {
                            settings = settings.copy(syllableSplitKey = it); settings.save(context)
                        })
                    SyllableSplitKeyLegend(settings.syllableSplitKey, settings.qwertyCommaKey, settings.qwertyPeriodKey,
                        settings.reverseSymbolSwipe)
                    HorizontalDivider()
                    SettingsToggleItem(Icons.TwoTone.ContentCut, "Shift 键分词",
                        "中文全拼、双拼的 26 键和 14 键：打拼音时 Shift 键写「分词」，点一下插入分词（xi'an）；" +
                            "没在打拼音时照旧切换大小写，按住 Shift 打大写也不变。",
                        checked = settings.shiftSyllableSplit, onCheckedChange = {
                            settings = settings.copy(shiftSyllableSplit = it); settings.save(context)
                        },
                        // 和微软双拼的「ing 放在 Shift 键上」互锁：打字时都要占 Shift 键。
                        lockMessage = com.kingzcheung.xime.ui.keyboard.InputGestureLocks.lockFor(settings, com.kingzcheung.xime.ui.keyboard.InputGestureLock.Option.SHIFT_SYLLABLE_SPLIT, true)
                            ?.takeIf { !settings.shiftSyllableSplit }?.message)
                    ShiftSyllableSplitLegend(settings.shiftSyllableSplit)
                    HorizontalDivider()
                    JapaneseLongVowelKeySettings(settings) { settings = it; it.save(context) }
                })
                }
                LayoutInputCategory.Nine -> Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    SettingsSection(title = "九键手势", content = {
                        SettingsToggleItem(Icons.TwoTone.Gesture, "九键滑行输入",
                            "连续划过 2～9 键的中心，松手后照常选词。例如划 5→2→4 可以选“来”。",
                            checked = settings.t9GlideTyping, onCheckedChange = {
                                settings = settings.copy(t9GlideTyping = it); settings.save(context)
                            },
                            lockMessage = com.kingzcheung.xime.ui.keyboard.InputGestureLocks.lockFor(settings,
                                com.kingzcheung.xime.ui.keyboard.InputGestureLock.Option.GLIDE_TYPING, true)
                                ?.takeIf { !settings.t9GlideTyping }?.message,
                            costItem = com.kingzcheung.xime.settings.PerformanceCosts.Item.T9_GLIDE)
                        HorizontalDivider()
                        SettingsToggleItem(Icons.TwoTone.OpenWith, "拂动选字母",
                            "左、上、右、下依次输入键上的第 1～4 个字母（7、9 用到下），点按仍是九键输入；" +
                                "开启后长按输入数字、不再弹出字母菜单。",
                            checked = settings.t9FlickLetters, onCheckedChange = {
                                settings = settings.copy(t9FlickLetters = it); settings.save(context)
                            },
                            // 角标“长按”时数字占了长按，拂动必须开着：锁成开，不能关。
                            lockMessage = com.kingzcheung.xime.ui.keyboard.InputGestureLocks.lockFor(settings,
                                com.kingzcheung.xime.ui.keyboard.InputGestureLock.Option.T9_FLICK_LETTERS, false)
                                ?.takeIf { settings.t9FlickLetters }?.message)
                        HorizontalDivider()
                        Column(Modifier.settingsSearchAnchor("拖到候选选词的距离").padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Text("拖到候选选词的距离：键盘的 ${settings.candidateDragPercent.toInt()}%", style = MaterialTheme.typography.titleSmall)
                            Slider(value = settings.candidateDragPercent,
                                modifier = Modifier.testTag("t9-candidate-drag-percent"),
                                valueRange = 20f..80f, steps = 5,
                                onValueChange = { settings = settings.copy(candidateDragPercent = normalizeCandidateDragPercent(it)) },
                                onValueChangeFinished = { settings.save(context) })
                            Text("默认 50%。按住「下一项」拖过这么远才算拖到候选选词；没拖这么远仍是「下一项」自己的左滑、右滑。",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    })
                    SettingsSection(title = "双字母键", content = {
                        SettingsToggleItem(Icons.TwoTone.SwapHoriz, "左右滑动选字母",
                            "例如 qw：左滑输入 q，右滑输入 w，松手确认；点按仍使用合并键。" +
                                "适用于拼音和小鹤双拼 14 键，以及自己合并成一格的两个字母（如自建 18 键的 as）。",
                            checked = settings.fourteenLetterSwipe, onCheckedChange = {
                                settings = settings.copy(fourteenLetterSwipe = it); settings.save(context)
                            })
                        // 原来各自一组、组里只有一行（11-02 整理合并）；距离九键拂动、假名拂动也共用，说明里写着。
                        HorizontalDivider()
                        Column(Modifier.settingsSearchAnchor("滑动触发距离").padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Text("滑动触发距离：${settings.fourteenLetterSwipeDp.toInt()} dp", style = MaterialTheme.typography.titleSmall)
                            Slider(value = settings.fourteenLetterSwipeDp,
                                modifier = Modifier.testTag("fourteen-letter-distance"),
                                valueRange = 12f..72f, steps = 29,
                                onValueChange = { settings = settings.copy(fourteenLetterSwipeDp = normalizeLetterSwipeDistance(it)) },
                                onValueChangeFinished = { settings.save(context) })
                            Text("默认 24 dp，越大越不易误触。九键拂动选字母、14 键和自建合并键左右滑选字母、日语九键假名拂动共用。",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    })
                    InputExperienceSettings(setOf(InputExperiencePart.FLICK_DISC, InputExperiencePart.KANA), "拂动与假名提示")
                }
            }
            SettingsJumpText("分体键盘、按键符号在上一页「键盘布局」；角标、光标与空格在「按键与手势」；字号在「主题与外观 › 按键字号」；" +
                "模糊音、邻键纠错在「语言与方案 › 中文输入」。",
                jumps = listOf("键盘布局", "按键与手势", "主题与外观 › 按键字号", "语言与方案 › 中文输入"),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}


/**
 * 邻键纠错（11-01 从 26 键页搬到「语言与方案」）：只列当前方案用得上的那一行（全拼或双拼），
 * 另一行改过默认值时也列出来，方便关回去。存的值不变。
 */
@Composable
internal fun NeighborCorrectionSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = rememberKeyboardInputPreferences()
    val usingDoublePinyin = remember { runCatching { SpellingPatches.usingDoublePinyin(context) }.getOrDefault(false) }
    val defaults = com.kingzcheung.xime.ui.keyboard.KeyboardInputPreferences()
    val showFull = !usingDoublePinyin || settings.neighborCorrection != defaults.neighborCorrection
    val showDouble = usingDoublePinyin || settings.doublePinyinCorrection != defaults.doublePinyinCorrection
    SettingsSection(title = "拼音纠错", content = {
        if (showFull) SettingsToggleItem(Icons.TwoTone.Spellcheck, "邻键纠错",
            "全拼 26 键按偏到旁边的键时，也给出本来想打的词，原来的候选还在",
            checked = settings.neighborCorrection, onCheckedChange = {
                settings.copy(neighborCorrection = it).save(context)
            }, costItem = com.kingzcheung.xime.settings.PerformanceCosts.Item.NEIGHBOR_CORRECTION)
        if (showFull && showDouble) HorizontalDivider()
        if (showDouble) SettingsToggleItem(Icons.TwoTone.Spellcheck, "双拼邻键纠错",
            "双拼 26 键按偏到旁边的键时，也给出本来想打的词（关着时只纠拼不成字的两个键）；改完要几秒重新部署",
            checked = settings.doublePinyinCorrection, onCheckedChange = {
                settings.copy(doublePinyinCorrection = it).save(context)
                scope.launch(Dispatchers.IO) { runCatching { SpellingPatches.apply(context) } }
            }, costItem = com.kingzcheung.xime.settings.PerformanceCosts.Item.DOUBLE_PINYIN_CORRECTION)
    })
}

/** 模糊音：每组一行开关，默认全关；改了在后台写方案补丁，输入法重新部署后生效（见 [SpellingPatches]）。放在「语言与方案」。 */
@Composable
internal fun FuzzyPinyinSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var enabled by remember { mutableStateOf(SpellingPatches.fuzzy(context)) }
    var doublePinyin by remember { mutableStateOf(SpellingPatches.fuzzyDoublePinyin(context)) }
    var t9Digits by remember { mutableStateOf(SpellingPatches.fuzzyT9Digits(context)) }
    // 现在打的是双拼而开关关着：下面各组对双拼不起作用（10-31 反馈小鹤 bkgr 出不来宾馆）。
    val usingDoublePinyin = remember { runCatching { SpellingPatches.usingDoublePinyin(context) }.getOrDefault(false) }
    // 「模糊音也用于双拼」默认开；11-01 起只给关掉过它的人显示，好让他们开回来（用户 11-01 批准）。
    val showDoublePinyinSwitch = remember { !doublePinyin }
    Column(Modifier.settingsSearchAnchor("模糊音")) {
        SettingsSection(title = "模糊音", content = {
            Text("14 键双拼不适用。九键按数字后点左侧读音，带 ≈ 的是相近读音。" +
                "改完要几秒重新部署。",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            // 用户 11-02：九键按数字也模糊很耗性能，做成开关、默认关，开关和每组下面写实测代价，卡了自己关。
            SettingsToggleItem(Icons.TwoTone.Hearing, "九键按数字也模糊",
                "九键直接按数字也出下面所开各组的相近读音（如 74 也出“是”），不用再点左侧读音。" +
                    "候选变多、每键变慢，首选也更容易不是想要的字；开的组越多越明显，打字卡了就关掉。改完要几秒重新部署。",
                checked = t9Digits, onCheckedChange = { on ->
                    SpellingPatches.setFuzzyT9Digits(context, on)
                    t9Digits = on
                    scope.launch(Dispatchers.IO) { runCatching { SpellingPatches.apply(context) } }
                },
                extraCost = {
                    PerformanceCostTotalNote(enabled.mapTo(linkedSetOf()) { com.kingzcheung.xime.settings.PerformanceCosts.Item.t9Fuzzy(it) },
                        label = "性能开销（已开的 ${enabled.size} 组合计）", tag = "t9-fuzzy-digits")
                })
            if (showDoublePinyinSwitch) {
                HorizontalDivider()
                SettingsToggleItem(Icons.TwoTone.Hearing, "模糊音也用于双拼",
                    "关着时下面各组只对全拼起作用；打开后双拼也按读音模糊（in 和 ing 互通）",
                    checked = doublePinyin, onCheckedChange = { on ->
                        SpellingPatches.setFuzzyDoublePinyin(context, on)
                        doublePinyin = on
                        scope.launch(Dispatchers.IO) { runCatching { SpellingPatches.apply(context) } }
                    })
                if (usingDoublePinyin && !doublePinyin) Text("你现在用的是双拼：要打开上面这个开关，下面打开的各组才对双拼起作用。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp).testTag("fuzzy-double-pinyin-hint"))
            }
            SpellingPatches.Fuzzy.entries.forEach { item ->
                HorizontalDivider()
                SettingsToggleItem(Icons.TwoTone.Hearing, item.title, item.example,
                    checked = item in enabled, onCheckedChange = { on ->
                        SpellingPatches.setFuzzy(context, item, on)
                        enabled = SpellingPatches.fuzzy(context)
                        scope.launch(Dispatchers.IO) { runCatching { SpellingPatches.apply(context) } }
                    }, costItem = com.kingzcheung.xime.settings.PerformanceCosts.Item.fuzzy(item),
                    // 「九键按数字也模糊」开着时，再写这一组在九键上的代价（另在九键上实测）。
                    extraCost = if (!t9Digits) null else {
                        { PerformanceCostNote(com.kingzcheung.xime.settings.PerformanceCosts.Item.t9Fuzzy(item), label = "九键性能开销") }
                    })
            }
        })
    }
}

@Composable
private fun QwertyStaggerSettings(
    settings: com.kingzcheung.xime.ui.keyboard.KeyboardInputPreferences,
    onChange: (com.kingzcheung.xime.ui.keyboard.KeyboardInputPreferences) -> Unit,
) {
    val modes = com.kingzcheung.xime.ui.keyboard.QwertyStaggerMode.entries
    Column(Modifier.settingsSearchAnchor("三行字母错位").padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("三行字母错位", style = MaterialTheme.typography.titleSmall)
        Text("实体键盘：A 行右移 1/4 键、Z 行右移 3/4 键；手机常见：Z 与 S 上下对齐；按尺寸自动：手机用手机排布，" +
            "平板逐渐过渡到实体排布。", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth().choiceCardRowHeight(108.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
