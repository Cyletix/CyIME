package com.kingzcheung.xime.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kingzcheung.xime.ui.keyboard.EditKeyboardLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EditKeyboardLayoutTest {
    @get:Rule val rule = createComposeRule()

    private val panelWidth = mutableStateOf(400.dp)
    private val panelHeight = mutableStateOf(184.dp)
    private val actions = mutableListOf<String>()

    private fun setPanel() {
        rule.setContent {
            Box(Modifier.fillMaxSize().clickable { }, contentAlignment = Alignment.Center) {
                EditKeyboardLayout(
                    onAction = { actions += it }, onBack = {},
                    backgroundColor = Color(0xFF211E29), textColor = Color(0xFFF2EDF7),
                    accentColor = Color(0xFFCEB7F7), keyBgColor = Color(0xFF35313E),
                    modifier = Modifier.size(panelWidth.value, panelHeight.value).testTag("edit-panel"),
                    shadowEnabled = false,
                )
            }
        }
    }

    @Test fun circleAndCentreSquareKeepTheirMinimumHeightSizeAndLabelsStayClear() {
        setPanel()
        var initialPad = 0f
        var initialCentre = 0f
        for (height in listOf(184.dp, 280.dp, 400.dp)) {
            rule.runOnIdle { panelHeight.value = height }
            val pad = rule.onNodeWithTag("editor-direction-pad", true).fetchSemanticsNode().boundsInRoot
            val centre = rule.onNodeWithContentDescription("选择").fetchSemanticsNode().boundsInRoot
            assertEquals(pad.width, pad.height, 1f)
            assertEquals(centre.width, centre.height, 1f)
            if (initialPad == 0f) { initialPad = pad.width; initialCentre = centre.width }
            assertEquals(initialPad, pad.width, 1f)
            assertEquals(initialCentre, centre.width, 1f)
            val corners = listOf("段首", "段尾", "复制", "粘贴")
            for (label in corners) {
                val key = rule.onNodeWithContentDescription(label).fetchSemanticsNode().boundsInRoot
                val content = rule.onNodeWithTag("editor-label-$label", true).fetchSemanticsNode().boundsInRoot
                assertTrue("$label outer margin", content.left > key.left && content.right < key.right && content.top > key.top && content.bottom < key.bottom)
                val dx = (kotlin.math.abs(content.center.x - pad.center.x) - content.width / 2).coerceAtLeast(0f)
                val dy = (kotlin.math.abs(content.center.y - pad.center.y) - content.height / 2).coerceAtLeast(0f)
                assertTrue("$label clear of circle", dx * dx + dy * dy > pad.width * pad.width / 4)
            }
            for (row in listOf(listOf("撤销", "段首", "段尾", "删除"), listOf("剪切", "复制", "粘贴", "回车"))) {
                val keys = row.map { rule.onNodeWithContentDescription(it).fetchSemanticsNode().boundsInRoot }
                keys.forEach { assertEquals(keys.first().top, it.top, 1f); assertEquals(keys.first().bottom, it.bottom, 1f) }
            }
        }
    }

    @Test fun exportMinimumAndMaximumHeightPreviews() {
        setPanel()
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        for (height in listOf(184.dp, 400.dp)) {
            rule.runOnIdle { panelHeight.value = height }
            rule.waitForIdle()
            val image = rule.onNodeWithTag("edit-panel", true).captureToImage().asAndroidBitmap()
            java.io.File(context.getExternalFilesDir(null), "editor-preview-${height.value.toInt()}.png").outputStream().use {
                image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    }

    @Test fun selectionModeChangesBoundaryActionsAndSecondTapCancelsIt() {
        setPanel()
        rule.onNodeWithContentDescription("选择").performClick()
        rule.onNodeWithContentDescription("段首").performClick()
        rule.onNodeWithContentDescription("段尾").performClick()
        rule.onNodeWithContentDescription("向左").performClick()
        rule.onNodeWithContentDescription("取消选择").performClick()
        rule.onNodeWithContentDescription("段首").performClick()
        rule.runOnIdle {
            assertEquals(listOf("select_begin", "select_paragraph_start", "select_paragraph_end", "select_arrow_left", "select_end", "home"), actions)
        }
    }

    @Test fun copyPasteAndAuxiliaryEditingActionsRemainAvailable() {
        setPanel()
        listOf("撤销", "重做", "复制", "粘贴", "全选", "剪切", "删除", "回车").forEach {
            rule.onNodeWithContentDescription(it).performClick()
        }
        rule.runOnIdle { assertEquals(listOf("undo", "redo", "copy", "paste", "select_all", "cut", "delete", "enter"), actions) }
    }
}
