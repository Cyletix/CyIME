package com.kingzcheung.xime.ui.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 分体键盘 = 正常 26 键 + 每行中线处同宽的一条缝。
 * 每个键只出现一次，左右两半各自保持正常键盘的上下错位。
 */
class SplitKeyboardGeometryTest {
    private val top = "qwertyuiop".map { it.toString() }
    private val middle = "asdfghjkl".map { it.toString() }
    private val bottom = "zxcvbnm".map { it.toString() }

    @Test fun everyKeyAppearsOnceAndRowsSplitAtTheMidline() {
        for (stagger in listOf(0f, 0.5f, 1f)) {
            val g = LetterKeyboardFrame(listOf(10, 9, 7)).geometry(1f, stagger)
            val counts = listOf(
                SplitKeyboardGeometry.leftCount(top, 0f, false),
                SplitKeyboardGeometry.leftCount(middle, g.middleRowInsetDp, false),
                SplitKeyboardGeometry.leftCount(bottom, g.outerKeyWeight, false),
            )
            // qwert|yuiop, asdfg|hjkl, zxcv|bnm：没有重复的 g / v。
            assertEquals("stagger=$stagger", listOf(5, 5, 4), counts)
        }
    }

    @Test fun customTenKeyRowsUseTheSameRule() {
        val colemakMiddle = "arstdhneio".map { it.toString() }
        assertEquals(5, SplitKeyboardGeometry.leftCount(colemakMiddle, 0f, false))
        assertEquals(4, SplitKeyboardGeometry.leftCount("zxcvbkm".map { it.toString() }, 1.5f, false))
        // 合并键按实际占位计算；中心正好压在中线上的键归左半（与 g 留在左边同一规则）。
        assertEquals(3, SplitKeyboardGeometry.leftCount(listOf("qw", "er", "ty", "ui", "op"), 0f, true))
        assertEquals(2, SplitKeyboardGeometry.leftCount(listOf("qw", "er", "ty", "ui", "op"), 0.5f, true))
    }

    @Test fun gapKeepsEachHalfIdenticalToTheNormalKeyboard() {
        val g = LetterKeyboardFrame(listOf(10, 9, 7)).geometry(1f, 1f)
        val gap = 3.2f
        fun centers(keys: List<String>, start: Float, split: Int) = keys.indices.map { i ->
            start + i + 0.5f + if (split in 0..i) gap else 0f
        }
        val rows = listOf(
            Triple(top, 0f, SplitKeyboardGeometry.leftCount(top, 0f, false)),
            Triple(middle, g.middleRowInsetDp, SplitKeyboardGeometry.leftCount(middle, g.middleRowInsetDp, false)),
            Triple(bottom, g.outerKeyWeight, SplitKeyboardGeometry.leftCount(bottom, g.outerKeyWeight, false)),
        )
        for ((keys, start, split) in rows) {
            val normal = centers(keys, start, -1)
            val shifted = centers(keys, start, split)
            keys.indices.forEach { i ->
                // 左半位置不变，右半整体右移同一条缝宽：行间错位与正常键盘完全相同。
                assertEquals(keys[i], if (i < split) 0f else gap, shifted[i] - normal[i], 0.0001f)
            }
        }
    }

    @Test fun bottomRowSplitsInsideTheSpaceKey() {
        val (left, right) = SplitKeyboardGeometry.spaceSplit(QwertyBottomRowWeights.Standard)
        assertEquals(1.6f, left, 0.0001f)
        assertEquals(2.3f, right, 0.0001f)
        val b = QwertyBottomRowWeights.Standard
        assertEquals(5f, b.mode * 2 + b.punctuation + left, 0.0001f)
    }

