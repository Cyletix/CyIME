package com.kingzcheung.xime.ui.keyboard

/**
 * 每个键盘族的“视觉键帽”策略。
 *
 * 只决定键帽画多大：命中区域始终是整格（见 [scaledKeyVisualPadding] 与 KeyButton 中
 * pointerInput 先于视觉 padding 的顺序），所以把缝从 3dp 放大到 6dp 不会让用户更难按。
 *
 * `gapX/gapY` 是**相邻键帽之间的最终视觉缝**（= 两侧 inset 之和），不是单侧 padding。
 * 数值来自 1080 宽真机截图的第一轮对照（26 键 / 14 键 / 九宫格），集中在这里便于真机微调。
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
            maxGapX = 6f, maxGapY = 7f,
            maxKeyWidth = 62f, minGutter = 8f,
        )

        /** 14 键等合并键布局：键更宽，缝给绝对 dp，不按键宽比例放大。 */
        val FourteenKey = KeyVisualPolicy(
            gapX = 5f, gapY = 6f,
            minGapX = 3f, minGapY = 3.5f,
            maxGapX = 7f, maxGapY = 8f,
            maxKeyWidth = 100f, minGutter = 8f,
        )

        /** 九宫格（T9）/ 数字 / 笔画 / 日语九宫格：宽键，6dp 缝已足够明显。 */
        val T9 = KeyVisualPolicy(
            gapX = 6f, gapY = 6f,
            minGapX = 3.5f, minGapY = 3.5f,
            maxGapX = 8f, maxGapY = 8f,
            maxKeyWidth = 140f, minGutter = 8f,
        )

        /** 缝的有限缩放：60dp 参考格，只允许 0.85x（悬浮缩小）~1.20x（大屏）。 */
        const val ReferenceCell = 60f
        const val ScaleMin = 0.85f
        const val ScaleMax = 1.20f

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
) {
    companion object {
        /** 无策略上下文：保持声明值。 */
        val Unspecified = KeyVisualMetrics(0f, null, null, 1f, 0f)
    }
}

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
    val scale = (minOf(cellWidth, cellHeight) / KeyVisualPolicy.ReferenceCell)
        .coerceIn(if (allowShrink) KeyVisualPolicy.ScaleMin else 1f, KeyVisualPolicy.ScaleMax)
    val gapX = (policy.gapX * scale).coerceIn(policy.minGapX, policy.maxGapX)
    val gapY = (policy.gapY * scale).coerceIn(policy.minGapY, policy.maxGapY)
    return KeyVisualMetrics(
        gutterX = ((availableWidthDp - contentWidth) / 2f).coerceAtLeast(0f),
        insetX = gapX / 2f,
        insetY = gapY / 2f,
        scale = scale,
        capShortEdge = minOf(cellWidth - gapX, cellHeight - gapY).coerceAtLeast(1f),
    )
}
