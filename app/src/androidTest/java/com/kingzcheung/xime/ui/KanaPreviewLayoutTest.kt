package com.kingzcheung.xime.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.kingzcheung.xime.ui.keyboard.JapaneseKanaKeyboardLayout
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class KanaPreviewLayoutTest {
    @get:Rule val rule = createComposeRule()
    @Test fun narrowKeyboardPunctuationAndPopupLabelsDoNotOverlap() = checkLayout(280, 1.3f)
    @Test fun standardKeyboardPopupStaysAboveThePressedKey() = checkLayout(360, 1f)
    private fun checkLayout(width: Int, fontScale: Float) {
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                MaterialTheme {
                    Box(Modifier.size(width.dp, 500.dp).testTag("kana-window"), contentAlignment = Alignment.BottomCenter) {
                        JapaneseKanaKeyboardLayout(onKanaAction = {}, onKeyPress = {}, keyBackgroundColor = Color.DarkGray,
                            keyTextColor = Color.White, specialKeyBackgroundColor = Color.Blue,
                            shadowEnabled = false, modifier = Modifier.size(width.dp, 220.dp))
                    }
                }
            }
        }
        val labels = listOf("、", "。", "？", "！", "・")
        val labelBounds = labels.map { label ->
            val node = rule.onNodeWithText(label, useUnmergedTree = true)
            val layouts = mutableListOf<TextLayoutResult>()
            node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            val layout = layouts.single()
            assertFalse("$label 不得垂直裁切", layout.didOverflowHeight)
            // Android 的单字标点测量有亚像素取整差；允许最多1像素，实际字形不能超出键帽。
            assertTrue("$label right=${layout.getLineRight(0)} width=${layout.size.width}",
                layout.getLineRight(0) <= layout.size.width + 1f)
            node.fetchSemanticsNode().boundsInRoot
        }
        labelBounds.forEachIndexed { index, a -> labelBounds.drop(index + 1).forEach { b -> assertFalse("字符边界重叠: $a / $b", a.overlaps(b)) } }
        for (tag in listOf("kana-key:a", "kana-key:sa", "kana-punctuation")) {
            val key = rule.onNodeWithTag(tag)
            key.performTouchInput { down(center) }
            val popup = rule.onNodeWithTag("kana-flick-preview").fetchSemanticsNode()
            val anchor = key.fetchSemanticsNode()
            assertTrue(popup.positionOnScreen.x >= 0)
            assertTrue("$tag popup=${popup.positionOnScreen}, size=${popup.size}, anchor=${anchor.positionOnScreen}", popup.positionOnScreen.y + popup.size.height <= anchor.positionOnScreen.y)
            key.performTouchInput { cancel() }
            rule.onNodeWithTag("kana-flick-preview").assertDoesNotExist()
        }
    }
}
