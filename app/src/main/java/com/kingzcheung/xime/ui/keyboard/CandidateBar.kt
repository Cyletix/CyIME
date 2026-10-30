package com.kingzcheung.xime.ui.keyboard

import com.kingzcheung.xime.ui.theme.VisualStyles
import com.kingzcheung.xime.ui.theme.visualMaterial

import com.kingzcheung.xime.service.PredictionManager
import android.annotation.SuppressLint
import android.content.SharedPreferences
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.draw.rotate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardDoubleArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.animation.core.VectorConverter
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.layout.boundsInRoot
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.layout.FirstBaseline
import kotlin.math.roundToInt
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import com.kingzcheung.xime.R
import com.kingzcheung.xime.keyboard.MainType
import com.kingzcheung.xime.keyboard.KeyboardPage
import com.kingzcheung.xime.keyboard.OverlayRoute
import com.kingzcheung.xime.keyboard.PanelType
import com.kingzcheung.xime.keyboard.ToolbarAction
import com.kingzcheung.xime.settings.SettingsPreferences
import com.kingzcheung.xime.speech.RecognitionState

@Immutable
data class CandidateBarVisuals(
    val backgroundColor: Color,
    val textColor: Color,
    val dividerColor: Color,
    val accentColor: Color = Color(0xFF1A73E8),
    val selectedTextColor: Color = Color(0xFF1A73E8),
    val isDarkTheme: Boolean = false,
    val preeditBackgroundColor: Color = Color.Unspecified,
    val opaqueComments: Boolean = false,
    val textShadow: androidx.compose.ui.graphics.Shadow? = null,
    val glassControlBackground: Color? = null,
)

data class CandidateBarCallbacks(
    val onCandidateSelect: (Int) -> Unit,
    val onVisibleCandidatesChanged: ((List<String>) -> Unit)? = null,
    val onVisibleAssociationsChanged: ((List<String>) -> Unit)? = null,
    val onLogoClick: (() -> Unit)? = null,
    val onBack: (() -> Unit)? = null,
    val onHideKeyboard: (() -> Unit)? = null,
    val onShowMoreCandidates: (() -> Unit)? = null,
    val onClearAssociation: (() -> Unit)? = null,
    val onInputTextClick: (() -> Unit)? = null,
    val onAssociationSelect: ((Int) -> Unit)? = null,
    val onDismissClipboardPreview: (() -> Unit)? = null,
    val onOpenClipboard: (() -> Unit)? = null,
    val onClipboardImageSelect: ((com.kingzcheung.xime.clipboard.ClipboardImage) -> Unit)? = null,
    val onExpandClipboardImages: (() -> Unit)? = null,
    val onCancelInput: (() -> Unit)? = null,
    val onReorderToolbar: ((List<String>) -> Unit)? = null,
    // 长按候选：抛事件给宿主（键盘视图内弹确认覆盖层，不弹独立窗口——
    // 焦点型弹窗会抢焦点导致 IME 被系统收起）。
    val onCandidateLongPress: ((Int) -> Unit)? = null,
    /** 手指开始左右滑打字候选。 */
    val onCandidateStripScrolled: (() -> Unit)? = null,
    /** 打字候选滑到最后几个了。 */
    val onCandidateStripNearEnd: (() -> Unit)? = null,
)

/** 空间充足时均匀排布，项目溢出时保留完整触控尺寸并允许滚动。 */
@Composable
internal fun ToolbarItemsRow(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    BoxWithConstraints(modifier) {
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .widthIn(min = maxWidth),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
            content = content,
        )
    }
}

