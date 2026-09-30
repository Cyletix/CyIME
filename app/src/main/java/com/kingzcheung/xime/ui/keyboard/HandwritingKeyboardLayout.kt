package com.kingzcheung.xime.ui.keyboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.KeyboardReturn
import androidx.compose.material.icons.filled.SpaceBar
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.CloseFullscreen
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kingzcheung.xime.handwriting.HandwritingEngine
import com.kingzcheung.xime.handwriting.HandwritingStrokeFx
import com.kingzcheung.xime.handwriting.OverlappedHandwritingRecognizer
import com.kingzcheung.xime.handwriting.StrokePoint
import com.kingzcheung.xime.handwriting.renderStrokes
import com.kingzcheung.xime.handwriting.handwritingInk
import com.kingzcheung.xime.model.ModelDownloadState
import com.kingzcheung.xime.model.ModelManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

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
    panelBackgroundColor: Color = Color.Transparent,
    expandedCandidateBar: @Composable () -> Unit = {},
) {
    KeyboardKeySpacingScope(modifier, columns = 5f, verticalInset = (4 + bottomPaddingDp).dp) { bodyModifier ->
    val context = LocalContext.current
    val inkColor = if (expanded) Color.Black else handwritingInk(keyTextColor, panelBackgroundColor)
    val scope = rememberCoroutineScope()
    val keyAction by rememberUpdatedState(onKeyPress)
    val newCharacter by rememberUpdatedState(onNewCharacter)
    val result by rememberUpdatedState(onRecognition)
    val feedback by rememberUpdatedState(onButtonFeedback)
    val pauseMs by rememberUpdatedState((LocalKeyboardInputPreferences.current.handwritingPauseSeconds * 1000).toLong())
    val recognitionMutex = remember { Mutex() }
    val session = remember(scope, sessionKey) {
        HandwritingInputSession(scope, { pauseMs }, recognize = { strokes ->
            // 只传不可变快照；每次创建新窗口，取消的本地推理不会污染下一字缓存。
            val gaps = HandwritingStrokeFx.windowGaps(strokes).map { it * OverlappedHandwritingRecognizer.GAP_SPLIT_MS / pauseMs }
            recognitionMutex.withLock {
                withContext(Dispatchers.Default) {
                    OverlappedHandwritingRecognizer().recognize(
                        strokes.map { stroke -> stroke.map { it.x to it.y } }, gaps,
                    ).segments
                }
            }
        }, onNewCharacter = { newCharacter?.invoke() },
            onRecognition = { result?.invoke(it) }, onKey = { keyAction(it) })
    }
    val modelDownloaded by remember {
        ModelManager.downloadStates.map { it["ochwpro"] is ModelDownloadState.Complete }.distinctUntilChanged()
    }.collectAsState(ModelManager.downloadStates.value["ochwpro"] is ModelDownloadState.Complete)
    LaunchedEffect(modelDownloaded) { withContext(Dispatchers.IO) { HandwritingEngine.initialize(context) } }
    LaunchedEffect(clearSignal, session) { session.clear() }
    DisposableEffect(session) { onDispose { session.clear() } }

    fun press(action: String) {
        feedback?.invoke(action)
        session.press(action)
    }

    // Five equal rows: four writing/side-key rows and one full-width function row.
    BoxWithConstraints(bodyModifier.fillMaxSize().testTag("handwriting-panel")
        .padding(start = 4.dp, end = 4.dp, bottom = (4 + bottomPaddingDp).dp)) {
        // Bound function-key width on wide panels; extra width belongs to writing and space.
        val functionKeyWidth = (maxWidth / 7.5f).coerceAtMost(104.dp)
        val footerHeight = if (expanded) 52.dp.coerceAtMost(maxHeight / 3) else maxHeight / 5
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.weight(1f).fillMaxWidth()) {
                Box(Modifier.weight(1f).fillMaxHeight()) {
            Canvas(Modifier.fillMaxSize().clipToBounds().testTag("handwriting-canvas")
                .semantics { contentDescription = "手写区域"; stateDescription = session.phase.description }
                .pointerInput(session) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        val token = session.begin(StrokePoint(down.position.x, down.position.y))
                        var released = false
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                val p = change.position
                                // 离开画布仍由本次手势持有，不能触发旁边按钮，也不在按钮上画线。
                                if (p.x >= 0 && p.y >= 0 && p.x < size.width && p.y < size.height) {
                                    session.move(token, StrokePoint(p.x, p.y))
                                }
                                change.consume()
                                if (!change.pressed) { released = true; break }
                            }
                        } finally { session.end(token, cancelled = !released) }
                    }
                }) {
                renderStrokes(session.strokes + listOfNotNull(session.currentStroke.takeIf { it.isNotEmpty() }), emptyList(), inkColor)
            }
                }
                if (!expanded) Column(Modifier.width(functionKeyWidth).fillMaxHeight().testTag("handwriting-side-keys")) {
                    listOf("delete", "？", "，", "。").forEach { action ->
                        HandwritingFunctionKey(action, { press(action) },
                            if (action == "delete") specialKeyBackgroundColor else keyBackgroundColor,
                            if (action == "delete") specialKeyTextColor else keyTextColor,
                            Modifier.weight(1f).fillMaxWidth(), onClear = { press("clear_all") })
                    }
                }
            }
            if (expanded) Box(Modifier.fillMaxWidth().background(panelBackgroundColor)) { expandedCandidateBar() }
            if (expanded) {
                Row(Modifier.fillMaxWidth().height(footerHeight).background(panelBackgroundColor)
                    .testTag("handwriting-symbol-row")) {
                    listOf("；", "：", "！", "？", "，", "。").forEach { action ->
                        HandwritingFunctionKey(action, { press(action) }, keyBackgroundColor, keyTextColor,
                            Modifier.weight(1f).fillMaxHeight())
                    }
                    HandwritingFunctionKey("delete", { press("delete") }, specialKeyBackgroundColor,
                        specialKeyTextColor, Modifier.width(functionKeyWidth).fillMaxHeight(), onClear = { press("clear_all") })
                }
            }
            Row(Modifier.fillMaxWidth().height(footerHeight).background(panelBackgroundColor)
                .testTag("handwriting-bottom-row")) {
                val keys = listOf("symbol", "number", "space",
                    if (expanded) "collapse" else "expand", "ime_switch", "enter")
                keys.forEach { action ->
                    val special = action != "space"
                    HandwritingFunctionKey(action, { press(action) },
                        if (special) specialKeyBackgroundColor else keyBackgroundColor,
                        if (special) specialKeyTextColor else keyTextColor,
                        (if (action == "space") Modifier.weight(1f) else Modifier.width(functionKeyWidth)).fillMaxHeight())
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
            backgroundColor = keyBackground, iconColor = keyForeground,
            modifier = modifier.semantics { contentDescription = label }.testTag("handwriting-key:delete"),
            shadowEnabled = false,
        )
        return
    }
    BoxWithConstraints(modifier.clickable(onClick = onClick).semantics { contentDescription = label }
        .testTag("handwriting-key:$action")
        .padding(scaledKeyVisualPadding(PaddingValues(2.dp))).keyGlow(Modifier.clip(RoundedCornerShape(LocalKeyCornerRadius.current)).background(keyBackground)), contentAlignment = Alignment.Center) {
        val icon = when (action) {
            "delete" -> Icons.AutoMirrored.Filled.Backspace
            "enter" -> Icons.AutoMirrored.Filled.KeyboardReturn
            "space" -> Icons.Default.SpaceBar
            "ime_switch" -> Icons.Default.Language
            "expand" -> Icons.Default.OpenInFull
            "collapse" -> Icons.Default.CloseFullscreen
            else -> null
        }
        if (icon != null) Icon(icon, contentDescription = null, tint = keyForeground, modifier = Modifier.size(keyIconSizeDp(maxWidth.value, maxHeight.value).dp))
        else {
            val fontSize = keyLabelSizeSp(label, 18f, maxWidth.value, maxHeight.value,
                androidx.compose.ui.platform.LocalDensity.current.fontScale).sp
            Text(label, color = keyForeground, fontSize = fontSize, lineHeight = fontSize * 1.2f,
                maxLines = 1, fontFamily = AppFonts.keyFontFamily)
        }
    }
}
