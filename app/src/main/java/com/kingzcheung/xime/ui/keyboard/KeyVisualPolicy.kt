package com.kingzcheung.xime.ui.keyboard

/**
 * 每个键盘族的“视觉键帽”策略。
 *
 * 只决定键帽画多大：命中区域始终是整格（见 [scaledKeyVisualPadding] 与 KeyButton 中
 * pointerInput 先于视觉 padding 的顺序），所以把缝从 3dp 放大到 6dp 不会让用户更难按。
 *
 * `gapX/gapY` 是**相邻键帽之间的最终视觉缝**（= 两侧 inset 之和），不是单侧 padding。
 * 手机基准保持原有密度；键盘变宽时，按各布局的参考键宽增加横纵间隙。
 */
internal data class KeyVisualPolicy(
    /** 相邻键帽之间的目标横向视觉缝（dp）。 */
    val gapX: Float,
    /** 相邻键帽之间的目标纵向视觉缝（dp）。 */
    val gapY: Float,
    val minGapX: Float,
    val minGapY: Float,
    val maxGapX: Float,
    val maxGapY: Float,
    /** 360dp 手机扣除两侧 8dp 后，该布局的单位格宽。不是单个宽功能键的宽度。 */
    val referenceCellWidth: Float,
    /** 视觉键帽宽度上限（dp）：大屏多出的宽度转成左右 gutter，不允许键无限拉宽。 */
    val maxKeyWidth: Float,
    /** 内容左右最小 gutter（每侧 dp）。 */
    val minGutter: Float,
) {
    companion object {
        /** 26 键（中/英/日语罗马音，以及 17/18 键等窄键布局）。 */
        val Qwerty = KeyVisualPolicy(
            gapX = 4.5f, gapY = 5.5f,
            minGapX = 2.5f, minGapY = 3f,
            maxGapX = 12f, maxGapY = 11f,
            referenceCellWidth = 344f / 10f,
            maxKeyWidth = 62f, minGutter = 8f,
        )

        /** 14 键等合并键布局：使用自身的参考格宽，避免手机宽键被误当作平板。 */
        val FourteenKey = KeyVisualPolicy(
            gapX = 5f, gapY = 6f,
            minGapX = 3f, minGapY = 3.5f,
            maxGapX = 10f, maxGapY = 12f,
            referenceCellWidth = 344f / 5f,
            maxKeyWidth = 100f, minGutter = 8f,
        )

        /** 九宫格（T9）/ 数字 / 笔画 / 日语九宫格：手机约 6dp，大键盘可到 12dp。 */
        val T9 = KeyVisualPolicy(
            gapX = 6f, gapY = 6f,
            minGapX = 3.5f, minGapY = 3.5f,
            maxGapX = 12f, maxGapY = 12f,
            referenceCellWidth = 344f / (5f * 3f / 3.4f),
            maxKeyWidth = 140f, minGutter = 8f,
        )

        /** 保留原有短边缩放，同时允许宽键盘按参考键宽放大，最高 2 倍。 */
        const val ReferenceCell = 60f
        const val ScaleMin = 0.85f
        const val ScaleMax = 2f
        /** 很矮/窄的键格至少保留 80% 给键帽，避免扩大间隙挤掉文字。 */
        const val MaxGapFraction = 0.2f

        /** Keep the phone baseline; automatic row-gap growth is half its original rate. */
        const val RowGapGrowth = 0.5f

        /** 悬浮键盘保留的 gutter：不套用手机 8dp 下限，允许整体缩小。 */
        const val FloatingGutter = 4f

        /** `LocalKeyVisualPadding` 里表示“交给布局策略”的哨兵值（历史默认值，见 xime.yaml）。 */
        const val DeclaredDefaultGap = 2f
    }
}

/**
 * 一个键盘体算出的视觉度量。
 *
 * `insetX/insetY` 为 null 表示“没有布局策略上下文”，此时沿用声明值
 * （候选栏等嵌套网格不套用主体策略，保持原有 2dp 视觉）。
 */
internal data class KeyVisualMetrics(
    val gutterX: Float,
    val insetX: Float?,
    val insetY: Float?,
    val scale: Float,
    val capShortEdge: Float,
    /** 实际内容留白后的单位格宽，供标准字母行共用列尺寸。 */
    val cellWidthDp: Float = 0f,
    val cellHeightDp: Float = 0f,
    val extraInsetY: Float = 0f,
    val topSpaceDp: Float = 0f,
    val bottomSpaceDp: Float = 0f,
) {
    companion object {
        /** 无策略上下文：保持声明值。 */
        val Unspecified = KeyVisualMetrics(0f, null, null, 1f, 0f)
    }
}

