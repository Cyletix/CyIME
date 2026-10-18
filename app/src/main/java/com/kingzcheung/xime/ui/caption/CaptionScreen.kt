package com.kingzcheung.xime.ui.caption

import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.delay
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kingzcheung.xime.ui.theme.MaterialLevel
import com.kingzcheung.xime.ui.theme.VisualStyles
import com.kingzcheung.xime.ui.theme.visualMaterial
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kingzcheung.xime.model.ModelDownloadState
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val LightBackground = Color(0xFFF5F5F0)
private val LightText = Color(0xFF111111)
private val DarkBackground = Color(0xFF121212)
private val DarkText = Color(0xFFEDEDED)

internal fun speakerColor(index: Int) = Color(CaptionController.COLORS[index.coerceIn(0, CaptionController.COLORS.size - 1)])

@Composable
fun CaptionScreen(controller: CaptionController, onExit: () -> Unit) {
    // The app's own theme (keyboard theme, dynamic colour, light/dark with the system).
    val context = androidx.compose.ui.platform.LocalContext.current
    val darkMode = remember { com.kingzcheung.xime.settings.SettingsPreferences.getEffectiveDarkMode(context) }
    val dark = when (darkMode) { 2 -> androidx.compose.foundation.isSystemInDarkTheme(); 1 -> true; else -> false }
    // Exactly the settings app's theme: its saved light/dark choice and the keyboard theme.
    com.kingzcheung.xime.ui.theme.XimeTheme(darkTheme = dark, themeId = com.kingzcheung.xime.settings.SettingsPreferences.getKeyboardTheme(context)) {
        // 再套一层：颜色全部换成键盘主题（键帽、回车键、文字、玻璃透明度），对话框里也一样。
        CaptionThemed(dark) {
            // 玻璃主题时整页不铺不透明底，让窗口后面的（模糊）壁纸透出来，面板和气泡才是真的半透明。
            val glass = com.kingzcheung.xime.ui.keyboard.rememberKeyboardInputPreferences(dark).frostedGlass.enabled
            Surface(Modifier.fillMaxSize(), color = if (glass) Color.Transparent else MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface) {
                Box(Modifier.fillMaxSize().captionPageBackground()) { CaptionPage(controller, onExit) }
            }
        }
    }
}

