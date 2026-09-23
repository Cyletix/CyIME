package com.kingzcheung.xime.ui

import android.app.Application
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.kingzcheung.xime.clipboard.ClipboardItem
import com.kingzcheung.xime.keyboard.*
import com.kingzcheung.xime.settings.SchemaInfo
import com.kingzcheung.xime.ui.keyboard.*
import com.kingzcheung.xime.viewmodel.*
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PanelLayoutAuditTest {
    @get:Rule val rule = createComposeRule()
    private val app = ApplicationProvider.getApplicationContext<Application>()

    @Test fun portraitPanelsAtLowHeight() = checkPanels(360, 220, false, 1f)
    @Test fun narrowPanelsWithTransparencyAndLargeText() = checkPanels(280, 220, false, 1.3f)
    @Test fun landscapePanelsAtLowHeight() = checkPanels(640, 180, true, 1f)

    private fun checkPanels(width: Int, height: Int, landscape: Boolean, font: Float) {
        val vm = KeyboardViewModel(app)
        val schemas = listOf("中文九键", "小鹤双拼", "日语26键", "日语九宫格")
            .mapIndexed { index, name -> SchemaInfo("mode$index", name, "", "", "") }
        rule.setContent {
            val config = Configuration(LocalConfiguration.current).apply {
                orientation = if (landscape) Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT
                screenWidthDp = if (landscape) 640 else 360
                screenHeightDp = if (landscape) 360 else 800
            }
            CompositionLocalProvider(LocalConfiguration provides config, LocalDensity provides Density(1f, font)) {
                MaterialTheme {
                    Box(Modifier.background(Color.White)) {
                        KeyboardView(vm, KeyboardUiState(isDarkTheme = true, schemas = schemas,
                            clipboardItems = (1..8).map { ClipboardItem(it.toLong(), "复制内容 $it") },
                            quickSendItems = listOf(ClipboardItem(9, "常用短语")), toolPanelTitle = "插件面板"),
                            KeyboardCallbacks(onKeyPress = { _, _ -> }, onCandidateSelect = {}, onReorderSchemas = {}),
                            modifier = Modifier.size(width.dp, height.dp).graphicsLayer { alpha = 0.5f }.testTag("audit-keyboard"))
                    }
                }
            }
        }
        rule.waitForIdle()
        val issues = mutableListOf<String>()
        val routes = listOf(OverlayRoute.Menu, OverlayRoute.SchemaList, OverlayRoute.Edit, OverlayRoute.Emoji,
            OverlayRoute.Clipboard(0), OverlayRoute.Clipboard(1), OverlayRoute.ToolbarCustomize,
            OverlayRoute.SplitWords("这是一个拆词测试，包含标点与多个可以选择的文字。"), OverlayRoute.Symbol, OverlayRoute.ToolPanel)
        routes.forEachIndexed { index, route ->
            rule.runOnIdle { vm.closeOverlay(); vm.showOverlay(route) }
            val keyboard = rule.onNodeWithTag("audit-keyboard").fetchSemanticsNode().boundsInRoot
            val toolbar = rule.onNodeWithTag("toolbar-order-row").fetchSemanticsNode().boundsInRoot
            val panel = rule.onNodeWithTag("keyboard-overlay").fetchSemanticsNode().boundsInRoot
            if (panel.top < toolbar.bottom || panel.bottom > keyboard.bottom) issues += "$route 面板越过工具栏或底边"
            val exitLabel = when (route) {
                OverlayRoute.ToolbarCustomize -> "确定"
                OverlayRoute.ToolPanel -> "关闭"
                OverlayRoute.Symbol -> "返回键盘"
                else -> "返回"
            }
            val backs = rule.onAllNodesWithContentDescription(exitLabel).fetchSemanticsNodes()
            if (backs.size != 1) issues += "$route $exitLabel 入口数量 ${backs.size}"
            if (route == OverlayRoute.Edit) {
                val delete = rule.onNodeWithContentDescription("删除").fetchSemanticsNode().boundsInRoot
                if (delete.top - panel.top > 3f || panel.right - delete.right > 5f) issues += "编辑回格未贴右上角"
            }
            if (route == OverlayRoute.SchemaList) {
                val tiles = rule.onAllNodes(hasTestTag("schema-tile:mode0") or hasTestTag("schema-tile:mode1") or
                    hasTestTag("schema-tile:mode2") or hasTestTag("schema-tile:mode3")).fetchSemanticsNodes().map { it.boundsInRoot }
                tiles.forEachIndexed { n, a -> tiles.drop(n + 1).forEach { b -> if (a.overlaps(b)) issues += "方案卡片重叠" } }
            }
            save("panel-$width-$index")
        }
        assertTrue(issues.joinToString("\n"), issues.isEmpty())
    }

    @Test fun compactResizeKeepsSliderAboveAllThreeActions() {
        val size = mutableStateOf(280 to 180)
        rule.setContent {
            val config = Configuration(LocalConfiguration.current).apply { screenWidthDp = 640; screenHeightDp = 360 }
            CompositionLocalProvider(LocalConfiguration provides config, LocalDensity provides Density(1f)) {
                MaterialTheme {
                    key(size.value) {
                        KeyboardResizeOverlay(size.value.second, size.value.second, 0, false,
                            onHeightChange = {}, onBottomPaddingChange = {}, onOpacityChange = {}, onReset = {},
                            onConfirm = { _, _, _, _ -> }, onCancel = {}, modifier = Modifier.size(size.value.first.dp, size.value.second.dp))
                    }
                }
            }
        }
        for (next in listOf(280 to 180, 640 to 140)) {
            rule.runOnIdle { size.value = next }
            val slider = rule.onNodeWithTag("keyboard-opacity-slider", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val reset = rule.onNodeWithContentDescription("重置").fetchSemanticsNode().boundsInRoot
            val floating = rule.onNodeWithContentDescription("悬浮键盘").fetchSemanticsNode().boundsInRoot
            val confirm = rule.onNodeWithContentDescription("确认").fetchSemanticsNode().boundsInRoot
            assertTrue("透明度不得与底部按钮相叠", slider.bottom <= reset.top)
            assertTrue(reset.right <= floating.left && floating.right <= confirm.left)
        }
    }

    private fun save(name: String) {
        val dir = File(app.getExternalFilesDir(null), "panel-audit").apply { mkdirs() }
        val bitmap = rule.onNodeWithTag("audit-keyboard").captureToImage().asAndroidBitmap()
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
