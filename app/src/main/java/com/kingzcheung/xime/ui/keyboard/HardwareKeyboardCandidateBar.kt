package com.kingzcheung.xime.ui.keyboard

import android.graphics.Rect
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import com.kingzcheung.xime.settings.SettingsPreferences
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import kotlin.math.ceil
import kotlin.math.floor
import com.kingzcheung.xime.service.HardwareCursorAnchor

private const val MAX_VISIBLE_CANDIDATES = 10
internal val HardwareCandidateCornerRadius = 8.dp

@Composable
fun HardwareKeyboardCandidateBar(
    inputText: String,
    preeditText: String,
    candidates: List<String>,
    hasNextPage: Boolean,
    hasPrevPage: Boolean,
    cursorAnchor: HardwareCursorAnchor?,
    highlightIndex: Int,
    cardBackgroundColor: Color,
    candidateTextColor: Color,
    activeColor: Color,
    selectedTextColor: Color = activeColor,
    onCandidateSelect: ((Int) -> Unit)? = null,
    onBoundsChanged: (Rect?) -> Unit = {},
    avoidBoundsInWindow: Rect? = null,
    preeditBackgroundColor: Color = cardBackgroundColor,
    comments: List<String> = emptyList(),
    onPrevious: (() -> Unit)? = null,
    onNext: (() -> Unit)? = null,
    surface: (@Composable () -> Modifier)? = null,
    bottomDocked: Boolean = false,
    onPreeditBoundsChanged: (Rect?) -> Unit = {},
    showNumberLabels: Boolean = true,
) {
    val reportBounds by rememberUpdatedState(onBoundsChanged)
    val reportPreeditBounds by rememberUpdatedState(onPreeditBoundsChanged)
    if (candidates.isEmpty() && inputText.isEmpty() && preeditText.isEmpty()) {
        LaunchedEffect(Unit) { reportBounds(null); reportPreeditBounds(null) }
        return
    }
    DisposableEffect(Unit) {
        onDispose { reportBounds(null); reportPreeditBounds(null) }
    }

    val density = LocalDensity.current
    val context = LocalContext.current
    val fontSize = SettingsPreferences.getCandidateTextSize(context).sp
    val measurer = rememberTextMeasurer()
    val visuals = CandidateBarVisuals(cardBackgroundColor, candidateTextColor, Color.Transparent,
        activeColor, selectedTextColor, preeditBackgroundColor = preeditBackgroundColor)
    val showCandidates = !bottomDocked && candidates.isNotEmpty()
    LaunchedEffect(showCandidates) { if (!showCandidates) reportBounds(null) }

    val displayText = if (preeditText.isNotEmpty()) preeditText else inputText
    LaunchedEffect(displayText) { if (displayText.isEmpty()) reportPreeditBounds(null) }

    val view = LocalView.current
    val viewLoc = remember { IntArray(2) }
    var hostPositionInRoot by remember { mutableStateOf<IntOffset?>(null) }
    var hostPositionInWindow by remember { mutableStateOf<IntOffset?>(null) }
    var cardSize by remember { mutableStateOf(IntSize.Zero) }
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().testTag("hardware-candidate-host").onGloballyPositioned { coordinates ->
            val position = coordinates.positionInRoot()
            hostPositionInRoot = IntOffset(position.x.roundToInt(), position.y.roundToInt())
            val windowPosition = coordinates.positionInWindow()
            hostPositionInWindow = IntOffset(windowPosition.x.roundToInt(), windowPosition.y.roundToInt())
        }
    ) {
        // Use the actual host and measured card, including split-window origins.
        // The insertion marker is already in screen pixels; zero is a valid position.
        val marginPx = with(density) { 8.dp.roundToPx() }
        val maxCardWidth = (maxWidth - 16.dp).coerceIn(1.dp, 560.dp)
        val maxCardHeight = (maxHeight - 16.dp).coerceAtLeast(1.dp)
        view.getLocationOnScreen(viewLoc)
        val origin = (hostPositionInRoot ?: IntOffset.Zero) + IntOffset(viewLoc[0], viewLoc[1])
        val placement = hardwareCandidatePosition(
            constraints.maxWidth, constraints.maxHeight, cardSize.width, cardSize.height,
            cursorAnchor.takeIf { hostPositionInRoot != null && !bottomDocked }, origin.x, origin.y,
            marginPx, with(density) { 8.dp.roundToPx() },
            avoidBounds = hostPositionInWindow?.let { hostOrigin ->
                avoidBoundsInWindow?.let { excluded ->
                    HardwareCandidateExclusion(
                        excluded.left - hostOrigin.x, excluded.top - hostOrigin.y,
                        excluded.right - hostOrigin.x, excluded.bottom - hostOrigin.y,
                    )
                }
            },
        )
        val textWidth = candidates.take(MAX_VISIBLE_CANDIDATES).mapIndexed { index, text ->
            measurer.measure(AnnotatedString("${(index + 1) % 10} $text"),
                candidatePrimaryTextStyle(fontSize, AppFonts.candidateFontFamily, index == highlightIndex), softWrap = false).size.width
        }.sum()
        val contentWidth = (with(density) { textWidth.toDp() } + (12 * candidates.size + 104).dp)
            .coerceIn(minOf(160.dp, maxCardWidth), maxCardWidth)
        val dockBounds = if (bottomDocked) hostPositionInWindow?.let { originInWindow ->
            avoidBoundsInWindow?.let { IntOffset(it.left - originInWindow.x, it.top - originInWindow.y) }
        } else null
        val x = dockBounds?.x?.coerceIn(marginPx, maxOf(marginPx, constraints.maxWidth - cardSize.width - marginPx)) ?: placement.x
        val y = dockBounds?.let { (it.y - cardSize.height - marginPx).coerceAtLeast(marginPx) } ?: placement.y
        // This group has no painted background or pointer handler. Each visible surface reports
        // its own touch region, leaving the gap and the rest of the editor usable.
        Column(Modifier.absoluteOffset { IntOffset(x, y) }.widthIn(max = maxCardWidth)
            .heightIn(max = maxCardHeight).onSizeChanged { cardSize = it },
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (displayText.isNotEmpty()) {
                val shape = RoundedCornerShape(4.dp)
                // Let the displayed text and its real padding determine the width. A second
                // text measurement can disagree with font resolution or pixel-rounded padding.
                Box(Modifier.widthIn(min = minOf(24.dp, maxCardWidth), max = maxCardWidth)
                    .clip(shape)
                    .then(surface?.invoke() ?: Modifier.background(preeditBackgroundColor))
                    .testTag("hardware-preedit-card")
                    .onGloballyPositioned { reportPreeditBounds(it.boundsInWindow().toAndroidBounds()) }) {
                    PreeditLabel(displayText, visuals.copy(preeditBackgroundColor = Color.Transparent),
                        followTextTail = true)
                }
            }
            if (showCandidates) {
                val shape = RoundedCornerShape(HardwareCandidateCornerRadius)
                Box(Modifier.width(contentWidth).clip(shape)
                    .then(surface?.invoke() ?: Modifier.background(cardBackgroundColor))
                    .testTag("hardware-candidate-card")
                    .onGloballyPositioned { reportBounds(it.boundsInWindow().toAndroidBounds()) }
                    .padding(horizontal = 6.dp, vertical = 4.dp)) {
                    HardwareCandidateRow(candidates, comments, highlightIndex, hasPrevPage, hasNextPage,
                        visuals, onCandidateSelect, onPrevious, onNext, showNumberLabels)
                }
            }
        }
    }
}

