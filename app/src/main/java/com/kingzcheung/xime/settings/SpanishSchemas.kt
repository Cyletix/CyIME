package com.kingzcheung.xime.settings

/**
 * 西班牙语入口方案（assets/rime_spanish/spanish.schema.yaml）。它不组字：字母像英文一样逐字上屏，
 * 补全来自 [com.kingzcheung.xime.association.SpanishCompletion]。键盘是 xime.yaml 的 qwerty_spanish。
 * 默认不启用：在「语言与方案」打开西班牙语时才加入方案列表并部署。
 */
object SpanishSchemas {
    const val ID = "spanish"
    val ids = listOf(ID)
}
