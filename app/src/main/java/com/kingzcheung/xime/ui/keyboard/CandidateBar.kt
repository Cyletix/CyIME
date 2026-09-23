package com.kingzcheung.xime.ui.keyboard

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
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kingzcheung.xime.R
import com.kingzcheung.xime.keyboard.MainType
import com.kingzcheung.xime.keyboard.KeyboardPage
import com.kingzcheung.xime.keyboard.OverlayRoute
import com.kingzcheung.xime.keyboard.PanelType
import com.kingzcheung.xime.keyboard.ToolbarAction
import com.kingzcheung.xime.settings.SettingsPreferences
import com.kingzcheung.xime.speech.RecognitionState
import kotlinx.coroutines.flow.collectLatest

@Immutable
data class CandidateBarVisuals(
    val backgroundColor: Color,
    val textColor: Color,
    val dividerColor: Color,
    val accentColor: Color = Color(0xFF1A73E8),
    val selectedTextColor: Color = Color(0xFF1A73E8),
    val isDarkTheme: Boolean = false,
    val preeditBackgroundColor: Color = Color.Unspecified,
)

data class CandidateBarCallbacks(
    val onCandidateSelect: (Int) -> Unit,
    val onLogoClick: (() -> Unit)? = null,
    val onBack: (() -> Unit)? = null,
    val onHideKeyboard: (() -> Unit)? = null,
    val onShowMoreCandidates: (() -> Unit)? = null,
    val onClearAssociation: (() -> Unit)? = null,
    val onInputTextClick: (() -> Unit)? = null,
    val onAssociationSelect: ((Int) -> Unit)? = null,
    val onDismissClipboardPreview: (() -> Unit)? = null,
    val onOpenClipboard: (() -> Unit)? = null,
    val onCancelInput: (() -> Unit)? = null,
    val onReorderToolbar: ((List<String>) -> Unit)? = null,
    // 长按候选：抛事件给宿主（键盘视图内弹确认覆盖层，不弹独立窗口——
    // 焦点型弹窗会抢焦点导致 IME 被系统收起）。
    val onCandidateLongPress: ((Int) -> Unit)? = null
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
) {
    val configuration = LocalConfiguration.current
    val isLandscape = !isFloatingMode && configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val horizontalPadding = if (isLandscape && state is CandidateBarState.Idle) 50.dp else 8.dp
    val context = LocalContext.current

    // M3 角色色：图标按钮背景用 surface 与 primary 的混合色调（带种子色但不过于强烈），
    // 按压态用 onSurface 12% state layer
    val iconButtonContainer = androidx.compose.ui.graphics.lerp(
        MaterialTheme.colorScheme.surface,
        MaterialTheme.colorScheme.primary,
        0.15f
    )
    val iconButtonTint = MaterialTheme.colorScheme.onSurfaceVariant
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
    val showComments = SettingsPreferences.showCandidateComments(context)
    val inputTextLocation = SettingsPreferences.getInputTextLocation(context)
    val showInputBoxStyle = inputTextLocation == SettingsPreferences.INPUT_TEXT_INPUT_BOX
    val candidateTextSize = SettingsPreferences.getCandidateTextSize(context)
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
            val taken = s.candidates.take(20)
            // 候选栏按设置的"每页候选词数"显示引擎当前页，可左右滑动查看放不下的候选
            displayCandidates = taken
            displayComments = s.comments

            hasAnyMore = s.hasMore || candidateListState.canScrollForward
            showLeftIcon = false
            displayAssociation = remember(s.associationCandidates, taken, s.inputText, textMeasurer, candidateTextSize, showCompositionCancel, screenWidthPx, density) {
                if (s.associationCandidates.isEmpty()) {
                    // 常规输入没有追加联想时，避免每次按键测量整页候选文字。
                    emptyList()
                } else if (taken.isEmpty()) {
                    s.associationCandidates.take(PredictionManager.MAX_ASSOCIATION_COUNT)
                } else {
                    val measureText = { text: String ->
                        textMeasurer.measure(
                            text = AnnotatedString(text),
                            style = TextStyle(fontSize = candidateTextSize.sp)
                        ).size.width.toFloat()
                    }
                    val leftPx = with(density) { rowPaddingPx + if (showCompositionCancel) 44.dp.toPx() else 0f }
                    val rowWidthPx = screenWidthPx - leftPx - rightSidePx
                    val regularWidthPx = taken.sumOf { c ->
                        measureText(c).toDouble() + itemPaddingPx
                    }.toFloat()
                    val dividerWidthPx = with(density) { 9.dp.toPx() }
                    val availablePx = rowWidthPx - regularWidthPx - dividerWidthPx

                    var used = 0f
                    val result = mutableListOf<String>()
                    for (c in s.associationCandidates) {
                        val w =
                            measureText(c) + itemPaddingPx + (if (result.isEmpty()) 0f else spacingPx)
                        if (used + w <= availablePx) {
                            used += w
                            result.add(c)
                        } else break
                    }
                    result
                }
            }
        }
        is CandidateBarState.AssociationOnly -> {
            displayCandidates = emptyList()
            displayAssociation = s.candidates.take(PredictionManager.MAX_ASSOCIATION_COUNT)
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

    LaunchedEffect(displayCandidates) {
        candidateListState.scrollToItem(0)
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

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(visuals.backgroundColor)
            .padding(horizontal = horizontalPadding),
        verticalArrangement = Arrangement.Center,
    ) {
        if (showPreedit) {
            PreeditPreview(preeditText, visuals, onEditPreedit)
        }

        if (state is CandidateBarState.ClipboardDisplay) {
            ClipboardPreviewBar(state.candidates, visuals, callbacks)
            return@Column
        }

        Row(
            modifier = Modifier
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showCompositionCancel) {
                KeyboardBackButton({ callbacks.onCancelInput?.invoke() }, iconButtonContainer, visuals.textColor, label = "取消输入")
                Spacer(Modifier.width(4.dp))
            }
            if (showLeftIcon) {
                when (state) {
                    is CandidateBarState.Idle -> {
                        val panelHasToolbarBack = page is KeyboardPage.Overlay &&
                            (page.route is OverlayRoute.Menu || page.route is OverlayRoute.SchemaList || page.route is OverlayRoute.Edit ||
                                page.route is OverlayRoute.Emoji || page.route is OverlayRoute.Clipboard)
                        if ((page is KeyboardPage.Main && page.type == MainType.HANDWRITING || panelHasToolbarBack) && callbacks.onBack != null) {
                            KeyboardBackButton(callbacks.onBack, iconButtonContainer, visuals.textColor,
                                modifier = Modifier.testTag("toolbar-leading"))
                        } else {
                            KeyboardToolbarButton({ callbacks.onLogoClick?.invoke() }, iconButtonContainer,
                                modifier = Modifier.testTag("toolbar-leading")) {
                                Icon(painterResource(id = if (visuals.isDarkTheme) R.drawable.logo_dark else R.drawable.logo),
                                    contentDescription = "CyIME Logo", tint = Color.Unspecified, modifier = Modifier.size(20.dp))
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
                                tint = visuals.accentColor,
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
                                color = visuals.dividerColor,
                            )
                        }
                    }
                }
            }

            LazyRow(
                modifier = if (state is CandidateBarState.Idle) Modifier else Modifier.weight(1f),
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
                        textColor = visuals.textColor,
                        comment = if (showComments) {
                            when (val s = state) {
                                is CandidateBarState.ChineseCandidates -> s.comments.getOrElse(index) { "" }
                                is CandidateBarState.EnglishCandidates -> s.comments.getOrElse(index) { "" }
                                else -> ""
                            }
                        } else "",
                        isSelected = index == 0,
                        accentColor = visuals.accentColor,
                        selectedTextColor = visuals.selectedTextColor,
                        fontSize = candidateTextSize.sp,
                        candidateFontFamily = candidateFontFamily,
                        commentFontFamily = commentFontFamily
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
                                .background(visuals.dividerColor.copy(alpha = 0.5f))
                                .padding(horizontal = 4.dp)
                        )
                    }
                }

                itemsIndexed(displayAssociation, key = { index, _ -> "assoc-$index" }) { index, candidate ->
                    val assocState = state as? CandidateBarState.AssociationOnly
                    CandidateItem(
                        text = candidate,
                        index = -1,
                        onClick = { callbacks.onAssociationSelect?.invoke(index) },
                        textColor = visuals.textColor,
                        comment = displayComments.getOrElse(index) { "" },
                        isSelected = assocState?.highlightIndex == index,
                        accentColor = visuals.accentColor,
                        selectedTextColor = visuals.selectedTextColor,
                        fontSize = candidateTextSize.sp,
                        candidateFontFamily = candidateFontFamily,
                        commentFontFamily = commentFontFamily
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            when {
                state is CandidateBarState.Idle -> {
                    // 显示内联建议时隐藏工具栏按钮区，把宽度让给建议；logo 与
                    // 收起按钮保留，退格回到 idle 时的状态感知不变
                    if (inlineSuggestions.isEmpty()) {
                        ReorderableToolbar(toolbarActions, visuals.accentColor, callbacks.onReorderToolbar,
                            modifier = Modifier.weight(1f)) { action ->
                            ToolbarActionButton(action, visuals, iconButtonTint, voiceAmplitude, voiceRecognitionState)
                        }
                    }

                    if (callbacks.onHideKeyboard != null) {
                        Spacer(modifier = Modifier.width(4.dp))
                        KeyboardToolbarButton(callbacks.onHideKeyboard, iconButtonContainer,
                            modifier = Modifier.testTag("toolbar-hide")) {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "收起键盘",
                                tint = visuals.textColor, modifier = Modifier.size(24.dp))
                        }
                    }
                }
                candidatePageExpanded -> {
                    callbacks.onBack?.let {
                        CandidateExpansionButton(it, iconButtonContainer, visuals.accentColor, expanded = true, rotation = expansionRotation)
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
                            .background(visuals.dividerColor).padding(end = 1.dp)
                    )
                    Box(
                        modifier = Modifier
                            .width(30.dp)
                            .height(24.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (isClearPressed) (if (visuals.isDarkTheme) Color.White.copy(alpha = 0.15f) else Color.Black.copy(
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
                            color = if (isClearPressed) visuals.textColor.copy(alpha = 0.6f) else visuals.textColor,
                            fontSize = 11.sp
                        )
                    }
                }
                hasAnyMore && callbacks.onShowMoreCandidates != null -> {
                    CandidateExpansionButton(callbacks.onShowMoreCandidates,
                        iconButtonContainer, visuals.accentColor, expanded = false, rotation = expansionRotation)
                }
            }
            if (state !is CandidateBarState.Idle) {
                toolbarActions.filter { it.active }.forEach { action ->
                    ToolbarActionButton(action, visuals, iconButtonTint, voiceAmplitude, voiceRecognitionState)
                }
            }
        }
    }
}

