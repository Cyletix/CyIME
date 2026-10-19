package com.kingzcheung.xime.ui.keyboard

import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.KeyboardReturn
import androidx.compose.material.icons.filled.SpaceBar
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.CloseFullscreen
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import com.kingzcheung.xime.handwriting.SPenSounds
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kingzcheung.xime.handwriting.FormulaRecognizer
import com.kingzcheung.xime.handwriting.HandwritingCandidate
import com.kingzcheung.xime.handwriting.HandwritingEngine
import com.kingzcheung.xime.handwriting.HandwritingTemplates
import com.kingzcheung.xime.handwriting.HandwritingLanguages
import com.kingzcheung.xime.settings.InputLanguage
import com.kingzcheung.xime.settings.KeysConfigHelper
import com.kingzcheung.xime.handwriting.HandwritingStrokeFx
import com.kingzcheung.xime.handwriting.OverlappedHandwritingRecognizer
import com.kingzcheung.xime.handwriting.renderStrokes
import com.kingzcheung.xime.handwriting.rememberHandwritingInk
import com.kingzcheung.xime.handwriting.rememberSPenInk
import com.kingzcheung.xime.model.ModelDownloadState
import com.kingzcheung.xime.model.ModelManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Composable
internal fun HandwritingUnavailable(language: InputLanguage, modifier: Modifier, onExit: () -> Unit,
    exitLabel: String = "返回键盘", message: String = HandwritingLanguages.unavailableMessage(language)) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).testTag("handwriting-unavailable"),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(message, color = MaterialTheme.colorScheme.onSurface)
        TextButton(onClick = onExit) { Text(exitLabel) }
    }
}