/** 反映 Scope 真正应用的留白；悬浮度量的 4dp 仍可能被调用方的 8dp 下限提高。 */
internal fun KeyVisualMetrics.withAppliedGutter(
    availableWidthDp: Float,
    columns: Float,
    horizontalInsetDp: Float,
    applyGutter: Boolean,
    widthFraction: Float = 1f,
): KeyVisualMetrics {
    val gutter = if (applyGutter) maxOf(gutterX, horizontalInsetDp) else 0f
    return copy(
        gutterX = gutter,
        cellWidthDp = ((availableWidthDp - 2f * gutter) * widthFraction / columns).coerceAtLeast(0f),
    )
}

internal data class LetterRowGeometry(val middleRowInsetDp: Float, val outerKeyWeight: Float)

/** 只校准标准 10/9/7 字母行；自定义、合并键和分体仍走原布局。 */
internal fun standardLetterRowGeometry(
    keyRows: List<List<String>>,
    cellWidthDp: Float,
    hasCustomLayout: Boolean,
    isSplit: Boolean,
): LetterRowGeometry? = if (!hasCustomLayout && !isSplit && cellWidthDp > 0f &&
    keyRows.map { it.size } == listOf(10, 9, 7) && keyRows.all { row -> row.all { it.length == 1 } }
) LetterRowGeometry(cellWidthDp / 2f, (10f - 7f) / 2f) else null

/**
 * 由可用尺寸、列数与策略算出 gutter / 每侧 inset / 有限缩放 / 键帽短边。
 *
 * 纯函数：不依赖 Context 与 Compose，可直接 JVM 单测（见 KeyVisualPolicyTest）。
 *
 * @param columns 每行“单位列数”：26 键 = 首行键数；九键/数字/笔画 = 4.41
 *   （左功能列 0.8 + 主区 3.4 的单位数，与按键实际宽度同尺度）。
 * @param verticalInsetDp 上下预留（底部留白等），与列无关的先扣掉。
 * @param allowShrink 悬浮键盘：允许缝缩到目标值以下（不套用手机最小缝）。
 */
internal fun keyVisualMetrics(
    policy: KeyVisualPolicy,
    availableWidthDp: Float,
    availableHeightDp: Float,
    columns: Float,
    rows: Float = 4f,
    verticalInsetDp: Float = 8f,
    allowShrink: Boolean = false,
): KeyVisualMetrics {
    if (!availableWidthDp.isFinite() || !availableHeightDp.isFinite() ||
        availableWidthDp <= 0f || availableHeightDp <= 0f || columns <= 0f || rows <= 0f
    ) return KeyVisualMetrics.Unspecified

    val gutterFloor = if (allowShrink) minOf(policy.minGutter, KeyVisualPolicy.FloatingGutter) else policy.minGutter
    // 大屏：内容宽度先被“键宽上限 + 目标缝”限制，多出的宽度全部转为 gutter（居中）
    val contentWidth = minOf(
        (availableWidthDp - 2f * gutterFloor).coerceAtLeast(1f),
        columns * (policy.maxKeyWidth + policy.gapX),
    )
    val cellWidth = (contentWidth / columns).coerceAtLeast(1f)
    val cellHeight = ((availableHeightDp - verticalInsetDp) / rows).coerceAtLeast(1f)
    val scale = maxOf(
        minOf(cellWidth, cellHeight) / KeyVisualPolicy.ReferenceCell,
        cellWidth / policy.referenceCellWidth,
    )
        .coerceIn(if (allowShrink) KeyVisualPolicy.ScaleMin else 1f, KeyVisualPolicy.ScaleMax)
    // 手机基准到两倍键宽之间平滑增大至大屏间距；窄悬浮仍按比例收紧。
    fun gap(base: Float, minimum: Float, maximum: Float, cell: Float): Float {
        val target = if (scale <= 1f) base * scale
            else base + (maximum - base) * (scale - 1f) / (KeyVisualPolicy.ScaleMax - 1f)
        return target.coerceIn(minimum, maximum).coerceAtMost(cell * KeyVisualPolicy.MaxGapFraction)
    }
    val gapX = gap(policy.gapX, policy.minGapX, policy.maxGapX, cellWidth)
    val gapY = gap(policy.gapY, policy.minGapY, policy.maxGapY, cellHeight)
    return KeyVisualMetrics(
        gutterX = ((availableWidthDp - contentWidth) / 2f).coerceAtLeast(0f),
        insetX = gapX / 2f,
        insetY = gapY / 2f,
        scale = scale,
        capShortEdge = minOf(cellWidth - gapX, cellHeight - gapY).coerceAtLeast(1f),
        cellWidthDp = cellWidth,
        cellHeightDp = cellHeight,
    )
}