@Composable
fun CandidateBar(
    state: CandidateBarState,
    page: KeyboardPage = KeyboardPage.Main(com.kingzcheung.xime.keyboard.MainType.FULL),
    candidatePageExpanded: Boolean = false,
    toolbarActions: List<ToolbarAction> = emptyList(),
    visuals: CandidateBarVisuals,
    callbacks: CandidateBarCallbacks,
    inlineSuggestions: List<*> = listOf<Any>(),
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier,
    isFloatingMode: Boolean = false,
    isVoiceSticky: Boolean = false,
    voiceAmplitude: Float = 0f,
    voiceSpectrum: FloatArray = FloatArray(16),
    voiceRecognitionState: RecognitionState = RecognitionState.IDLE,
    voicePluginName: String = "",
    onEditPreedit: (() -> Unit)? = null,
    showPreeditPreview: Boolean = true,
    showNumberLabels: Boolean = false,
) {
    val glass = LocalKeyboardInputPreferences.current.frostedGlass
    val glassEnabled = glass.enabled
    val themeSurface = com.kingzcheung.xime.ui.theme.LocalMaterialPalette.current?.surface
        ?: visuals.preeditBackgroundColor.takeUnless { it == Color.Unspecified || it.alpha == 0f }
        ?: if (visuals.isDarkTheme) Color(0xFF202020) else Color(0xFFF2F2F2)
    val themeKey = com.kingzcheung.xime.ui.theme.LocalKeyboardPalette.current?.key ?: themeSurface
    val shadowRadius = with(LocalDensity.current) { 1.dp.toPx() }
    val textSurface = remember(glass, themeSurface, themeKey, shadowRadius, visuals.textColor, visuals.selectedTextColor, visuals.accentColor) {
        if (glassEnabled) resolveFrostedTextSurface(themeSurface, visuals.textColor,
            visuals.selectedTextColor, visuals.accentColor, glass, themeKey, shadowRadius) else null
    }
    val protectedVisuals = visuals.withTextSurface(textSurface)
    val configuration = LocalConfiguration.current
    val isLandscape = !isFloatingMode && configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val horizontalPadding = 8.dp
    val context = LocalContext.current

    // Toolbar and keys share the selected keyboard palette, including in previews.
    val iconButtonContainer = protectedVisuals.glassControlBackground ?: androidx.compose.ui.graphics.lerp(
        protectedVisuals.backgroundColor,
        protectedVisuals.accentColor,
        0.15f
    )
    val iconButtonTint = protectedVisuals.textColor
    // 候选栏两端的返回、展开按钮平时垫一层淡底，不画边框（用户 10-20）。从中间工具按钮按下时的底推出来，
    // 只取一半左右的浓度，处在候选栏底和中间按钮之间：玻璃时是同一种键帽玻璃（半透明，跟键帽一致），
    // 非玻璃时是同一种白/灰叠色，所以黑白主题也只是比底色稍亮或稍暗一点，不会冒出一块别的颜色。
    val edgeStrength = LocalEdgeButtonStrength.current
    val edgeButtonFill = protectedVisuals.glassControlBackground?.let { it.copy(alpha = it.alpha * edgeStrength) }
        ?: if (protectedVisuals.isDarkTheme) Color.White.copy(alpha = 0.25f * edgeStrength)
        else Color(0xFFE0E0E0).copy(alpha = edgeStrength)
    val preferences = remember(context) { SettingsPreferences.getPrefsPublic(context) }
    var showCancelButton by remember(preferences) {
        mutableStateOf(SettingsPreferences.shouldShowCandidateCancelButton(context))
    }
    DisposableEffect(preferences) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == SettingsPreferences.KEY_SHOW_CANDIDATE_CANCEL_BUTTON || key == null) {
                showCancelButton = SettingsPreferences.shouldShowCandidateCancelButton(context)
            }
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val showCompositionCancel = showCancelButton && state !is CandidateBarState.Idle && callbacks.onCancelInput != null
    val commentsAllowed = rememberCandidateCommentsAllowed()
    val inputTextLocation = SettingsPreferences.getInputTextLocation(context)
    val showInputBoxStyle = inputTextLocation == SettingsPreferences.INPUT_TEXT_INPUT_BOX
    val candidateTextSize = rememberCandidateTextSize()
    val candidateFontFamily = AppFonts.candidateFontFamily
    val commentFontFamily = AppFonts.commentFontFamily

    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val itemPaddingPx = with(density) { 8.dp.toPx() }
    val spacingPx = with(density) { 4.dp.toPx() }

    val screenWidthPx = with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
    val rowPaddingPx = with(density) { 16.dp.toPx() }
    val rightSidePx = with(density) {
        val moreBtn = if (callbacks.onShowMoreCandidates != null) 38.dp.toPx() else 0f
        val clearBtn = if (callbacks.onClearAssociation != null) 38.dp.toPx() else 0f
        val hideBtn = if (callbacks.onHideKeyboard != null) 28.dp.toPx() else 0f
        rowPaddingPx + maxOf(moreBtn, clearBtn) + hideBtn + 8.dp.toPx()
    }

    // 候选行滚动状态：需在 state 分支前声明——ChineseCandidates 的 hasAnyMore
    // 叠加 canScrollForward 判断（见分支内注释）
    val candidateListState = rememberLazyListState()

    val displayCandidates: List<String>
    val displayAssociation: List<String>
    val displayComments: List<String>
    val hasAnyMore: Boolean
    val showInputTextRow: Boolean
    val showLeftIcon: Boolean

    when (val s = state) {
        is CandidateBarState.Idle -> {
            displayCandidates = emptyList()
            displayAssociation = emptyList()
            displayComments = emptyList()
            hasAnyMore = false
            showLeftIcon = true
        }
        is CandidateBarState.ChineseCandidates -> {
            // 候选栏只排放得下的几个，其余可左右滑动查看；展开列表接到候选栏时也全部可滑到（只排可见的几个）。
            displayCandidates = s.candidates
            displayComments = s.comments

            hasAnyMore = s.hasMore || s.candidates.size > 1
            showLeftIcon = false
            displayAssociation = s.associationCandidates.take(PredictionManager.MAX_ASSOCIATION_COUNT)
        }
        is CandidateBarState.AssociationOnly -> {
            displayCandidates = emptyList()
            displayAssociation = s.candidates.take(maxOf(PredictionManager.MAX_ASSOCIATION_COUNT, s.highlightIndex + 1))
            hasAnyMore = s.hasMore
            showLeftIcon = false
            displayComments = s.comments
        }
        is CandidateBarState.EnglishCandidates -> {
            displayCandidates = s.candidates.take(20)
            displayComments = s.comments
            displayAssociation = emptyList()
            hasAnyMore = false
            showLeftIcon = false
        }
        is CandidateBarState.ClipboardDisplay -> {
            displayCandidates = s.candidates.take(20)
            displayComments = emptyList()
            displayAssociation = emptyList()
            hasAnyMore = false
            showLeftIcon = true
        }
        is CandidateBarState.Calculator -> {
            displayCandidates = s.candidates.take(20)
            displayComments = s.comments
            displayAssociation = emptyList()
            hasAnyMore = false
            showLeftIcon = false
        }
    }
    showInputTextRow = when (page) {
        is KeyboardPage.Overlay -> page.route !is OverlayRoute.Clipboard
        else -> true
    }

    val associationHighlight = (state as? CandidateBarState.AssociationOnly)?.highlightIndex
        ?.takeIf { it in displayAssociation.indices }
    LaunchedEffect(displayCandidates, displayAssociation, associationHighlight) {
        candidateListState.scrollToItem(associationHighlight ?: 0)
    }

    // Keep this animation outside the conditional button branches so changing pages
    // does not discard the previous angle and jump directly to the destination.
    val expansionRotation by animateFloatAsState(
        targetValue = if (candidatePageExpanded) -180f else 0f,
        animationSpec = tween(200, easing = FastOutSlowInEasing), label = "candidateExpansion",
    )
    val preeditText = (state as? CandidateBarState.ChineseCandidates)?.let {
        it.preeditText.ifEmpty { it.inputText }
    }.orEmpty()
    val showPreedit = showPreeditPreview && showInputTextRow && preeditText.isNotEmpty() &&
        (!showInputBoxStyle || onEditPreedit != null)
    // 分体时工具栏和候选栏也分到中缝两侧；剪贴板预览与自动填充建议仍是整条。
    // 拼音在栏内候选前面，靠左，落在中缝左侧。
    val splitGap = LocalSplitKeyboardGap.current
    val splitGapX = splitGap?.gapX()?.takeIf {
        state !is CandidateBarState.ClipboardDisplay && inlineSuggestions.isEmpty()
    }
    var barOrigin by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    var barTopInWindow by remember { mutableStateOf<Float?>(null) }
    // 根布局坐标：Shift 拖线和候选位置都用这个坐标系。
    var barRootOrigin by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    SideEffect { splitGap?.reportBarTop(if (splitGapX != null) barTopInWindow else null) }
    DisposableEffect(splitGap) { onDispose { splitGap?.reportBarTop(null) } }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(if (state is CandidateBarState.ClipboardDisplay) Modifier.heightIn(min = 44.dp)
                else Modifier.height(44.dp))
            .onGloballyPositioned {
                barOrigin = it.positionInWindow()
                barTopInWindow = barOrigin.y
                barRootOrigin = it.positionInRoot()
            }
            .shiftDragOverlay({ barRootOrigin }, protectedVisuals.accentColor)
            .clipSplitGapColumn(splitGapX) { barOrigin }
            .background(if (glassEnabled) Color.Transparent else protectedVisuals.backgroundColor)
            // Glass rails inherit the keyboard background; only individual controls get a material.
            .then(if (glassEnabled) Modifier else Modifier.visualMaterial(VisualStyles.current, 0.dp,
                level = if (state is CandidateBarState.Idle) com.kingzcheung.xime.ui.theme.MaterialLevel.FLOATING
                    else com.kingzcheung.xime.ui.theme.MaterialLevel.BASE))
            .padding(horizontal = horizontalPadding),
        verticalArrangement = Arrangement.Center,
    ) {
        if (state is CandidateBarState.ClipboardDisplay) {
            ClipboardPreviewBar(state.candidates, protectedVisuals, callbacks, iconButtonContainer, state.image)
            return@Column
        }

        // 空闲工具栏：菜单、收起与中间各图标同为等宽一格，胶囊同尺寸（分体时扣除中缝）。
        val rowDensity = LocalDensity.current
        var toolbarRowWidth by remember { mutableStateOf(0.dp) }
        val toolbarSlot = if (state is CandidateBarState.Idle && inlineSuggestions.isEmpty() && toolbarRowWidth > 0.dp) {
            val gap = splitGapX?.let { (l, r) -> with(rowDensity) { (r - l).toDp() } } ?: 0.dp
            val fixed = (if (callbacks.onHideKeyboard != null) 1 else 0) + (if (showLeftIcon) 1 else 0)
            // 悬浮小窗里格子随宽度收窄（最窄 36dp），图标都留在面板内，不再横向溢出压住边框。
            maxOf((toolbarRowWidth - 8.dp - gap) / (toolbarActions.size + fixed).coerceAtLeast(1), 36.dp)
        } else null
        // 打字时两端「<」「^」用和空闲工具栏同样宽的格子：宽屏下同样变成胶囊（用户 10-20）。
        val edgeStyle = LocalEdgeButtonStyle.current
        val edgeSlot = if (edgeStyle == EdgeButtonStyle.CIRCLE || toolbarRowWidth <= 0.dp) null else {
            val gap = splitGapX?.let { (l, r) -> with(rowDensity) { (r - l).toDp() } } ?: 0.dp
            val baseSlot = maxOf((toolbarRowWidth - 8.dp - gap) / (toolbarActions.size + 2).coerceAtLeast(1), 36.dp)
            // 手机（含横屏）和平板竖屏都保持正圆；候选栏宽过 940dp 才开始拉长，到 1400dp（平板横屏）
            // 拉到最宽 52dp（用户 10-21：原来手机上就开始拉，太早）。悬浮小窗格子窄时照旧缩小的圆。
            val stretch = ((toolbarRowWidth - EDGE_STRETCH_START) / (EDGE_STRETCH_FULL - EDGE_STRETCH_START)).coerceIn(0f, 1f)
            if (baseSlot < 44.dp) baseSlot else 44.dp + 20.dp * stretch
        }
        // 同心：胶囊圆角 = 键盘卡片圆角 - 胶囊离栏边的距离（上下各 6dp），和卡片四角同心。
        val edgeCorner = if (edgeStyle == EdgeButtonStyle.CONCENTRIC) FloatingKeyboardCardCorner - 6.dp else null
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { toolbarRowWidth = with(rowDensity) { it.width.toDp() } },
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showCompositionCancel) {
                KeyboardBackButton({ callbacks.onCancelInput?.invoke() }, iconButtonContainer, protectedVisuals.textColor, label = "取消输入",
                    restingFill = edgeButtonFill, slotWidth = edgeSlot, cornerRadius = edgeCorner, continuousPill = true)
                Spacer(Modifier.width(4.dp))
            }
            // 拼音放在候选栏里、候选前面：浮在键盘上方会压住应用底部输入框的按钮（用户 10-30）。
            // 限宽，长拼音在框内滚到最新的音节。
            if (showPreedit) {
                PreeditLabel(preeditText, protectedVisuals, onEditPreedit,
                    Modifier.widthIn(max = if (toolbarRowWidth > 0.dp) toolbarRowWidth * 0.35f else 120.dp)
                        .height(PreeditStyle.SurfaceHeight), followTextTail = true)
                Spacer(Modifier.width(4.dp))
            }
            if (showLeftIcon) {
                when (state) {
                    is CandidateBarState.Idle -> {
                        val panelHasToolbarBack = page is KeyboardPage.Overlay &&
                            (page.route is OverlayRoute.Menu || page.route is OverlayRoute.SchemaList || page.route is OverlayRoute.Edit ||
                                page.route is OverlayRoute.Emoji || page.route is OverlayRoute.Clipboard)
                        if ((page is KeyboardPage.Main && page.type == MainType.HANDWRITING || panelHasToolbarBack) && callbacks.onBack != null) {
                            KeyboardBackButton(callbacks.onBack, iconButtonContainer, protectedVisuals.textColor,
                                modifier = Modifier.testTag("toolbar-leading"), slotWidth = toolbarSlot)
                        } else {
                            KeyboardToolbarButton({ callbacks.onLogoClick?.invoke() }, iconButtonContainer,
                                modifier = Modifier.testTag("toolbar-leading"), slotWidth = toolbarSlot) {
                                Icon(painterResource(R.drawable.cyime_toolbar_mark), contentDescription = "CyIME Logo",
                                    tint = iconButtonTint, modifier = Modifier.size(22.dp))
                            }
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    is CandidateBarState.ClipboardDisplay -> {
                        Row(
                            modifier = Modifier.padding(end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "剪切板",
                                tint = protectedVisuals.accentColor,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    else -> {}
                }
            }

            if (inlineSuggestions.isNotEmpty()) {
                LazyRow(
                    // 占满配额：内容少时建议靠左、右侧留白到收起按钮（收起按钮
                    // 因此固定最右）；内容超出配额时占满并可横向滑动查看后续建议；
                    // clipToBounds：滑动时滑出边界的建议裁剪掉，避免与左侧 logo 重叠
                    modifier = Modifier
                        .weight(1f)
                        .clipToBounds(),
                ) {
                    itemsIndexed(inlineSuggestions, key = { index, _ -> index }) { _, suggestion ->
                        Box(modifier = Modifier.fillMaxHeight()) {
                            InlineSuggestionView(
                                suggestion = suggestion,
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .width(180.dp),
                            )
                            // 每条尾部 1dp 分隔线：条目之间为间隔，最后一条的尾线
                            // 同时充当与候选词区的分界（与旧平铺布局视觉一致）
                            InlineSuggestionDivider(
                                modifier = Modifier.align(Alignment.CenterEnd),
                                color = protectedVisuals.dividerColor,
                            )
                        }
                    }
                }
            }

            if (state is CandidateBarState.ChineseCandidates || (splitGapX != null && state !is CandidateBarState.Idle)) {
                FixedCandidateStrip(
                    candidates = displayCandidates, associations = displayAssociation,
                    comments = displayComments,
                    visuals = protectedVisuals, callbacks = callbacks,
                    fontSize = candidateTextSize.sp,
                    modifier = Modifier.weight(1f),
                    highlightIndex = (state as? CandidateBarState.ChineseCandidates)?.highlightIndex ?: 0,
                    showNumberLabels = showNumberLabels,
                    gapWindowX = splitGapX,
                    scrollable = state is CandidateBarState.ChineseCandidates,
                    onUserScroll = callbacks.onCandidateStripScrolled,
                    onNearEnd = callbacks.onCandidateStripNearEnd,
                    scrollKey = (state as? CandidateBarState.ChineseCandidates)?.let { it.inputText to it.candidates.firstOrNull() },
                )
            } else if (state !is CandidateBarState.Idle) BoxWithConstraints(Modifier.weight(1f)) {
                val showComments = candidateCommentsVisible(maxWidth.value, density.fontScale,
                    candidateTextSize.toFloat(), commentsAllowed)
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    state = candidateListState,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    itemsIndexed(displayCandidates, key = { index, _ -> index }) { index, candidate ->
                        CandidateItem(
                            text = candidate,
                            index = index,
                            onClick = { callbacks.onCandidateSelect(index) },
                            onLongClick = if (callbacks.onCandidateLongPress != null) {
                                { callbacks.onCandidateLongPress(index) }
                            } else null,
                            textColor = protectedVisuals.textColor,
                            comment = if (showComments) {
                                when (val s = state) {
                                    is CandidateBarState.ChineseCandidates -> s.comments.getOrElse(index) { "" }
                                    is CandidateBarState.EnglishCandidates -> s.comments.getOrElse(index) { "" }
                                    else -> ""
                                }
                            } else "",
                            isSelected = index == 0,
                            accentColor = protectedVisuals.accentColor,
                            selectedTextColor = protectedVisuals.selectedTextColor,
                            opaqueComment = protectedVisuals.opaqueComments,
                            textShadow = protectedVisuals.textShadow,
                            fontSize = candidateTextSize.sp,
                            candidateFontFamily = candidateFontFamily,
                            commentFontFamily = commentFontFamily,
                            learned = state is CandidateBarState.ChineseCandidates && candidate in LocalLearnedCandidates.current,
                            correction = if (state is CandidateBarState.ChineseCandidates) LocalCorrectedCandidates.current[candidate].orEmpty() else "",
                        )
                    }

                    // 仅当左侧存在打字候选时才需要分隔线；纯联想态（无打字候选）下
                    // 该竖线会孤悬列表最左缘，属多余元素。
                    // 注意：分隔线在条件内，联想词 items 必须在条件外——纯联想态
                    // displayCandidates 为空，若一并包进条件会导致联想词整个不渲染。
                    if (displayCandidates.isNotEmpty() && displayAssociation.isNotEmpty()) {
                        item(key = "divider") {
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(20.dp)
                                    .background(protectedVisuals.dividerColor.copy(alpha = 0.5f))
                                    .padding(horizontal = 4.dp)
                            )
                        }
                    }

                    itemsIndexed(displayAssociation, key = { index, _ -> "assoc-$index" }) { index, candidate ->
                        val assocState = state as? CandidateBarState.AssociationOnly
                        CandidateItem(
                            text = candidateNumberedText(candidate, index, showNumberLabels && assocState != null),
                            index = -1,
                            onClick = { callbacks.onAssociationSelect?.invoke(index) },
                            textColor = protectedVisuals.textColor,
                            comment = if (showComments) displayComments.getOrElse(index) { "" } else "",
                            isSelected = assocState?.highlightIndex == index,
                            accentColor = protectedVisuals.accentColor,
                            selectedTextColor = protectedVisuals.selectedTextColor,
                            opaqueComment = protectedVisuals.opaqueComments,
                            textShadow = protectedVisuals.textShadow,
                            fontSize = candidateTextSize.sp,
                            candidateFontFamily = candidateFontFamily,
                            commentFontFamily = commentFontFamily,
                            modifier = Modifier.testTag("bar-association:$index"),
                        )
                    }
                }
            }

            if (state !is CandidateBarState.Idle) Spacer(modifier = Modifier.width(8.dp))

            when {
                state is CandidateBarState.Idle -> {
                    // 显示内联建议时隐藏工具栏按钮区，把宽度让给建议；logo 与
                    // 收起按钮保留，退格回到 idle 时的状态感知不变
                    if (inlineSuggestions.isEmpty() && splitGapX != null) {
                        // 分体：图标按两侧宽度比例分到中缝左右，各自整格可点。
                        SplitGapRow(splitGapX, Modifier.weight(1f).fillMaxHeight(), left = { leftWidth, rightWidth ->
                            ToolbarHalf(toolbarActions, protectedVisuals, iconButtonTint, voiceAmplitude, voiceRecognitionState,
                                callbacks.onReorderToolbar, leftWidth, rightWidth, rightSide = false)
                        }, right = { leftWidth, rightWidth ->
                            ToolbarHalf(toolbarActions, protectedVisuals, iconButtonTint, voiceAmplitude, voiceRecognitionState,
                                callbacks.onReorderToolbar, leftWidth, rightWidth, rightSide = true)
                        })
                    } else if (inlineSuggestions.isEmpty()) {
                        ReorderableToolbar(toolbarActions, protectedVisuals.accentColor, callbacks.onReorderToolbar,
                            modifier = Modifier.weight(1f)) { action ->
                            ToolbarActionButton(action, protectedVisuals, iconButtonTint, voiceAmplitude, voiceRecognitionState,
                                modifier = Modifier.fillMaxWidth())
                        }
                    }

                    if (callbacks.onHideKeyboard != null) {
                        Spacer(modifier = Modifier.width(4.dp))
                        KeyboardToolbarButton(callbacks.onHideKeyboard, iconButtonContainer,
                            modifier = Modifier.testTag("toolbar-hide"), slotWidth = toolbarSlot) {
                            // 语音听写中：收起成底部悬浮条，语音继续。
                            Icon(if (isVoiceSticky) Icons.Default.KeyboardDoubleArrowDown else Icons.Default.KeyboardArrowDown,
                                contentDescription = if (isVoiceSticky) "收起成悬浮条" else "收起键盘",
                                tint = protectedVisuals.textColor, modifier = Modifier.size(24.dp))
                        }
                    }
                }
                candidatePageExpanded -> {
                    callbacks.onBack?.let {
                        CandidateExpansionButton(it, iconButtonContainer, protectedVisuals.accentColor, expanded = true, rotation = expansionRotation,
                            restingFill = edgeButtonFill, slotWidth = edgeSlot, cornerRadius = edgeCorner, continuousPill = true)
                    }
                }
                displayAssociation.isNotEmpty() && callbacks.onClearAssociation != null && callbacks.onCancelInput == null -> {
                    val clearInteractionSource = remember { MutableInteractionSource() }
                    val isClearPressed by clearInteractionSource.collectIsPressedAsState()

                    Spacer(modifier = Modifier.width(4.dp))
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(28.dp)
                            .background(protectedVisuals.dividerColor).padding(end = 1.dp)
                    )
                    Box(
                        modifier = Modifier
                            .width(30.dp)
                            .height(24.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (isClearPressed) (if (protectedVisuals.isDarkTheme) Color.White.copy(alpha = 0.15f) else Color.Black.copy(
                                    alpha = 0.1f
                                ))
                                else Color.Transparent
                            )
                            .clickable(
                                interactionSource = clearInteractionSource,
                                indication = null,
                                onClick = { callbacks.onClearAssociation() }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "清空",
                            color = if (isClearPressed) protectedVisuals.textColor.copy(alpha = 0.6f) else protectedVisuals.textColor,
                            fontSize = 11.sp
                        )
                    }
                }
                hasAnyMore && callbacks.onShowMoreCandidates != null -> {
                    CandidateExpansionButton(callbacks.onShowMoreCandidates,
                        iconButtonContainer, protectedVisuals.accentColor, expanded = false, rotation = expansionRotation,
                        restingFill = edgeButtonFill, slotWidth = edgeSlot, cornerRadius = edgeCorner, continuousPill = true)
                }
            }
            if (state !is CandidateBarState.Idle) {
                toolbarActions.filter { it.active }.forEach { action ->
                    ToolbarActionButton(action, protectedVisuals, iconButtonTint, voiceAmplitude, voiceRecognitionState)
                }
            }
        }
    }
}

