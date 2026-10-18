package com.kingzcheung.xime.ui.caption

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Why the bar is not listening: the call is on the earpiece or a headset. */
internal enum class CallRoute { Speaker, Earpiece, Headset }

private const val CALL_FONT = 24
private const val CALL_LINES = 3

/**
 * The subtitle bar shown during calls. Only the other party is captioned: the bar listens only while
 * the call is on speakerphone, and the phone holder's remembered voice ("这是我") never shows. New
 * words fade in one by one and older lines glide up; nothing jumps.
 */
@Composable
internal fun CallCaptionBar(controller: CaptionController, route: CallRoute, onDrag: (Float) -> Unit, onClose: () -> Unit) {
    var choosing by remember { mutableStateOf<SpeakerUi?>(null) }
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.Black.copy(alpha = .66f))
        .pointerInput(Unit) { detectVerticalDragGestures { change, dy -> change.consume(); onDrag(dy) } }
        .padding(start = 16.dp, end = 44.dp, top = 10.dp, bottom = 10.dp)) {
        Column {
            val hint = when (route) {
                CallRoute.Earpiece -> "开免提后，这里会显示对方说的话"
                CallRoute.Headset -> "戴着耳机时手机听不到对方，请改用免提"
                CallRoute.Speaker -> when {
                    controller.status == CaptionController.Status.Error -> controller.message
                    controller.micTaken -> "麦克风被别的应用占着（比如带声音的录屏），这会儿听不到对方"
                    else -> null
                }
            }
            if (hint != null) Text(hint, color = Color.White, fontSize = 20.sp, lineHeight = 26.sp)
            else CallLines(controller, onSpeakerTap = { choosing = it })
            choosing?.let { speaker ->
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("“${speaker.name}”是拿手机的人吗？", color = Color.White, fontSize = 18.sp, modifier = Modifier.weight(1f))
                    BarButton("是我，不显示") { controller.markSelf(speaker.id); choosing = null }
                    BarButton("不是") { choosing = null }
                }
            }
        }
        Text("×", color = Color.White, fontSize = 24.sp, modifier = Modifier.align(Alignment.TopEnd).padding(start = 8.dp)
            .clickable(onClick = onClose).padding(horizontal = 6.dp))
    }
}

@Composable
private fun CallLines(controller: CaptionController, onSpeakerTap: (SpeakerUi) -> Unit) {
    RevealTicker(controller)
    val lines = controller.segments.filter { line ->
        line.text.isNotEmpty() && line.speakerId != CaptionController.SELF_SPEAKER &&
            controller.speakers.none { it.id == line.speakerId && it.ignored }
    }.takeLast(8)
    val list = rememberLazyListState()
    val lineHeight = (CALL_FONT * 1.3f)
    if (lines.isEmpty()) {
        Text("通话字幕已开启，对方说的话会显示在这里", color = Color.White.copy(alpha = .8f), fontSize = 20.sp)
        return
    }
    val newest = lines.last()
    LaunchedEffect(lines.size, newest.id, newest.reveal.shown) { list.animateScrollToItem(lines.lastIndex, Int.MAX_VALUE / 2) }
    LazyColumn(state = list, modifier = Modifier.fillMaxWidth().height((lineHeight * CALL_LINES + 8).dp),
        verticalArrangement = Arrangement.spacedBy(4.dp), userScrollEnabled = false) {
        items(lines.size, key = { lines[it].id }) { index ->
            val line = lines[index]
            val speaker = controller.speakers.firstOrNull { it.id == line.speakerId }
            Row(Modifier.fillMaxWidth().animateItem(fadeInSpec = tween(320),
                placementSpec = tween(360, easing = FastOutSlowInEasing)), verticalAlignment = Alignment.Top) {
                if (speaker != null) {
                    Text(speaker.name + "：", color = speakerColor(speaker.colorIndex), fontSize = CALL_FONT.sp,
                        lineHeight = lineHeight.sp, fontWeight = FontWeight.Medium,
                        modifier = Modifier.clickable { onSpeakerTap(speaker) })
                    Spacer(Modifier.width(2.dp))
                }
                ProgressiveText(listOf(line), fontSize = CALL_FONT.sp, lineHeight = lineHeight.sp, color = Color.White,
                    suffix = if (!line.confirmed) "…" else "")
            }
        }
    }
}

@Composable
private fun BarButton(text: String, onClick: () -> Unit) {
    Text(text, color = Color.White, fontSize = 18.sp, modifier = Modifier.clip(RoundedCornerShape(10.dp))
        .background(Color.White.copy(alpha = .22f)).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp))
}
