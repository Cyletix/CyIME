package com.kingzcheung.xime.keyboard

/**
 * 工具栏与菜单共用一组快捷入口：同一入口最多出现在一处，其余为“未使用”。
 * 工具栏沿用旧的已保存顺序；未使用的入口可在菜单编辑模式中重新添加。
 */
data class QuickActionLayout(val toolbar: List<String>, val menu: List<String>) {
    /** 排列编辑器中的单一列表：分隔条之间即所在区域，拖过分隔条就换区。 */
    fun editorOrder(available: List<String>): List<String> =
        toolbar + MENU_DIVIDER + menu + UNUSED_DIVIDER + available.filter { it !in toolbar && it !in menu }

    companion object {
        const val MENU_DIVIDER = "#menu"
        const val UNUSED_DIVIDER = "#unused"
        /** 2026-10-31 起默认在菜单里的开关；已保存的菜单由 SettingsPreferences 插入一次。 */
        val MENU_TOGGLES_1031 = listOf(ToolbarButton.FLOATING, ToolbarButton.TRADITIONAL)

        fun normalize(toolbar: List<String>, menu: List<String>): QuickActionLayout {
            // 旧版“固定内容”并入剪贴板：工具栏和菜单都按同一规则改写。
            val bar = ToolbarButton.normalizeIds(toolbar.filter { !it.startsWith("#") })
            val rest = ToolbarButton.normalizeIds(menu.filter { !it.startsWith("#") }).filter { it !in bar }
            return QuickActionLayout(bar, rest)
        }

        /** 未保存过菜单时使用默认菜单，但不重复已放在工具栏上的入口；布局手势开关放在“排列”之前。 */
        fun defaultMenu(toolbar: List<String>): List<String> {
            val base = (ToolbarButton.DEFAULT_MENU - ToolbarButton.ARRANGE).toMutableList()
            // 悬浮、简繁两个开关也默认放进菜单，紧跟全角/半角（用户 2026-10-31）；简繁只在拼音、双拼、仓颉键盘出现。
            base.addAll(base.indexOf(ToolbarButton.FULL_SHAPE) + 1, MENU_TOGGLES_1031)
            return (base + ToolbarButton.LAYOUT_GESTURES + ToolbarButton.ARRANGE).map { it.id }.filter { it !in toolbar }
        }

        /**
         * 排列页只列当前键盘会出现的入口；保存时把没列出的（[hidden]，属于别的键盘）按原来的区和位置放回，
         * 不能因为这次没显示就从工具栏／菜单里删掉。
         */
        fun restoreHidden(edited: QuickActionLayout, original: QuickActionLayout, hidden: Set<String>): QuickActionLayout {
            fun merge(section: List<String>, originalSection: List<String>): List<String> {
                val result = section.filterNot { it in hidden }.toMutableList()
                originalSection.forEachIndexed { index, id ->
                    if (id in hidden) result.add(index.coerceAtMost(result.size), id)
                }
                return result
            }
            return normalize(merge(edited.toolbar, original.toolbar), merge(edited.menu, original.menu))
        }

        /**
         * 刚启用的插件按钮（[fresh]）放进菜单「排列」前面；已在工具栏或菜单里的不动。
         * 以前启用插件后按钮落在「不使用」，键盘上找不到（用户 2026-11-02）。
         */
        fun withPluginButtons(toolbar: List<String>, menu: List<String>, fresh: List<String>): List<String> {
            val result = menu.toMutableList()
            fresh.filter { it.isNotEmpty() && it !in toolbar && it !in result }.forEach { id ->
                val at = result.indexOf(ToolbarButton.ARRANGE.id).takeIf { it >= 0 } ?: result.size
                result.add(at, id)
            }
            return result
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
