package com.kingzcheung.xime.service

/** jaroomaji 使用大写罗马音编码片假名；不能走中文 Shift 直接提交英文字母的路径。 */
object JapaneseTyping {
    fun usesKanaCase(schemaId: String, asciiMode: Boolean): Boolean =
        !asciiMode && schemaId in setOf("japanese", "jaroomaji")

    fun keyCode(key: String, shifted: Boolean): Int =
        (if (shifted) key.uppercase() else key.lowercase()).first().code
}
