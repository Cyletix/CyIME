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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
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
) {
    val context = LocalContext.current
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

    // 实际布局就是触摸边界：左侧书写区与底部按键、右侧按键互不覆盖。
    Row(modifier.fillMaxSize().testTag("handwriting-panel").padding(bottom = bottomPaddingDp.dp)) {
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Canvas(Modifier.weight(1f).fillMaxWidth().clipToBounds().testTag("handwriting-canvas")
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
                renderStrokes(session.strokes + listOfNotNull(session.currentStroke.takeIf { it.isNotEmpty() }), emptyList(), keyTextColor)
            }
            Row(Modifier.fillMaxWidth().height(48.dp)) {
                listOf("symbol" to 1f, "number" to 0.7f, "space" to 1.8f, "ime_switch" to 0.7f).forEach { (action, weight) ->
                    HandwritingFunctionKey(action, { press(action) },
                        specialKeyBackgroundColor,
                        specialKeyTextColor,
                        Modifier.weight(weight).fillMaxHeight())
                }
            }
        }
        Column(Modifier.width(56.dp).fillMaxHeight()) {
            listOf("delete", "，", "。", "enter").forEach { action ->
                val special = action == "delete" || action == "enter"
                HandwritingFunctionKey(action, { press(action) },
                    if (special) specialKeyBackgroundColor else keyBackgroundColor,
                    if (special) specialKeyTextColor else keyTextColor,
                    Modifier.weight(1f).fillMaxWidth())
            }
        }
    }
}

@Composable
private fun HandwritingFunctionKey(action: String, onClick: () -> Unit, background: Color, foreground: Color, modifier: Modifier) {
    val label = when (action) {
        "delete" -> "删除"; "enter" -> "回车"; "space" -> "空格"
        "symbol" -> "符号"; "number" -> "123"; "ime_switch" -> "ABC"; else -> action
    }
    Box(modifier.padding(2.dp).clip(RoundedCornerShape(LocalKeyCornerRadius.current)).background(background)
        .clickable(onClick = onClick).semantics { contentDescription = label }
        .testTag("handwriting-key:$action"), contentAlignment = Alignment.Center) {
        val icon = when (action) {
            "delete" -> Icons.AutoMirrored.Filled.Backspace
            "enter" -> Icons.AutoMirrored.Filled.KeyboardReturn
            "space" -> Icons.Default.SpaceBar
            else -> null
        }
        if (icon != null) Icon(icon, contentDescription = null, tint = foreground, modifier = Modifier.size(24.dp))
        else Text(label, color = foreground, fontSize = 18.sp, fontFamily = AppFonts.keyFontFamily)
    }
}