/**
 * 工具栏一格里的胶囊：随格宽向两边拉长（参照 Gboard 大屏），窄格退为圆。
 * 中间按钮的按下/开启底色、菜单与收起按钮的底色、拖动排序的外框共用这一尺寸。
 */
internal fun toolbarPillSize(slot: androidx.compose.ui.unit.Dp): androidx.compose.ui.unit.DpSize {
    // 高 32dp，在 44dp 的栏里上下各留 6dp；窄格（悬浮小窗）缩成圆，左右至少留 4dp，不压面板边框。
    val width = (slot - 12.dp).coerceIn(28.dp, 112.dp)
    return if (width > 40.dp) androidx.compose.ui.unit.DpSize(width, 32.dp)
        else androidx.compose.ui.unit.DpSize(minOf(width, 32.dp), minOf(width, 32.dp))
}

@Composable
internal fun ToolbarActionButton(
    action: ToolbarAction, visuals: CandidateBarVisuals, tint: Color,
    amplitude: Float, voiceState: RecognitionState,
    source: MutableInteractionSource = remember { MutableInteractionSource() },
    modifier: Modifier = Modifier,
) {
    val voiceActive = action.active && action.item.id == "voice"
    // 外圈（胶囊底色）固定，内圈只跟随实时音量；峰值按分贝映射，正常说话也能明显撑开。
    val voiceLevel by animateFloatAsState(
        if (voiceActive && voiceState == RecognitionState.LISTENING) microphoneLevel(amplitude) else 0f,
        animationSpec = tween(100), label = "microphoneLevel")
    val pressed by source.collectIsPressedAsState()
    // Hover/focus events must not cancel a release fade and leave a permanent glow.
    val pressAlpha by animateFloatAsState(if (pressed) 1f else 0f,
        animationSpec = tween(if (pressed) 0 else 300), label = "toolbar-press")
    // The disc appears only while pressed or on (no resting disc, glass or not). Under glass
    // the active tool is filled solidly with an inverted glyph; voice keeps its pulsing glow.
    val glassActiveFill = visuals.glassControlBackground?.takeIf { action.active && !voiceActive }?.let { fill ->
        val surface = fill.copy(alpha = 1f)
        if (textContrast(visuals.accentColor, surface) >= 3f) visuals.accentColor.copy(alpha = 1f) else tint.copy(alpha = 1f)
    }
    val iconTint = when {
        glassActiveFill != null -> if (glassActiveFill.luminance() > 0.5f) Color.Black else Color.White
        action.active -> visuals.accentColor
        else -> tint
    }
    // 大屏参照 Gboard：整格都可点，按下/开启的底色是随格宽向两边拉长的胶囊；窄格仍是圆形。
    val listening = voiceActive && voiceState == RecognitionState.LISTENING
    BoxWithConstraints(
        modifier.widthIn(min = 36.dp).height(40.dp)
            // 音量光圈画在不裁切的外层：始终正圆、比图标大，宽格也不会被拉成胶囊。
            .then(if (listening) Modifier.microphoneLevelHalo({ voiceLevel }, visuals.accentColor) else Modifier)
            .semantics {
                selected = action.active
                if (voiceActive) stateDescription = if (voiceState == RecognitionState.PROCESSING) "正在转录" else "正在聆听，点击结束"
            }
            .clickable(interactionSource = source, indication = null, onClick = action.onClick),
        contentAlignment = Alignment.Center,
    ) {
    Box(
        Modifier.size(if (listening) androidx.compose.ui.unit.DpSize(36.dp, 36.dp) else toolbarPillSize(maxWidth))
            .clip(CircleShape)
            .background(if (listening) Color.Transparent else glassActiveFill ?: visuals.glassControlBackground?.let { fill ->
                if (action.active) androidx.compose.ui.graphics.lerp(fill, visuals.accentColor, 0.18f).copy(alpha = fill.alpha)
                else fill.copy(alpha = fill.alpha * pressAlpha)
            } ?: if (action.active) visuals.accentColor.copy(alpha = 0.28f)
                else if (visuals.isDarkTheme) Color.White.copy(alpha = 0.25f * pressAlpha)
                else Color(0xFFE0E0E0).copy(alpha = pressAlpha)),
        contentAlignment = Alignment.Center,
    ) {
        if (voiceActive && voiceState == RecognitionState.PROCESSING) {
            androidx.compose.material3.CircularProgressIndicator(Modifier.size(36.dp), color = visuals.accentColor, strokeWidth = 2.dp)
        }
        ToolbarButtonIcon(action.item, tint = iconTint, modifier = Modifier.size(22.dp), active = action.active)
    }
    }
}

