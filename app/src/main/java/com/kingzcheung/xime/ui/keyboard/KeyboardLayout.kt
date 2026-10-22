package com.kingzcheung.xime.ui.keyboard

import com.kingzcheung.xime.ui.theme.VisualStyles
import com.kingzcheung.xime.ui.theme.visualMaterial

import android.annotation.SuppressLint
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.twotone.EmojiEmotions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import com.kingzcheung.xime.settings.SettingsPreferences
import com.kingzcheung.xime.settings.DisplayMode
import com.kingzcheung.xime.settings.ButtonLayout
import com.kingzcheung.xime.settings.KeysConfigHelper
import com.kingzcheung.xime.settings.swipeHandlerFor
import com.kingzcheung.xime.keyboard.GestureAction

/** 半角 → 全角标点映射，中文模式下键帽显示用。提交仍走半角由 Rime 处理。 */
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kingzcheung.xime.keyboard.KeyboardDimensions
import com.kingzcheung.xime.util.PermissionHelper
import com.kingzcheung.xime.util.CharInfo
import com.kingzcheung.xime.util.FileLogger
import com.kingzcheung.xime.util.SubcharHelper
import com.kingzcheung.xime.viewmodel.KeyboardUiState
import com.kingzcheung.xime.viewmodel.KeyboardViewModel
import com.kingzcheung.xime.viewmodel.ShiftMode
import com.kingzcheung.xime.keyboard.OverlayRoute
import com.kingzcheung.xime.ui.theme.KeyboardThemes
import com.kingzcheung.xime.ui.theme.keyboardBackground

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.TextUnit