@Composable
private fun CaptionPage(controller: CaptionController, onExit: () -> Unit) {
    val fontSp = CaptionController.FONT_SP[controller.fontLevel]
    var menuSpeaker by remember { mutableStateOf<SpeakerUi?>(null) }
    var reassignTurn by remember { mutableStateOf<Turn?>(null) }
    var confirmExit by remember { mutableStateOf(false) }
    var replying by remember { mutableStateOf(false) }
    var flipped by remember { mutableStateOf<String?>(null) }

    // The navigation bar stays (with its back button); bottom buttons sit above it and the
    // system gesture area, so swiping up for the bar never presses "请再说一遍" (1.13.10).
    // 状态栏是隐藏的，但下拉或麦克风指示点会临时出现：顶部始终按状态栏高度留白，不盖住按钮。
    @OptIn(ExperimentalLayoutApi::class)
    val statusBarSpace = androidx.compose.foundation.layout.WindowInsets.statusBarsIgnoringVisibility
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val portrait = configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT
    // 手机横屏高度只有约 400 dp：控制条和按钮挪到右侧一栏，字幕占满左边整个高度。
    val sideRail = !portrait && configuration.screenHeightDp < 600
    val pageInsets = Modifier.fillMaxSize().windowInsetsPadding(statusBarSpace.only(androidx.compose.foundation.layout.WindowInsetsSides.Top))
        .windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.safeDrawing)
        .windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.systemGestures.only(
            androidx.compose.foundation.layout.WindowInsetsSides.Bottom))
        .padding(horizontal = CaptionStyle.Edge, vertical = 8.dp)
    val banners: @Composable () -> Unit = {
        if (!controller.streamingReady) ModelBanner("没有实时出字模型：要等对方说完一句才整句出字", "下载实时出字模型（226 MB）",
            controller.streamingDownload) { controller.downloadStreaming() }
        if (!controller.refinementReady) ModelBanner("没有 SenseVoice 校正：字幕没有标点、错字多", "下载 SenseVoice（228 MB）",
            controller.refinementDownload) { controller.downloadRefinement() }
        if (!controller.callCaptionEnabled) {
            val context = androidx.compose.ui.platform.LocalContext.current
            ModelBanner("打电话、微信视频时也想看字幕？开启后通话中会自动在屏幕底部显示", "去开启通话字幕", ModelDownloadState.Idle) {
                runCatching { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
        }
        if (!controller.speakerModelReady) ModelBanner("还没有声纹模型，暂时分不出是谁在说", "下载声纹模型（27 MB）",
            controller.downloadState) { controller.downloadSpeakerModel() }
    }
    val transcript: @Composable (Modifier) -> Unit = { modifier ->
        Box(modifier.fadeTopEdge()) {
            // Landscape: one track per speaker on a time axis. Portrait: a chat log of messages.
            RevealTicker(controller)
            if (portrait) CaptionChat(controller, fontSp, onSpeakerTap = { menuSpeaker = it }, onMessageLongPress = { reassignTurn = it })
            else CaptionTimeline(controller, fontSp, onSpeakerTap = { menuSpeaker = it }, onBlockLongPress = { reassignTurn = it })
            if (controller.status == CaptionController.Status.Paused) PausedOverlay { controller.resume() }
            if (controller.status == CaptionController.Status.Starting) LoadingOverlay(controller.message)
            if (controller.status == CaptionController.Status.Error) ErrorOverlay(controller.message) { controller.start() }
        }
    }
    // Sound made visible above the buttons, in its own strip so it never covers words or buttons.
    val ribbon: @Composable (Dp) -> Unit = { height ->
        SoundRibbon(controller.spectrum, controller.status == CaptionController.Status.Listening, MaterialTheme.colorScheme.primary,
            Modifier.fillMaxWidth().height(height))
    }

    if (sideRail) Row(pageInsets, horizontalArrangement = Arrangement.spacedBy(CaptionStyle.Gap)) {
        // 字幕占左边几乎整个屏幕：提示条限高、底下一条状态和声音丝带。
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Column(Modifier.fillMaxWidth().heightIn(max = (configuration.screenHeightDp * .3f).dp).verticalScroll(rememberScrollState())) {
                banners()
            }
            transcript(Modifier.weight(1f).fillMaxWidth())
            RailStatus(controller) { ribbon(it) }
        }
        // 右边一条窄栏（约屏宽一成）：五个按钮竖排，图标在上字在下，每个仍有老人好按的高度。
        SlimRail(controller, onExit = { confirmExit = true }, onReply = { replying = true }, onRepeat = { flipped = "请再说一遍" })
    } else Column(pageInsets, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // 顶部一块玻璃面板：三个大按钮 + 状态与音量，按钮同高同宽同间距。
        CaptionPanel(Modifier.fillMaxWidth()) {
            TopControls(controller, onExit = { confirmExit = true })
            StatusRow(controller)
        }
        // 提示条最多占屏高约三成，多了就在这一块里滚动：字幕区和底部按钮永远留得住。
        Column(Modifier.fillMaxWidth().heightIn(max = (configuration.screenHeightDp * .3f).dp).verticalScroll(rememberScrollState())) {
            banners()
        }
        transcript(Modifier.weight(1f).fillMaxWidth())
        // 底部悬浮玻璃操作条：声音丝带单独一条，在按钮上方，不压文字也不压按钮。
        CaptionPanel(Modifier.fillMaxWidth(), padding = 10.dp) {
            ribbon(if (configuration.screenHeightDp < 480) 24.dp else 36.dp)
            BottomButtons(onReply = { replying = true }, onRepeat = { flipped = "请再说一遍" })
        }
    }

    menuSpeaker?.let { speaker ->
        SpeakerMenu(speaker, controller, onDismiss = { menuSpeaker = null })
    }
    reassignTurn?.let { turn ->
        ReassignDialog(turn, controller, onDismiss = { reassignTurn = null })
    }
    if (confirmExit) BigChoiceDialog("真的退出字幕吗？", "退出", "继续听", onYes = { confirmExit = false; controller.exit(); onExit() }, onNo = { confirmExit = false })
    if (replying) ReplyDialog(controller, fontSp, onDismiss = { replying = false })
    flipped?.let { text -> FlippedCard(text) { flipped = null } }
}

