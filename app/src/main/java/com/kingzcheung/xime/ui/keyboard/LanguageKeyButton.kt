package com.kingzcheung.xime.ui.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.kingzcheung.xime.settings.ButtonLayout
import com.kingzcheung.xime.settings.SchemaInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

internal fun languageMenuSelection(
    schemas: List<SchemaInfo>,
    pointer: Offset,
    viewport: Rect,
    scrollOffset: Int,
    rowHeight: Float,
    previousSelection: String? = null,
): String? {
    if (schemas.isEmpty() || rowHeight <= 0f || !rowHeight.isFinite() ||
        viewport.width <= 0f || viewport.height <= 0f ||
        !pointer.x.isFinite() || !pointer.y.isFinite()) return null
    val previous = previousSelection?.takeIf { id -> schemas.any { it.schemaId == id } }
    // 尚未进入菜单时，原地松手仍只是关闭菜单。选中过后越界不能退回当前语言。
    if (!viewport.contains(pointer) && previous == null) return null
    if (pointer.x < viewport.left || pointer.x >= viewport.right) return previous
    val y = pointer.y.coerceIn(viewport.top, Math.nextDown(viewport.bottom))
    val index = ((y - viewport.top + scrollOffset.coerceAtLeast(0)) / rowHeight).toInt()
        .coerceIn(0, schemas.lastIndex)
    return schemas[index].schemaId
}