@Composable
fun KeyboardLayout(
    onKeyPress: (String) -> Unit,
    viewModel: KeyboardViewModel,
    callbacks: KeyboardCallbacks,
    uiState: KeyboardUiState,
    isAsciiMode: Boolean,
    modifier: Modifier = Modifier,
) {
    // 配置或方案变化时，行列与间距一起更新。
    val cfgVer by KeysConfigHelper.configVersion.collectAsState()
    // Chinese layout schemas, and the arrangement English / Japanese romaji chose, share one component.
    val letterLayout = remember(cfgVer, isAsciiMode, uiState.currentSchemaId) {
        KeysConfigHelper.letterLayout(uiState.currentSchemaId, isAsciiMode)
    }
    val keyRows = remember(cfgVer, isAsciiMode, uiState.currentSchemaId, letterLayout) {
        letterLayout?.typingRows() ?: KeysConfigHelper.getKeyRows(isAsciiMode)
    }
    // Typewise honeycomb: a fourth letter row, short rows offset half a key, hexagonal letter caps.
    val honeycomb = letterLayout?.honeycomb == true
    val splitKeyboard = LocalKeyboardInputPreferences.current.splitKeyboardEnabled &&
        supportsSplitKeyboard(uiState.currentSchemaId, isAsciiMode)
    val correctionEnabled = LocalKeyboardInputPreferences.current.neighborCorrection && !isAsciiMode
    val geometry = remember(cfgVer, keyRows, correctionEnabled, uiState.currentSchemaId, splitKeyboard) {
        LetterGeometry { encoded -> callbacks.onLetterNeighbors?.invoke(uiState.currentSchemaId, if (correctionEnabled) encoded else "") }
    }
    DisposableEffect(geometry) {
        callbacks.onLetterNeighbors?.invoke(uiState.currentSchemaId, "")
        onDispose { }
    }
    // Shared physical frame and bottom-row spacing for 14/26 keys. The paired
    // alphabet keys still occupy their own five-column rows inside that frame.
    val fourteenKey = !isAsciiMode && uiState.inputProfile.layout == com.kingzcheung.xime.settings.InputLayout.MERGED14
    val metricContext = LocalContext.current
    val metricDisplay = LocalView.current.display
    val metricConfiguration = LocalConfiguration.current
    val pixelsPerDp = LocalDensity.current.density
    val physicalTargetCellDp = remember(metricContext, metricDisplay, metricConfiguration, pixelsPerDp) {
        keyboardPhysicalMetrics(metricContext, metricDisplay, pixelsPerDp)
            ?.dpFromMillimeters(CALIBRATED_QWERTY_PITCH_MM)
    }
    // 分体 = 正常 26 键 + 每行中线处同宽的一条缝：错位、键宽、按键功能都与正常键盘一致。
    val splitGap = LocalSplitKeyboardGap.current
    val splitGapSetting = LocalSplitGapPreview.current?.value ?: LocalKeyboardInputPreferences.current.let {
        if (metricConfiguration.screenWidthDp > metricConfiguration.screenHeightDp) it.splitGapLandscape else it.splitGapPortrait
    }
    BoxWithConstraints(modifier.then(if (splitKeyboard && splitGap != null)
        Modifier.onGloballyPositioned { splitGap.reportAreaTop(it.boundsInWindow().top) } else Modifier)) {
    val splitGutterDp = SplitKeyboardGeometry.gutterDp(uiState.isFloatingMode)
    val keyboardHeight = maxHeight
    val splitGapFraction = if (splitKeyboard) SplitKeyboardGeometry.resolvedGap(
        splitGapSetting, maxWidth.value, splitGutterDp, physicalTargetCellDp) else 0f
    val splitGapDp = (splitGapFraction * maxWidth.value).dp
    // 分体两半贴住左右边：不套用居中的键宽上限。
    CompositionLocalProvider(LocalKeyboardExplicitWidth provides (LocalKeyboardExplicitWidth.current || splitKeyboard)) {
    KeyboardKeySpacingScope(Modifier.fillMaxSize(),
        columns = letterKeyboardColumns(keyRows, fourteenKey),
        widthFraction = if (splitKeyboard) SplitKeyboardGeometry.keyWidthFraction(
            maxWidth.value, splitGapFraction, splitGutterDp) else 1f,
        policy = KeyVisualPolicy.Qwerty,
        allowShrink = uiState.isFloatingMode,
        applyGutter = true,
        growthSpacing = KeysConfigHelper.getKeyboardKeyConfig().spacingFor("qwerty"),
    ) { bodyModifier ->
    val splitCellDp = LocalKeyboardKeyVisualMetrics.current.cellWidthDp
    val letterRowGeometry = standardLetterRowGeometry(
        keyRows = keyRows,
        cellWidthDp = splitCellDp,
        hasCustomLayout = letterLayout != null,
        isSplit = false,
        physicalTargetCellDp = physicalTargetCellDp,
        stagger = LocalKeyboardInputPreferences.current.let {
            resolvedQwertyStagger(it.qwertyStagger, it.qwertyStaggerCustom, splitCellDp, physicalTargetCellDp)
        },
    )
    val standardRowInset = letterRowGeometry?.middleRowInsetDp?.dp
    val standardRowEndInset = letterRowGeometry?.middleRowEndInsetDp?.dp
    val letterSideKeyWeight = letterRowGeometry?.outerKeyWeight ?: 1.4f
    val letterDeleteKeyWeight = letterRowGeometry?.deleteKeyWeight ?: 1.4f
    val letterBottomWeight = letterRowGeometry?.bottomLetterWeight ?: 7f
    val splitWeighted = letterLayout != null || fourteenKey
    val baseRow1 = keyRows.getOrElse(1) { listOf("a", "s", "d", "f", "g", "h", "j", "k", "l") }
    val splitRow1InsetDp = standardRowInset
        ?: if ((if (splitWeighted) baseRow1.sumOf { it.length } else baseRow1.size) > 9) 0.dp else 16.dp
    // 14 键分体：跨中线的双字母键拆成两个单字母键，其余行与键不变。
    val pairedRows = if (splitKeyboard && fourteenKey && keyRows.size >= 3) listOf(
        SplitKeyboardGeometry.splitStraddlingPairs(keyRows[0], 0f),
        SplitKeyboardGeometry.splitStraddlingPairs(keyRows[1], splitRow1InsetDp.value / splitCellDp.coerceAtLeast(1f)),
        SplitKeyboardGeometry.splitStraddlingPairs(keyRows[2], letterSideKeyWeight),
    ) + keyRows.drop(3) else keyRows
    val shiftSlideTargets = remember(uiState.currentSchemaId, isAsciiMode) { ShiftSlideTargets() }
    // 14 键按住 Shift 拖动时，双字母键拆回单字母键，键帽显示手指实际停的那个字母；松开 Shift 恢复（用户 10-21）。
    // 按字母数定宽，拆开后每个字母的位置和宽度不变。
    val renderRows = if (fourteenKey && shiftSlideTargets.dragging) pairedRows.mapIndexed { index, row ->
        if (index > 2) row else row.flatMap { key ->
            if (key.length == 2 && key.all { it.lowercaseChar() in 'a'..'z' }) listOf(key.take(1), key.drop(1)) else listOf(key)
        }
    } else pairedRows
    val splitRow0 = renderRows.getOrElse(0) { listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p") }
    val splitRow1 = renderRows.getOrElse(1) { listOf("a", "s", "d", "f", "g", "h", "j", "k", "l") }
    val splitRow2 = renderRows.getOrElse(2) { listOf("z", "x", "c", "v", "b", "n", "m") }
    // 每行左半的键数；-1 表示不分体。
    val splitAt0 = if (splitKeyboard) SplitKeyboardGeometry.leftCount(splitRow0, 0f, splitWeighted) else -1
    val splitAt1 = if (splitKeyboard) SplitKeyboardGeometry.leftCount(splitRow1,
        splitRow1InsetDp.value / splitCellDp.coerceAtLeast(1f), splitWeighted) else -1
    val splitAt2 = if (splitKeyboard) SplitKeyboardGeometry.leftCount(splitRow2, letterSideKeyWeight, splitWeighted) else -1
    val shiftMode by viewModel.shiftMode.collectAsStateWithLifecycle()
    val isShifted = shiftMode.isShifted

    val context = LocalContext.current
    val kbColors = KeysConfigHelper.getKeyboardColors()
    val longToColor: (Long) -> Color = { if (it > 0xFFFFFF) Color(it) else Color(0xFF000000 or it) }
    val keyboardBackgroundColor = KeyboardThemes.getKeyboardBackgroundColor(uiState.themeId, uiState.isDarkTheme)
    val themeScheme = KeyboardThemes.getRenderingScheme(uiState.themeId)
    val themeSpecialKeyColor = KeyboardThemes.getSpecialKeyColor(uiState.themeId, uiState.isDarkTheme)
    val keyBackgroundColor = KeyboardThemes.getKeyBgColorOverride(uiState.themeId, uiState.isDarkTheme)
        ?: if (uiState.isDarkTheme) longToColor(kbColors.keyBgColorDark) else longToColor(kbColors.keyBgColor)
    val keyTextColor = KeyboardThemes.getKeyTextColorOverride(uiState.themeId, uiState.isDarkTheme)
        ?: if (uiState.isDarkTheme) longToColor(kbColors.keyTextColorDark) else longToColor(kbColors.keyTextColor)
    val specialKeyBackgroundColor = com.kingzcheung.xime.ui.theme.resolvedSpecialKeyColor(
        themeScheme, uiState.isDarkTheme,
        (if (uiState.isDarkTheme) kbColors.specialKeyBgColorDark else kbColors.specialKeyBgColor)?.let(longToColor)
    )
    val specialKeyTextColor = if (uiState.isDarkTheme) Color.White
        else KeyboardThemes.getSpecialKeyTextColor(uiState.themeId, false)
    val bubbleBgColor = if (uiState.isDarkTheme) themeScheme.specialKeyDark
        else themeScheme.specialKeyLight
    val kbShadow = KeysConfigHelper.getKeyboardShadow()
    val kbKey = KeysConfigHelper.getKeyboardKeyConfig()
    val shadowEnabled = kbShadow.enabled
    val shadowElevation = kbShadow.elevation.dp
    val shadowShapeRadius = kbShadow.shapeRadius.dp
    val schemaName = uiState.schemaName
    val enterKeyText = uiState.enterKeyText
    val isDarkTheme = uiState.isDarkTheme
    val isSttEnabled = uiState.isSttEnabled
    val isVoiceMode = uiState.isVoiceMode
    val isVoiceSticky = uiState.voiceSticky
    val onKeyPressDown = callbacks.onKeyPressDown
    val onKeyRelease = callbacks.onKeyRelease
    val onVoiceModeChange = callbacks.onVoiceModeChange
    val onCommitText = callbacks.onCommitText
    val onGestureAction: (GestureAction, String) -> Unit = { action, value ->
        when (action) {
            GestureAction.SWITCH_ROUTE -> {
                val overlayRoute = when (value) {
                    "emoji" -> OverlayRoute.Emoji
                    "symbol" -> OverlayRoute.Symbol
                    else -> null
                }
                overlayRoute?.let { viewModel.showOverlay(it) }
            }
            GestureAction.TOGGLE_ASCII -> {
                FileLogger.i("XimeKeyboard", "earth key toggle_ascii tapped, ui ascii=${uiState.isAsciiMode}")
                viewModel.resetShift()
                callbacks.onKeyPress("ime_switch", uiState.isAsciiMode)
            }
            GestureAction.DELETE -> {
                callbacks.onKeyPress("delete", false)
            }
            GestureAction.TOGGLE_SYMBOLS -> {
                callbacks.onKeyPress("mode_change", false)
            }
            GestureAction.TOGGLE_SHIFT -> {
                viewModel.toggleShift()
            }
            else -> callbacks.onGestureAction?.invoke(action, value) ?: Unit
        }
    }
    val suppressCursorMove = LocalSuppressCursorMove.current
    var swipeUpHintsEnabled by remember {
        mutableStateOf(
            SettingsPreferences.isSwipeUpHintsEnabled(
                context
            )
        )
    }
    var swipeDownHintsEnabled by remember {
        mutableStateOf(
            SettingsPreferences.isSwipeDownHintsEnabled(
                context
            )
        )
    }
    val effectiveSwipeDownHintsEnabled = swipeDownHintsEnabled

    // 监听设置变化
    DisposableEffect(context) {
        val prefs = SettingsPreferences.getPrefsPublic(context)
        val listener =
            android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                when (key) {
                    SettingsPreferences.KEY_SWIPE_UP_HINTS_ENABLED ->
                        swipeUpHintsEnabled = SettingsPreferences.isSwipeUpHintsEnabled(context)

                    SettingsPreferences.KEY_SWIPE_DOWN_HINTS_ENABLED ->
                        swipeDownHintsEnabled = SettingsPreferences.isSwipeDownHintsEnabled(context)
                }
            }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    LaunchedEffect(Unit) {
        SubcharHelper.init(context)
    }

    val swipeBubble = rememberSwipeBubbleController()
    var keyboardBounds by remember { mutableStateOf(Rect(0f, 0f, 0f, 0f)) }
    // 英文滑行：只在英文 26 键（含分体）启用；整键盘滑动移动光标与其冲突时让位。
    val glidePrefs = LocalKeyboardInputPreferences.current
    val glideEnabled = isAsciiMode && glidePrefs.glideTyping && callbacks.onGlideWord != null &&
        glidePrefs.cursorGesture != CursorGestureMode.KEYBOARD && !(isVoiceMode && !isVoiceSticky)
    val glideTrail = remember { GlideTrail() }
    val glideScope = rememberCoroutineScope()
    val glideContext = LocalContext.current.applicationContext
    val onGlideTrace: (FloatArray, FloatArray) -> Unit = trace@{ xs, ys ->
        val keys = geometry.snapshot()
        if (keys.size < 26) return@trace
        // 输入框归属在抬手时确定；识别完成前切换输入框则作废。
        val inputContext = runCatching { callbacks.captureInputContext() }.getOrNull() ?: return@trace
        val shift = viewModel.shiftMode.value
        glideScope.launch {
            val words = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                com.kingzcheung.xime.glide.GlideLexicon.decoder(glideContext,
                    com.kingzcheung.xime.glide.GlideLexicon.keyboard(keys)).decode(xs, ys)
            }.map { com.kingzcheung.xime.glide.glideCased(it.word, shift.isShifted,
                shift == com.kingzcheung.xime.viewmodel.ShiftMode.CAPS) }
            if (words.isEmpty()) return@launch
            viewModel.onCharacterTyped()
            kotlinx.coroutines.withContext(inputContext) { callbacks.onGlideWord?.invoke(words.first(), words.drop(1)) }
        }
    }
    // 提前读入词表，第一次滑行不必等待。
    LaunchedEffect(glideEnabled) {
        if (glideEnabled) kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { com.kingzcheung.xime.glide.GlideLexicon.words(glideContext) }
        }
    }

    fun processSwipeState(state: SwipeState, bounds: Rect) {
        val newState = if (state.isSwipeDown && state.swipeText != null) {
            state.copy(charInfos = SubcharHelper.parseSwipeDownText(state.swipeText))
        } else {
            state
        }
        swipeBubble.update(
            newState,
            Rect(
                left = bounds.left - keyboardBounds.left,
                top = bounds.top - keyboardBounds.top,
                right = bounds.right - keyboardBounds.left,
                bottom = bounds.bottom - keyboardBounds.top
            )
        )
    }

    val bubbleData = rememberSwipeBubbleDrawData(
        swipeState = swipeBubble.state,
        keyBounds = swipeBubble.keyBounds,
        keyBackgroundColor = bubbleBgColor,
        keyTextColor = keyTextColor,
        accentColor = specialKeyTextColor,
        keyWidth = if (swipeBubble.state.isSwiping || swipeBubble.state.isPressed) swipeBubble.keyBounds.width else 0f,
        keyboardWidth = keyboardBounds.width
    )


    CompositionLocalProvider(
        LocalFourteenKeyLayout provides fourteenKey,
        LocalLetterGeometry provides geometry,
        LocalCustomLayout provides letterLayout,
        LocalHoneycombKeys provides honeycomb,
        LocalCustomAccent provides KeyboardThemes.getPrimaryColor(uiState.themeId, uiState.isDarkTheme),
        LocalShiftSlideTargets provides shiftSlideTargets,
        LocalKeyCornerRadius provides kbKey.cornerRadius.dp,
        LocalEnterKeyColors provides KeyboardKeyColors(KeyboardThemes.getEnterKeyColor(uiState.themeId, uiState.isDarkTheme), specialKeyTextColor),
        LocalFunctionKeyColors provides KeyboardKeyColors(specialKeyBackgroundColor, specialKeyTextColor),
        LocalKeyVisualPadding provides PaddingValues(
            horizontal = kbKey.spacingFor("qwerty").first?.dp ?: 2.dp,
            vertical = kbKey.spacingFor("qwerty").second?.dp ?: 2.dp,
        ),
    ) {
    Box(
        modifier = bodyModifier
            .onGloballyPositioned { coordinates ->
                keyboardBounds = coordinates.boundsInRoot()
                shiftSlideTargets.keyboardBounds = keyboardBounds
            }
            .glideTyping(glideEnabled, geometry, { keyboardBounds.topLeft }, glideTrail, onGlideTrace)

    ) {
        SwipeBubbleOverlay(bubbleData)
        if (glideEnabled) GlideTrailOverlay(glideTrail,
            KeyboardThemes.getAccentColor(uiState.themeId, uiState.isDarkTheme),
            Modifier.matchParentSize().zIndex(1f))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .padding(bottom = 8.dp)
                        .then(if (splitKeyboard) Modifier.testTag("split-keyboard") else Modifier),
            ) {
                // Hexagon caps reach a sixth of a row beyond their cell; keep that room above the
                // first row and below the last letter row (five equal rows share the height).
                val honeycombOverhang = if (honeycomb) (keyboardHeight - 8.dp) / 32 else 0.dp

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(top = honeycombOverhang),
                    verticalArrangement = Arrangement.Top,
                ) {
                    // 第一行
                    if (isVoiceMode && !isVoiceSticky) {
                        Box(modifier = Modifier.weight(1f)) {
                            DummyKeyboardRow(
                                keys = renderRows.getOrNull(0).orEmpty(),
                                weightedLetters = LocalCustomLayout.current != null || fourteenKey,
                                keyBackgroundColor = keyBackgroundColor.copy(alpha = 0.5f),
                                keyboardBackgroundColor = keyboardBackgroundColor,
                                splitAt = splitAt0, splitGap = splitGapDp, splitRow = 0,
                            )
                        }
                    } else {
                        Box(modifier = Modifier.weight(1f)) {
                            val row0 = renderRows.getOrElse(0) { listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p") }
                            KeyboardRowWithConfig(
                                keys = row0,
                                edgeInset = if (honeycomb) (splitCellDp / 2f).dp else 0.dp,
                                onKeyPress = onKeyPress,
                                config = KeyboardRowConfig(
                                    keyBackgroundColor = keyBackgroundColor,
                                    keyTextColor = keyTextColor,
                                    keyboardBackgroundColor = keyboardBackgroundColor,
                                    shadowEnabled = shadowEnabled,
                                    shadowElevation = shadowElevation,
                                    shadowShapeRadius = shadowShapeRadius,
                                ),
                                isShifted = isShifted,
                                isAsciiMode = isAsciiMode,
                                onSwipeStateChange = { state, bounds ->
                                    processSwipeState(
                                        state,
                                        bounds
                                    )
                                },
                                onKeyPressDown = onKeyPressDown,
                                onKeyRelease = onKeyRelease,
                                swipeDownHintsEnabled = effectiveSwipeDownHintsEnabled,
                                swipeUpHintsEnabled = swipeUpHintsEnabled,
                                onCommitText = onCommitText,
                                onGestureAction = onGestureAction,
                                configVersion = cfgVer,
                                splitAt = splitAt0, splitGap = splitGapDp, splitRow = 0,
                            )
                        }
                    }

                    // 第二行
                    if (isVoiceMode && !isVoiceSticky) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = standardRowInset ?: 16.dp, end = standardRowEndInset ?: 16.dp)
                        ) {
                            DummyKeyboardRow(
                                keys = renderRows.getOrNull(1).orEmpty(),
                                weightedLetters = LocalCustomLayout.current != null || fourteenKey,
                                keyBackgroundColor = keyBackgroundColor.copy(alpha = 0.5f),
                                keyboardBackgroundColor = keyboardBackgroundColor,
                                splitAt = splitAt1, splitGap = splitGapDp, splitRow = 1,
                            )
                        }
                    } else {
                        Box(modifier = Modifier.weight(1f)) {
                            val row1 = renderRows.getOrElse(1) { listOf("a", "s", "d", "f", "g", "h", "j", "k", "l") }
                            val row1Inset = standardRowInset
                                ?: if ((if (LocalCustomLayout.current != null) row1.sumOf { it.length } else row1.size) > 9) 0.dp else 16.dp
                            KeyboardRowWithConfig(
                                keys = row1,
                                onKeyPress = onKeyPress,
                                config = KeyboardRowConfig(
                                    keyBackgroundColor = keyBackgroundColor,
                                    keyTextColor = keyTextColor,
                                    keyboardBackgroundColor = keyboardBackgroundColor,
                                    shadowEnabled = shadowEnabled,
                                    shadowElevation = shadowElevation,
                                    shadowShapeRadius = shadowShapeRadius,
                                ),
                                isShifted = isShifted,
                                isAsciiMode = isAsciiMode,
                                edgeInset = row1Inset,
                                edgeEndInset = standardRowEndInset ?: row1Inset,
                                extendLastKeyToEdge = fourteenKey && row1.lastOrNull()?.length == 1,
                                onSwipeStateChange = { state, bounds ->
                                    processSwipeState(
                                        state,
                                        bounds
                                    )
                                },
                                onKeyPressDown = onKeyPressDown,
                                onKeyRelease = onKeyRelease,
                                swipeDownHintsEnabled = effectiveSwipeDownHintsEnabled,
                                swipeUpHintsEnabled = swipeUpHintsEnabled,
                                onCommitText = onCommitText,
                                onGestureAction = onGestureAction,
                                configVersion = cfgVer,
                                splitAt = splitAt1, splitGap = splitGapDp, splitRow = 1,
                            )
                        }
                    }

                    // 第三行
                    if (isVoiceMode && !isVoiceSticky) {
                        Box(modifier = Modifier.weight(1f)) {
                            DummyBottomRow(
                                keys = renderRows.getOrNull(2).orEmpty(),
                                weightedLetters = LocalCustomLayout.current != null || fourteenKey,
                                keyBackgroundColor = keyBackgroundColor.copy(alpha = 0.5f),
                                specialKeyBackgroundColor = specialKeyBackgroundColor.copy(alpha = 0.5f),
                                keyboardBackgroundColor = keyboardBackgroundColor,
                                sideKeyWeight = if (standardRowInset != null) letterSideKeyWeight else 1.2f,
                                endKeyWeight = if (standardRowInset != null) letterDeleteKeyWeight else 1.2f,
                                letterWeight = letterBottomWeight,
                                splitAt = splitAt2, splitGap = splitGapDp,
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .fillMaxHeight(),
                        ) {
                            CompositionLocalProvider(LocalHoneycombEdge provides if (honeycomb) HoneycombEdge.START else null) {
                            ShiftCapsKeyButton(
                                viewModel = viewModel,
                                shiftMode = shiftMode,
                                onKeyPress = onKeyPress,
                                onKeyPressDown = onKeyPressDown,
                                backgroundColor = specialKeyBackgroundColor,
                                iconColor = specialKeyTextColor,
                                accentColor = KeyboardThemes.getAccentColor(uiState.themeId, uiState.isDarkTheme),
                                panelColor = keyboardBackgroundColor,
                                modifier = Modifier
                                    .weight(letterSideKeyWeight)
                                    .fillMaxHeight(),
                                shadowEnabled = shadowEnabled,
                                shadowElevation = shadowElevation,
                                shadowShapeRadius = shadowShapeRadius,
                            )
                            }

                                val bottomKeys = renderRows.getOrElse(2) { listOf("z", "x", "c", "v", "b", "n", "m") }
                                val bottomWeighted = LocalCustomLayout.current != null || fourteenKey
                                // 14 键的 m 与 26 键的 m 同位同宽；删除键保持与 Shift 对称的宽度。
                                // 宽屏错位让删除键比 Shift 宽时，m 只借这部分富余的一半，最多一个键宽。
                                val fourteenMExtra = if (fourteenKey && splitAt2 < 0 && bottomKeys.lastOrNull()?.length == 1)
                                    fourteenMBorrowedWeight(letterSideKeyWeight, letterDeleteKeyWeight) else 0f
                                val bottomSegments = if (splitAt2 < 0) listOf(bottomKeys)
                                    else listOf(bottomKeys.take(splitAt2), bottomKeys.drop(splitAt2))
                                bottomSegments.forEachIndexed { segment, segmentKeys ->
                                if (segment > 0) SplitGapSpacer(2, splitGapDp)
                                Row(
                                    modifier = Modifier
                                        .weight(if (splitAt2 < 0) letterBottomWeight + fourteenMExtra else segmentKeys
                                            .sumOf { if (splitWeighted) it.length else 1 }.toFloat().coerceAtLeast(0.001f))
                                        .fillMaxHeight(),
                                ) {
                                segmentKeys.forEach { key ->
                                    val rawSwipeUpLabel = KeysConfigHelper.getSwipeUpLabel(key, isAsciiMode)
                                    val swipeUpText =
                                        if (swipeUpHintsEnabled) rawSwipeUpLabel else null
                                    val swipeUpAction = KeysConfigHelper.getSwipeUpAction(key, isAsciiMode)
                                    val swipeUpDisplay = KeysConfigHelper.getSwipeUpDisplay(key, isAsciiMode)
                                    val swipeUpKeyLabel =
                                        if (swipeUpDisplay != DisplayMode.BUBBLE && swipeUpHintsEnabled) swipeUpText else ""
                                    val swipeUpCommitValue = KeysConfigHelper.getSwipeUpCommitValue(key, isAsciiMode)
                                    val swipeDownRaw =
                                        KeysConfigHelper.getKeyGesture(key, isAsciiMode)?.swipeDown
                                    val swipeDownLabel =
                                        swipeDownRaw?.label?.takeIf { it.isNotEmpty() }
                                    val swipeDownAction = swipeDownRaw?.action
                                    val swipeDownValue = swipeDownRaw?.value
                                    val swipeDownDisplay = swipeDownRaw?.display ?: DisplayMode.BOTH
                                    val swipeDownBubbleText = if (swipeDownDisplay != DisplayMode.KEY && swipeDownHintsEnabled) swipeDownLabel else null
                                    val longPressConfig =
                                        KeysConfigHelper.getKeyGesture(key, isAsciiMode)?.longPress
                                    val longPressDisplay = (longPressConfig?.display ?: "key").takeIf {
                letterLongPressMenuAllowed(key, LocalKeyboardInputPreferences.current.letterLongPressMenu) } ?: "key"
                                    val longPressLabels = if (longPressDisplay == "bubble") {
                                        longPressConfig?.values?.map { it.label }
                                            ?.filter { it.isNotEmpty() }?.ifEmpty { null }
                                    } else null
                                    val longPressGestureMap = if (longPressDisplay == "bubble") {
                                        longPressConfig?.values?.associateBy { it.label }
                                    } else null

                                    val rawCommitValue = KeysConfigHelper.getKeyCommitValue(key, isAsciiMode)
                                    val commitValue = com.kingzcheung.xime.rime.MergedLetterInput.tapValue(
                key, rawCommitValue, LocalFourteenKeyLayout.current, isShifted)
                                    val displayText = KeysConfigHelper.getKeyDisplayLabel(key, isAsciiMode, isShifted)

                                    val onClick = remember(key, commitValue, onKeyPress) { { onKeyPress(commitValue) } }
                                    val onPress: (() -> Unit)? = remember(key, onKeyPressDown) { { onKeyPressDown?.invoke(key); Unit } }
                                    val onRelease: (() -> Unit)? = remember(key, onKeyRelease) { { onKeyRelease?.invoke(key); Unit } }
                                    val onSwipeDown = if (swipeDownAction != null && swipeDownAction != GestureAction.NONE && swipeDownLabel != null) {
                                        remember(key, onKeyPress, onGestureAction, onCommitText, swipeDownAction, swipeDownValue, swipeDownLabel) {
                                            val label = swipeDownLabel
                                            { _: String ->
                                                if (swipeDownAction == GestureAction.COMMIT) {
                                                    (onCommitText ?: onKeyPress)(swipeDownValue?.ifEmpty { label } ?: label)
                                                } else {
                                                    onGestureAction?.invoke(
                                                        swipeDownAction,
                                                        swipeDownValue?.ifEmpty { label } ?: label)
                                                }
                                                Unit
                                            }
                                        }
                                    } else null
                                    val onSwipeStateChange = remember(key) { { state: SwipeState, bounds: Rect -> processSwipeState(state, bounds) } }
                                    val onLongPressSelect: ((String) -> Unit)? = remember(key, longPressGestureMap, onGestureAction, onCommitText, onKeyPress) { { selectedLabel: String ->
                                        val gesture = longPressGestureMap?.get(selectedLabel)
                                        if (gesture != null && gesture.action != GestureAction.COMMIT) {
                                            onGestureAction?.invoke(
                                                gesture.action!!,
                                                gesture.value.ifEmpty { selectedLabel })
                                        } else {
                                            commitLongPressChoice(selectedLabel, onCommitText, onKeyPress)
                                        }
                                        Unit
                                    } }

                                    SwipeableKeyButton(
                                        layoutMode = KeysConfigHelper.getButtonLayout(isAsciiMode),
                                        followSymbolSwipeDirection = true,
                                        text = displayText,
                                        letterGroup = if (isShifted) key.uppercase() else key,
                                        onLetterSelection = onKeyPress,
                                        onClick = onClick,
                                        backgroundColor = keyBackgroundColor,
                                        textColor = keyTextColor,
                                        modifier = Modifier.weight(if (bottomWeighted) key.length.toFloat() +
                                            (if (key === bottomKeys.last()) fourteenMExtra else 0f) else 1f)
                                            .testTag("qwerty-key:$key"),
                                        swipeText = swipeUpText,
                                        symbolInputText = symbolInputValue(swipeUpCommitValue, swipeUpAction),
                                        swipeDownText = swipeDownBubbleText,
                                        swipeUpKeyLabel = swipeUpKeyLabel,
                                        swipeDownKeyLabel = if ((swipeDownDisplay == DisplayMode.KEY || swipeDownDisplay == DisplayMode.BOTH) && swipeDownHintsEnabled) swipeDownLabel else null,
                                        onSwipe = if (swipeUpCommitValue != null && swipeUpAction != GestureAction.NONE) { {
                    if (swipeUpAction == null || swipeUpAction == GestureAction.COMMIT) {
                        (onCommitText ?: onKeyPress)(swipeUpCommitValue)
                    } else {
                        onGestureAction?.invoke(swipeUpAction, swipeUpCommitValue)
                    }
                    Unit
                } } else null,
                                        onSwipeDown = onSwipeDown,
                                        onSwipeStateChange = onSwipeStateChange,
                                        onPress = onPress,
                                        onRelease = onRelease,
                                        onLongPressSelect = onLongPressSelect,
                                        longPressItems = longPressLabels,
                                        shadowEnabled = shadowEnabled,
                                        shadowElevation = shadowElevation,
                                        shadowShapeRadius = shadowShapeRadius,
                                    )
                                }
                            }
                            }

                            CompositionLocalProvider(LocalHoneycombEdge provides if (honeycomb) HoneycombEdge.END else null) {
                            SwipeableIconKeyButton(
                                icon = rememberVectorPainter(Icons.AutoMirrored.Filled.Backspace),
                                onClick = { onKeyPress("delete") },
                                backgroundColor = specialKeyBackgroundColor,
                                iconColor = specialKeyTextColor,
                                modifier = Modifier
                                    .weight(letterDeleteKeyWeight - fourteenMExtra)
                                    .fillMaxHeight().testTag("qwerty-delete-key").semantics { contentDescription = "删除" },
                                swipeText = "清空",
                                onSwipe = { onKeyPress("clear_composition") },
                                onLongClick = { onKeyPress("delete") },
                                onPress = { onKeyPressDown?.invoke("delete") },
                                onRelease = { onKeyRelease?.invoke("delete") },
                                followSymbolSwipeDirection = true,
                                upFeedback = KeyFlickFeedback.DELETE,
                                swipeUpLabel = "上滑清空",
                                downFeedback = KeyFlickFeedback.UNDO,
                                swipeDownLabel = "下滑撤回",
                                onSwipeUp = { onKeyPress("clear_all") },
                                onSwipeDown = { onKeyPress("undo_clear") },
                                onSwipeLeft = { suppressCursorMove.value = true; onKeyPress("clear_composition") },
                                onSwipeStateChange = { state, bounds ->
                                    processSwipeState(
                                        state,
                                        bounds
                                    )
                                },
                                shadowEnabled = shadowEnabled,
                                shadowElevation = shadowElevation,
                                shadowShapeRadius = shadowShapeRadius,
                            )
                            }
                        }
                    }

                    // 蜂窝布局的第四行字母（zxcvbnm），与第二行同宽对齐。
                    renderRows.getOrNull(3)?.let { row3 ->
                        Box(modifier = Modifier.weight(1f)) {
                            if (isVoiceMode && !isVoiceSticky) DummyKeyboardRow(
                                keys = row3, weightedLetters = true,
                                keyBackgroundColor = keyBackgroundColor.copy(alpha = 0.5f),
                                keyboardBackgroundColor = keyboardBackgroundColor,
                            ) else KeyboardRowWithConfig(
                                keys = row3,
                                onKeyPress = onKeyPress,
                                config = KeyboardRowConfig(
                                    keyBackgroundColor = keyBackgroundColor,
                                    keyTextColor = keyTextColor,
                                    keyboardBackgroundColor = keyboardBackgroundColor,
                                    shadowEnabled = shadowEnabled,
                                    shadowElevation = shadowElevation,
                                    shadowShapeRadius = shadowShapeRadius,
                                ),
                                isShifted = isShifted,
                                isAsciiMode = isAsciiMode,
                                onSwipeStateChange = { state, bounds -> processSwipeState(state, bounds) },
                                onKeyPressDown = onKeyPressDown,
                                onKeyRelease = onKeyRelease,
                                swipeDownHintsEnabled = effectiveSwipeDownHintsEnabled,
                                swipeUpHintsEnabled = swipeUpHintsEnabled,
                                onCommitText = onCommitText,
                                onGestureAction = onGestureAction,
                                configVersion = cfgVer,
                            )
                        }
                        Spacer(Modifier.height(honeycombOverhang))
                    }

                    // 底排保留共用权重及逗号/地球等宽；宽屏实体字母错位不移动底排。
                    val bottom = letterKeyboardBottomRow(keyRows, fourteenKey)
                    // 空格左侧逗号、右侧句号；只开逗号时沿用原配置的逗号键。
                    val bottomPrefs = LocalKeyboardInputPreferences.current
                    val commaOnLeft = bottomPrefs.qwertyCommaKey
                    val periodOnRight = bottomPrefs.qwertyPeriodKey
                    val punctuationFull = LocalKeyboardPunctuation.current?.full ?: !isAsciiMode
                    val (leftPunctuation, rightPunctuation) = spacePunctuationKeys(commaOnLeft, periodOnRight,
                        punctuationFull, english = isAsciiMode,
                        japanese = LocalKeyboardPunctuation.current?.japanese == true)
                    val punctuationCount = (if (commaOnLeft) 1 else 0) + (if (periodOnRight) 1 else 0)
                    val bottomSpaceWeight = bottom.space + bottom.punctuation * (1 - punctuationCount)
                    // 键面写什么就上屏什么：只有与当前宽度一致的全角标点走普通按键；
                    // 半角模式下滑的全角标点、中文状态的半角标点都原样上屏，避免被宽度设置转回去。
                    val commitPunctuation: (String) -> Unit = { text ->
                        if (isAsciiMode || (punctuationFull && text.any { it.code > 0x7E })) onKeyPress(text)
                        else (callbacks.onCommitExactText ?: onCommitText ?: onKeyPress)(text)
                    }
                    @Composable
                    fun RowScope.PunctuationKey(key: SpacePunctuationKey, tag: String) =
                        // 主副字符已按全角/半角写定并原样上屏；键面不能再转一次，否则角标与副字符的宽度相反。
                        CompositionLocalProvider(LocalKeyboardPunctuation provides null) {
                        SwipeableKeyButton(
                            layoutMode = KeysConfigHelper.getButtonLayout(isAsciiMode),
                            followSymbolSwipeDirection = true,
                            text = key.primary,
                            onClick = { commitPunctuation(key.primary) },
                            backgroundColor = keyBackgroundColor,
                            textColor = keyTextColor,
                            modifier = Modifier.weight(bottom.punctuation).testTag(tag),
                            swipeText = key.alternate.ifEmpty { null },
                            symbolInputText = key.alternate.ifEmpty { null },
                            onSwipe = if (key.alternate.isEmpty()) null else { _ -> commitPunctuation(key.alternate) },
                            onSwipeStateChange = { state, bounds -> processSwipeState(state, bounds) },
                            onPress = { onKeyPressDown?.invoke(key.primary) },
                            onRelease = { onKeyRelease?.invoke(key.primary) },
                            shadowEnabled = shadowEnabled,
                            shadowElevation = shadowElevation,
                            shadowShapeRadius = shadowShapeRadius,
                        )
                        }
                    // 第四行（控制行）
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    ) {
                        if (isVoiceMode && !isVoiceSticky) {
                            DummyKeyButton(
                                backgroundColor = specialKeyBackgroundColor.copy(alpha = 0.5f),
                                modifier = Modifier.weight(bottom.mode * 2)
                            )
                            if (commaOnLeft) DummyKeyButton(
                                backgroundColor = specialKeyBackgroundColor.copy(alpha = 0.5f),
                                modifier = Modifier.weight(bottom.punctuation)
                            )
                        } else {
                            for (slot in 1..2) {
                                KeyboardModeKey(
                                    slot = slot, onKeyPress = onKeyPress,
                                    backgroundColor = specialKeyBackgroundColor,
                                    textColor = specialKeyTextColor,
                                    modifier = Modifier.weight(bottom.mode),
                                    onKeyPressDown = onKeyPressDown,
                                    shadowEnabled = shadowEnabled, shadowElevation = shadowElevation,
                                    shadowShapeRadius = shadowShapeRadius,
                                )
                            }

                            if (leftPunctuation != null) PunctuationKey(leftPunctuation, "qwerty-punctuation-key")
                            else if (commaOnLeft) {
                            // 逗号 — 从配置读取 "'"
                            val k2KeyGesture = KeysConfigHelper.getKeyGesture("'", isAsciiMode)
                            val k2TapAction = k2KeyGesture?.tap?.action
                            val k2TapValue = k2KeyGesture?.tap?.value?.takeIf { it.isNotEmpty() }
                                ?: k2KeyGesture?.tap?.label?.takeIf { it.isNotEmpty() }
                                ?: if (isAsciiMode) "," else "，"
                            val k2TapLabel = k2KeyGesture?.tap?.label?.takeIf { it.isNotEmpty() } ?: k2TapValue
                            val k2SwipeUpRaw = k2KeyGesture?.swipeUp
                            val k2SwipeUpLabel = if (isAsciiMode)
                                (k2SwipeUpRaw?.value?.takeIf { it.isNotEmpty() } ?: "")
                                else (k2SwipeUpRaw?.label?.takeIf { it.isNotEmpty() } ?: k2SwipeUpRaw?.value?.takeIf { it.isNotEmpty() } ?: "")
                            val k2SwipeUpValue = k2SwipeUpRaw?.value?.takeIf { it.isNotEmpty() }
                                ?: k2SwipeUpRaw?.label?.takeIf { it.isNotEmpty() }
                            val k2SwipeUpDisplay = k2SwipeUpRaw?.display ?: DisplayMode.BOTH
                            val k2SwipeUpCommitValue = k2SwipeUpValue
                            val k2SwipeDownRaw = k2KeyGesture?.swipeDown
                            val k2SwipeDownLabel = k2SwipeDownRaw?.label?.takeIf { it.isNotEmpty() }
                            val k2SwipeDownAction = k2SwipeDownRaw?.action
                            val k2SwipeDownValue = k2SwipeDownRaw?.value
                            val k2SwipeDownDisplay = k2SwipeDownRaw?.display ?: DisplayMode.BOTH
                            val k2SwipeDownBubbleText = if (k2SwipeDownDisplay != DisplayMode.KEY) k2SwipeDownLabel else null
                            val k2LongPressConfig = k2KeyGesture?.longPress
                            val k2LongPressDisplay = k2LongPressConfig?.display ?: "key"
                            val k2LongPressLabels = if (k2LongPressDisplay == "bubble") {
                                k2LongPressConfig?.values?.map { it.label }?.filter { it.isNotEmpty() }
                                    ?.ifEmpty { null }
                            } else null
                            val k2LongPressGestureMap = if (k2LongPressDisplay == "bubble") {
                                k2LongPressConfig?.values?.associateBy { it.label }
                            } else null
                            val k2OnClick: () -> Unit = remember(k2TapAction, k2TapValue, onKeyPress, onGestureAction) {
                                {
                                    if (k2TapAction != null && k2TapAction != GestureAction.COMMIT) {
                                        onGestureAction?.invoke(k2TapAction, k2TapValue)
                                    } else {
                                        onKeyPress(k2TapValue)
                                    }
                                }
                            }
                            val k2OnSwipeDown: ((String) -> Unit)? = if (k2SwipeDownAction != null && k2SwipeDownLabel != null) {
                                remember(k2SwipeDownAction, k2SwipeDownValue, k2SwipeDownLabel, onKeyPress, onGestureAction, onCommitText) {
                                    val label = k2SwipeDownLabel
                                    { _: String ->
                                        if (k2SwipeDownAction == GestureAction.COMMIT) {
                                            (onCommitText ?: onKeyPress)(k2SwipeDownValue?.ifEmpty { label } ?: label)
                                        } else {
                                            onGestureAction?.invoke(k2SwipeDownAction, k2SwipeDownValue?.ifEmpty { label } ?: label)
                                        }
                                        Unit
                                    }
                                }
                            } else null
                            val k2OnLongPressSelect: ((String) -> Unit)? = remember(k2LongPressGestureMap, onGestureAction, onCommitText, onKeyPress) {
                                { selectedLabel: String ->
                                    val gesture = k2LongPressGestureMap?.get(selectedLabel)
                                    if (gesture != null && gesture.action != GestureAction.COMMIT) {
                                        onGestureAction?.invoke(gesture.action!!, gesture.value.ifEmpty { selectedLabel })
                                    } else {
                                        // 气泡里写的就是要上屏的写法（如全角时选半角 , .），不再随全角/半角转换。
                                        (callbacks.onCommitExactText ?: onCommitText ?: onKeyPress)(selectedLabel)
                                    }
                                    Unit
                                }
                            }
                            if (k2TapAction == GestureAction.TOGGLE_ASCII) {
                                LanguageKeyButton(
                                    icon = rememberVectorPainter(Icons.Default.Language),
                                    onClick = k2OnClick,
                                    backgroundColor = specialKeyBackgroundColor,
                                    iconColor = specialKeyTextColor,
                                    modifier = Modifier.weight(bottom.punctuation).testTag("qwerty-punctuation-key"),
                                    onPress = { onKeyPressDown?.invoke(k2TapValue) },
                                    onRelease = { onKeyRelease?.invoke(k2TapValue) },
                                    swipeText = k2SwipeUpLabel,
                                    swipeDownText = k2SwipeDownBubbleText,
                                    swipeDownKeyLabel = if (k2SwipeDownDisplay != DisplayMode.BUBBLE) k2SwipeDownLabel else null,
                                    onSwipe = if (k2SwipeUpCommitValue != null) { { onKeyPress(k2SwipeUpCommitValue) } } else null,
                                    onSwipeDown = k2OnSwipeDown,
                                    onSwipeStateChange = { state, bounds -> processSwipeState(state, bounds) },
                                    shadowEnabled = shadowEnabled,
                                    shadowElevation = shadowElevation,
                                    shadowShapeRadius = shadowShapeRadius,
                                )
                            } else {
                                SwipeableKeyButton(
                                    layoutMode = KeysConfigHelper.getButtonLayout(isAsciiMode),
                                    followSymbolSwipeDirection = true,
                                    text = k2TapLabel,
                                    onClick = k2OnClick,
                                    backgroundColor = keyBackgroundColor,
                                    textColor = keyTextColor,
                                    modifier = Modifier.weight(bottom.punctuation).testTag("qwerty-punctuation-key"),
                                    swipeText = k2SwipeUpLabel,
                                    symbolInputText = symbolInputValue(k2SwipeUpCommitValue, k2SwipeUpRaw?.action),
                                    swipeDownText = k2SwipeDownBubbleText,
                                    swipeDownKeyLabel = if ((k2SwipeDownDisplay == DisplayMode.KEY || k2SwipeDownDisplay == DisplayMode.BOTH)) k2SwipeDownLabel else null,
                                    onSwipe = if (k2SwipeUpCommitValue != null) { { onKeyPress(k2SwipeUpCommitValue) } } else null,
                                    onSwipeDown = k2OnSwipeDown,
                                    onSwipeStateChange = { state, bounds ->
                                        processSwipeState(state, bounds)
                                    },
                                    onPress = { onKeyPressDown?.invoke(k2TapValue) },
                                    onRelease = { onKeyRelease?.invoke(k2TapValue) },
                                    onLongPressSelect = k2OnLongPressSelect,
                                    longPressItems = k2LongPressLabels,
                                    shadowEnabled = shadowEnabled,
                                    shadowElevation = shadowElevation,
                                    shadowShapeRadius = shadowShapeRadius,
                                )
                            }
                            }
                        }

                        // 分体时空格在中线处分成左右两块，两块都是完整的空格键。
                        val spaceParts = if (splitKeyboard) SplitKeyboardGeometry.spaceSplit(bottom, bottomSpaceWeight,
                            bottom.mode * 2f + if (commaOnLeft) bottom.punctuation else 0f).toList()
                            else listOf(bottomSpaceWeight)
                        spaceParts.forEachIndexed { part, spaceWeight ->
                            if (part > 0) SplitGapSpacer(3, splitGapDp)
                            SpaceKey(
                                schemaName = if (part == 0) schemaName else "",
                                isAsciiMode = isAsciiMode && part == 0,
                                isSttEnabled = isSttEnabled,
                                isVoiceMode = isVoiceMode,
                                voiceSticky = isVoiceSticky,
                                keyBackgroundColor = keyBackgroundColor,
                                keyTextColor = keyTextColor,
                                shadowEnabled = shadowEnabled,
                                shadowElevation = shadowElevation,
                                shadowShapeRadius = shadowShapeRadius,
                                modifier = Modifier.weight(spaceWeight),
                                onKeyPress = onKeyPress,
                                onKeyPressDown = onKeyPressDown,
                                onKeyRelease = onKeyRelease,
                                onVoiceModeChange = onVoiceModeChange,
                            )
                        }

                        if (rightPunctuation != null) {
                            if (isVoiceMode && !isVoiceSticky) DummyKeyButton(
                                backgroundColor = keyBackgroundColor.copy(alpha = 0.5f),
                                modifier = Modifier.weight(bottom.punctuation)
                            ) else PunctuationKey(rightPunctuation, "qwerty-period-key")
                        }

                        // 中/英切换 + 回车（硬编码 + 配置驱动）
                        if (isVoiceMode && !isVoiceSticky) {
                            DummyKeyButton(
                                backgroundColor = keyBackgroundColor.copy(alpha = 0.5f),
                                modifier = Modifier.weight(bottom.language)
                            )
                            DummyKeyButton(
                                backgroundColor = specialKeyBackgroundColor.copy(alpha = 0.5f),
                                modifier = Modifier.weight(bottom.enter)
                            )
                        } else {
                            // earth — 从配置读取
                            val k4KeyGesture = KeysConfigHelper.getKeyGesture("earth", isAsciiMode)
                            val k4TapAction = k4KeyGesture?.tap?.action
                            // 无 tap 手势配置时回退地球键默认语义：地球图标 + 中英切换。
                            // 否则键面空白（label=""/icon=null）且点击发出空键值——空键值在
                            // 中文模式会触发 ImeKeyRouter lowercase()[0] 越界崩溃（2026-09-14 实证）。
                            val isEarthDefaultTap = k4KeyGesture?.tap == null
                            val k4TapValue = k4KeyGesture?.tap?.value?.takeIf { it.isNotEmpty() } ?: "ime_switch"
                            val k4TapLabel = k4KeyGesture?.tap?.label?.takeIf { it.isNotEmpty() } ?: "中"
                            val k4Icon: Painter? = k4KeyGesture?.tap?.icon?.takeIf { it.isNotEmpty() }?.let { iconName ->
                                val iv = when (iconName) {
                                    "language", "globe" -> Icons.Default.Language
                                    else -> null
                                }
                                iv?.let { rememberVectorPainter(it) }
                            } ?: if (isEarthDefaultTap) rememberVectorPainter(Icons.Default.Language) else null
                            val k4SwipeUpRaw = k4KeyGesture?.swipeUp
                            val k4SwipeUpLabel = if (isAsciiMode)
                                (k4SwipeUpRaw?.value?.takeIf { it.isNotEmpty() } ?: "")
                                else (k4SwipeUpRaw?.label?.takeIf { it.isNotEmpty() } ?: k4SwipeUpRaw?.value?.takeIf { it.isNotEmpty() } ?: "")
                            val k4SwipeUpValue = k4SwipeUpRaw?.value?.takeIf { it.isNotEmpty() }
                                ?: k4SwipeUpRaw?.label?.takeIf { it.isNotEmpty() }
                            val k4SwipeUpAction = k4SwipeUpRaw?.action
                            val k4SwipeDownRaw = k4KeyGesture?.swipeDown
                            val k4SwipeDownLabel = k4SwipeDownRaw?.label?.takeIf { it.isNotEmpty() }
                            val k4SwipeDownAction = k4SwipeDownRaw?.action
                            val k4SwipeDownValue = k4SwipeDownRaw?.value
                            val k4SwipeDownDisplay = k4SwipeDownRaw?.display ?: DisplayMode.BOTH
                            val k4SwipeDownBubbleText = if (k4SwipeDownDisplay != DisplayMode.KEY) k4SwipeDownLabel else null
                            val k4LongPressConfig = k4KeyGesture?.longPress
                            val k4LongPressDisplay = k4LongPressConfig?.display ?: "key"
                            val k4LongPressLabels = if (k4LongPressDisplay == "bubble") {
                                k4LongPressConfig?.values?.map { it.label }?.filter { it.isNotEmpty() }
                                    ?.ifEmpty { null }
                            } else null
                            val k4LongPressGestureMap = if (k4LongPressDisplay == "bubble") {
                                k4LongPressConfig?.values?.associateBy { it.label }
                            } else null
                            val k4OnClick: () -> Unit = remember(k4TapAction, k4TapValue, onKeyPress, onGestureAction) {
                                {
                                    if (k4TapAction != null && k4TapAction != GestureAction.COMMIT) {
                                        onGestureAction?.invoke(k4TapAction, k4TapValue)
                                    } else {
                                        onKeyPress(k4TapValue)
                                    }
                                }
                            }
                            val k4OnSwipe: ((String) -> Unit)? = if (k4SwipeUpValue != null && k4SwipeUpAction != GestureAction.NONE) {
                                remember(k4SwipeUpValue, onKeyPress) { { onKeyPress(k4SwipeUpValue) } }
                            } else null
                            val k4OnSwipeDown: ((String) -> Unit)? = if (k4SwipeDownAction != null && k4SwipeDownLabel != null) {
                                remember(k4SwipeDownAction, k4SwipeDownValue, k4SwipeDownLabel, onKeyPress, onGestureAction, onCommitText) {
                                    val label = k4SwipeDownLabel
                                    { _: String ->
                                        if (k4SwipeDownAction == GestureAction.COMMIT) {
                                            (onCommitText ?: onKeyPress)(k4SwipeDownValue?.ifEmpty { label } ?: label)
                                        } else {
                                            onGestureAction.invoke(k4SwipeDownAction, k4SwipeDownValue?.ifEmpty { label } ?: label)
                                        }
                                        Unit
                                    }
                                }
                            } else null
                            val k4OnLongPressSelect: ((String) -> Unit)? = remember(k4LongPressGestureMap, onGestureAction, onCommitText, onKeyPress) {
                                { selectedLabel: String ->
                                    val gesture = k4LongPressGestureMap?.get(selectedLabel)
                                    if (gesture != null && gesture.action != GestureAction.COMMIT) {
                                        onGestureAction.invoke(gesture.action!!, gesture.value.ifEmpty { selectedLabel })
                                    } else {
                                        commitLongPressChoice(selectedLabel, onCommitText, onKeyPress)
                                    }
                                    Unit
                                }
                            }
                            if ((k4TapAction == GestureAction.TOGGLE_ASCII && k4Icon != null || isEarthDefaultTap) && k4LongPressLabels == null) {
                                LanguageKeyButton(
                                    icon = k4Icon ?: rememberVectorPainter(Icons.Default.Language),
                                    onClick = k4OnClick,
                                    backgroundColor = specialKeyBackgroundColor,
                                    iconColor = specialKeyTextColor,
                                    modifier = Modifier.weight(bottom.language),
                                    onPress = { onKeyPressDown?.invoke(k4TapValue) },
                                    onRelease = { onKeyRelease?.invoke(k4TapValue) },
                                    swipeText = k4SwipeUpLabel.takeIf { it.isNotEmpty() },
                                    swipeDownText = k4SwipeDownBubbleText,
                                    swipeDownKeyLabel = if (k4SwipeDownDisplay != DisplayMode.BUBBLE) k4SwipeDownLabel else null,
                                    onSwipe = k4OnSwipe,
                                    onSwipeDown = k4OnSwipeDown,
                                    onSwipeStateChange = { state, bounds -> processSwipeState(state, bounds) },
                                    shadowEnabled = shadowEnabled,
                                    shadowElevation = shadowElevation,
                                    shadowShapeRadius = shadowShapeRadius,
                                )
                            } else {
                                LanguageKeyButton(
                                    layoutMode = KeysConfigHelper.getButtonLayout(isAsciiMode),
                                    text = k4TapLabel,
                                    onClick = k4OnClick,
                                    backgroundColor = specialKeyBackgroundColor,
                                    textColor = specialKeyTextColor,
                                    modifier = Modifier.weight(bottom.language),
                                    icon = k4Icon,
                                    swipeText = k4SwipeUpLabel.takeIf { it.isNotEmpty() },
                                    swipeDownText = k4SwipeDownBubbleText,
                                    swipeDownKeyLabel = if ((k4SwipeDownDisplay == DisplayMode.KEY || k4SwipeDownDisplay == DisplayMode.BOTH)) k4SwipeDownLabel else null,
                                    onSwipe = k4OnSwipe,
                                    onSwipeDown = k4OnSwipeDown,
                                    onSwipeStateChange = { state, bounds ->
                                        processSwipeState(state, bounds)
                                    },
                                    onPress = { onKeyPressDown?.invoke(k4TapValue) },
                                    onRelease = { onKeyRelease?.invoke(k4TapValue) },
                                    onLongPressSelect = k4OnLongPressSelect,
                                    longPressItems = k4LongPressLabels,
                                    shadowEnabled = shadowEnabled,
                                    shadowElevation = shadowElevation,
                                    shadowShapeRadius = shadowShapeRadius,
                                )
                            }

                            // 回车 — 硬编码
                            ActionKeyButton(
                                text = enterKeyText,
                                onClick = { onKeyPress("enter") },
                                backgroundColor = specialKeyBackgroundColor,
                                textColor = specialKeyTextColor,
                                modifier = Modifier.weight(bottom.enter).testTag("qwerty-enter-key"),
                                onPress = { onKeyPressDown?.invoke("enter") },
                                onRelease = { onKeyRelease?.invoke("enter") },
                                shadowEnabled = shadowEnabled,
                                shadowElevation = shadowElevation,
                                shadowShapeRadius = shadowShapeRadius,
                            )
                        }
                    }
                }

            }

        ShiftSlideLine(shiftSlideTargets, { keyboardBounds.topLeft },
            KeyboardThemes.getAccentColor(uiState.themeId, uiState.isDarkTheme), Modifier.matchParentSize())

        // 语音模式中央麦克风图标
        if (isVoiceMode && !isVoiceSticky) {
            Box(
                modifier = Modifier.matchParentSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "语音输入",
                    tint = keyTextColor.copy(alpha = 0.6f),
                    modifier = Modifier.size(64.dp)
                )
            }
        }
    }

    }
    }
    }
    }
}

