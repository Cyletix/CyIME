package com.kingzcheung.xime.ui.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.Icon
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kingzcheung.xime.ui.theme.VisualStyles
import com.kingzcheung.xime.ui.theme.visualMaterial

/** 固定40dp占位和32dp圆形底板，切换图标不会挤动工具栏。 */
@Composable
internal fun KeyboardToolbarButton(
    onClick: () -> Unit,
    background: Color,
    modifier: Modifier = Modifier,
    outline: Color = Color.Transparent,
    /** 平时也在的实色底（不随按下淡出）；默认没有，只给候选栏两端的返回和展开按钮用。 */
    restingFill: Color = Color.Transparent,
    /** 在工具栏里占等宽一格：整格可点，底色为与其他工具图标同尺寸的胶囊。 */
    slotWidth: androidx.compose.ui.unit.Dp? = null,
    /** 胶囊改成这个圆角（与键盘卡片同心）；null 时是两头全圆的胶囊。 */
    cornerRadius: androidx.compose.ui.unit.Dp? = null,
    /** 按格子宽度连续地从圆变成胶囊（不像工具栏那样在 40dp 处跳变），最宽 52dp。 */
    continuousPill: Boolean = false,
    content: @Composable () -> Unit,
) {
    // 底板只在按下时出现，松手淡出；平时只有图标，与工具栏其他图标一致。
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val pressAlpha by animateFloatAsState(if (pressed) 1f else 0f,
        animationSpec = tween(if (pressed) 0 else 300), label = "toolbar-button-press")
    val disc = (if (restingFill.alpha > 0f) Modifier.background(restingFill) else Modifier)
        .background(background.copy(alpha = background.alpha * pressAlpha))
        .border(1.dp, outline.copy(alpha = outline.alpha * pressAlpha), CircleShape)
    if (slotWidth != null) {
        val pill = if (continuousPill) edgePillSize(slotWidth) else toolbarPillSize(slotWidth)
        val shape = cornerRadius?.let { androidx.compose.foundation.shape.RoundedCornerShape(it) } ?: CircleShape
        Box(modifier.width(slotWidth).height(40.dp).clickable(source, indication = null, onClick = onClick),
            contentAlignment = Alignment.Center) {
            Box(Modifier.size(pill).clip(shape).then(disc)
                .then(if (pressed) Modifier.visualMaterial(VisualStyles.current, cornerRadius ?: (pill.height / 2),
                    com.kingzcheung.xime.ui.theme.MaterialLevel.RAISED) else Modifier),
                contentAlignment = Alignment.Center) { content() }
        }
        return
    }
    Box(modifier.size(40.dp).clip(CircleShape).clickable(source, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center) {
        Box(Modifier.size(32.dp).clip(CircleShape).then(disc)
            .then(if (pressed) Modifier.visualMaterial(VisualStyles.current, 16.dp,
                com.kingzcheung.xime.ui.theme.MaterialLevel.RAISED) else Modifier), contentAlignment = Alignment.Center) { content() }
    }
}

@Composable
internal fun KeyboardBackButton(
    onBack: () -> Unit,
    background: Color,
    foreground: Color,
    modifier: Modifier = Modifier,
    label: String = "返回",
    slotWidth: androidx.compose.ui.unit.Dp? = null,
    restingFill: Color = Color.Transparent,
    cornerRadius: androidx.compose.ui.unit.Dp? = null,
    continuousPill: Boolean = false,
) {
    KeyboardToolbarButton(onBack, background, modifier, slotWidth = slotWidth, restingFill = restingFill,
        cornerRadius = cornerRadius, continuousPill = continuousPill) {
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = label,
            tint = foreground, modifier = Modifier.size(24.dp))
    }
}

/**
 * 候选栏两端按钮：格子窄时是 32dp 圆（更窄的悬浮小窗跟着缩，最小 28dp），
 * 格子变宽时宽度随之连续增加成胶囊，最宽 52dp，不多占候选的地方（用户 10-20）。
 */
internal fun edgePillSize(slot: androidx.compose.ui.unit.Dp): androidx.compose.ui.unit.DpSize {
    val width = slot - 12.dp
    if (width <= 32.dp) {
        val side = width.coerceAtLeast(28.dp)
        return androidx.compose.ui.unit.DpSize(side, side)
    }
    return androidx.compose.ui.unit.DpSize(width.coerceAtMost(52.dp), 32.dp)
}