/** 统一语言键：点按切换英文、长按选语言；不接受方案定义的文字预览和滑动菜单。 */
@Composable
fun LanguageKeyButton(
    onClick: () -> Unit,
    backgroundColor: Color,
    modifier: Modifier = Modifier,
    text: String = "",
    icon: Painter? = null,
    textColor: Color = Color.Unspecified,
    iconColor: Color = textColor,
    layoutMode: ButtonLayout = ButtonLayout.STANDARD,
    fontSize: TextUnit = TextUnit.Unspecified,
    swipeFontSize: TextUnit = 9.sp,
    swipeText: String? = null,
    swipeDownText: String? = null,
    swipeUpKeyLabel: String? = null,
    swipeDownKeyLabel: String? = null,
    badgeText: String? = null,
    onSwipe: ((String) -> Unit)? = null,
    onSwipeDown: ((String) -> Unit)? = null,
    onSwipeStateChange: ((SwipeState, Rect) -> Unit)? = null,
    onPress: (() -> Unit)? = null,
    onRelease: (() -> Unit)? = null,
    longPressItems: List<String>? = null,
    onLongPressSelect: ((String) -> Unit)? = null,
    shadowEnabled: Boolean = true,
    shadowElevation: Dp = 1.dp,
    shadowShapeRadius: Dp = 8.dp,
) {
    val actions = LocalKeyboardInputActions.current
    val context = LocalContext.current
    val schemas by rememberUpdatedState(com.kingzcheung.xime.settings.InputModes.languageChoices(
        actions.schemas, actions.currentInputModeId,
        com.kingzcheung.xime.settings.InputModes.rememberedModes(context, actions.schemas),
        com.kingzcheung.xime.settings.InputModes.languageOrder(context)))
    val switchSchema by rememberUpdatedState(actions.onSwitchSchema)
    val hasMenu = schemas.isNotEmpty() && switchSchema != null
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val suppressCursorMove = LocalSuppressCursorMove.current
    var keyWindowBounds by remember { mutableStateOf(Rect.Zero) }
    var keyScreenPosition by remember { mutableStateOf(Offset.Zero) }
    var menuSchemas by remember { mutableStateOf<List<SchemaInfo>>(emptyList()) }
    var menuOpen by remember { mutableStateOf(false) }
    var pointer by remember { mutableStateOf(Offset.Unspecified) }
    var viewport by remember { mutableStateOf(Rect.Zero) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    val panelBounds = LocalLanguageMenuPanel.current
    val fallbackBounds = Rect(0f, 0f, view.width.toFloat(), view.height.toFloat())
    val menuGeometry = languageMenuGeometry(
        panelBounds.takeIf { it.width > 0f && it.height > 0f } ?: fallbackBounds,
        keyWindowBounds, density.density, menuSchemas.size)
    val rowHeight = menuGeometry.rowHeightPx
    val edgeSize = with(density) { 20.dp.toPx() }
    val scrollStep = with(density) { 8.dp.toPx() }

    LaunchedEffect(menuOpen) { if (menuOpen) scroll.scrollTo((menuSchemas.indexOfFirst { it.schemaId == actions.currentInputModeId }.coerceAtLeast(0) * rowHeight).toInt()) }
    // 仅在手指进入边缘时滚动；新菜单不继承上次手势的位置。
    LaunchedEffect(menuOpen, pointer, viewport) {
        if (!menuOpen || selectedId == null || !pointer.x.isFinite() || !pointer.y.isFinite() ||
            pointer.x < viewport.left || pointer.x >= viewport.right) return@LaunchedEffect
        val delta = when {
            pointer.y < viewport.top + edgeSize -> -scrollStep
            pointer.y > viewport.bottom - edgeSize -> scrollStep
            else -> return@LaunchedEffect
        }
        while (menuOpen) {
            if (scroll.scrollBy(delta) == 0f) break
            selectedId = languageMenuSelection(menuSchemas, pointer, viewport, scroll.value, rowHeight, selectedId)
            delay(30L)
        }
    }

    Box(
        modifier.fillMaxWidth().fillMaxHeight()
            .onGloballyPositioned {
                keyWindowBounds = it.boundsInWindow()
                keyScreenPosition = it.positionOnScreen()
            }
            .pointerInput(hasMenu) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    if (down.isConsumed) return@awaitEachGesture
                    var chosenId: String? = null
                    var cancelledSwipe = false
                    val menuJob = if (hasMenu) scope.launch {
                        delay(200L)
                        menuSchemas = schemas
                        selectedId = null
                        viewport = Rect.Zero
                        pointer = Offset.Unspecified
                        menuOpen = true
                        suppressCursorMove.value = true
                        view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                    } else null
                    try {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (change.isConsumed) break
                            if (cancelledSwipe) {
                                change.consume()
                                if (!change.pressed) break
                            } else if (menuOpen) {
                                change.consume()
                                pointer = keyScreenPosition + change.position
                                selectedId = languageMenuSelection(menuSchemas, pointer, viewport, scroll.value, rowHeight, selectedId)
                                if (!change.pressed) {
                                    chosenId = selectedId
                                    break
                                }
                            } else {
                                val displacement = change.position - down.position
                                if (displacement.y < -viewConfiguration.touchSlop) {
                                    menuJob?.cancel()
                                    cancelledSwipe = true
                                    change.consume()
                                    if (!change.pressed) break
                                } else if (!change.pressed || abs(displacement.x) > viewConfiguration.touchSlop ||
                                    abs(displacement.y) > viewConfiguration.touchSlop) break
                            }
                        }
                    } finally {
                        menuJob?.cancel()
                        menuOpen = false
                        suppressCursorMove.value = false
                        pointer = Offset.Unspecified
                        selectedId = null
                    }
                    chosenId?.let { switchSchema?.invoke(it) }
                }
            }
    ) {
        SwipeableKeyButton(
            text = "语言切换", icon = rememberVectorPainter(Icons.Default.Language), onClick = onClick,
            backgroundColor = backgroundColor,
            textColor = if (icon != null) iconColor else textColor,
            modifier = Modifier.fillMaxSize().testTag("language-key-control"), layoutMode = ButtonLayout.STANDARD,
            fontSize = fontSize, swipeFontSize = swipeFontSize,
            swipeText = null, swipeDownText = null,
            swipeUpKeyLabel = null, swipeDownKeyLabel = null,
            badgeText = null, onSwipe = null, onSwipeDown = null,
            onSwipeStateChange = null,
            onPress = onPress, onRelease = onRelease,
            longPressItems = null,
            onLongPressSelect = null,
            shadowEnabled = shadowEnabled, shadowElevation = shadowElevation,
            shadowShapeRadius = shadowShapeRadius,
        )
        if (menuOpen) {
            val positionProvider = remember(menuGeometry) {
                object : PopupPositionProvider {
                    override fun calculatePosition(
                        anchorBounds: IntRect, windowSize: IntSize,
                        layoutDirection: LayoutDirection, popupContentSize: IntSize,
                    ): IntOffset = IntOffset(
                        menuGeometry.bounds.left.roundToInt()
                            .coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0)),
                        menuGeometry.bounds.top.roundToInt()
                            .coerceIn(0, (windowSize.height - popupContentSize.height).coerceAtLeast(0)),
                    )
                }
            }
            Popup(
                popupPositionProvider = positionProvider,
                properties = PopupProperties(focusable = false, dismissOnBackPress = false,
                    dismissOnClickOutside = false, clippingEnabled = false),
            ) {
                // Popup owns a separate view; retain the keyboard density and font scale.
                androidx.compose.runtime.CompositionLocalProvider(LocalDensity provides density) {
                Surface(
                    shape = RoundedCornerShape(16.dp), tonalElevation = 6.dp, shadowElevation = 8.dp,
                    modifier = Modifier.width(with(density) { menuGeometry.bounds.width.toDp() })
                        .height(with(density) { menuGeometry.bounds.height.toDp() }).testTag("language-menu"),
                ) {
                    Column(Modifier.padding(6.dp)) {
                        Box(Modifier.fillMaxWidth().height(28.dp), contentAlignment = Alignment.Center) {
                            Text("语言", style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
                        }
                        Column(
                            Modifier.fillMaxWidth().height(with(density) { (menuGeometry.bounds.height - 40.dp.toPx()).coerceAtLeast(1f).toDp() })
                                .onGloballyPositioned {
                                    val topLeft = it.positionOnScreen()
                                    viewport = Rect(topLeft, androidx.compose.ui.geometry.Size(it.size.width.toFloat(), it.size.height.toFloat()))
                                }
                                .verticalScroll(scroll)
                        ) {
                            menuSchemas.forEach { schema ->
                                val modeName = if (schema.schemaId == com.kingzcheung.xime.settings.InputModes.ENGLISH) "English"
                                    else actions.schemas.firstOrNull { it.schemaId == schema.schemaId }?.name ?: schema.schemaId
                                Column(
                                    Modifier.fillMaxWidth().height(with(density) { rowHeight.toDp() })
                                        .testTag("language-schema:${schema.schemaId}")
                                        .semantics {
                                            selected = (selectedId ?: actions.currentInputModeId) == schema.schemaId
                                            contentDescription = "选择${schema.name}"
                                        }
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (schema.schemaId == (selectedId ?: actions.currentInputModeId))
                                            MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                        .padding(horizontal = 12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                ) {
                                    Text(schema.name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center,
                                        color = if (schema.schemaId == (selectedId ?: actions.currentInputModeId))
                                            MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface)
                                    Text(modeName, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
                }
            }
        }
    }
}
