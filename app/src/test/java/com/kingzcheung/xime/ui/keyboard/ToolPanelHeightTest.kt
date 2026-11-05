package com.kingzcheung.xime.ui.keyboard

import org.junit.Assert.assertEquals
import org.junit.Test

/** 键盘窗口为工具面板多留的高度要和面板本身一样高，否则容器多/少一截。 */
class ToolPanelHeightTest {
    @Test
    fun `翻译栏只占一行，输入面板照旧，全屏面板不撑高`() {
        assertEquals(TOOL_PANEL_BAR_HEIGHT, toolPanelExtraHeight(visible = true, display = "LIVE"))
        assertEquals(48, TOOL_PANEL_BAR_HEIGHT)
        assertEquals(TOOL_PANEL_HEIGHT, toolPanelExtraHeight(visible = true, display = "DIRECT"))
        assertEquals(TOOL_PANEL_HEIGHT, toolPanelExtraHeight(visible = true, display = null))
        assertEquals(TOOL_PANEL_HEIGHT, toolPanelExtraHeight(visible = true, display = "EDITING"))
        assertEquals(0, toolPanelExtraHeight(visible = true, display = "PASSIVE"))
        assertEquals(0, toolPanelExtraHeight(visible = false, display = "LIVE"))
    }
}