@Composable
internal fun BigButton(text: String, modifier: Modifier = Modifier, outlined: Boolean = false,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null, onClick: () -> Unit) {
    // Labels never wrap or get cut to one character on a phone: one line, ellipsis last.
    // 统一成字幕页的玻璃按钮：outlined = 普通，否则 = 主操作（主题强调色容器）。
    CaptionButton(text, modifier, icon = icon, emphasis = if (outlined) CaptionEmphasis.Normal else CaptionEmphasis.Primary, onClick = onClick)
}

@Composable
private fun TopControls(controller: CaptionController, onExit: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // Phone width: three equal buttons fill the row; tablet: font on the left, pause/exit on the right.
        val narrow = maxWidth < 600.dp
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(if (narrow) 8.dp else CaptionStyle.Gap)) {
            // 手机三个按钮等宽铺满；平板字号在左，暂停/退出在右，宽度一致。
            val share = if (narrow) Modifier.weight(1f) else Modifier.width(180.dp)
            // 手机上三个按钮各约 100 dp：字号按钮不放图标，文字写全“字号特大”。
            if (narrow) CaptionButton("字号" + CaptionController.FONT_LABEL[controller.fontLevel], share, fontSize = 18.sp) { controller.cycleFont() }
            else BigButton("字号 " + CaptionController.FONT_LABEL[controller.fontLevel], share, outlined = true,
                icon = Icons.Rounded.FormatSize) { controller.cycleFont() }
            if (!narrow) Spacer(Modifier.weight(1f))
            when (controller.status) {
                CaptionController.Status.Listening, CaptionController.Status.Starting -> BigButton("暂停", share, outlined = true,
                    icon = Icons.Rounded.Pause) { controller.pause() }
                CaptionController.Status.Paused -> BigButton("继续", share, icon = Icons.Rounded.PlayArrow) { controller.resume() }
                else -> {}
            }
            BigButton("退出", share, outlined = true, icon = Icons.Rounded.Close, onClick = onExit)
        }
    }
}

/** 横屏侧栏：同样三个按钮，竖着排、等宽。 */
@Composable
private fun SlimRail(controller: CaptionController, onExit: () -> Unit, onReply: () -> Unit, onRepeat: () -> Unit) {
    Column(Modifier.width(RAIL_WIDTH).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val each = Modifier.fillMaxWidth().weight(1f)
        CaptionButton("字号" + CaptionController.FONT_LABEL[controller.fontLevel], each, icon = Icons.Rounded.FormatSize,
            height = RAIL_BUTTON, fontSize = 15.sp, stacked = true) { controller.cycleFont() }
        when (controller.status) {
            CaptionController.Status.Paused -> CaptionButton("继续", each, icon = Icons.Rounded.PlayArrow, emphasis = CaptionEmphasis.Primary,
                height = RAIL_BUTTON, fontSize = 15.sp, stacked = true) { controller.resume() }
            else -> CaptionButton("暂停", each, icon = Icons.Rounded.Pause,
                height = RAIL_BUTTON, fontSize = 15.sp, stacked = true) { controller.pause() }
        }
        CaptionButton("快捷回复", each, icon = Icons.Rounded.Forum, emphasis = CaptionEmphasis.Primary,
            height = RAIL_BUTTON, fontSize = 15.sp, stacked = true, onClick = onReply)
        CaptionButton("再说一遍", each, icon = Icons.Rounded.Replay, height = RAIL_BUTTON, fontSize = 15.sp, stacked = true, onClick = onRepeat)
        CaptionButton("退出", each, icon = Icons.Rounded.Close, height = RAIL_BUTTON, fontSize = 15.sp, stacked = true, onClick = onExit)
    }
}

