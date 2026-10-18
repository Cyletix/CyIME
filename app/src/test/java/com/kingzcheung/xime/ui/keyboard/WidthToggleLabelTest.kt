package com.kingzcheung.xime.ui.keyboard

import com.kingzcheung.xime.keyboard.ToolbarButton
import com.kingzcheung.xime.keyboard.ToolbarButtonItem
import org.junit.Assert.assertEquals
import org.junit.Test

/** 全角/半角开关按当前状态显示文字，其它按钮不受影响。 */
class WidthToggleLabelTest {
    @Test fun widthToggleShowsTheCurrentState() {
        val width = ToolbarButtonItem.Builtin(ToolbarButton.FULL_SHAPE)
        assertEquals("全角", width.stateLabel(active = true))
        assertEquals("半角", width.stateLabel(active = false))
        val glass = ToolbarButtonItem.Builtin(ToolbarButton.GLASS)
        assertEquals(glass.label, glass.stateLabel(active = true))
        assertEquals(glass.label, glass.stateLabel(active = false))
    }
}
