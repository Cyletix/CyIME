package com.kingzcheung.xime.ui.keyboard

import android.graphics.Rect
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import kotlin.math.ceil
import kotlin.math.floor
import com.kingzcheung.xime.service.HardwareCursorAnchor

private const val MAX_VISIBLE_CANDIDATES = 10

@OptIn(ExperimentalLayoutApi::class)
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
) {
    val reportBounds by rememberUpdatedState(onBoundsChanged)
    if (candidates.isEmpty() && inputText.isEmpty() && preeditText.isEmpty()) {
        LaunchedEffect(Unit) { reportBounds(null) }
        return
    }
    DisposableEffect(Unit) {
        onDispose { reportBounds(null) }
    }

    val density = LocalDensity.current
    val displayText = if (preeditText.isNotEmpty()) preeditText else inputText

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
        val maxCardWidth = (maxWidth - 16.dp).coerceIn(1.dp, 420.dp)
        val maxCardHeight = (maxHeight - 16.dp).coerceAtLeast(1.dp)
        view.getLocationOnScreen(viewLoc)
        val origin = (hostPositionInRoot ?: IntOffset.Zero) + IntOffset(viewLoc[0], viewLoc[1])
        val placement = hardwareCandidatePosition(
            constraints.maxWidth, constraints.maxHeight, cardSize.width, cardSize.height,
            cursorAnchor.takeIf { hostPositionInRoot != null }, origin.x, origin.y,
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
        Box(
            modifier = Modifier
                .absoluteOffset { IntOffset(placement.x, placement.y) }
                .widthIn(min = minOf(160.dp, maxCardWidth), max = maxCardWidth)
                .heightIn(max = maxCardHeight)
                .shadow(12.dp, RoundedCornerShape(8.dp))
                .clip(RoundedCornerShape(8.dp))
                .background(cardBackgroundColor)
                .testTag("hardware-candidate-card")
                .onSizeChanged { cardSize = it }
                .onGloballyPositioned { coordinates ->
                    val bounds = coordinates.boundsInWindow()
                    reportBounds(Rect(floor(bounds.left).toInt(), floor(bounds.top).toInt(),
                        ceil(bounds.right).toInt(), ceil(bounds.bottom).toInt()))
                }
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 4.dp)
                    .widthIn(max = (maxCardWidth - 24.dp).coerceAtLeast(1.dp))
            ) {
                if (displayText.isNotEmpty()) {
                    Text(
                        text = displayText,
                        fontSize = 13.sp,
                        color = activeColor,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }

                if (candidates.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        candidates.take(MAX_VISIBLE_CANDIDATES).forEachIndexed { index, candidate ->
                            val isActive = index == highlightIndex
                            val label = (index + 1) % 10
                            val labelText = if (index == 9) "0" else "$label"
                            Column(
                                modifier = Modifier.testTag("hardware-candidate-$index")
                                    .then(if (onCandidateSelect != null) Modifier.clickable {
                                        onCandidateSelect(index)
                                    } else Modifier)
                                    .padding(horizontal = 2.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = "$labelText $candidate",
                                    fontSize = 15.sp,
                                    color = if (isActive) selectedTextColor else candidateTextColor,
                                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1,
                                    fontFamily = AppFonts.candidateFontFamily
                                )
                            }
                        }
                    }
                }
            }

            if (hasNextPage || hasPrevPage) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 8.dp, bottom = 6.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        if (hasPrevPage) {
                            Text("◀", fontSize = 10.sp, color = candidateTextColor.copy(alpha = 0.5f))
                        }
                        if (hasNextPage) {
                            Text("▶", fontSize = 10.sp, color = candidateTextColor.copy(alpha = 0.5f))
                        }
                    }
                }
            }
        }
    }
}