/** Landscape status: one line under the captions, the sound ribbon taking the rest of it. */
@Composable
private fun RailStatus(controller: CaptionController, ribbon: @Composable (Dp) -> Unit) {
    val (dotColor, text) = statusOf(controller)
    Row(Modifier.fillMaxWidth().height(28.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(dotColor))
        Spacer(Modifier.width(8.dp))
        Text(text, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 280.dp))
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f)) { ribbon(24.dp) }
    }
}

private val RAIL_WIDTH = 104.dp
private val RAIL_BUTTON = 56.dp

@Composable
private fun statusOf(controller: CaptionController) = when {
    controller.micMuted -> MaterialTheme.colorScheme.error to "手机的麦克风被关了，请家人看一下"
    controller.status == CaptionController.Status.Listening -> MaterialTheme.colorScheme.primary to "正在听 · 只记字，不录音"
    controller.status == CaptionController.Status.Starting -> MaterialTheme.colorScheme.tertiary to controller.message.ifEmpty { "正在准备…" }
    controller.status == CaptionController.Status.Paused -> MaterialTheme.colorScheme.outline to "已暂停"
    controller.status == CaptionController.Status.Error -> MaterialTheme.colorScheme.error to controller.message
    else -> MaterialTheme.colorScheme.outline to ""
}

@Composable
private fun StatusRow(controller: CaptionController, volumeWidth: Dp = 96.dp) {
    val (dotColor, text) = statusOf(controller)
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(14.dp).clip(CircleShape).background(dotColor))
        Spacer(Modifier.width(10.dp))
        // 状态允许两行，不再被截成“…”。
        Text(text, fontSize = 18.sp, lineHeight = 22.sp, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.width(12.dp))
        // Volume bar: the reader sees that the phone hears something and whether it is loud enough.
        val level = controller.amplitude.coerceIn(0f, 1f)
        Box(Modifier.width(volumeWidth).height(10.dp).clip(RoundedCornerShape(5.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = .12f))) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(level).clip(RoundedCornerShape(5.dp))
                .background(if (level < .03f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary))
        }
    }
}

@Composable
private fun ModelBanner(message: String, action: String, state: ModelDownloadState, onDownload: () -> Unit) {
    // Same container as the settings pages (surfaceContainerLow, 12 dp, visual material).
    Surface(Modifier.fillMaxWidth().padding(vertical = 4.dp), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        BoxWithConstraints(Modifier.visualMaterial(VisualStyles.current, 12.dp, level = MaterialLevel.RAISED).padding(16.dp)) {
            // 横屏窄栏旁的字幕区也约 530 dp 宽：仍放一行，免得按钮被限高区裁掉。
            val narrow = maxWidth < 440.dp
            val text: @Composable () -> Unit = {
                Text(message, style = MaterialTheme.typography.bodyLarge, fontSize = 18.sp)
                when (state) {
                    is ModelDownloadState.Downloading -> LinearProgressIndicator(progress = { state.progress }, Modifier.fillMaxWidth().padding(top = 6.dp))
                    is ModelDownloadState.Error -> Text("下载失败：${state.message}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    else -> {}
                }
            }
            if (narrow) Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                text()
                if (state !is ModelDownloadState.Downloading) BigButton(action, Modifier.fillMaxWidth(), onClick = onDownload)
            } else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) { text() }
                if (state !is ModelDownloadState.Downloading) BigButton(action, onClick = onDownload)
            }
        }
    }
}

/** One row per speaker; time flows left→right, the newest turn sits at the right edge. */
/** One row of the transcript: consecutive sentences of one speaker, or the sentence still being spoken. */
class Turn(val key: Long, val speaker: SpeakerUi?, val segments: List<SegmentUi>) {
    val text: String get() = segments.joinToString("") { it.text }
    val live: Boolean get() = segments.any { !it.confirmed }
}

/** Same speaker, less than this apart → one row; the unlabeled tail is its own live row. */
private const val MERGE_GAP_MS = 10_000L