@Composable
private fun DummyKeyboardRow(
    keys: List<String>,
    weightedLetters: Boolean,
    keyBackgroundColor: Color,
    keyboardBackgroundColor: Color = Color.Transparent,
    modifier: Modifier = Modifier,
    splitAt: Int = -1,
    splitGap: Dp = 0.dp,
    splitRow: Int = 0,
) {
    Row(
        modifier = modifier
            .fillMaxWidth(),
    ) {
        keys.forEachIndexed { index, key ->
            if (index == splitAt) SplitGapSpacer(splitRow, splitGap)
            DummyKeyButton(
                backgroundColor = keyBackgroundColor,
                modifier = Modifier.weight(if (weightedLetters) key.length.toFloat() else 1f)
            )
        }
        if (splitAt == keys.size) SplitGapSpacer(splitRow, splitGap)
    }
}

@Composable
private fun DummyBottomRow(
    keys: List<String>,
    weightedLetters: Boolean,
    keyBackgroundColor: Color,
    specialKeyBackgroundColor: Color,
    keyboardBackgroundColor: Color = Color.Transparent,
    sideKeyWeight: Float = 1.2f,
    endKeyWeight: Float = sideKeyWeight,
    letterWeight: Float = 7f,
    splitAt: Int = -1,
    splitGap: Dp = 0.dp,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth(),
    ) {
        DummyKeyButton(
            backgroundColor = specialKeyBackgroundColor,
            modifier = Modifier.weight(sideKeyWeight)
        )
        val segments = if (splitAt < 0) listOf(keys) else listOf(keys.take(splitAt), keys.drop(splitAt))
        segments.forEachIndexed { segment, segmentKeys ->
            if (segment > 0) SplitGapSpacer(2, splitGap)
            Row(
                modifier = Modifier
                    .weight(if (splitAt < 0) letterWeight else segmentKeys
                        .sumOf { if (weightedLetters) it.length else 1 }.toFloat().coerceAtLeast(0.001f))
                    .fillMaxHeight(),
            ) {
                segmentKeys.forEach { key ->
                    DummyKeyButton(
                        backgroundColor = keyBackgroundColor,
                        modifier = Modifier.weight(if (weightedLetters) key.length.toFloat() else 1f)
                    )
                }
            }
        }
        DummyKeyButton(
            backgroundColor = specialKeyBackgroundColor,
            modifier = Modifier.weight(endKeyWeight)
        )
    }
}