@Composable
private fun ToolbarActionButton(
    action: ToolbarAction, visuals: CandidateBarVisuals, tint: Color,
    amplitude: Float, voiceState: RecognitionState,
) {
    val voiceActive = action.active && action.item.id == "voice"
    val pulse = if (voiceActive) {
        val transition = rememberInfiniteTransition(label = "microphone")
        val value by transition.animateFloat(0.35f, 1f,
            infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "microphoneGlow")
        value
    } else 0f
    val source = remember { MutableInteractionSource() }
    val pressAlpha = remember { Animatable(0f) }
    LaunchedEffect(source) {
        source.interactions.collectLatest { interaction ->
            when (interaction) {
                is PressInteraction.Press -> pressAlpha.snapTo(1f)
                is PressInteraction.Release, is PressInteraction.Cancel -> pressAlpha.animateTo(0f, tween(300))
                else -> Unit
            }
        }
    }
    Box(
        Modifier.padding(horizontal = 2.dp).size(40.dp).clip(CircleShape)
            .background(if (action.active) visuals.accentColor.copy(alpha = 0.28f)
                else if (visuals.isDarkTheme) Color.White.copy(alpha = 0.25f * pressAlpha.value)
                else Color(0xFFE0E0E0).copy(alpha = pressAlpha.value))
            .drawBehind {
                if (voiceActive) {
                    drawCircle(Brush.radialGradient(listOf(visuals.accentColor.copy(alpha = 0.55f), Color.Transparent)),
                        radius = size.minDimension * (0.36f + 0.14f * maxOf(pulse, amplitude.coerceIn(0f, 1f))))
                }
            }
            .semantics {
                selected = action.active
                if (voiceActive) stateDescription = if (voiceState == RecognitionState.PROCESSING) "正在转录" else "正在聆听，点击结束"
            }
            .clickable(interactionSource = source, indication = null, onClick = action.onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (voiceActive && voiceState == RecognitionState.PROCESSING) {
            androidx.compose.material3.CircularProgressIndicator(Modifier.size(36.dp), color = visuals.accentColor, strokeWidth = 2.dp)
        }
        ToolbarButtonIcon(action.item, tint = if (action.active) visuals.accentColor else tint, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun ClipboardPreviewBar(
    candidates: List<String>,
    visuals: CandidateBarVisuals,
    callbacks: CandidateBarCallbacks,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        KeyboardBackButton({ callbacks.onDismissClipboardPreview?.invoke() },
            visuals.textColor.copy(alpha = 0.12f), visuals.textColor, label = "返回工具栏")
        Box(Modifier.weight(1f).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
            Row(
                Modifier.fillMaxWidth().height(36.dp).clip(CircleShape)
                    .background(if (visuals.isDarkTheme) Color(0xFF242832) else Color(0xFFF3F5FA))
                    .border(1.dp, Brush.horizontalGradient(listOf(
                        Color(0xFF8D8DEF).copy(alpha = 0.5f),
                        Color(0xFF668ECC).copy(alpha = 0.3f),
                        Color(0xFF69BCAF).copy(alpha = 0.5f),
                    )), CircleShape)
                    .padding(start = 12.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.ContentPaste, contentDescription = null,
                    tint = visuals.textColor, modifier = Modifier.size(18.dp))
                LazyRow(Modifier.weight(1f).padding(horizontal = 8.dp).testTag("clipboard-preview-scroll")) {
                    itemsIndexed(candidates) { index, text ->
                        // 预览可横向滚动全部内容；换行仅在显示时折成空格，粘贴仍使用原文。
                        val preview = remember(text) { text.replace(Regex("[\\r\\n]+"), " ") }
                        Text(preview, color = visuals.textColor, fontSize = 15.sp, maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.testTag("clipboard-preview-text:$index")
                                .clickable { callbacks.onCandidateSelect(index) }
                                .padding(vertical = 7.dp, horizontal = if (index == 0) 0.dp else 8.dp))
                    }
                }
                Box(Modifier.size(30.dp).clip(CircleShape).clickable { callbacks.onOpenClipboard?.invoke() },
                    contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Filled.FormatListBulleted, contentDescription = "打开剪贴板",
                        tint = Color(0xFF75A7FF), modifier = Modifier.size(22.dp))
                }
            }
        }
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
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(5.dp))
            .background(
                if (isSelected) accentColor.copy(alpha = 0.2f)
                else Color.Transparent
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            color = if (isSelected) selectedTextColor else textColor,
            fontSize = fontSize,
            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
            maxLines = 1,
            fontFamily = candidateFontFamily
        )
        if (comment.isNotEmpty()) {
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = comment,
                color = if (isSelected) selectedTextColor.copy(alpha = 0.6f) else textColor.copy(alpha = 0.5f),
                fontSize = (fontSize.value * 11f / 19f).sp,
                fontWeight = FontWeight.Normal,
                maxLines = 1,
                fontFamily = commentFontFamily
            )
        }
    }
}