private fun buildTurns(controller: CaptionController): List<Turn> {
    val turns = ArrayList<Turn>()
    var current = ArrayList<SegmentUi>()
    var speaker: SpeakerUi? = null
    var lastWall = 0L
    fun flush() {
        if (current.isNotEmpty()) turns.add(Turn(current.first().id, speaker, current))
        current = ArrayList()
    }
    for (line in controller.segments) {
        if (line.text.isEmpty()) continue
        val who = line.speakerId?.let { id -> controller.speakers.firstOrNull { it.id == id } }
        if (who?.ignored == true) continue
        val same = current.isNotEmpty() && who != null && speaker?.id == who.id && line.wallMs - lastWall < MERGE_GAP_MS
        if (!same) { flush(); speaker = who }
        current.add(line)
        lastWall = line.wallMs
    }
    flush()
    return turns
}

/** Vertical transcript using the full width: newest row at the bottom, followed automatically. */
@Composable
private fun Transcript(controller: CaptionController, fontSp: Int, onSpeakerTap: (SpeakerUi) -> Unit, onTurnLongPress: (Turn) -> Unit) {
    val turns = buildTurns(controller)
    val hidden = controller.segments.count { line -> line.text.isNotEmpty() && controller.speakers.any { it.id == line.speakerId && it.ignored } }
    val listState = rememberLazyListState()
    var follow by remember { mutableStateOf(true) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
            if (!scrolling) {
                val info = listState.layoutInfo
                val last = info.visibleItemsInfo.lastOrNull()
                follow = last == null || last.index >= info.totalItemsCount - 1
            }
        }
    }
    val tail = turns.lastOrNull()
    val itemCount = turns.size + if (hidden > 0) 1 else 0
    LaunchedEffect(itemCount, tail?.text, follow) {
        if (follow && itemCount > 0) listState.scrollToItem(itemCount - 1, Int.MAX_VALUE / 2)
    }
    Box(Modifier.fillMaxSize()) {
        if (turns.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("等对方说话，字会出现在这里。", fontSize = fontSp.sp, lineHeight = (fontSp * 1.3f).sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (hidden > 0) item(key = "hidden") { Text("（电视声 ×$hidden 已折叠）", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(turns.size, key = { turns[it].key }) { index ->
                TurnRow(turns[index], fontSp, onSpeakerTap, onTurnLongPress)
            }
        }
        if (!follow && turns.isNotEmpty()) {
            Box(Modifier.align(Alignment.BottomEnd).padding(8.dp)) { BigButton("回到最新") { follow = true } }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TurnRow(turn: Turn, fontSp: Int, onSpeakerTap: (SpeakerUi) -> Unit, onLongPress: (Turn) -> Unit) {
    val speaker = turn.speaker
    val color = speaker?.let { speakerColor(it.colorIndex) } ?: Color.Gray
    val time = remember(turn.key) { SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(turn.segments.first().wallMs)) }
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).clip(RoundedCornerShape(14.dp)).background(color.copy(alpha = .10f))
        .combinedClickable(onClick = {}, onLongClick = { if (speaker != null) onLongPress(turn) })) {
        Box(Modifier.width(10.dp).fillMaxHeight().background(color))
        // Name column: the whole block is the tap target for "这是谁？".
        Column(Modifier.width(150.dp).fillMaxHeight().heightIn(min = 72.dp)
            .combinedClickable(onClick = { speaker?.let(onSpeakerTap) }).padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(speaker?.name ?: "正在说", fontSize = 22.sp, fontWeight = FontWeight.Bold,
                color = if (speaker == null) Color.Gray else Color.Unspecified, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(time, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(turn.text + if (turn.live) "…" else "", fontSize = fontSp.sp, lineHeight = (fontSp * 1.3f).sp,
            modifier = Modifier.weight(1f).padding(top = 8.dp, bottom = 10.dp, end = 14.dp))
    }
}


@Composable
private fun BottomButtons(onReply: () -> Unit, onRepeat: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(CaptionStyle.Gap)) {
        CaptionButton("快捷回复", Modifier.weight(1f), icon = Icons.Rounded.Forum,
            emphasis = CaptionEmphasis.Primary, height = CaptionStyle.PrimaryHeight, onClick = onReply)
        CaptionButton("请再说一遍", Modifier.weight(1f), icon = Icons.Rounded.Replay,
            height = CaptionStyle.PrimaryHeight, onClick = onRepeat)
    }
}

@Composable
private fun PausedOverlay(onResume: () -> Unit) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface.copy(alpha = .78f)), contentAlignment = Alignment.Center) {
        CaptionButton("已暂停，点这里继续", Modifier.fillMaxWidth(.8f), icon = Icons.Rounded.PlayArrow,
            emphasis = CaptionEmphasis.Primary, height = 110.dp, fontSize = 28.sp, maxLines = 2, onClick = onResume)
    }
}

/**
 * Speech model still loading: the same waiting page the keyboard shows while its dictionary loads
 * (title, thin progress bar, what is happening, that it continues by itself). Appears only when the
 * wait is noticeable, so a warm restart does not flash it.
 */
@Composable
private fun LoadingOverlay(message: String) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(300); visible = true }
    if (!visible) return
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface.copy(alpha = 1f)).clickable(
        interactionSource = remember { MutableInteractionSource() }, indication = null) {}, contentAlignment = Alignment.Center) {
        Column(Modifier.padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("正在加载语音模型", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(Modifier.width(180.dp), color = MaterialTheme.colorScheme.onSurface,
                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f))
            Spacer(Modifier.height(10.dp))
            Text(message.ifEmpty { "正在初始化..." }, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
            Text("首次打开或更新后需要一点时间，加载完成后自动开始显示字幕",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ErrorOverlay(message: String, onRestart: () -> Unit) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface.copy(alpha = .78f)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(message, fontSize = 24.sp, modifier = Modifier.padding(horizontal = 24.dp))
            CaptionButton("重新开始", Modifier.fillMaxWidth(.6f), icon = Icons.Rounded.Replay,
                emphasis = CaptionEmphasis.Primary, height = 90.dp, fontSize = 28.sp, onClick = onRestart)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SpeakerMenu(speaker: SpeakerUi, controller: CaptionController, onDismiss: () -> Unit) {
    var typing by remember { mutableStateOf(false) }
    var custom by remember { mutableStateOf("") }
    val others = controller.speakers.filter { it.id != speaker.id && it.id != CaptionController.REPLY_SPEAKER }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(.9f).padding(16.dp), shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("这是谁？", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Existing names first: picking one means "same person", and the rows merge.
                    for (other in others.filter { it.named && !it.ignored }) {
                        BigButton(other.name) { controller.mergeInto(speaker.id, other.id); onDismiss() }
                    }
                    for (name in CaptionController.PRESET_NAMES) {
                        if (others.any { it.name == name && it.named }) continue
                        BigButton(name, outlined = true) { controller.rename(speaker.id, if (name == "这是我") "我" else name); onDismiss() }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BigButton("这是电视", outlined = true) { controller.markTelevision(speaker.id); onDismiss() }
                    BigButton("输入名字", outlined = true) { typing = true }
                }
                if (typing) {
                    OutlinedTextField(custom, { custom = it }, Modifier.fillMaxWidth(), singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 24.sp), placeholder = { Text("名字", fontSize = 24.sp) })
                    BigButton("好了") { if (custom.isNotBlank()) { controller.rename(speaker.id, custom.trim()); onDismiss() } }
                }
                Spacer(Modifier.height(4.dp))
                BigButton("关闭", Modifier.fillMaxWidth(), outlined = true, onClick = onDismiss)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReassignDialog(turn: Turn, controller: CaptionController, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(.9f).padding(16.dp), shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("这句话是谁说的？", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text(turn.text, fontSize = 22.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (speaker in controller.speakers.filter { it.id != CaptionController.REPLY_SPEAKER && !it.ignored }) {
                        BigButton(speaker.name, outlined = speaker.id != turn.speaker?.id) { turn.segments.forEach { controller.reassign(it.id, speaker.id) }; onDismiss() }
                    }
                }
                BigButton("关闭", Modifier.fillMaxWidth(), outlined = true, onClick = onDismiss)
            }
        }
    }
}

@Composable
private fun BigChoiceDialog(title: String, yes: String, no: String, onYes: () -> Unit, onNo: () -> Unit) {
    Dialog(onDismissRequest = onNo) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(title, fontSize = 28.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    BigButton(yes, Modifier.weight(1f), onClick = onYes)
                    BigButton(no, Modifier.weight(1f), outlined = true, onClick = onNo)
                }
            }
        }
    }
}

