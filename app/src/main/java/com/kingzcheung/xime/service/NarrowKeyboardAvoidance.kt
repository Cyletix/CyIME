package com.kingzcheung.xime.service

/**
 * 固定键盘比窗口窄（两侧露出后面的应用）时，输入框整个落在卡片左边或右边，键盘就没盖住它。
 *
 * 输入法只能向系统报一条顶线（contentTopInsets / visibleTopInsets），报不了左右范围；系统和小窗管理按这条线
 * 把整屏宽度都当成被键盘盖住，旁边的小窗也会被推上去。输入框在卡片旁边时，按悬浮键盘的方式报「不占地方」；
 * 输入框和卡片左右有重叠（包括整屏宽度的输入框）、卡片铺满宽度或开了「填满留空处」时照旧避让。
 * 坐标都是键盘窗口里的横坐标。
 */
internal fun narrowKeyboardLeavesEditorUncovered(
    cardLeft: Int,
    cardRight: Int,
    windowWidth: Int,
    sidesFilled: Boolean,
    editorLeft: Float,
    editorRight: Float,
): Boolean {
    if (sidesFilled || cardRight - cardLeft >= windowWidth) return false
    if (!editorLeft.isFinite() || !editorRight.isFinite() || editorRight <= editorLeft) return false
    return editorRight <= cardLeft || editorLeft >= cardRight
}
