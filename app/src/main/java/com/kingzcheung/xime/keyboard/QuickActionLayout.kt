package com.kingzcheung.xime.keyboard

/**
 * 工具栏与菜单共用一组快捷入口：同一入口最多出现在一处，其余为“未使用”。
 * 工具栏沿用旧的已保存顺序；“设置”不能被移出，否则用户无法回到设置页。
 */
data class QuickActionLayout(val toolbar: List<String>, val menu: List<String>) {
    /** 排列编辑器中的单一列表：分隔条之间即所在区域，拖过分隔条就换区。 */
    fun editorOrder(available: List<String>): List<String> =
        toolbar + MENU_DIVIDER + menu + UNUSED_DIVIDER + available.filter { it !in toolbar && it !in menu }

    companion object {
        const val MENU_DIVIDER = "#menu"
        const val UNUSED_DIVIDER = "#unused"
        val PINNED = ToolbarButton.SETTINGS.id

        fun normalize(toolbar: List<String>, menu: List<String>): QuickActionLayout {
            // 旧版“固定内容”并入剪贴板：工具栏和菜单都按同一规则改写。
            val bar = ToolbarButton.normalizeIds(toolbar.filter { !it.startsWith("#") })
            val rest = ToolbarButton.normalizeIds(menu.filter { !it.startsWith("#") }).filter { it !in bar }
            return QuickActionLayout(bar, if (PINNED in bar || PINNED in rest) rest else rest + PINNED)
        }

        /** 未保存过菜单时使用默认菜单，但不重复已放在工具栏上的入口；布局手势开关放在“排列”之前。 */
        fun defaultMenu(toolbar: List<String>): List<String> {
            val base = (ToolbarButton.DEFAULT_MENU - ToolbarButton.ARRANGE).toMutableList()
            // 字幕是主入口：新装用户的默认菜单也有它（紧跟拍照识别），不放进一页 8 项的 DEFAULT_MENU 契约。
            base.add(base.indexOf(ToolbarButton.OCR) + 1, ToolbarButton.CAPTION)
            return (base + ToolbarButton.LAYOUT_GESTURES + ToolbarButton.ARRANGE).map { it.id }.filter { it !in toolbar }
        }

        fun fromEditorOrder(order: List<String>): QuickActionLayout {
            val menuAt = order.indexOf(MENU_DIVIDER).takeIf { it >= 0 } ?: order.size
            val unusedAt = order.indexOf(UNUSED_DIVIDER).takeIf { it >= 0 } ?: order.size
            // 分隔条本身不可拖动，但列表重排后仍可能先后颠倒；按位置分区，保证每项只归一处。
            val first = minOf(menuAt, unusedAt)
            val second = maxOf(menuAt, unusedAt)
            val toolbar = order.subList(0, first)
            val middle = order.subList((first + 1).coerceAtMost(order.size), second.coerceAtMost(order.size))
            return normalize(toolbar, if (menuAt <= unusedAt) middle else emptyList())
        }
    }
}
