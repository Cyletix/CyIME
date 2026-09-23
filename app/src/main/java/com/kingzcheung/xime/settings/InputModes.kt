package com.kingzcheung.xime.settings

import android.content.Context

/** 英文始终存在；排序独立于方案的启用与部署，不改变市场方案。 */
object InputModes {
    const val ENGLISH = "__xime_english"
    private const val ORDER_KEY = "input_mode_order"
    val english = SchemaInfo(ENGLISH, "英文", "", "", "内置英文模式，始终启用", isDownloaded = true)

    fun available(schemas: List<SchemaInfo>, order: List<String> = emptyList()): List<SchemaInfo> {
        val available = schemas.map { it.schemaId }.toSet()
        val visible = schemas.filter { CyimeInputDefaults.visibleSchema(it.schemaId, available) }
            .map { it.copy(name = ChineseSchemas.displayName(it.schemaId, it.name)) }
        val unique = (visible + english).distinctBy { it.schemaId }
        val byId = unique.associateBy { it.schemaId }
        val canonicalOrder = order.flatMap { id ->
            if (id == ENGLISH) listOf(id) else CyimeInputDefaults.canonicalIds(listOf(id), available)
        }
        val ordered = canonicalOrder.distinct().mapNotNull { byId[it] }
        val used = ordered.mapTo(mutableSetOf()) { it.schemaId }
        return ordered + unique.filterNot { it.schemaId in used }
    }

    fun ordered(context: Context, schemas: List<SchemaInfo>): List<SchemaInfo> =
        available(schemas, SettingsPreferences.getPrefsPublic(context).getString(ORDER_KEY, "").orEmpty().lines())

    fun saveOrder(context: Context, ids: List<String>) {
        SettingsPreferences.getPrefsPublic(context).edit()
            .putString(ORDER_KEY, ids.distinct().joinToString("\n")).apply()
    }

    fun selectedId(schemaId: String, isAsciiMode: Boolean): String = if (isAsciiMode) ENGLISH else schemaId
}
