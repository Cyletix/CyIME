package com.kingzcheung.xime.ui

import com.kingzcheung.xime.keyboard.ToolbarAction
import com.kingzcheung.xime.keyboard.ToolbarButton
import com.kingzcheung.xime.keyboard.ToolbarButtonItem

/** Menu and toolbar share one action list; tests build it from the same built-in entries. */
internal fun quickActions(buttons: List<ToolbarButton>, onClick: (ToolbarButton) -> Unit = {}): List<ToolbarAction> =
    buttons.map { button -> ToolbarAction(ToolbarButtonItem.Builtin(button)) { onClick(button) } }