@Composable
fun HandwritingKeyboardLayout(
    onKeyPress: (String) -> Unit = {},
    onNewCharacter: (() -> Unit)? = null,
    onRecognition: ((List<OverlappedHandwritingRecognizer.Segment>) -> Unit)? = null,
    onButtonFeedback: ((String) -> Unit)? = null,
    keyTextColor: Color = Color(0xFF333333),
    keyBackgroundColor: Color = Color(0xFFE0E0E0),
    specialKeyBackgroundColor: Color = Color(0xFFD0D0D0),
    bottomPaddingDp: Int = 18,
    modifier: Modifier = Modifier,
    clearSignal: Int = 0,
    sessionKey: Long = 0,
    specialKeyTextColor: Color = Color.White,
    expanded: Boolean = false,
    expandedControlsWidthDp: Int = 0,
    expandedControlsOffsetX: Int = 0,
    panelBackgroundColor: Color = Color.Transparent,
    expandedCandidateBar: @Composable () -> Unit = {},
    language: InputLanguage = InputLanguage.CHINESE,
    onUnsupportedExit: () -> Unit = {},
    unsupportedExitLabel: String = "返回键盘",
    floating: Boolean = false,
) {
    val sideSymbols = LocalKeyboardInputPreferences.current.symbolsFor(SideSymbolTarget.HANDWRITING)
    val rowSymbols = LocalKeyboardInputPreferences.current.symbolsFor(SideSymbolTarget.HANDWRITING_ROW)
    // 假名、英文字母：有公式模型包时走“边用边学”的模板比对（HandwritingTemplates），日语手写也因此可用
    val templateScript = HandwritingTemplates.Script.forLanguage(language)
        ?.takeIf { FormulaRecognizer.isAvailable(LocalContext.current) }
    val recognitionLanguage = HandwritingLanguages.recognitionLanguageForKeyboard(language)
        ?: if (templateScript != null) language else null
    if (recognitionLanguage == null) {
        // 日语手写靠公式模型算特征来比对假名：缺的是这个模型时，直接说去哪下载。
        val missingModel = HandwritingTemplates.Script.forLanguage(language) != null
        HandwritingUnavailable(language, modifier, onUnsupportedExit, unsupportedExitLabel,
            message = if (missingModel) "${language.displayName}手写需要先在 设置 → 模型管理 下载「公式手写模型」"
                else HandwritingLanguages.unavailableMessage(language))
        return
    }
    val configVersion by KeysConfigHelper.configVersion.collectAsState()
    val keyConfig = remember(configVersion) { KeysConfigHelper.getKeyboardKeyConfig() }
    val spacing = keyConfig.spacingFor("qwerty")
    // Measure gaps against the same four-row QWERTY reference. The handwriting
    // controls below still divide the actual body into five equal rows.
    KeyboardKeySpacingScope(modifier, verticalInset = (8 + bottomPaddingDp).dp,
        allowShrink = floating, applyGutter = !expanded, growthSpacing = spacing) { bodyModifier ->
    CompositionLocalProvider(
        LocalKeyCornerRadius provides keyConfig.cornerRadius.dp,
        LocalKeyVisualPadding provides PaddingValues(
            horizontal = spacing.first?.dp ?: 2.dp, vertical = spacing.second?.dp ?: 2.dp),
    ) {
    val context = LocalContext.current
    var normalControlHeight by remember { mutableFloatStateOf(52f) }
    var normalControlScale by remember { mutableStateOf<Float?>(null) }
    var normalControlMetrics by remember { mutableStateOf<KeyVisualMetrics?>(null) }
    var normalControlGutter by remember { mutableFloatStateOf(8f) }
    val currentContentScale = LocalKeyboardKeyContentScale.current
    val currentVisualMetrics = LocalKeyboardKeyVisualMetrics.current
    val inkColor = rememberHandwritingInk(panelBackgroundColor)
    val penInkColor = rememberSPenInk(panelBackgroundColor)
    val scope = rememberCoroutineScope()
    val keyAction by rememberUpdatedState(onKeyPress)
    val newCharacter by rememberUpdatedState(onNewCharacter)
    val result by rememberUpdatedState(onRecognition)
    val feedback by rememberUpdatedState(onButtonFeedback)
    // The rows sit on the keyboard's own background (gradient, image or glass); a solid panel colour
    // here showed as a dark band around the bottom row on gradient themes.
    val functionRowBackground = Color.Transparent
    val pauseMs by rememberUpdatedState((LocalKeyboardInputPreferences.current.handwritingPauseSeconds * 1000).toLong())
    val recognitionMutex = remember { Mutex() }
    val stylusPresence = remember { StylusPresence() }
    val eraserRadiusPx = with(LocalDensity.current) { com.kingzcheung.xime.handwriting.PenEraser.Radius.toPx() }
    // 三星设备上用系统自带的 S Pen 书写/橡皮声，满音量对应约 1 dp/ms 的笔速。
    val penSoundFullSpeed = with(LocalDensity.current) { 1.dp.toPx() }
    val penSounds = remember(context) { SPenSounds(context.applicationContext) }
    LaunchedEffect(penSounds) { withContext(Dispatchers.IO) { penSounds.load() } }
    DisposableEffect(penSounds) { onDispose { penSounds.release() } }
    // 公式模式：只有电脑上训练的公式模型包放到了设备上才显示开关（见 FormulaRecognizer）
    val formulaPrefs = remember(context) { context.getSharedPreferences("handwriting", android.content.Context.MODE_PRIVATE) }
    val formulaAvailable = remember(context) { FormulaRecognizer.isAvailable(context) }
    var formulaMode by remember { mutableStateOf(formulaAvailable && formulaPrefs.getBoolean("formula_mode", false)) }
    val formulaActive by rememberUpdatedState(formulaMode && formulaAvailable)
    val delimiter by rememberUpdatedState(LocalKeyboardInputPreferences.current.formulaDelimiter)
    // 模板库：第一次打开时把训练页写过的假名 / 字母算成模板（后台），之后每次选候选都会追加
    var templateCount by remember(templateScript) {
        mutableIntStateOf(templateScript?.let { HandwritingTemplates.count(context, it) } ?: 0)
    }
    // 只在后台读样本那一会儿显示“正在读取”；读完仍没有模板就直接说缺什么，不一直转圈。
    var importingTemplates by remember(templateScript) { mutableStateOf(templateScript != null) }
    LaunchedEffect(templateScript) {
        val script = templateScript ?: return@LaunchedEffect
        try {
            withContext(Dispatchers.IO) { runCatching { HandwritingTemplates.importTrainingSamples(context, script) } }
            templateCount = HandwritingTemplates.count(context, script)
        } finally { importingTemplates = false }
    }
    val useTemplates by rememberUpdatedState(templateScript != null && templateCount > 0)
    // 公式模式下首选候选的渲染预览（LaTeX → KaTeX），板上没笔迹时不显示
    var formulaPreview by remember { mutableStateOf("") }
    val session = remember(scope, sessionKey, language) {
        HandwritingInputSession(scope, { pauseMs }, recognize = { strokes ->
            if (formulaActive) recognitionMutex.withLock {
                // 公式：整段笔迹一次识别成 LaTeX，候选按设置的标记上屏；首选另外渲染成公式预览（候选栏仍是纯文本）
                val results = withContext(Dispatchers.Default) {
                    FormulaRecognizer.recognize(context, strokes.map { stroke -> stroke.map { it.x to it.y } })
                }
                formulaPreview = results.firstOrNull()?.first.orEmpty()
                if (results.isEmpty()) emptyList()
                else listOf(OverlappedHandwritingRecognizer.Segment(0, strokes.size,
                    results.map { (latex, score) -> HandwritingCandidate(delimiter.wrap(latex), score) }, replacesPending = true))
            } else if (useTemplates) recognitionMutex.withLock {
                // 假名 / 字母：每次停顿算一个字，和模板比，最像的几个当候选
                withContext(Dispatchers.Default) {
                    val script = templateScript ?: return@withContext emptyList()
                    val results = HandwritingTemplates.recognize(context, script, strokes.map { stroke -> stroke.map { it.x to it.y } })
                    if (results.isEmpty()) emptyList()
                    else listOf(OverlappedHandwritingRecognizer.Segment(0, strokes.size,
                        results.map { (text, score) -> HandwritingCandidate(text, score) }))
                }
            } else {
                // 只传不可变快照；每次创建新窗口，取消的本地推理不会污染下一字缓存。
                val gaps = HandwritingStrokeFx.windowGaps(strokes).map { it * OverlappedHandwritingRecognizer.GAP_SPLIT_MS / pauseMs }
                recognitionMutex.withLock {
                    withContext(Dispatchers.Default) {
                        OverlappedHandwritingRecognizer().recognize(
                            strokes.map { stroke -> stroke.map { it.x to it.y } }, gaps,
                            predictFn = { points, topK -> HandwritingEngine.predict(points, topK, recognitionLanguage) },
                        ).segments
                    }
                }
            }
        }, onNewCharacter = { newCharacter?.invoke() },
            onRecognition = { result?.invoke(it) }, onKey = { keyAction(it) }, keepInk = { formulaActive })
    }
    val modelDownloaded by remember {
        ModelManager.downloadStates.map { it["ochwpro"] is ModelDownloadState.Complete }.distinctUntilChanged()
    }.collectAsState(ModelManager.downloadStates.value["ochwpro"] is ModelDownloadState.Complete)
    LaunchedEffect(modelDownloaded, language) { withContext(Dispatchers.IO) { HandwritingEngine.initialize(context, recognitionLanguage) } }
    LaunchedEffect(clearSignal, session) { session.clear() }
    DisposableEffect(session) { onDispose { session.clear() } }

    fun press(action: String) {
        feedback?.invoke(action)
        session.press(action)
    }

    // Keep QWERTY's content edges and bottom inset, with five handwriting rows.
    BoxWithConstraints(bodyModifier.fillMaxSize().testTag("handwriting-panel")
        .padding(bottom = (8 + bottomPaddingDp).dp)) {
        // Fullscreen applies to ink only. Keep the footer in the user's normal keyboard bounds.
        val controlsWidth = if (expanded && expandedControlsWidthDp > 0)
            (expandedControlsWidthDp.dp - normalControlGutter.dp * 2).coerceIn(1.dp, maxWidth.coerceAtLeast(1.dp)) else maxWidth
        val travel = ((maxWidth - controlsWidth) / 2).value.coerceAtLeast(0f)
        val controlsOffset = if (expanded) expandedControlsOffsetX.toFloat().coerceIn(-travel, travel).dp else 0.dp
        val bottom = QwertyBottomRowWeights.Standard
        val functionKeyWidth = controlsWidth * (bottom.enter / bottom.total)
        val footerHeight = if (expanded) normalControlHeight.dp.coerceAtMost(maxHeight / 3) else maxHeight / 5
        if (!expanded) SideEffect {
            normalControlHeight = footerHeight.value
            normalControlScale = currentContentScale
            normalControlMetrics = currentVisualMetrics
            normalControlGutter = currentVisualMetrics.gutterX
        }
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.weight(1f).fillMaxWidth()) {
                Box(Modifier.weight(1f).fillMaxHeight()) {
            // 公式预览只是叠在画板顶部的显示内容，放在画布下层：整块区域都能落笔，笔迹画在预览之上。
            if (formulaActive) Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(52.dp)
                .padding(horizontal = 96.dp).testTag("handwriting-formula-preview"), contentAlignment = Alignment.Center) {
                if (session.hasInk && formulaPreview.isNotEmpty())
                    com.kingzcheung.xime.ui.settings.KatexView(formulaPreview, Modifier.fillMaxSize(), color = keyTextColor.copy(alpha = 0.7f))
                else Text("写完停笔，这里显示公式预览", color = keyTextColor.copy(alpha = 0.4f), fontSize = 12.sp)
            }
            Canvas(Modifier.fillMaxSize().clipToBounds().testTag("handwriting-canvas")
                .semantics { contentDescription = "手写区域"; stateDescription = session.phase.description }
                .handwritingInk(session, stylusPresence, eraserRadiusPx,
                    onErased = { if (!penSounds.active) feedback?.invoke("handwriting_erase") },
                    onPenMotion = { erasing, speed -> penSounds.onMotion(erasing, speed, penSoundFullSpeed) },
                    onPenLifted = { penSounds.stop() })) {
                renderStrokes(session.strokes + listOfNotNull(session.currentStroke.takeIf { it.isNotEmpty() }), emptyList(), inkColor, transparentPaper = expanded, penColor = penInkColor)
            }
            if (language != recognitionLanguage || templateScript != null) Text(when {
                    useTemplates && language == InputLanguage.JAPANESE -> "日语手写 · 边写边学"
                    useTemplates -> "英文手写 · 边写边学"
                    templateScript != null && importingTemplates -> "正在读取你的手写样本…"
                    templateScript != null && language == InputLanguage.JAPANESE -> "还没有你的假名样本：去 设置 → 手写训练模式 写几个假名"
                    templateScript != null -> "还没有你的字母样本：去 设置 → 手写训练模式 写几个字母"
                    else -> "中文手写"
                }, color = keyTextColor.copy(alpha = 0.6f),
                modifier = Modifier.align(Alignment.TopStart).padding(start = 12.dp, top = 12.dp, end = 96.dp).testTag("handwriting-language"))
            // 公式开关是浮在画板右上角的实心小按钮，不用对钩：开启时强调色填满、文字在黑白里取反差大的；
            // 关闭时用主题功能键的底色（强制不透明）和文字色，不加描边。
            if (formulaAvailable) Surface(
                onClick = {
                    session.clear()
                    keyAction("clear_composition")
                    formulaMode = !formulaMode
                    formulaPreview = ""
                    formulaPrefs.edit().putBoolean("formula_mode", formulaMode).apply()
                    if (!formulaMode) FormulaRecognizer.release()
                },
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).testTag("handwriting-formula-toggle")
                    .semantics { stateDescription = if (formulaMode) "公式模式已开" else "公式模式已关" },
                shape = RoundedCornerShape(50),
                color = if (formulaMode) MaterialTheme.colorScheme.primary.copy(alpha = 1f) else specialKeyBackgroundColor.copy(alpha = 1f),
                contentColor = if (formulaMode) {
                    if (MaterialTheme.colorScheme.primary.luminance() > 0.179f) Color.Black else Color.White
                } else specialKeyTextColor,
                shadowElevation = if (formulaMode) 2.dp else 0.dp,
            ) {
                Text("公式", fontSize = 14.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp))
            }
                }
                if (!expanded) Column(Modifier.width(functionKeyWidth).fillMaxHeight().testTag("handwriting-side-keys")) {
                    // 「按键符号」的手写侧栏，三格。
                    (listOf("delete") + SideSymbolTarget.HANDWRITING.resolve(sideSymbols)).forEach { action ->
                        HandwritingFunctionKey(action, { press(action) },
                            if (action == "delete") specialKeyBackgroundColor else keyBackgroundColor,
                            if (action == "delete") specialKeyTextColor else keyTextColor,
                            Modifier.weight(1f).fillMaxWidth(), onClear = { press("clear_all") })
                    }
                }
            }
            val reportGlassRegion = HandwritingGlassRegion.reporter
            androidx.compose.runtime.DisposableEffect(expanded, reportGlassRegion) {
                if (!expanded) reportGlassRegion?.invoke(null)
                onDispose { reportGlassRegion?.invoke(null) }
            }
            Box(Modifier.fillMaxWidth()) {
            Column(Modifier.align(Alignment.TopCenter).absoluteOffset(x = controlsOffset).width(controlsWidth)
                // Fullscreen ink stays transparent; only the saved footer bounds receive the keyboard material.
                .then(if (expanded) Modifier.keyboardPanelBackground(panelBackgroundColor)
                    .onGloballyPositioned { coords ->
                        val pos = coords.positionInWindow()
                        reportGlassRegion?.invoke(android.graphics.Rect(kotlin.math.round(pos.x).toInt(), kotlin.math.round(pos.y).toInt(),
                            kotlin.math.round(pos.x + coords.size.width).toInt(), kotlin.math.round(pos.y + coords.size.height).toInt()))
                    } else Modifier)
                .testTag("handwriting-controls")) {
            CompositionLocalProvider(LocalKeyboardKeyVisualMetrics provides
                if (expanded) normalControlMetrics ?: currentVisualMetrics else currentVisualMetrics,
                LocalKeyboardKeyContentScale provides if (expanded) normalControlScale ?: currentContentScale else currentContentScale) {
            if (expanded) Box(Modifier.fillMaxWidth()) { expandedCandidateBar() }
            if (expanded) {
                Row(Modifier.fillMaxWidth().height(footerHeight).background(functionRowBackground)
                    .testTag("handwriting-symbol-row")) {
                    SideSymbolTarget.HANDWRITING_ROW.resolve(rowSymbols).forEach { action ->
                        HandwritingFunctionKey(action, { press(action) }, keyBackgroundColor, keyTextColor,
                            Modifier.weight(1f).fillMaxHeight())
                    }
                    HandwritingFunctionKey("delete", { press("delete") }, specialKeyBackgroundColor,
                        specialKeyTextColor, Modifier.width(functionKeyWidth).fillMaxHeight(), onClear = { press("clear_all") })
                }
            }
            Row(Modifier.fillMaxWidth().height(footerHeight).background(functionRowBackground)
                .testTag("handwriting-bottom-row")) {
                // The expand control occupies QWERTY's punctuation slot, so every
                // bottom-row boundary stays in place when switching input modes.
                val keys = listOf("symbol" to bottom.mode, "number" to bottom.mode,
                    (if (expanded) "collapse" else "expand") to bottom.punctuation,
                    "space" to bottom.space, "ime_switch" to bottom.language, "enter" to bottom.enter)
                keys.forEach { (action, weight) ->
                    val special = action != "space"
                    HandwritingFunctionKey(action, { press(action) },
                        if (special) specialKeyBackgroundColor else keyBackgroundColor,
                        if (special) specialKeyTextColor else keyTextColor,
                        Modifier.weight(weight).fillMaxHeight())
                }
            }
            }
            } // constrained controls
            }
        }
    }
    }
    }
}