/** The existing minimum panel includes a 44dp toolbar. This is a baseline, not a cap. */
internal const val MINIMUM_KEY_BODY_HEIGHT_DP = FLOATING_RESIZE_MIN_HEIGHT_DP - 44f

/** Resolve the same edge-aligned grid for typing, symbols, candidates and live resizing.
 * Row gaps depend on actual cell width, never on surplus panel height. The renderer
 * fills the chosen body height; no hidden top/bottom spacers squeeze the keycaps.
 */
internal fun keyboardGridMetrics(
    policy: KeyVisualPolicy,
    availableWidthDp: Float,
    availableHeightDp: Float,
    columns: Float,
    rows: Float = 4f,
    horizontalInsetDp: Float = 8f,
    verticalInsetDp: Float = 8f,
    widthFraction: Float = 1f,
    allowShrink: Boolean = false,
    applyGutter: Boolean = true,
    growthSpacing: Pair<Float?, Float?>? = null,
): KeyVisualMetrics {
    // Preserve the existing horizontal gaps while resizing only the height.
    // This reference is used for gap measurement, never as a cap-height limit.
    val spacingHeight = if (growthSpacing != null)
        minOf(availableHeightDp, MINIMUM_KEY_BODY_HEIGHT_DP) else availableHeightDp
    val placed = keyVisualMetrics(policy, availableWidthDp * widthFraction, spacingHeight,
        columns, rows, verticalInsetDp, allowShrink).withAppliedGutter(
        availableWidthDp, columns, horizontalInsetDp, applyGutter, widthFraction)
    if (growthSpacing == null || placed.insetX == null || placed.insetY == null) return placed
    // Floating's proposed gutter can be raised by the renderer. Derive gaps from the
    // actual remaining width, so equal-sized docked/floating caps have equal geometry.
    val metrics = keyVisualMetrics(policy.copy(minGutter = 0f, maxKeyWidth = Float.MAX_VALUE),
        placed.cellWidthDp * columns, spacingHeight, columns, rows, verticalInsetDp, allowShrink)
        .copy(gutterX = placed.gutterX,
            cellHeightDp = ((availableHeightDp - verticalInsetDp) / rows).coerceAtLeast(1f))
    if (metrics.insetX == null || metrics.insetY == null) return placed

    val gapX = resolvedVisualGap(growthSpacing.first, metrics.insetX)
    val gapY = resolvedVisualGap(growthSpacing.second, metrics.insetY)
    val capWidth = (metrics.cellWidthDp - gapX).coerceAtLeast(1f)
    val automaticGap = growthSpacing.second == null || growthSpacing.second == KeyVisualPolicy.DeclaredDefaultGap
    // Width-only growth preserves phone spacing and halves the tablet increment.
    // Short panels may still shrink the gap so that at least 80% remains a keycap.
    val widthScale = (metrics.cellWidthDp / policy.referenceCellWidth)
        .coerceIn(if (allowShrink) KeyVisualPolicy.ScaleMin else 1f, KeyVisualPolicy.ScaleMax)
    val widthGap = if (widthScale <= 1f) policy.gapY * widthScale
        else policy.gapY + (policy.maxGapY - policy.gapY) * (widthScale - 1f) * KeyVisualPolicy.RowGapGrowth
    val compactGap = if (automaticGap)
        widthGap.coerceIn(policy.minGapY, policy.maxGapY)
            .coerceAtMost(metrics.cellHeightDp * KeyVisualPolicy.MaxGapFraction)
        else gapY
    return metrics.copy(
        insetY = if (automaticGap) compactGap / 2f else metrics.insetY,
        capShortEdge = minOf(capWidth, (metrics.cellHeightDp - compactGap).coerceAtLeast(1f)),
    )
}

internal fun resolvedVisualGap(explicit: Float?, inset: Float): Float =
    explicit?.takeUnless { it == KeyVisualPolicy.DeclaredDefaultGap } ?: (inset * 2f)
