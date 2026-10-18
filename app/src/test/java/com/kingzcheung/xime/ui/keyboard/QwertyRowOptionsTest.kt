package com.kingzcheung.xime.ui.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QwertyRowOptionsTest {
    @Test fun staggerPresetsMatchPhysicalAndPhoneKeyboards() {
        val frame = LetterKeyboardFrame(listOf(10, 9, 7))
        // 实体键盘（ANSI）：A 行右移 1/4 键，Z 行右移 3/4 键。
        val physical = frame.geometry(1f, resolvedQwertyStagger(QwertyStaggerMode.PHYSICAL, 0f, 40f, null))
        assertEquals(0.25f, physical.middleRowInsetDp, 0.0001f)
        assertEquals(0.75f, physical.outerKeyWeight, 0.0001f)
        // 手机常见：S 占 [1.5, 2.5]，Z 也从 1.5 开始，上下对齐。
        val phone = frame.geometry(1f, resolvedQwertyStagger(QwertyStaggerMode.PHONE, 1f, 40f, null))
        assertEquals(phone.middleRowInsetDp + 1f, phone.outerKeyWeight, 0.0001f)
        // 自定义夹在两者之间；自动沿用按尺寸的原有曲线。
        assertEquals(0.3f, resolvedQwertyStagger(QwertyStaggerMode.CUSTOM, 0.3f, 40f, null), 0.0001f)
        assertEquals(1f, resolvedQwertyStagger(QwertyStaggerMode.CUSTOM, 7f, 40f, null), 0.0001f)
        assertEquals(wideQwertyStagger(90f, 98f), resolvedQwertyStagger(QwertyStaggerMode.AUTO, 0f, 90f, 98f), 0.0001f)
    }

    @Test fun spacePunctuationFollowsTheChosenKeysAndWidth() {
        // 只开一个键时，它和两个都开时完全一样：点按当前宽度，滑动另一种宽度。
        assertEquals(Pair(SpacePunctuationKey("，", ","), null), spacePunctuationKeys(comma = true, period = false, fullWidth = true))
        val periodOnly = spacePunctuationKeys(comma = false, period = true, fullWidth = false)
        assertNull(periodOnly.first)
        assertEquals(SpacePunctuationKey(".", "。"), periodOnly.second)
        assertEquals(Pair(null, null), spacePunctuationKeys(comma = false, period = false, fullWidth = true))
        // 日文的全角逗号是 、。
        assertEquals(Pair(SpacePunctuationKey("、", ","), SpacePunctuationKey("。", ".")),
            spacePunctuationKeys(comma = true, period = true, fullWidth = true, japanese = true))
        assertEquals(SpacePunctuationKey(",", "、"),
            spacePunctuationKeys(comma = true, period = false, fullWidth = false, japanese = true).first)
        // 两个都开：副字符是另一种宽度，主字符跟随全角／半角状态。
        assertEquals(Pair(SpacePunctuationKey("，", ","), SpacePunctuationKey("。", ".")),
            spacePunctuationKeys(comma = true, period = true, fullWidth = true))
        assertEquals(Pair(SpacePunctuationKey(",", "，"), SpacePunctuationKey(".", "。")),
            spacePunctuationKeys(comma = true, period = true, fullWidth = false))
        // 英文不分全角半角：两个都开时只有 , 和 .，没有副字符。
        assertEquals(Pair(SpacePunctuationKey(",", ""), SpacePunctuationKey(".", "")),
            spacePunctuationKeys(comma = true, period = true, fullWidth = false, english = true))
    }

    @Test fun splitSpaceFollowsTheKeysLeftOfIt() {
        val b = QwertyBottomRowWeights.Standard
        // 没有左侧逗号时空格多一个键宽，中线仍在 5。
        val (left, right) = SplitKeyboardGeometry.spaceSplit(b, b.space + b.punctuation, b.mode * 2f)
        assertEquals(5f, b.mode * 2f + left, 0.0001f)
        assertEquals(b.space + b.punctuation, left + right, 0.0001f)
    }
}
