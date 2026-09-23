package com.kingzcheung.xime.ui.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.KeyboardReturn
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class EditorKeyShadow(
    val enabled: Boolean = true,
    val elevation: Dp = 1.dp,
    val shapeRadius: Dp = 8.dp,
)

private val LocalEditorKeyShadow = staticCompositionLocalOf { EditorKeyShadow() }

@Composable
fun EditKeyboardLayout(
    onAction: (String) -> Unit,
    onBack: () -> Unit,
    backgroundColor: Color,
    textColor: Color,
    accentColor: Color,
    keyBgColor: Color,
    bottomPaddingDp: Int = 0,
    showBackKey: Boolean = true,
    keyCornerRadius: Dp = 8.dp,
    shadowEnabled: Boolean = true,
    shadowElevation: Dp = 1.dp,
    shadowShapeRadius: Dp = 8.dp,
    modifier: Modifier = Modifier,
) {
    var selecting by remember { mutableStateOf(false) }
    val latestAction by rememberUpdatedState(onAction)
    // 离开面板只清本地锚点，复制等操作后仍可保留宿主中的选区。
    DisposableEffect(Unit) { onDispose { latestAction("select_reset") } }
    CompositionLocalProvider(
        LocalKeyCornerRadius provides keyCornerRadius,
        LocalEditorKeyShadow provides EditorKeyShadow(shadowEnabled, shadowElevation, shadowShapeRadius),
    ) {
        Column(modifier.fillMaxSize().background(backgroundColor).padding(horizontal = 2.dp)) {
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                // 中央九宫格保持方形；两侧操作独立贴边，不跟随九宫格缩进。
                val keySide = minOf(maxWidth / 5, maxHeight / 3)
                Box(Modifier.fillMaxSize()) {
                    Column(Modifier.align(Alignment.CenterStart).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                        listOf(Triple(Icons.Default.SelectAll, "全选", "select_all"),
                            Triple(Icons.Default.ContentCut, "剪切", "cut"),
                            Triple(Icons.Default.KeyboardArrowLeft, "返回键盘", "back")).forEach { (icon, label, action) ->
                            if (action != "back" || showBackKey)
                                EditorActionKey(icon, label, { if (action == "back") onBack() else onAction(action) }, keyBgColor, textColor, Modifier.size(keySide))
                            else Spacer(Modifier.size(keySide))
                        }
                    }
                    Column(Modifier.align(Alignment.Center)) {
                        val rows = listOf(
                            listOf("home", "up", "end"),
                            listOf("left", "select", "right"),
                            listOf("copy", "down", "paste"))
                        rows.forEach { row ->
                            Row {
                                row.forEach { direction ->
                                    if (direction == "select") EditorActionKey(
                                    Icons.Default.SelectAll, if (selecting) "取消选择" else "选择",
                                    { selecting = !selecting; onAction(if (selecting) "select_begin" else "select_end") },
                                    if (selecting) accentColor.copy(alpha = 0.3f) else keyBgColor, textColor, Modifier.size(keySide))
                                    else {
                                        val (icon, label, action) = when (direction) {
                                            "home" -> Triple(Icons.Default.FirstPage, "段首", if (selecting) "select_paragraph_start" else "home")
                                            "end" -> Triple(Icons.Default.LastPage, "段尾", if (selecting) "select_paragraph_end" else "end")
                                            "copy" -> Triple(Icons.Default.ContentCopy, "复制", "copy")
                                            "paste" -> Triple(Icons.Default.ContentPaste, "粘贴", "paste")
                                            else -> {
                                                val icon = when (direction) {
                                                    "up" -> Icons.Default.KeyboardArrowUp
                                                    "down" -> Icons.Default.KeyboardArrowDown
                                                    "left" -> Icons.Default.KeyboardArrowLeft
                                                    else -> Icons.Default.KeyboardArrowRight
                                                }
                                                val label = when (direction) { "up" -> "向上"; "down" -> "向下"; "left" -> "向左"; else -> "向右" }
                                                Triple(icon, label, (if (selecting) "select_" else "") + "arrow_" + direction)
                                            }
                                        }
                                        val arrow = direction in listOf("up", "down", "left", "right")
                                        EditorActionKey(icon, label, { onAction(action) },
                                            if (arrow) androidx.compose.ui.graphics.lerp(keyBgColor, accentColor, 0.28f) else keyBgColor,
                                            textColor, Modifier.size(keySide).padding(if (arrow) 0.dp else keySide * 0.06f),
                                            repeatable = arrow)
                                    }
                                }
                            }
                        }
                    }
                    Column(Modifier.align(Alignment.CenterEnd).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                        listOf(Triple(Icons.AutoMirrored.Filled.Backspace, "删除", "delete"),
                            Triple(Icons.AutoMirrored.Filled.KeyboardReturn, "回车", "enter")).forEach { (icon, label, action) ->
                            EditorActionKey(icon, label, { onAction(action) }, keyBgColor, textColor,
                                Modifier.size(keySide), repeatable = true)
                        }
                    }
                }
            }
            Spacer(Modifier.height(bottomPaddingDp.dp))
        }
    }
}

/** 松手/移出/面板销毁都会取消重复任务，长按结束不再补一次点击。 */
@Composable
internal fun EditorActionKey(
    icon: ImageVector,
    label: String,
    onAction: () -> Unit,
    background: Color,
    foreground: Color,
    modifier: Modifier = Modifier,
    repeatable: Boolean = false,
) {
    val action by rememberUpdatedState(onAction)
    val scope = rememberCoroutineScope()
    var pressed by remember { mutableStateOf(false) }
    val shadow = LocalEditorKeyShadow.current
    val density = LocalDensity.current
    val shadowModifier = remember(shadow, density, background) {
        if (shadow.enabled) {
            val offsetPx = with(density) { shadow.elevation.toPx() }
            val cornerPx = with(density) { shadow.shapeRadius.toPx() }
            val color = crispShadowColor(background)
            Modifier.drawBehind {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(0f, offsetPx),
                    size = size,
                    cornerRadius = CornerRadius(cornerPx),
                )
            }
        } else Modifier
    }
    Box(modifier.fillMaxSize().padding(2.dp)
        .then(shadowModifier)
        .clip(RoundedCornerShape(LocalKeyCornerRadius.current))
        .background(if (pressed) foreground.copy(alpha = 0.18f) else background)
        // 独立合并每个按键，避免被面板的点击屏障合并成一个无障碍节点。
        .semantics(mergeDescendants = true) { contentDescription = label; role = Role.Button; onClick { action(); true } }
        .pointerInput(repeatable) {
            detectTapGestures(onPress = {
                pressed = true
                var repeated = false
                val timer = if (repeatable) scope.launch {
                    delay(300L)
                    repeated = true
                    while (true) { action(); delay(70L) }
                } else null
                try {
                    val released = tryAwaitRelease()
                    timer?.cancel()
                    if (released && !repeated) action()
                } finally {
                    timer?.cancel()
                    pressed = false
                }
            })
        }, contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = foreground, modifier = Modifier.size(24.dp))
    }
}