    @Test fun automaticGapRestoresTheCalibratedPitchWithinLimits() {
        // 平板横屏：两半各五个标定键距，其余留作中缝。
        val wide = SplitKeyboardGeometry.defaultGap(1493f, 8f, 98.4f)
        assertEquals((1493f - 16f - 984f) / 1493f, wide, 0.0001f)
        // 屏幕不够宽：只留最小的自动中缝。
        assertEquals(SplitKeyboardGeometry.AUTO_MIN_GAP_FRACTION, SplitKeyboardGeometry.defaultGap(934f, 8f, 98.4f), 0.0001f)
        assertEquals(SplitKeyboardGeometry.FALLBACK_GAP_FRACTION, SplitKeyboardGeometry.defaultGap(1493f, 8f, null), 0.0001f)
        // 保存值被夹到每半仍能放下最小键宽的范围内。
        val range = SplitKeyboardGeometry.gapRange(600f, 8f)
        assertTrue(range.endInclusive * 600f <= 600f - 16f - 10f * SplitKeyboardGeometry.MIN_UNIT_DP + 0.01f)
        assertEquals(range.endInclusive, SplitKeyboardGeometry.resolvedGap(0.9f, 600f, 8f, null), 0.0001f)
        assertEquals(wide, SplitKeyboardGeometry.resolvedGap(-1f, 1493f, 8f, 98.4f), 0.0001f)
    }

    @Test fun fourteenKeySplitBreaksOnlyThePairsAcrossTheMidline() {
        val phone = listOf(
            SplitKeyboardGeometry.splitStraddlingPairs(listOf("qw", "er", "ty", "ui", "op"), 0f),
            SplitKeyboardGeometry.splitStraddlingPairs(listOf("as", "df", "gh", "jk", "l"), 0.5f),
            SplitKeyboardGeometry.splitStraddlingPairs(listOf("zx", "cv", "bn", "m"), 1.5f))
        assertEquals(listOf("qw", "er", "t", "y", "ui", "op"), phone[0])
        assertEquals(listOf("as", "df", "g", "h", "jk", "l"), phone[1])
        assertEquals(listOf("zx", "c", "v", "bn", "m"), phone[2])
        // 拆开后左右手与 26 键一致：t、g、v 在左，y、h、b 在右。
        assertEquals(3, SplitKeyboardGeometry.leftCount(phone[0], 0f, true))
        assertEquals(3, SplitKeyboardGeometry.leftCount(phone[1], 0.5f, true))
        assertEquals(3, SplitKeyboardGeometry.leftCount(phone[2], 1.5f, true))
        // 实体键盘错位：bn 跨中线而 cv 不跨。
        assertEquals(listOf("zx", "cv", "b", "n", "m"),
            SplitKeyboardGeometry.splitStraddlingPairs(listOf("zx", "cv", "bn", "m"), 0.75f))
        // 正好落在中线两侧的键不拆。
        assertEquals(listOf("as", "df", "gh", "jk"),
            SplitKeyboardGeometry.splitStraddlingPairs(listOf("as", "df", "gh", "jk"), 1f))
    }

    @Test fun keyWidthFractionLeavesExactlyTheGap() {
        val width = 1000f
        val fraction = SplitKeyboardGeometry.keyWidthFraction(width, 0.3f, 8f)
        assertEquals(width - 16f - 300f, (width - 16f) * fraction, 0.01f)
    }

    /** 「填满留空处」：两侧照常分开排布，但中缝不挖空、不让出触摸。 */
    @Test fun filledGapKeepsTheSplitLayoutButLeavesNoHole() {
        val gap = SplitKeyboardGap()
        gap.reportRow(0, androidx.compose.ui.geometry.Rect(400f, 100f, 600f, 160f))
        gap.reportRow(1, androidx.compose.ui.geometry.Rect(380f, 160f, 620f, 220f))
        assertEquals(androidx.compose.ui.geometry.Rect(400f, 100f, 600f, 300f), gap.hole(300f))
        gap.updateFilled(true)
        assertEquals(null, gap.hole(300f))
        assertEquals(400f to 600f, gap.gapX())
        gap.updateFilled(false)
        assertTrue(gap.hole(300f) != null)
    }
}
