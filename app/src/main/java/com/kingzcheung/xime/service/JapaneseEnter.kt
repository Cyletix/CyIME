package com.kingzcheung.xime.service

import com.kingzcheung.xime.rime.RimeEngine
import com.kingzcheung.xime.rime.RimeProcessResult
import com.kingzcheung.xime.settings.JapaneseSchemas

/**
 * jaroomaji 的 Return 绑定提交格式化后的假名读音，不能用原始罗马音替代。
 * 返回值已取走原生 commit；调用方只提交 committedText 一次，并保留引擎返回的剩余组合。
 */
internal fun RimeEngine.processJapaneseEnterIfComposing(): RimeProcessResult? {
    val schemaId = getCurrentSchema()
    if (schemaId !in JapaneseSchemas.ids && schemaId != "jaroomaji") return null
    if (getInput().isEmpty()) return null
    return processKeyAndGetResult(0xff0d, 0)
}