private fun androidx.compose.ui.geometry.Rect.toAndroidBounds() = Rect(
    floor(left).toInt(), floor(top).toInt(), ceil(right).toInt(), ceil(bottom).toInt())

/** The exact same single-row renderer is used beside the caret and inside the dock. */
@Composable
internal fun hardwareCandidateRowHeight(): androidx.compose.ui.unit.Dp =
    (candidateItemHeight(SettingsPreferences.getCandidateTextSize(LocalContext.current).sp) + 8.dp)
        .coerceAtLeast(48.dp)

@Composable
internal fun HardwareCandidateRow(
    candidates: List<String>, comments: List<String>, highlightIndex: Int,
    hasPrevPage: Boolean, hasNextPage: Boolean, visuals: CandidateBarVisuals,
    onCandidateSelect: ((Int) -> Unit)?, onPrevious: (() -> Unit)?, onNext: (() -> Unit)?,
    showNumberLabels: Boolean = true,
) {
    val context = LocalContext.current
    val shownComments = if (SettingsPreferences.showCandidateComments(context)) comments else emptyList()
    Row(verticalAlignment = Alignment.CenterVertically) {
        FixedCandidateStrip(candidates.take(MAX_VISIBLE_CANDIDATES), comments = shownComments,
            visuals = visuals,
            callbacks = CandidateBarCallbacks(onCandidateSelect = { onCandidateSelect?.invoke(it) }),
            fontSize = SettingsPreferences.getCandidateTextSize(context).sp,
            highlightIndex = highlightIndex, showNumberLabels = showNumberLabels, itemSpacing = 8.dp,
            modifier = Modifier.weight(1f).padding(vertical = 4.dp))
        if (candidates.isNotEmpty() && onPrevious != null && (hasPrevPage || highlightIndex > 0)) {
            KeyboardToolbarButton(onPrevious, Color.Transparent,
                modifier = Modifier.testTag("hardware-candidate-previous")) {
                Icon(Icons.Default.ChevronLeft, "上一候选", tint = visuals.textColor)
            }
        }
        if (candidates.isNotEmpty() && onNext != null && (hasNextPage || highlightIndex < candidates.lastIndex)) {
            KeyboardToolbarButton(onNext, Color.Transparent,
                modifier = Modifier.testTag("hardware-candidate-next")) {
                Icon(Icons.Default.ChevronRight, "下一候选", tint = visuals.textColor)
            }
        }
    }
}