@Composable
private fun DummyKeyButton(
    backgroundColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(LocalKeyCornerRadius.current))
            .background(frostedKeyColor(backgroundColor, Color.White,
                LocalKeyboardInputPreferences.current.frostedGlass, opacityScale = 0.5f))
    )
}

@Composable
fun KeyboardRowWithConfig(
    keys: List<String>,
    onKeyPress: (String) -> Unit,
    config: KeyboardRowConfig,
    isShifted: Boolean,
    isAsciiMode: Boolean = false,
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier,
    onSwipeStateChange: ((SwipeState, Rect) -> Unit)? = null,
    onKeyPressDown: ((String) -> Unit)? = null,
    onKeyRelease: ((String) -> Unit)? = null,
    swipeDownHintsEnabled: Boolean = true,
    swipeUpHintsEnabled: Boolean = true,
    onCommitText: ((String) -> Unit)? = null,
    onGestureAction: ((GestureAction, String) -> Unit)? = null,
    configVersion: Int = 0,
    edgeInset: Dp = 0.dp,
    edgeStartInset: Dp = edgeInset,
    edgeEndInset: Dp = edgeInset,
    /** 最后一个键画到行尾（14 键的 l 与 p 右边对齐）。 */
    extendLastKeyToEdge: Boolean = false,
    /** 分体：在第 [splitAt] 个键之前插入宽 [splitGap] 的中缝；-1 不分体。 */
    splitAt: Int = -1,
    splitGap: Dp = 0.dp,
    splitRow: Int = 0,
) {
    val customLayout = LocalCustomLayout.current
    // 14 键合并键与自定义布局一样按字母数占位，和 26 键同一网格对齐。
    val weighted = customLayout != null || LocalFourteenKeyLayout.current
    BoxWithConstraints(modifier.fillMaxWidth()) {
    val totalWeight = keys.sumOf { if (weighted) it.length else 1 }.coerceAtLeast(1)
    val startInset = edgeStartInset.coerceAtMost(maxWidth / 4)
    val endInset = edgeEndInset.coerceAtMost(maxWidth / 4)
    val gap = if (splitAt in 0..keys.size) splitGap.coerceAtMost(maxWidth) else 0.dp
    val unitWidth = (maxWidth - startInset - endInset - gap) / totalWeight
    Row(
        modifier = Modifier.fillMaxWidth(),
    ) {
        keys.forEachIndexed { index, key ->
            if (index == splitAt) SplitGapSpacer(splitRow, gap)
            val rawSwipeUpLabel = KeysConfigHelper.getSwipeUpLabel(key, isAsciiMode)
            val swipeUpText = if (swipeUpHintsEnabled) rawSwipeUpLabel else null
            val swipeUpAction = KeysConfigHelper.getSwipeUpAction(key, isAsciiMode)
            val swipeUpDisplay = KeysConfigHelper.getSwipeUpDisplay(key, isAsciiMode)
            val swipeUpKeyLabel =
                if (swipeUpDisplay != DisplayMode.BUBBLE && swipeUpHintsEnabled) swipeUpText else ""
            val swipeUpCommitValue = KeysConfigHelper.getSwipeUpCommitValue(key, isAsciiMode)
            val swipeDownRaw = KeysConfigHelper.getKeyGesture(key, isAsciiMode)?.swipeDown
            val swipeDownLabel = swipeDownRaw?.label?.takeIf { it.isNotEmpty() }
            val swipeDownAction = swipeDownRaw?.action
            val swipeDownValue = swipeDownRaw?.value
            val swipeDownDisplay = swipeDownRaw?.display ?: DisplayMode.BOTH
            val swipeDownBubbleText =
                if (swipeDownDisplay != DisplayMode.KEY && swipeDownHintsEnabled) swipeDownLabel else null

            // 长按选项
            val longPressConfig = KeysConfigHelper.getKeyGesture(key, isAsciiMode)?.longPress
            val longPressDisplay = (longPressConfig?.display ?: "key").takeIf {
                letterLongPressMenuAllowed(key, LocalKeyboardInputPreferences.current.letterLongPressMenu) } ?: "key"
            val longPressLabels = if (longPressDisplay == "bubble") {
                longPressConfig?.values?.map { it.label }?.filter { it.isNotEmpty() }
                    ?.ifEmpty { null }
            } else null
            val longPressGestureMap = if (longPressDisplay == "bubble") {
                longPressConfig?.values?.associateBy { it.label }
            } else null

            // 键帽显示文本
            val rawCommitValue = KeysConfigHelper.getKeyCommitValue(key, isAsciiMode)
            val commitValue = com.kingzcheung.xime.rime.MergedLetterInput.tapValue(
                key, rawCommitValue, LocalFourteenKeyLayout.current, isShifted)
            val displayText = KeysConfigHelper.getKeyDisplayLabel(key, isAsciiMode, isShifted)

            val onClick = remember(key, commitValue, onKeyPress) { { onKeyPress(commitValue) } }
            val onPress: (() -> Unit)? = remember(key, onKeyPressDown) { { onKeyPressDown?.invoke(key); Unit } }
            val onRelease: (() -> Unit)? = remember(key, onKeyRelease) { { onKeyRelease?.invoke(key); Unit } }
            val onSwipeDown: ((String) -> Unit)? = if (swipeDownAction != null && swipeDownAction != GestureAction.NONE && swipeDownLabel != null) {
                remember(key, onKeyPress, onGestureAction, onCommitText, swipeDownAction, swipeDownValue, swipeDownLabel) {
                    val label = swipeDownLabel
                    { _: String ->
                        if (swipeDownAction == GestureAction.COMMIT) {
                            (onCommitText ?: onKeyPress)(swipeDownValue?.ifEmpty { label } ?: label)
                        } else {
                            onGestureAction?.invoke(
                                swipeDownAction,
                                swipeDownValue?.ifEmpty { label } ?: label)
                        }
                        Unit
                    }
                }
            } else null
            val onLongPressSelect: ((String) -> Unit)? = remember(key, longPressGestureMap, onGestureAction, onCommitText, onKeyPress) { { selectedLabel: String ->
                val gesture = longPressGestureMap?.get(selectedLabel)
                if (gesture != null && gesture.action != GestureAction.COMMIT) {
                    onGestureAction?.invoke(
                        gesture.action!!,
                        gesture.value.ifEmpty { selectedLabel })
                } else {
                    commitLongPressChoice(selectedLabel, onCommitText, onKeyPress)
                }
                Unit
            } }

            SwipeableKeyButton(
                layoutMode = KeysConfigHelper.getButtonLayout(isAsciiMode),
                followSymbolSwipeDirection = true,
                text = displayText,
                letterGroup = if (isShifted) key.uppercase() else key,
                onLetterSelection = onKeyPress,
                onClick = onClick,
                backgroundColor = config.keyBackgroundColor,
                textColor = config.keyTextColor,
                modifier = Modifier.weight((unitWidth * (if (weighted) key.length else 1) +
                    (if (index == 0) startInset else 0.dp) + (if (index == keys.lastIndex) endInset else 0.dp)).value.coerceAtLeast(1f))
                    .testTag("qwerty-key:$key"),
                edgeContentPadding = PaddingValues(
                    start = if (index == 0) startInset else 0.dp,
                    end = if (index == keys.lastIndex && !extendLastKeyToEdge) endInset else 0.dp,
                ),
                swipeText = swipeUpText,
                symbolInputText = symbolInputValue(swipeUpCommitValue, swipeUpAction),
                swipeDownText = swipeDownBubbleText,
                swipeUpKeyLabel = swipeUpKeyLabel,
                swipeDownKeyLabel = if ((swipeDownDisplay == DisplayMode.KEY || swipeDownDisplay == DisplayMode.BOTH) && swipeDownHintsEnabled) swipeDownLabel else null,
                onSwipe = if (swipeUpCommitValue != null && swipeUpAction != GestureAction.NONE) { {
                    if (swipeUpAction == null || swipeUpAction == GestureAction.COMMIT) {
                        (onCommitText ?: onKeyPress)(swipeUpCommitValue)
                    } else {
                        onGestureAction?.invoke(swipeUpAction, swipeUpCommitValue)
                    }
                    Unit
                } } else null,
                onSwipeDown = onSwipeDown,
                onSwipeStateChange = onSwipeStateChange,
                onPress = onPress,
                onRelease = onRelease,
                onLongPressSelect = onLongPressSelect,
                longPressItems = longPressLabels,
                fontSize = config.fontSize,
                swipeFontSize = config.swipeFontSize,
                shadowEnabled = config.shadowEnabled,
                shadowElevation = config.shadowElevation,
                shadowShapeRadius = config.shadowShapeRadius,
            )
        }
        if (splitAt == keys.size) SplitGapSpacer(splitRow, gap)
    }
    }
}