/** 麦克风峰值（0~1 线性）转成 0~1 的显示音量：-50 dB 以下当静音，-15 dB 以上算满，小声说话也能明显撑开。 */
internal fun microphoneLevel(peak: Float): Float {
    if (peak <= 0f) return 0f
    val db = 20f * kotlin.math.log10(peak.coerceAtMost(1f))
    return ((db + 50f) / 35f).coerceIn(0f, 1f)
}

/**
 * 麦克风音量光圈：以控件中心画正圆，静音 30dp（比 22dp 图标大一圈），满音量 42dp。
 * 展开键盘的工具栏和收起后的悬浮条共用，外观一致。
 */
internal fun Modifier.microphoneLevelHalo(level: () -> Float, color: Color): Modifier = drawBehind {
    val value = level()
    drawCircle(color.copy(alpha = 0.30f + 0.30f * value), radius = (30.dp.toPx() + 12.dp.toPx() * value) / 2f)
}

@Composable
private fun ClipboardPreviewBar(
    candidates: List<String>,
    visuals: CandidateBarVisuals,
    callbacks: CandidateBarCallbacks,
    iconButtonContainer: Color,
    image: com.kingzcheung.xime.clipboard.ClipboardImage? = null,
) {
    val previews = remember(candidates) { candidates.map { it.replace(Regex("[\\r\\n]+"), " ") } }
    val textMeasurer = rememberTextMeasurer()
    val textStyle = LocalTextStyle.current.merge(TextStyle(fontSize = 15.sp))
    val density = LocalDensity.current
    val tallestPreviewPx = previews.maxOfOrNull {
        textMeasurer.measure(it.take(80), style = textStyle, maxLines = 1, softWrap = false).size.height
    } ?: 0
    val verticalTextPadding = with(density) { ((44.dp - tallestPreviewPx.toDp()) / 2).coerceIn(0.dp, 7.dp) }
    val capsuleColor = visuals.preeditBackgroundColor.takeIf { it != Color.Unspecified }
        ?: MaterialTheme.colorScheme.surfaceContainer
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        KeyboardBackButton({ callbacks.onDismissClipboardPreview?.invoke() },
            iconButtonContainer, visuals.textColor, label = "返回工具栏")
        BoxWithConstraints(Modifier.weight(1f).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
            // Reserve space for both icons and the scroll area's padding. Short previews
            // size the capsule to their text; long previews keep the full scroll viewport.
            val chromeWidth = 82.dp
            val maxTextWidthPx = with(density) { (maxWidth - chromeWidth).coerceAtLeast(0.dp).roundToPx() }
            var textWidthPx = if (image != null) with(density) { 64.dp.roundToPx() }.coerceAtMost(maxTextWidthPx) else 0
            if (image == null) for ((index, preview) in previews.withIndex()) {
                if (textWidthPx >= maxTextWidthPx || preview.length > 80) {
                    textWidthPx = maxTextWidthPx
                    break
                }
                val itemWidth = textMeasurer.measure(preview, style = textStyle,
                    maxLines = 1, softWrap = false).size.width
                val itemPadding = if (index == 0) 0 else with(density) { 16.dp.roundToPx() }
                textWidthPx = (textWidthPx + itemWidth + itemPadding).coerceAtMost(maxTextWidthPx)
            }
            val capsuleWidth = (chromeWidth + with(density) { textWidthPx.toDp() }).coerceAtMost(maxWidth)
            Row(
                Modifier.width(capsuleWidth).heightIn(min = 36.dp).testTag("clipboard-preview-pill")
                    .clip(CircleShape)
                    .background(capsuleColor)
                    .border(1.dp, visuals.accentColor.copy(alpha = 0.45f), CircleShape)
                    .padding(start = 12.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.ContentPaste, contentDescription = null,
                    tint = visuals.textColor, modifier = Modifier.size(18.dp))
                if (image != null) {
                    ClipboardImagePreview(image,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                        onPaste = { callbacks.onClipboardImageSelect?.invoke(image) })
                } else LazyRow(Modifier.weight(1f).padding(horizontal = 8.dp).testTag("clipboard-preview-scroll")) {
                    itemsIndexed(previews) { index, preview ->
                        // 预览可横向滚动全部内容；换行仅在显示时折成空格，粘贴仍使用原文。
                        Text(preview, color = visuals.textColor, fontSize = 15.sp, maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.testTag("clipboard-preview-text:$index")
                                .clickable { callbacks.onCandidateSelect(index) }
                                .padding(vertical = verticalTextPadding, horizontal = if (index == 0) 0.dp else 8.dp))
                    }
                }
                Box(Modifier.size(30.dp).clip(CircleShape)
                    .then(if (image != null) Modifier.testTag("clipboard-image-more") else Modifier)
                    .clickable {
                        (callbacks.onOpenClipboard ?: if (image != null) callbacks.onExpandClipboardImages else null)?.invoke()
                    },
                    contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Filled.FormatListBulleted, contentDescription = "打开剪贴板",
                        tint = visuals.accentColor, modifier = Modifier.size(22.dp))
                }
            }
        }
        if (callbacks.onHideKeyboard != null) {
            KeyboardToolbarButton(callbacks.onHideKeyboard, iconButtonContainer,
                modifier = Modifier.testTag("toolbar-hide")) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "收起键盘",
                    tint = visuals.textColor, modifier = Modifier.size(24.dp))
            }
        } else {
            Spacer(Modifier.size(40.dp))
        }
    }
}