@Composable
private fun HandwritingFunctionKey(action: String, onClick: () -> Unit, background: Color, foreground: Color, modifier: Modifier,
    onClear: (() -> Unit)? = null) {
    val label = when (action) {
        "delete" -> "删除"; "enter" -> "回车"; "space" -> "空格"
        "symbol" -> "!@#"; "number" -> "123"; "ime_switch" -> "语言切换"
        "expand" -> "全屏手写"; "collapse" -> "收起全屏手写"; else -> punctuationKeyLabel(action)
    }
    val enter = LocalEnterKeyColors.current.takeIf { action == "enter" }
    val keyBackground = enter?.background ?: background
    val keyForeground = enter?.foreground ?: foreground
    if (action == "delete") {
        SwipeableIconKeyButton(
            icon = rememberVectorPainter(Icons.AutoMirrored.Filled.Backspace),
            onClick = onClick, onLongClick = onClick, onSwipeUp = onClear,
            followSymbolSwipeDirection = true,
            upFeedback = KeyFlickFeedback.DELETE,
            swipeText = "清空", swipeUpLabel = "清空",
            backgroundColor = keyBackground, iconColor = keyForeground,
            modifier = modifier.semantics { contentDescription = label }.testTag("handwriting-key:delete"),
        )
        return
    }
    val keyModifier = modifier.semantics {
        contentDescription = label
        onClick { onClick(); true }
    }.testTag("handwriting-key:$action")
    if (action == "ime_switch") {
        // 与其他键盘同一个语言键：点按切换，长按弹出语言菜单。
        LanguageKeyButton(icon = rememberVectorPainter(Icons.Default.Language), onClick = onClick,
            backgroundColor = keyBackground, iconColor = keyForeground, modifier = keyModifier)
        return
    }
    val icon = when (action) {
        "enter" -> Icons.AutoMirrored.Filled.KeyboardReturn
        "space" -> Icons.Default.SpaceBar
        "ime_switch" -> Icons.Default.Language
        "expand" -> Icons.Default.OpenInFull
        "collapse" -> Icons.Default.CloseFullscreen
        else -> null
    }
    if (icon != null) IconKeyButton(rememberVectorPainter(icon), onClick,
        keyBackground, keyForeground, keyModifier)
    else CompositionLocalProvider(LocalKeyboardPunctuation provides null) {
        // Labels are resolved above; mode labels must stay !@#/123 like KeyboardModeKey.
        KeyButton(label, onClick, keyBackground, keyForeground, keyModifier,
            fontSize = (if (action == "symbol" || action == "number") 16 else 18).sp)
    }
}