@Composable
internal fun ShiftCapsKeyButton(
    viewModel: KeyboardViewModel,
    shiftMode: ShiftMode,
    onKeyPress: (String) -> Unit,
    onKeyPressDown: ((String) -> Unit)?,
    backgroundColor: Color,
    iconColor: Color,
    accentColor: Color,
    panelColor: Color,
    modifier: Modifier = Modifier,
    shadowEnabled: Boolean = true,
    shadowElevation: Dp = 1.dp,
    shadowShapeRadius: Dp = 8.dp,
) {
    var isPressed by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val targets = LocalShiftSlideTargets.current
    val suppressCursorMove = LocalSuppressCursorMove.current
    val currentPress by rememberUpdatedState(onKeyPressDown)
    val currentKey by rememberUpdatedState(onKeyPress)
    var bounds by remember { mutableStateOf(Rect.Zero) }

    val frostedGlass = LocalKeyboardInputPreferences.current.frostedGlass
    val shadowModifier = remember(shadowEnabled, shadowElevation, shadowShapeRadius, density, backgroundColor, frostedGlass.enabled) {
        if (shadowEnabled && !frostedGlass.enabled) {
            val offsetPx = with(density) { shadowElevation.toPx() }
            val cornerPx = with(density) { shadowShapeRadius.toPx() }
            val color = crispShadowColor(backgroundColor)
            Modifier.drawBehind {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(0f, offsetPx),
                    size = size,
                    cornerRadius = CornerRadius(cornerPx)
                )
            }
        } else Modifier
    }
    val keyCornerRadius = LocalKeyCornerRadius.current
    val honeycombEdge = LocalHoneycombEdge.current
    val keyClipShape = remember(keyCornerRadius, honeycombEdge) {
        honeycombEdge?.let(::HalfHexagonShape) ?: RoundedCornerShape(keyCornerRadius)
    }

    fun darkenColor(color: Color, factor: Float = 0.15f): Color {
        return Color(
            red = (color.red * (1 - factor)).coerceIn(0f, 1f),
            green = (color.green * (1 - factor)).coerceIn(0f, 1f),
            blue = (color.blue * (1 - factor)).coerceIn(0f, 1f),
            alpha = color.alpha)
    }

    val colors = shiftKeyColors(backgroundColor, iconColor, accentColor, panelColor,
        frostedGlass, shiftMode, isPressed,
        legacyStateColor = if (isPressed) darkenColor(backgroundColor, 0.1f)
        else if (shiftMode == ShiftMode.CAPS) darkenColor(backgroundColor, 0.2f)
        else if (shiftMode == ShiftMode.SINGLE) darkenColor(backgroundColor, 0.1f)
        else backgroundColor,
        opacityScale = glassFunctionKeyScale(backgroundColor))

    Box(
        modifier = modifier
            .fillMaxHeight()
            .testTag("shift-key")
            .onGloballyPositioned { bounds = it.boundsInRoot() }
            .pointerInput(viewModel, targets) {
                var lastTap = Long.MIN_VALUE
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    isPressed = true
                    suppressCursorMove.value = true
                    currentPress?.invoke("shift")
                    // Typing consumed the previous one-shot Shift; it cannot start a double tap.
                    if (viewModel.shiftMode.value != ShiftMode.SINGLE) lastTap = Long.MIN_VALUE
                    viewModel.beginShiftHold()
                    var released = false
                    var dragged = false
                    var upTime = down.uptimeMillis
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (change.isConsumed) break
                            val pointer = bounds.topLeft + change.position
                            dragged = dragged || !bounds.contains(pointer) ||
                                (change.position - down.position).getDistance() > viewConfiguration.touchSlop
                            val target = if (dragged) targets?.move(bounds, pointer) else null
                            change.consume()
                            if (!change.pressed) {
                                released = true
                                upTime = change.uptimeMillis
                                if (dragged) target?.commit?.invoke()
                                break
                            }
                        }
                    } finally {
                        val used = viewModel.endShiftHold()
                        targets?.finish()
                        isPressed = false
                        suppressCursorMove.value = false
                        if (released && !dragged && !used && upTime - down.uptimeMillis < 350L) {
                            if (lastTap != Long.MIN_VALUE && down.uptimeMillis - lastTap in 0..viewConfiguration.doubleTapTimeoutMillis) {
                                currentKey("shift_caps")
                                lastTap = Long.MIN_VALUE
                            } else {
                                currentKey("shift_single")
                                lastTap = upTime
                            }
                        } else lastTap = Long.MIN_VALUE
                    }
                }
            }
            .padding(scaledKeyVisualPadding())
            .then(if (honeycombEdge != null) Modifier.honeycombCap() else shadowModifier)
            .clip(keyClipShape)
            .background(colors.background).visualMaterial(VisualStyles.current, keyCornerRadius, com.kingzcheung.xime.ui.theme.MaterialLevel.RAISED),
        contentAlignment = Alignment.Center
    ) {
        // 空心箭头＝小写，实心＝下一个字母大写，实心加底横线＝大写锁定。
        Icon(
            imageVector = ShiftKeyIcons.forMode(shiftMode),
            contentDescription = ShiftKeyIcons.description(shiftMode),
            tint = colors.foreground,
            modifier = Modifier.size(22.dp)
        )
    }
}