/** Use the exact same explicit primary style for rendering and width measurement. */
internal fun candidatePrimaryTextStyle(
    fontSize: androidx.compose.ui.unit.TextUnit,
    fontFamily: androidx.compose.ui.text.font.FontFamily,
    isSelected: Boolean,
) = TextStyle(
    fontSize = fontSize,
    fontFamily = fontFamily,
    fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
    lineHeight = (fontSize.value * 1.35f).sp,
    platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false),
)

/** 当前候选中用户以前打过的词（按文字），由键盘根部提供。 */
internal val LocalLearnedCandidates = androidx.compose.runtime.compositionLocalOf<Set<String>> { emptySet() }
internal val LocalCorrectedCandidates = androidx.compose.runtime.compositionLocalOf<Map<String, String>> { emptyMap() }

private val CorrectionRed = Color(0xFFE53935)

/** 纠错候选的拼音只显示改正后的读音（所打字母已在输入框里），改动的字母标红：补上的、换成的、对调的；多打的不再显示。 */
internal fun correctionAnnotatedString(marks: String, plain: Color): androidx.compose.ui.text.AnnotatedString =
    androidx.compose.ui.text.buildAnnotatedString {
        val keep = androidx.compose.ui.text.SpanStyle(color = plain)
        val changed = androidx.compose.ui.text.SpanStyle(color = CorrectionRed)
        for (mark in com.kingzcheung.xime.rime.CorrectionMarks.parse(marks)) when (mark) {
            is com.kingzcheung.xime.rime.CorrectionMark.Keep -> withStyle(keep) { append(mark.letter) }
            is com.kingzcheung.xime.rime.CorrectionMark.Extra -> Unit
            is com.kingzcheung.xime.rime.CorrectionMark.Missing -> withStyle(changed) { append(mark.letter) }
            is com.kingzcheung.xime.rime.CorrectionMark.Replace -> withStyle(changed) { append(mark.correct) }
            is com.kingzcheung.xime.rime.CorrectionMark.Swap -> withStyle(changed) { append(mark.correct) }
            com.kingzcheung.xime.rime.CorrectionMark.Gap -> withStyle(keep) { append(' ') }
        }
    }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CandidateItem(
    text: String,
    index: Int,
    onClick: () -> Unit,
    textColor: Color,
    comment: String = "",
    isSelected: Boolean = false,
    accentColor: Color = Color(0xFF1A73E8),
    selectedTextColor: Color = Color(0xFF1A73E8),
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier,
    fontSize: androidx.compose.ui.unit.TextUnit = 19.sp,
    candidateFontFamily: androidx.compose.ui.text.font.FontFamily = androidx.compose.ui.text.font.FontFamily.Default,
    commentFontFamily: androidx.compose.ui.text.font.FontFamily = androidx.compose.ui.text.font.FontFamily.Default,
    onLongClick: (() -> Unit)? = null,
    opaqueComment: Boolean = false,
    textShadow: androidx.compose.ui.graphics.Shadow? = null,
    /** 用户以前打过的词：文字右上角画一个小编辑图标。 */
    learned: Boolean = false,
    /** 纠错来的候选：引擎给的逐音节纠正，代替拼音注释标出怎么改的。 */
    correction: String = "",
    /** Shift 拖动指着别的候选时由候选栏统一画底色，这里先不画，保证只亮一个。 */
    selectionFill: Boolean = true,
    /** 候选栏还没滑动时藏起来的候选：不能点，也不接 Shift 拖动。 */
    interactive: Boolean = true,
) {
    // 登记位置给 Shift 拖动：从 Shift 拖到这里松手，按住 Shift 选它（释义模式上屏英文）。
    val dropId = remember { Any() }
    val currentClick by rememberUpdatedState(onClick)
    DisposableEffect(dropId) { onDispose { CandidateDropTargets.shared.remove(dropId) } }
    if (!interactive) SideEffect { CandidateDropTargets.shared.remove(dropId) }
    Row(
        modifier = modifier
            .then(if (interactive) Modifier.onGloballyPositioned {
                CandidateDropTargets.shared.put(dropId, it.boundsInRoot()) { currentClick() } } else Modifier)
            .height(candidateItemHeight(fontSize))
            .clip(RoundedCornerShape(5.dp))
            .background(
                if (isSelected && selectionFill) accentColor.copy(alpha = 0.2f)
                else Color.Transparent
            )
            .semantics { selected = isSelected }
            .then(if (interactive) Modifier.combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ) else Modifier)
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            modifier = Modifier.candidateBaseline(fontSize),
            color = if (isSelected) selectedTextColor else textColor,
            fontSize = fontSize,
            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
            maxLines = 1,
            fontFamily = candidateFontFamily,
            style = candidatePrimaryTextStyle(fontSize, candidateFontFamily, isSelected).copy(shadow = textShadow),
            overflow = TextOverflow.Ellipsis,
        )
        if (learned) {
            androidx.compose.material3.Icon(
                imageVector = androidx.compose.material.icons.Icons.Filled.Edit,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.align(Alignment.Top).size(with(LocalDensity.current) { (fontSize.value * 0.45f).sp.toDp() })
                    .testTag("candidate-learned:$index"),
            )
        }
        if (correction.isNotEmpty() || comment.isNotEmpty()) {
            Spacer(modifier = Modifier.width(3.dp))
            val commentColor = if (isSelected) selectedTextColor.copy(alpha = if (opaqueComment) 1f else 0.6f) else textColor.copy(alpha = if (opaqueComment) 1f else 0.5f)
            Text(
                text = if (correction.isNotEmpty()) correctionAnnotatedString(correction, commentColor)
                    else androidx.compose.ui.text.AnnotatedString(comment),
                modifier = Modifier.candidateBaseline(fontSize),
                color = commentColor,
                fontSize = (fontSize.value * 11f / 19f).sp,
                fontWeight = FontWeight.Normal,
                maxLines = 1,
                fontFamily = commentFontFamily,
                style = LocalTextStyle.current.copy(shadow = textShadow),
            )
        }
    }
}

