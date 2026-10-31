package com.kingzcheung.xime.ui.keyboard

import com.kingzcheung.xime.settings.KeysConfigHelper

/**
 * 「按键符号」中按位置设置第二输入的固定栏。每个位置可填一个符号或一个短语。
 * [slots] 为固定格数（手写侧栏 3 格、展开栏 6 格）；null 表示可增删、超出时键盘侧滚动。
 */
enum class SideSymbolTarget(val key: String, val label: String, val where: String, val slots: Int?, val vertical: Boolean) {
    T9("t9", "九键侧栏", "九键左侧一列，空闲时显示", null, true),
    STROKE("stroke", "笔画侧栏", "笔画键盘左侧一列", null, true),
    HANDWRITING("handwriting", "手写侧栏", "手写键盘右侧删除键下方的三格", 3, true),
    HANDWRITING_ROW("handwriting_row", "手写展开栏", "全屏手写底部删除键左侧的一排", 6, false),
    COMMON("common", "常用符号栏", "点键盘「符号」键，排在「常用」页最前面，留空只显示默认符号", null, false);

    val prefKey: String get() = "side_symbols_$key"

    /** 未设置时键盘使用的默认内容（九键、笔画来自 xime.yaml）。 */
    fun defaults(): List<String> = when (this) {
        T9 -> KeysConfigHelper.getT9SideSymbols()
        STROKE -> KeysConfigHelper.getStrokeSideSymbols()
        HANDWRITING -> listOf("？", "，", "。")
        HANDWRITING_ROW -> listOf("；", "：", "！", "？", "，", "。")
        COMMON -> emptyList()
    }

    /** 键盘实际显示的内容：固定格数的栏位不足时用默认补齐，多出的截掉。 */
    fun resolve(custom: List<String>?): List<String> = slots?.let { n ->
        val own = custom.orEmpty().take(n)
        own + defaults().drop(own.size).take(n - own.size)
    } ?: (custom ?: defaults())

    companion object {
        fun parse(raw: String?): List<String>? = raw?.lines()?.map { it.trim() }?.filter { it.isNotEmpty() }
            ?.takeIf { it.isNotEmpty() }
    }
}