/** Shown to the other person: the whole card is upside down so they can read it across the table. */
@Composable
private fun FlippedCard(text: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().captionPageBackground().combinedClickableNoRipple(onDismiss), contentAlignment = Alignment.Center) {
            Text(text, fontSize = 64.sp, lineHeight = 80.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.graphicsLayer { rotationZ = 180f }.padding(32.dp))
            Text("点一下关闭", fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableNoRipple(onClick: () -> Unit): Modifier = this.combinedClickable(onClick = onClick)

/**
 * Quick replies. The display card shows the chosen words upright for whoever holds the phone; the
 * flip button turns it around for someone across the table, and an upright line underneath always
 * says what the other person is seeing. Groups are tabs (underlined), phrases are buttons, and the
 * phrase on screen is marked, so the three never look alike.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReplyDialog(controller: CaptionController, fontSp: Int, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    var flipped by remember { mutableStateOf(false) }
    // Turning to face the other person is animated (like a payment code being shown across the counter).
    val turn by androidx.compose.animation.core.animateFloatAsState(if (flipped) 180f else 0f,
        androidx.compose.animation.core.tween(450, easing = androidx.compose.animation.core.FastOutSlowInEasing), label = "flip")
    val context = androidx.compose.ui.platform.LocalContext.current
    val emergency = text == CaptionController.EMERGENCY_TEXT
    // A phrase is shown at once and kept in the conversation for the family.
    fun say(phrase: String) { text = phrase; controller.addReply(phrase) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        // 比例：给对方看的大字卡片占剩下的全部高度；其余都是统一的 52 dp 小一号按钮，放在玻璃面板里。
        // 不透明底：玻璃主题下页面本身是透的，回话页不能透出后面的字幕。
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).captionPageBackground()
            .windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.safeDrawing)
            .padding(horizontal = CaptionStyle.Edge, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("快捷回复", fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                CaptionButton("返回", height = 48.dp, fontSize = 18.sp, onClick = onDismiss)
            }
            // The display card: upright by default; flipped only on request.
            CaptionPanel(Modifier.weight(1f).fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // The holder can always read what the other person sees: it is the header itself,
                    // so the upside-down text keeps the card's whole height.
                    Text(when {
                        flipped && text.isNotEmpty() -> "对方看到：$text"
                        flipped -> "给对面的人看（已倒过来）"
                        else -> "屏幕上显示"
                    }, fontSize = 18.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    CaptionButton(if (flipped) "转回来" else "倒过来给对面看", height = 48.dp, fontSize = 18.sp,
                        icon = Icons.Rounded.ScreenRotation) { flipped = !flipped }
                }
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(CaptionStyle.Corner))
                    .background(if (emergency) MaterialTheme.colorScheme.error.copy(alpha = .9f) else Color.Transparent),
                    contentAlignment = Alignment.Center) {
                    // 卡片矮时（小屏、横屏）字跟着缩，至少两行写得下，不被切掉。
                    val cardSp = (maxHeight.value / 2.6f).coerceIn(24f, 56f)
                    Text(text.ifEmpty { "点下面的话，或者自己写" }, fontSize = cardSp.sp, lineHeight = (cardSp * 1.25f).sp, fontWeight = FontWeight.Bold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        color = when {
                            text.isEmpty() -> MaterialTheme.colorScheme.onSurfaceVariant
                            emergency -> MaterialTheme.colorScheme.onError
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier.graphicsLayer { rotationZ = turn }.padding(8.dp))
                }
            }
            // Emergency: always visible; shows the request and opens the dialer with 120 typed in (still needs a press to call).
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CaptionButton("紧急：帮我打120", Modifier.weight(1f), icon = Icons.Rounded.Warning, emphasis = CaptionEmphasis.Danger,
                    height = 52.dp, fontSize = 19.sp) {
                    say(CaptionController.EMERGENCY_TEXT); flipped = true
                }
                CaptionButton("拨号 120", icon = Icons.Rounded.Call, emphasis = CaptionEmphasis.DangerQuiet, height = 52.dp, fontSize = 19.sp) {
                    runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_DIAL, android.net.Uri.parse("tel:120"))
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }
            }
            // 输入框、分类标签、常用的话、清空/保存同在一块玻璃面板里，颜色都取自键盘主题。
            CaptionPanel(Modifier.fillMaxWidth(), padding = 10.dp) {
                androidx.compose.material3.TextField(text, { text = it }, Modifier.fillMaxWidth(), singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 22.sp),
                    placeholder = { Text("自己写（可以用手写键盘）", fontSize = 19.sp) }, shape = RoundedCornerShape(CaptionStyle.Corner),
                    colors = androidx.compose.material3.TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent))
                // Groups as underlined tabs: clearly not phrases. 下面的话也可以左右滑动换组，和标签同步。
                val groups = CaptionController.PHRASE_GROUPS
                val pager = androidx.compose.foundation.pager.rememberPagerState { groups.size }
                val pageScope = androidx.compose.runtime.rememberCoroutineScope()
                androidx.compose.material3.PrimaryScrollableTabRow(selectedTabIndex = pager.currentPage, edgePadding = 0.dp,
                    containerColor = Color.Transparent, contentColor = MaterialTheme.colorScheme.onSurface, divider = {}) {
                    groups.forEachIndexed { index, (title, _) ->
                        androidx.compose.material3.Tab(selected = index == pager.currentPage,
                            onClick = { pageScope.launch { pager.animateScrollToPage(index) } },
                            selectedContentColor = MaterialTheme.colorScheme.onSurface,
                            unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            text = { Text(title, fontSize = 19.sp, fontWeight = if (index == pager.currentPage) FontWeight.Bold else FontWeight.Normal) },
                            modifier = Modifier.heightIn(min = 48.dp))
                    }
                }
                // 等宽网格：每格同宽同高，长的话换两行而不是把按钮撑宽，排起来整齐。
                // 网格约两行半高，多了在格子里滚：上面给对方看的大字卡片始终是页面上最大的一块。
                val gridMax = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp * .2f).dp.coerceIn(116.dp, 200.dp)
                // 灵敏度：手指横向拖过三分之一页才翻（默认一半），轻扫要够快才算；竖向滚动和点按不受影响，
                // 因为翻页只在横向移动超过系统滑动阈值后才接管。
                androidx.compose.foundation.pager.HorizontalPager(pager, Modifier.fillMaxWidth().heightIn(max = gridMax),
                    pageSpacing = 16.dp, beyondViewportPageCount = 1,
                    flingBehavior = androidx.compose.foundation.pager.PagerDefaults.flingBehavior(pager, snapPositionalThreshold = .33f),
                    verticalAlignment = Alignment.Top) { page ->
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        val phrases = groups[page].second
                        // 这一组里有长句就少分一列，两行内写得下。
                        val long = phrases.any { it.length > 5 }
                        val columns = (if (maxWidth >= 600.dp) 4 else 3) - if (long) 1 else 0
                        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (row in phrases.chunked(columns)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    for (phrase in row) {
                                        // The phrase on screen is filled and ticked; the rest are plain.
                                        CaptionButton(if (phrase == text) "✓ $phrase" else phrase, Modifier.weight(1f), height = 52.dp,
                                            fontSize = 18.sp, maxLines = 2,
                                            emphasis = if (phrase == text) CaptionEmphasis.Primary else CaptionEmphasis.Normal) { say(phrase) }
                                    }
                                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CaptionButton("清空", Modifier.weight(1f), height = 52.dp, fontSize = 19.sp) { text = ""; flipped = false }
                    CaptionButton("保存到对话", Modifier.weight(1f), height = 52.dp, fontSize = 19.sp) { if (text.isNotBlank()) controller.addReply(text) }
                }
            }
        }
    }
}