/** Reserve the same text height before asynchronous candidates arrive. */
@Composable
internal fun candidateItemHeight(fontSize: androidx.compose.ui.unit.TextUnit): androidx.compose.ui.unit.Dp =
    with(LocalDensity.current) { (fontSize.value * 1.35f).sp.toDp() } + 4.dp

/** 收起时向上、展开时向下；沿用工具栏的同一按钮与图标。 */
@Composable
private fun CandidateExpansionButton(
    onClick: () -> Unit,
    background: Color,
    foreground: Color,
    expanded: Boolean,
    rotation: Float,
    restingFill: Color = Color.Transparent,
    slotWidth: androidx.compose.ui.unit.Dp? = null,
    cornerRadius: androidx.compose.ui.unit.Dp? = null,
    continuousPill: Boolean = false,
) {
    KeyboardToolbarButton(onClick, background, restingFill = restingFill, slotWidth = slotWidth, cornerRadius = cornerRadius,
        continuousPill = continuousPill,
        modifier = Modifier.testTag("candidate-expansion").semantics { stateDescription = if (expanded) "已展开" else "已收起" }) {
        Icon(Icons.Default.KeyboardArrowUp,
            contentDescription = if (expanded) "返回键盘" else "展开候选词",
            tint = foreground, modifier = Modifier.size(24.dp).rotate(rotation))
    }
}

/** Shared by the screen keyboard preview and the hardware caret surface. */
@Composable
internal fun PreeditLabel(text: String, visuals: CandidateBarVisuals, onEdit: (() -> Unit)? = null,
    modifier: Modifier = Modifier, followTextTail: Boolean = false) {
    val scrollState = rememberScrollState()
    // The input preview must expose newly typed syllables once it reaches the available width.
    // A manual scroll remains untouched until the text or viewport changes.
    LaunchedEffect(text, followTextTail, scrollState.maxValue) {
        if (followTextTail && scrollState.maxValue != Int.MAX_VALUE) {
            scrollState.scrollTo(scrollState.maxValue)
        }
    }
    val background = if (visuals.preeditBackgroundColor != Color.Unspecified) visuals.preeditBackgroundColor
        else if (visuals.textColor.luminance() > 0.5f) Color(0xFF2D2F31) else Color(0xFFFAFAFA)
    Box(modifier.preeditSurface(background)
        .testTag("candidate-preedit")
        .then(if (onEdit != null) Modifier.clickable(role = Role.Button,
            onClickLabel = "编辑拼音", onClick = onEdit) else Modifier),
        contentAlignment = Alignment.CenterStart) {
        val shownText = if (onEdit != null) com.kingzcheung.xime.rime.pinyinPreviewText(text) else text
        // 光标像系统文字光标一样闪（530ms 一亮一灭，用户 10-21 同意）。节拍挂在拼音条上而不是光标位置上：
        // 移动光标、光标暂时到末尾不显示时都接着原来的节拍走，不会每移一下又从头亮起。
        val blink = androidx.compose.animation.core.rememberInfiniteTransition(label = "preedit-caret")
        val caretAlpha by blink.animateFloat(1f, 0f, androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.keyframes { durationMillis = 1060; 1f at 0; 1f at 529; 0f at 530; 0f at 1059 }),
            label = "preedit-caret-alpha")
        Text(preeditWithCaret(shownText, visuals.accentColor, visuals.textColor, visuals.selectedTextColor, caretAlpha),
            color = visuals.textColor.copy(alpha = if (visuals.opaqueComments) 1f else 0.9f), fontSize = PreeditStyle.FontSize, maxLines = 1,
            softWrap = false, style = LocalTextStyle.current.copy(shadow = visuals.textShadow), modifier = Modifier.horizontalScroll(scrollState).testTag("candidate-preedit-text"))
    }
}

/** 选中底色上一次对应的候选组和选中项；只是记录，不触发重组。 */
private class HighlightMove {
    var candidates: List<String>? = null
    var index = -1
    var animateNext = false
}

/** Layout history only; updating it after composition must not schedule another recomposition. */
private class CandidateStripViewport { var firstIndex: Int = 0 }

/** 平时显示的那几个候选；[oversized]：只有一个且比整条还宽（放不全，展开页里要再给它完整的一格）。 */
private class CandidateStripWindow(val split: CandidateWindowSplit, val oversized: Boolean)

