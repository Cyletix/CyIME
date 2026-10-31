package com.kingzcheung.xime.settings

import android.content.Context

/**
 * 自建双字母布局的角标修改，每个布局各存一份（B 站用户 10-31：自己合并的 14 键，下滑数字和符号也要能改）。
 * 预设就是 [MergedKeySymbols] 按读顺序排的那张表，和内置 14 键（[FourteenKeySymbols]）一样只存与预设不同的位置。
 * 位置按键存（合并键 qw、单字母 e）：之后又合并、拆开的键 id 变了，旧修改不再生效，这一格回到预设。
 * 没有合并键的自建布局按 26 键位置出角标，在「中文26键」里改，不在这里。
 */
internal object CustomLayoutSymbols {
    private fun prefix(layoutId: String) = "custom_layout_symbols_${layoutId}_"

    /** 符号设置页按这几排画键：只留字母键，分号键、空位不带角标。 */
    fun rows(layout: CustomKeyboardLayout): List<List<String>> = layout.typingRows()
        .map { row -> row.filter { cell -> cell.isNotEmpty() && cell.all { it in 'a'..'z' } } }
        .filter { it.isNotEmpty() }
    /** 读顺序，和键盘上 [MergedKeySymbols] 排预设用的是同一串。 */
    fun keys(layout: CustomKeyboardLayout): List<String> = rows(layout).flatten()
    /** 有合并键才按合并键规则出角标（InputProfile 里就是 MERGED），才在这里改。 */
    fun editable(layout: CustomKeyboardLayout) = layout.mergedGroups.isNotEmpty()
    private fun slotIds(layout: CustomKeyboardLayout) = listOf("up", "down").flatMap { direction -> keys(layout).map { "$direction.$it" } }

    fun load(context: Context, layout: CustomKeyboardLayout): Map<String, String> {
        val prefs = SettingsPreferences.getPrefsPublic(context)
        val prefix = prefix(layout.id)
        return buildMap {
            for (id in slotIds(layout)) if (prefs.contains(prefix + id)) put(id, prefs.getString(prefix + id, "").orEmpty())
        }
    }

    /** null 清掉修改回到预设；这个布局里已经没有的键，旧修改一并清掉。 */
    fun save(context: Context, layout: CustomKeyboardLayout, values: Map<String, String>) {
        val prefs = SettingsPreferences.getPrefsPublic(context)
        val prefix = prefix(layout.id)
        val edit = prefs.edit()
        prefs.all.orEmpty().keys.filter { it.startsWith(prefix) }.forEach { edit.remove(it) }
        for (id in slotIds(layout)) values[id]?.let { edit.putString(prefix + id, it) }
        check(edit.commit()) { "无法保存符号设置" }
        KeysConfigHelper.loadConfig(context)
    }

    /** 删布局时一起删它的角标修改。 */
    fun clear(context: Context, layoutId: String) {
        val prefs = SettingsPreferences.getPrefsPublic(context)
        val prefix = prefix(layoutId)
        val stale = prefs.all.orEmpty().keys.filter { it.startsWith(prefix) }
        if (stale.isEmpty()) return
        val edit = prefs.edit()
        stale.forEach { edit.remove(it) }
        edit.apply()
    }
}
