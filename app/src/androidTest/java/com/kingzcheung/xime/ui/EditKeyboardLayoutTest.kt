package com.kingzcheung.xime.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.geometry.Offset
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
    private val panelFontScale = mutableStateOf(1f)
    private val actions = mutableListOf<String>()

    private fun setPanel() {
        rule.setContent {
          CompositionLocalProvider(LocalDensity provides Density(1f, panelFontScale.value)) {
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
    }

    @Test fun circleAndCentreSquareGrowWithPanelAndLabelsStayClear() {
        setPanel()
        var initialPad = 0f
        var initialCentre = 0f
        var initialAvailableDiameter = 0f
        for ((width, height) in listOf(320.dp to 144.dp, 330.dp to 280.dp,
            480.dp to 280.dp, 640.dp to 400.dp)) {
            rule.runOnIdle { panelWidth.value = width; panelHeight.value = height }
            val pad = rule.onNodeWithTag("editor-direction-pad", true).fetchSemanticsNode().boundsInRoot
            val controls = rule.onNodeWithTag("editor-pad-controls", true).fetchSemanticsNode().boundsInRoot
            val grid = rule.onNodeWithTag("editor-grid", true).fetchSemanticsNode().boundsInRoot
            val centre = rule.onNodeWithContentDescription("选择").fetchSemanticsNode().boundsInRoot
            assertEquals(pad.width, pad.height, 1f)
            assertEquals(centre.width, centre.height, 1f)
            val availableDiameter = minOf(grid.width * 3f / 5f, grid.height)
            assertTrue("$width x $height respects the limiting dimension", controls.width <= availableDiameter + 1f)
            assertTrue("pad must not shrink as the measured panel grows", pad.width >= initialPad - 1f)
            assertTrue("centre must not shrink as the measured panel grows", centre.width >= initialCentre - 1f)
            if (availableDiameter > initialAvailableDiameter + 1f) {
                assertTrue("pad grows with measured available space", pad.width > initialPad)
                assertTrue("centre grows with measured available space", centre.width > initialCentre)
            }
            initialAvailableDiameter = availableDiameter
            initialPad = pad.width; initialCentre = centre.width
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

    @Test fun cornerLabelsStayOutsideDiscWhenTextOrPanelShapeLimitsIt() {
        setPanel()
        for ((width, height, font) in listOf(
            Triple(320.dp, 200.dp, 1f),
            Triple(280.dp, 220.dp, 1.3f),
            Triple(280.dp, 220.dp, 2f),
        )) {
            rule.runOnIdle { panelWidth.value = width; panelHeight.value = height; panelFontScale.value = font }
            val pad = rule.onNodeWithTag("editor-direction-pad", true).fetchSemanticsNode().boundsInRoot
            val controls = rule.onNodeWithTag("editor-pad-controls", true).fetchSemanticsNode().boundsInRoot
            val grid = rule.onNodeWithTag("editor-grid", true).fetchSemanticsNode().boundsInRoot
            assertTrue("$width x $height font=$font stays within the available dimensions",
                controls.width <= minOf(grid.width * 3f / 5f, grid.height) + 1f)
            for (label in listOf("段首", "段尾", "复制", "粘贴")) {
                val content = rule.onNodeWithTag("editor-label-$label", true).fetchSemanticsNode().boundsInRoot
                val dx = (kotlin.math.abs(content.center.x - pad.center.x) - content.width / 2).coerceAtLeast(0f)
                val dy = (kotlin.math.abs(content.center.y - pad.center.y) - content.height / 2).coerceAtLeast(0f)
                assertTrue("$label overlaps disc at $width x $height font=$font",
                    dx * dx + dy * dy > pad.width * pad.width / 4)
            }
        }
    }

    @Test fun minimumHeightReservesCornerBreathingRoom() {
        setPanel()
        for ((width, height) in listOf(280.dp to 140.dp, 320.dp to 144.dp, 400.dp to 144.dp, 640.dp to 140.dp)) {
            rule.runOnIdle { panelWidth.value = width; panelHeight.value = height }
            for (label in listOf("段首", "段尾", "复制", "粘贴")) {
                val key = rule.onNodeWithContentDescription(label).fetchSemanticsNode().boundsInRoot
                val content = rule.onNodeWithTag("editor-label-$label", true).fetchSemanticsNode().boundsInRoot
                val margin = minOf(content.left - key.left, key.right - content.right,
                    content.top - key.top, key.bottom - content.bottom)
                assertTrue("$label at $width x $height margin=$margin", margin >= 7f)
                rule.onNodeWithContentDescription(label).performTouchInput {
                    click(Offset(content.center.x - key.left, content.center.y - key.top))
                }
            }
        }
        rule.runOnIdle { assertEquals(List(4) { listOf("home", "end", "copy", "paste") }.flatten(), actions) }
    }

    @Test fun exportMinimumAndMaximumHeightPreviews() {
        setPanel()
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        for (height in listOf(144.dp, 184.dp, 400.dp)) {
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
        // A sector's bounding-box centre belongs to Select, not to the arrow.
        rule.onNodeWithContentDescription("向左").performTouchInput {
            click(Offset(width / 6f, height / 2f))
        }
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