/** One measured row. Keyboard focus can move its window without changing candidate identities. */
@Composable
internal fun FixedCandidateStrip(
    candidates: List<String>, associations: List<String> = emptyList(), comments: List<String>, visuals: CandidateBarVisuals,
    callbacks: CandidateBarCallbacks, fontSize: androidx.compose.ui.unit.TextUnit,
    modifier: Modifier = Modifier,
    highlightIndex: Int = 0,
    showNumberLabels: Boolean = false,
    itemSpacing: androidx.compose.ui.unit.Dp = 4.dp,
    /** 分体中缝（窗口横坐标）；候选先排左侧，越过中缝接着排右侧。 */
    gapWindowX: Pair<Float, Float>? = null,
    /** 能左右滑动看后面的候选；平时仍只显示放得下的那几个，手指一滑才露出后面的。分体中缝时不滑。 */
    scrollable: Boolean = false,
    /** 手指开始滑候选栏（展开着的话收起展开页，免得同一个候选出现两次）。 */
    onUserScroll: (() -> Unit)? = null,
    /** 滑到最后几个候选了（接着加载展开列表）。 */
    onNearEnd: (() -> Unit)? = null,
    /** 换了这个值（新的编码）就回到最前面。 */
    scrollKey: Any? = null,
) {
    val measurer = rememberTextMeasurer()
    var stripOriginX by remember { mutableStateOf(Float.NaN) }
    val density = LocalDensity.current
    val inheritedTextStyle = LocalTextStyle.current
    val commentsAllowed = rememberCandidateCommentsAllowed()
    // 选中底色由整条统一画，换选中的候选时从旧候选滑到新候选（用户 10-21 选 1）；
    // 候选一变（打字、翻页）就直接落到新位置，打字时什么都不动。
    var stripRootOrigin by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    var focusedBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    // 滑动时选中的候选滑出了候选栏：底色先不画，滑回来再画。
    var focusedOnScreen by remember { mutableStateOf(true) }
    val highlightBox = remember { androidx.compose.animation.core.Animatable(androidx.compose.ui.geometry.Rect.Zero,
        androidx.compose.ui.geometry.Rect.VectorConverter) }
    var highlightShown by remember { mutableStateOf(false) }
    // 只有同一组候选里换了选中项才滑；在组合时记下，等新位置量出来再决定滑还是直接落。
    val highlightMove = remember { HighlightMove() }
    if (highlightMove.candidates != candidates) highlightMove.animateNext = false
    else if (highlightMove.index != highlightIndex) highlightMove.animateNext = true
    highlightMove.candidates = candidates
    highlightMove.index = highlightIndex
    // 位置只在这里读，不让候选栏因为量位置而多重组一次。
    androidx.compose.runtime.LaunchedEffect(highlightBox) {
        androidx.compose.runtime.snapshotFlow { focusedBounds }.collectLatest { target ->
            if (target == null) return@collectLatest
            if (highlightShown && highlightMove.animateNext) {
                highlightMove.animateNext = false
                highlightBox.animateTo(target, androidx.compose.animation.core.tween(CANDIDATE_SLIDE_MS * 2,
                    easing = androidx.compose.animation.core.FastOutSlowInEasing))
            } else { highlightBox.snapTo(target); highlightShown = true }
        }
    }
    val highlightAccent = visuals.accentColor
    val highlightVisible = candidates.isNotEmpty()
    BoxWithConstraints(modifier.clipToBounds().testTag("candidate-fixed-strip")
        .onGloballyPositioned { stripOriginX = it.positionInWindow().x; stripRootOrigin = it.boundsInRoot().topLeft }
        .drawBehind {
            val rect = highlightBox.value
            if (highlightShown && highlightVisible && focusedOnScreen && CandidateDropTargets.shared.hoveredBounds == null && rect.width > 0f)
                drawRoundRect(highlightAccent.copy(alpha = 0.2f), rect.topLeft - stripRootOrigin, rect.size,
                    androidx.compose.ui.geometry.CornerRadius(5.dp.toPx()))
        }) {
        val panelWidth = maxWidth
        // 中缝在本条内的位置（像素）；未测到位置前按整条排。
        val localGap = gapWindowX?.takeUnless { stripOriginX.isNaN() }?.let { (l, r) ->
            val left = (l - stripOriginX).coerceIn(0f, constraints.maxWidth.toFloat())
            val right = (r - stripOriginX).coerceIn(left, constraints.maxWidth.toFloat())
            if (right - left >= 1f && left > 0f) left.toInt() to right.toInt() else null
        }
        val shownComments = if (candidateCommentsVisible(maxWidth.value, density.fontScale,
            fontSize.value, commentsAllowed)) comments else emptyList()
        val all = candidates + associations
        val focused = highlightIndex.takeIf { it in candidates.indices } ?: 0
        val viewport = remember(candidates, shownComments) { CandidateStripViewport() }
        val window = remember(viewport, all, shownComments, focused, fontSize, density, measurer, inheritedTextStyle, showNumberLabels, itemSpacing, constraints.maxWidth, AppFonts.candidateFontFamily, AppFonts.commentFontFamily, localGap) {
            val widthAt: (Int) -> Int = { index ->
                val text = candidateNumberedText(all[index], index, showNumberLabels && index < candidates.size)
                val primary = measurer.measure(AnnotatedString(text),
                    candidatePrimaryTextStyle(fontSize, AppFonts.candidateFontFamily, index == focused),
                    softWrap = false).size.width
                val comment = shownComments.getOrElse(index) { "" }
                val secondary = if (comment.isEmpty()) 0 else measurer.measure(AnnotatedString(comment),
                    inheritedTextStyle.merge(TextStyle(fontSize = (fontSize.value * 11f / 19f).sp,
                        fontWeight = FontWeight.Normal, fontFamily = AppFonts.commentFontFamily)), softWrap = false).size.width + with(density) { 3.dp.roundToPx() }
                // Padding rounds each side independently at fractional device densities.
                primary + secondary + with(density) { 2 * 4.dp.roundToPx() }
            }
            if (localGap != null) {
                CandidateStripWindow(splitCandidateWindow(all.size, focused, localGap.first, constraints.maxWidth - localGap.second,
                    with(density) { itemSpacing.roundToPx() }, viewport.firstIndex, widthAt), oversized = false)
            } else {
                val range = candidateWindow(all.size, focused, constraints.maxWidth,
                    with(density) { itemSpacing.roundToPx() }, windowStart = viewport.firstIndex, widthAt = widthAt)
                CandidateStripWindow(CandidateWindowSplit(range, Int.MAX_VALUE),
                    oversized = range.count() == 1 && widthAt(range.first) > constraints.maxWidth)
            }
        }
        val visibleIndices = window.split.range
        val splitLeftCount = window.split.leftCount
        androidx.compose.runtime.SideEffect {
            viewport.firstIndex = visibleIndices.first.takeUnless { visibleIndices.isEmpty() } ?: 0
            // Expanded pagination removes a prefix only. A shifted window must not hide earlier items.
            // 栏里放不全的长候选不算已显示：展开页从它开始排，能看到全文。
            callbacks.onVisibleCandidatesChanged?.invoke(if (visibleIndices.first == 0 && !window.oversized)
                candidates.take(visibleIndices.count()) else emptyList())
            callbacks.onVisibleAssociationsChanged?.invoke(visibleIndices.filter { it >= candidates.size }
                .map { all[it] })
        }
        // 分体：前半段排在中缝左边，后半段从中缝右边开始。
        val leftCount = splitLeftCount
        val segments = if (localGap == null) listOf(visibleIndices.toList() to 0)
            else listOf(visibleIndices.take(leftCount) to 0, visibleIndices.drop(leftCount) to localGap.second)
        // Shift 拖动指着候选时，选中底色改由候选栏画在手指指着的那个上，原来的先不画。
        val shiftDragFree = CandidateDropTargets.shared.hoveredBounds == null
        if (scrollable && localGap == null) {
            ScrollableCandidateRow(candidates, all, shownComments, visuals, callbacks, fontSize, focused, showNumberLabels,
                itemSpacing, visibleIndices, scrollKey, onUserScroll, onNearEnd,
                selectionFill = shiftDragFree && !highlightShown,
                onFocusedBounds = { focusedBounds = it; focusedOnScreen = true },
                onFocusedGone = { focusedOnScreen = false })
            return@BoxWithConstraints
        }
        segments.forEach { (segmentIndices, offsetPx) ->
        Row(Modifier.offset { androidx.compose.ui.unit.IntOffset(offsetPx, 0) },
            horizontalArrangement = Arrangement.spacedBy(itemSpacing), verticalAlignment = Alignment.CenterVertically) {
            segmentIndices.forEach { index ->
                CandidateItem(candidateNumberedText(all[index], index, showNumberLabels && index < candidates.size), index, {
                    if (index < candidates.size) callbacks.onCandidateSelect(index)
                    else callbacks.onAssociationSelect?.invoke(index - candidates.size)
                }, visuals.textColor,
                    comment = shownComments.getOrElse(index) { "" }, isSelected = index == focused,
                    accentColor = visuals.accentColor, selectedTextColor = visuals.selectedTextColor,
                    opaqueComment = visuals.opaqueComments,
                    textShadow = visuals.textShadow,
                    fontSize = fontSize, candidateFontFamily = AppFonts.candidateFontFamily,
                    commentFontFamily = AppFonts.commentFontFamily,
                    modifier = Modifier.widthIn(max = panelWidth).testTag("bar-candidate:$index")
                        .then(if (index == focused) Modifier.onGloballyPositioned { focusedBounds = it.boundsInRoot() } else Modifier),
                    onLongClick = if (index < candidates.size) callbacks.onCandidateLongPress?.let { action -> { action(index) } } else null,
                    learned = index < candidates.size && all[index] in LocalLearnedCandidates.current,
                    correction = if (index < candidates.size) LocalCorrectedCandidates.current[all[index]].orEmpty() else "",
                    selectionFill = shiftDragFree && !highlightShown)
            }
        }
        }
    }
}

/**
 * 能左右滑的候选行（用户 10-23）：平时和原来一样只显示放得下的那几个（[shown]），后面的先藏着；
 * 手指一滑就全部露出来，可以一直滑到展开列表里的候选。长候选不截断，滑过去就能看完。
 */