/**
 * 横屏紧凑版按键 — 主字符和上滑字符垂直堆叠居中
 */
@Composable
fun SwipeableKeyButtonLandscape(
    text: String,
    onClick: () -> Unit,
    letterGroup: String? = null,
    onLetterSelection: ((String) -> Unit)? = null,
    backgroundColor: Color,
    textColor: Color,
    modifier: Modifier = Modifier,
    swipeText: String? = null,
    symbolInputText: String? = null,
    swipeDownText: String? = null,
    swipeUpKeyLabel: String? = null,
    swipeDownKeyLabel: String? = null,
    onSwipe: ((String) -> Unit)? = null,
    onSwipeDown: ((String) -> Unit)? = null,
    onPress: (() -> Unit)? = null,
    onRelease: (() -> Unit)? = null,
    onLongPressSelect: ((String) -> Unit)? = null,
    longPressItems: List<String>? = null,
    fontSize: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified,
    swipeFontSize: androidx.compose.ui.unit.TextUnit = 10.sp,
    onSwipeStateChange: ((SwipeState, Rect) -> Unit)? = null,
    shadowEnabled: Boolean = true,
    shadowElevation: Dp = 1.dp,
    shadowShapeRadius: Dp = 8.dp,
) {

    SwipeableKeyButton(text = text, onClick = onClick, letterGroup = letterGroup, onLetterSelection = onLetterSelection, backgroundColor = backgroundColor,
        followSymbolSwipeDirection = true,
        textColor = textColor, modifier = modifier, swipeText = swipeText, swipeDownText = swipeDownText,
        symbolInputText = symbolInputText,
        swipeUpKeyLabel = swipeUpKeyLabel, swipeDownKeyLabel = swipeDownKeyLabel,
        onSwipe = onSwipe, onSwipeDown = onSwipeDown, onPress = onPress, onRelease = onRelease,
        onLongPressSelect = onLongPressSelect, longPressItems = longPressItems,
        fontSize = if (fontSize == TextUnit.Unspecified) 14.sp else fontSize, swipeFontSize = swipeFontSize,
        onSwipeStateChange = onSwipeStateChange, shadowEnabled = shadowEnabled,
        shadowElevation = shadowElevation, shadowShapeRadius = shadowShapeRadius)
}