/** 收起时向上、展开时向下；沿用工具栏的同一按钮与图标。 */
@Composable
private fun CandidateExpansionButton(
    onClick: () -> Unit,
    background: Color,
    foreground: Color,
    expanded: Boolean,
    rotation: Float,
) {
    KeyboardToolbarButton(onClick, background,
        modifier = Modifier.testTag("candidate-expansion").semantics { stateDescription = if (expanded) "已展开" else "已收起" }) {
        Icon(Icons.Default.KeyboardArrowUp,
            contentDescription = if (expanded) "返回键盘" else "展开候选词",
            tint = foreground, modifier = Modifier.size(24.dp).rotate(rotation))
    }
}

/** 非聚焦窗口提供真实触控区域，点击编码不会抢走宿主编辑框焦点。 */
@Composable
private fun PreeditPreview(text: String, visuals: CandidateBarVisuals, onEdit: (() -> Unit)?) {
    val density = LocalDensity.current
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val margin = with(density) { 4.dp.roundToPx() }
    val gap = with(density) { 2.dp.roundToPx() }
    val positionProvider = remember(margin, gap) {
        object : PopupPositionProvider {
            override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize,
                layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
                return IntOffset(
                    anchorBounds.left.coerceIn(margin,
                        (windowSize.width - popupContentSize.width - margin).coerceAtLeast(margin)),
                    (anchorBounds.top - popupContentSize.height - gap).coerceIn(0,
                        (windowSize.height - popupContentSize.height).coerceAtLeast(0)),
                )
            }
        }
    }
    val background = if (visuals.preeditBackgroundColor != Color.Unspecified) visuals.preeditBackgroundColor
        else if (visuals.textColor.luminance() > 0.5f) Color(0xFF2D2F31) else Color(0xFFFAFAFA)
    Popup(popupPositionProvider = positionProvider,
        properties = PopupProperties(focusable = false, dismissOnBackPress = false,
            dismissOnClickOutside = false, clippingEnabled = true)) {
        Box(Modifier.widthIn(max = screenWidth - 16.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(background.copy(alpha = 0.62f))
            .testTag("candidate-preedit")
            .then(if (onEdit != null) Modifier.clickable(role = Role.Button,
                onClickLabel = "编辑拼音", onClick = onEdit) else Modifier)
            .padding(horizontal = 8.dp, vertical = 3.dp),
            contentAlignment = Alignment.CenterStart) {
            Text(text, color = visuals.textColor.copy(alpha = 0.9f), fontSize = 12.sp, maxLines = 1,
                softWrap = false, modifier = Modifier.horizontalScroll(rememberScrollState()))
        }
    }
}