@Composable
private fun ScrollableCandidateRow(
    candidates: List<String>, all: List<String>, comments: List<String>, visuals: CandidateBarVisuals,
    callbacks: CandidateBarCallbacks, fontSize: androidx.compose.ui.unit.TextUnit, focused: Int,
    showNumberLabels: Boolean, itemSpacing: androidx.compose.ui.unit.Dp, shown: IntRange, scrollKey: Any?,
    onUserScroll: (() -> Unit)?, onNearEnd: (() -> Unit)?, selectionFill: Boolean,
    onFocusedBounds: (androidx.compose.ui.geometry.Rect) -> Unit,
    onFocusedGone: () -> Unit,
) {
    val start = shown.first.takeUnless { shown.isEmpty() } ?: 0
    // 新编码或键盘焦点换了一屏：直接从那一屏开始，不带上一次滑到的位置。
    val listState = remember(scrollKey, start) { androidx.compose.foundation.lazy.LazyListState(start) }
    var revealed by remember(listState) { mutableStateOf(false) }
    val currentUserScroll by rememberUpdatedState(onUserScroll)
    val currentNearEnd by rememberUpdatedState(onNearEnd)
    val candidateCount by rememberUpdatedState(candidates.size)
    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect {
            if (it is androidx.compose.foundation.interaction.DragInteraction.Start) {
                revealed = true
                currentUserScroll?.invoke()
            }
        }
    }
    LaunchedEffect(listState) {
        androidx.compose.runtime.snapshotFlow {
            revealed && (listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1) >= candidateCount - 4
        }.collect { if (it) currentNearEnd?.invoke() }
    }
    LazyRow(Modifier.fillMaxWidth().testTag("candidate-scroll-row"), state = listState,
        horizontalArrangement = Arrangement.spacedBy(itemSpacing), verticalAlignment = Alignment.CenterVertically) {
        items(all.size) { index ->
            val hidden = !revealed && index !in shown
            val numbered = index < candidates.size
            if (index == focused) DisposableEffect(Unit) { onDispose { onFocusedGone() } }
            CandidateItem(candidateNumberedText(all[index], index, showNumberLabels && numbered), index, {
                if (numbered) callbacks.onCandidateSelect(index)
                else callbacks.onAssociationSelect?.invoke(index - candidates.size)
            }, visuals.textColor,
                comment = comments.getOrElse(index) { "" }, isSelected = index == focused,
                accentColor = visuals.accentColor, selectedTextColor = visuals.selectedTextColor,
                opaqueComment = visuals.opaqueComments,
                textShadow = visuals.textShadow,
                fontSize = fontSize, candidateFontFamily = AppFonts.candidateFontFamily,
                commentFontFamily = AppFonts.commentFontFamily,
                modifier = if (hidden) Modifier.alpha(0f).clearAndSetSemantics {} else Modifier.testTag("bar-candidate:$index")
                    .then(if (index == focused) Modifier.onGloballyPositioned { onFocusedBounds(it.boundsInRoot()) } else Modifier),
                onLongClick = if (numbered) callbacks.onCandidateLongPress?.let { action -> { action(index) } } else null,
                learned = numbered && all[index] in LocalLearnedCandidates.current,
                correction = if (numbered) LocalCorrectedCandidates.current[all[index]].orEmpty() else "",
                selectionFill = selectionFill,
                interactive = !hidden)
        }
    }
}

// Latin and CJK fallback fonts have different ascents. Anchor actual baselines,
// not just differently sized text boxes, to the same position in the candidate row.
private fun Modifier.candidateBaseline(fontSize: androidx.compose.ui.unit.TextUnit): Modifier = layout { measurable, constraints ->
    val child = measurable.measure(constraints.copy(minHeight = 0))
    val height = (fontSize.toPx() * 1.35f).roundToInt().coerceIn(constraints.minHeight, constraints.maxHeight)
    val baseline = child[FirstBaseline]
    layout(child.width, height) {
        child.placeRelative(0, (fontSize.toPx() * 1.05f).roundToInt() - baseline)
    }
}

/** 分体工具栏的一侧：按两侧宽度比例分配图标；拖动排序后合回整条顺序保存。 */
@Composable
private fun ToolbarHalf(
    actions: List<ToolbarAction>, visuals: CandidateBarVisuals, tint: Color, amplitude: Float,
    voiceState: RecognitionState, onReorder: ((List<String>) -> Unit)?,
    leftWidth: androidx.compose.ui.unit.Dp, rightWidth: androidx.compose.ui.unit.Dp, rightSide: Boolean,
) {
    val total = (leftWidth.value + rightWidth.value).coerceAtLeast(1f)
    val leftCount = kotlin.math.round(actions.size * leftWidth.value / total).toInt().coerceIn(0, actions.size)
    val left = actions.take(leftCount)
    val right = actions.drop(leftCount)
    val mine = if (rightSide) right else left
    if (mine.isEmpty()) return
    val save: ((List<String>) -> Unit)? = onReorder?.let { persist ->
        { ids: List<String> ->
            persist(if (rightSide) left.map { it.item.id } + ids else ids + right.map { it.item.id })
        }
    }
    ReorderableToolbar(mine, visuals.accentColor, save, modifier = Modifier.fillMaxSize()) { action ->
        ToolbarActionButton(action, visuals, tint, amplitude, voiceState, modifier = Modifier.fillMaxWidth())
    }
}

/** 两端按钮平时的底相对中间按钮按下时的浓度；渲染对比测试里可以换值。 */
internal val LocalEdgeButtonStrength = androidx.compose.runtime.staticCompositionLocalOf { 0.45f }

/** 两端按钮的形状：圆（原样）、和空闲工具栏一样宽的胶囊、与键盘卡片同心的圆角胶囊。 */
internal enum class EdgeButtonStyle { CIRCLE, CAPSULE, CONCENTRIC }
internal val LocalEdgeButtonStyle = androidx.compose.runtime.staticCompositionLocalOf { EdgeButtonStyle.CAPSULE }

/** Shift 拖到候选时选中底色滑过去的时长；换选中候选时用它的两倍。 */
private const val CANDIDATE_SLIDE_MS = 120

/**
 * Shift 拖到候选上时（用户 10-21）：在候选栏里画出拖线的那一段（键盘那边只画到字母区为止），
 * 并把选中底色挪到手指指着的候选上（与平时选中候选同一样式，任何时候只亮一个）；换候选时底色滑过去（120ms）。
 * 只在绘制时读取拖动状态，不让候选栏重组。
 */
@Composable
private fun Modifier.shiftDragOverlay(rootOrigin: () -> androidx.compose.ui.geometry.Offset, accent: Color): Modifier {
    val drops = CandidateDropTargets.shared
    val hovered = drops.hoveredBounds
    // 底色和选中候选一样（强调色两成、5dp 圆角），不加边框；第一次指到候选时直接出现，之后换候选时滑过去。
    val box = remember { androidx.compose.animation.core.Animatable(androidx.compose.ui.geometry.Rect.Zero,
        androidx.compose.ui.geometry.Rect.VectorConverter) }
    var hadTarget by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(hovered) {
        if (hovered == null) { hadTarget = false; return@LaunchedEffect }
        if (!hadTarget) { box.snapTo(hovered); hadTarget = true }
        else box.animateTo(hovered, androidx.compose.animation.core.tween(CANDIDATE_SLIDE_MS))
    }
    return drawWithContent {
        drawContent()
        val origin = rootOrigin()
        val rect = box.value
        if (drops.hoveredBounds != null && rect.width > 0f) {
            drawRoundRect(accent.copy(alpha = 0.2f), rect.topLeft - origin, rect.size,
                androidx.compose.ui.geometry.CornerRadius(5.dp.toPx()))
        }
        drops.dragLine?.let { (from, to) ->
            drawLine(accent, from - origin, to - origin, strokeWidth = 6.dp.toPx(),
                cap = androidx.compose.ui.graphics.StrokeCap.Round)
        }
    }
}

/** 候选栏两端按钮从圆开始拉长成胶囊的候选栏宽度，和拉到最宽 52dp 的宽度。 */
private val EDGE_STRETCH_START = 940.dp
private val EDGE_STRETCH_FULL = 1400.dp

/**
 * 拼音里的编辑光标“|”用主题强调色加粗画，和拼音字母区分开（用户 10-21）。其余文字照旧。
 * 柔和蓝、黑白这类强调色和拼音几乎同色的主题，改用首选候选的选中字色；光标只是一道线，不垫底色（用户 10-21）。
 * [alpha] 是闪烁的当前亮度。
 */
internal fun preeditWithCaret(text: String, accent: Color, textColor: Color = Color.Unspecified,
                              selectedText: Color = accent, alpha: Float = 1f): AnnotatedString {
    val caret = text.indexOf(com.kingzcheung.xime.service.PREEDIT_CARET)
    if (caret < 0) return AnnotatedString(text)
    val style = if (textColor != Color.Unspecified && colorsClose(accent, textColor))
        androidx.compose.ui.text.SpanStyle(color = selectedText.copy(alpha = selectedText.alpha * alpha),
            fontWeight = FontWeight.Bold)
    else androidx.compose.ui.text.SpanStyle(color = accent.copy(alpha = accent.alpha * alpha), fontWeight = FontWeight.Bold)
    return androidx.compose.ui.text.buildAnnotatedString {
        append(text)
        addStyle(style, caret, caret + 1)
    }
}

/** 两个颜色肉眼几乎分不开（RGB 各分量差都很小）。 */
private fun colorsClose(a: Color, b: Color): Boolean =
    kotlin.math.abs(a.red - b.red) < 0.12f && kotlin.math.abs(a.green - b.green) < 0.12f &&
        kotlin.math.abs(a.blue - b.blue) < 0.12f