@Composable
fun CompactKeyboardRowWithConfig(
    keys: List<String>,
    onKeyPress: (String) -> Unit,
    config: KeyboardRowConfig,
    isShifted: Boolean,
    isAsciiMode: Boolean = false,
    modifier: Modifier = Modifier,
    onKeyPressDown: ((String) -> Unit)? = null,
    onKeyRelease: ((String) -> Unit)? = null,
    swipeDownHintsEnabled: Boolean = true,
    swipeUpHintsEnabled: Boolean = true,
    onCommitText: ((String) -> Unit)? = null,
    onGestureAction: ((GestureAction, String) -> Unit)? = null,
    onSwipeStateChange: ((SwipeState, Rect) -> Unit)? = null,
    configVersion: Int = 0,
) {
    Row(
        modifier = modifier
            .fillMaxSize(),
    ) {
        keys.forEach { key ->
            val rawSwipeUpLabel = KeysConfigHelper.getSwipeUpLabel(key, isAsciiMode)
            val swipeUpText = if (swipeUpHintsEnabled) rawSwipeUpLabel else null
            val swipeUpAction = KeysConfigHelper.getSwipeUpAction(key, isAsciiMode)
            val swipeUpDisplay = KeysConfigHelper.getSwipeUpDisplay(key, isAsciiMode)
            val swipeUpKeyLabel =
                if (swipeUpDisplay != DisplayMode.BUBBLE && swipeUpHintsEnabled) swipeUpText else ""
            val swipeUpCommitValue = KeysConfigHelper.getSwipeUpCommitValue(key, isAsciiMode)
            val swipeDownRaw = KeysConfigHelper.getKeyGesture(key, isAsciiMode)?.swipeDown
            val swipeDownLabel = swipeDownRaw?.label?.takeIf { it.isNotEmpty() }
            val swipeDownAction = swipeDownRaw?.action
            val swipeDownValue = swipeDownRaw?.value
            val swipeDownDisplay = swipeDownRaw?.display ?: DisplayMode.BOTH
            val swipeDownBubbleText =
                if (swipeDownDisplay != DisplayMode.KEY && swipeDownHintsEnabled) swipeDownLabel else null
            val swipeDownKeyLabel =
                if ((swipeDownDisplay == DisplayMode.KEY || swipeDownDisplay == DisplayMode.BOTH) && swipeDownHintsEnabled) swipeDownLabel else null

            val longPressConfig = KeysConfigHelper.getKeyGesture(key, isAsciiMode)?.longPress
            val longPressDisplay = (longPressConfig?.display ?: "key").takeIf {
                letterLongPressMenuAllowed(key, LocalKeyboardInputPreferences.current.letterLongPressMenu) } ?: "key"
            val longPressLabels = if (longPressDisplay == "bubble") {
                longPressConfig?.values?.map { it.label }?.filter { it.isNotEmpty() }
                    ?.ifEmpty { null }
            } else null
            val longPressGestureMap = if (longPressDisplay == "bubble") {
                longPressConfig?.values?.associateBy { it.label }
            } else null

            val rawCommitValue = KeysConfigHelper.getKeyCommitValue(key, isAsciiMode)
            val commitValue = com.kingzcheung.xime.rime.MergedLetterInput.tapValue(
                key, rawCommitValue, LocalFourteenKeyLayout.current, isShifted)
            val compactDisplayText = KeysConfigHelper.getKeyDisplayLabel(key, isAsciiMode, isShifted)
            val compactOnClick = remember(key, commitValue, onKeyPress) { { onKeyPress(commitValue) } }
            val compactOnPress: (() -> Unit)? = remember(key, onKeyPressDown) { { onKeyPressDown?.invoke(key); Unit } }
            val compactOnRelease: (() -> Unit)? = remember(key, onKeyRelease) { { onKeyRelease?.invoke(key); Unit } }
            val compactOnSwipeDown: ((String) -> Unit)? = if (swipeDownAction != null && swipeDownAction != GestureAction.NONE && swipeDownLabel != null) {
                remember(key, onKeyPress, onGestureAction, onCommitText, swipeDownAction, swipeDownValue, swipeDownLabel) {
                    val label = swipeDownLabel
                    { _: String ->
                        if (swipeDownAction == GestureAction.COMMIT) {
                            (onCommitText ?: onKeyPress)(swipeDownValue?.ifEmpty { label } ?: label)
                        } else {
                            onGestureAction?.invoke(
                                swipeDownAction,
                                swipeDownValue?.ifEmpty { label } ?: label!!)
                        }
                        Unit
                    }
                }
            } else null
            val compactOnLongPressSelect: ((String) -> Unit)? = remember(key, longPressGestureMap, onGestureAction, onCommitText, onKeyPress) { { selectedLabel: String ->
                val gesture = longPressGestureMap?.get(selectedLabel)
                if (gesture != null && gesture.action != GestureAction.COMMIT) {
                    onGestureAction?.invoke(
                        gesture.action!!,
                        gesture.value.ifEmpty { selectedLabel })
                } else {
                    commitLongPressChoice(selectedLabel, onCommitText, onKeyPress)
                }
                Unit
            } }

            SwipeableKeyButtonLandscape(
                text = compactDisplayText,
                letterGroup = if (isShifted) key.uppercase() else key,
                onLetterSelection = onKeyPress,
                onClick = compactOnClick,
                backgroundColor = config.keyBackgroundColor,
                textColor = config.keyTextColor,
                modifier = Modifier.weight(if (LocalCustomLayout.current != null) key.length.toFloat() else 1f),
                swipeText = swipeUpText,
                symbolInputText = symbolInputValue(swipeUpCommitValue, swipeUpAction),
                swipeDownText = swipeDownBubbleText,
                swipeUpKeyLabel = swipeUpKeyLabel,
                swipeDownKeyLabel = swipeDownKeyLabel,
                onSwipe = if (swipeUpCommitValue != null && swipeUpAction != GestureAction.NONE) { {
                    if (swipeUpAction == null || swipeUpAction == GestureAction.COMMIT) {
                        (onCommitText ?: onKeyPress)(swipeUpCommitValue)
                    } else {
                        onGestureAction?.invoke(swipeUpAction, swipeUpCommitValue)
                    }
                    Unit
                } } else null,
                onSwipeDown = compactOnSwipeDown,
                onSwipeStateChange = onSwipeStateChange,
                onPress = compactOnPress,
                onRelease = compactOnRelease,
                onLongPressSelect = compactOnLongPressSelect,
                longPressItems = longPressLabels,
                fontSize = config.fontSize,
                swipeFontSize = config.swipeFontSize,
                shadowEnabled = config.shadowEnabled,
                shadowElevation = config.shadowElevation,
                shadowShapeRadius = config.shadowShapeRadius,
            )
        }
    }
}

/** QWERTY 空格键 */
@Composable
private fun SpaceKey(
    schemaName: String,
    isAsciiMode: Boolean,
    isSttEnabled: Boolean,
    isVoiceMode: Boolean,
    voiceSticky: Boolean,
    keyBackgroundColor: Color,
    keyTextColor: Color,
    shadowEnabled: Boolean,
    shadowElevation: Dp,
    shadowShapeRadius: Dp,
    modifier: Modifier = Modifier,
    onKeyPress: (String) -> Unit,
    onKeyPressDown: ((String) -> Unit)?,
    onKeyRelease: ((String) -> Unit)?,
    onVoiceModeChange: ((Boolean) -> Unit)?,
) {

    SpaceKeyButton(onClick = { onKeyPress("space") }, backgroundColor = keyBackgroundColor,
        textColor = keyTextColor, schemaName = if (isAsciiMode) "英文" else schemaName,
        modifier = modifier, onPress = { onKeyPressDown?.invoke("space") },
        onRelease = { onKeyRelease?.invoke("space") }, isVoiceMode = isVoiceMode,
        voiceSticky = voiceSticky, isSttEnabled = isSttEnabled, onVoiceModeChange = onVoiceModeChange,
        shadowEnabled = shadowEnabled, shadowElevation = shadowElevation, shadowShapeRadius = shadowShapeRadius)
}

/**
 * 长按菜单选中的小写字母当作点按：拼音组词中接到编码后面，不先上屏首选（h + 长按 g 不再变成「和g」）。
 * 其他字符（大写、变音字母、符号）照旧直接上屏。
 */
internal fun commitLongPressChoice(label: String, onCommitText: ((String) -> Unit)?, onKeyPress: (String) -> Unit) {
    if (label.length == 1 && label[0] in 'a'..'z') onKeyPress(label) else (onCommitText ?: onKeyPress)(label)
}

/**
 * 字母键长按的大小写（及带音调字母）气泡默认关：它会打断上下拖动输入角标（用户 10-20）。
 * 打开“长按弹出字母气泡”才恢复；非字母键（标点、地球键等）的长按不受影响。
 */
internal fun letterLongPressMenuAllowed(key: String, enabled: Boolean): Boolean =
    // 字母键是 1～2 个字母（26 键 q、14 键 qw）；earth 之类的功能键名不算。
    enabled || key.length !in 1..2 || !key.all { it in 'a'..'z' || it in 'A'..'Z' }
