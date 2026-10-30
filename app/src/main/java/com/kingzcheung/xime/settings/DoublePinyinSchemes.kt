package com.kingzcheung.xime.settings

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 首道双拼（内置键位表）和用户自定义的双拼方案（键位表 custom-double-pinyin.json 是唯一来源）。
 * Rime 方案和中英混输词表每次初始化时由已部署的小鹤双拼方案重新生成；不删除词库和学习记录。
 */
object DoublePinyinSchemes {
    private const val TAG = "DoublePinyinSchemes"
    const val FILE = "custom-double-pinyin.json"
    private const val BASE = "double_pinyin_flypy"
    private const val SHOUDAO_OFFERED = "double_pinyin_shoudao_offered_v1"

    fun isGenerated(id: String) = id == DoublePinyinScheme.SHOUDAO_ID || id.startsWith(DoublePinyinScheme.CUSTOM_PREFIX)
    fun isCustom(id: String) = id.startsWith(DoublePinyinScheme.CUSTOM_PREFIX)

    @Volatile private var cached: List<DoublePinyinScheme> = emptyList()

    /** 方案卡片的示例编码用；自定义方案在读过一次键位表后可查到。 */
    fun find(id: String): DoublePinyinScheme? = DoublePinyinScheme.BUILTINS.firstOrNull { it.id == id }
        ?: cached.firstOrNull { it.id == id }

    @Synchronized fun load(context: Context): List<DoublePinyinScheme> {
        val file = File(context.filesDir, FILE)
        if (!file.isFile) return emptyList<DoublePinyinScheme>().also { cached = it }
        val array = JSONArray(file.readText())
        return (0 until array.length()).map { decode(array.getJSONObject(it)) }
            .filter { it.valid() && isCustom(it.id) }.distinctBy { it.id }.also { cached = it }
    }

    /** 小鹤双拼和首道双拼在前，可以复制成自己的方案。 */
    fun all(context: Context): List<DoublePinyinScheme> = DoublePinyinScheme.BUILTINS + load(context)

    private fun decode(o: JSONObject): DoublePinyinScheme {
        fun keys(name: String) = o.getJSONObject(name).let { map -> map.keys().asSequence().associateWith { map.getString(it) } }
        return DoublePinyinScheme(o.getString("id"), o.getString("name"),
            keys("initials").mapValues { it.value.single() }, keys("finals").mapValues { it.value.single() }, keys("zero"))
    }

    private fun encode(scheme: DoublePinyinScheme) = JSONObject().put("id", scheme.id).put("name", scheme.name)
        .put("initials", JSONObject(scheme.initials.mapValues { it.value.toString() }))
        .put("finals", JSONObject(scheme.finals.mapValues { it.value.toString() }))
        .put("zero", JSONObject(scheme.zero))

    private fun persist(context: Context, schemes: List<DoublePinyinScheme>) =
        writeAtomically(File(context.filesDir, FILE), JSONArray(schemes.map(::encode)).toString())

    private fun writeAtomically(file: File, content: String) {
        file.parentFile?.mkdirs()
        val pending = File(file.parentFile, file.name + ".pending")
        try {
            pending.outputStream().use { stream -> stream.write(content.toByteArray()); stream.fd.sync() }
            java.nio.file.Files.move(pending.toPath(), file.toPath(), java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        } finally { pending.delete() }
    }

    /** 写出一个方案的 Rime 配置；内容没变时不写，避免触发重新部署。 */
    private fun writeSchema(rimeDir: File, scheme: DoublePinyinScheme) {
        val base = File(rimeDir, "$BASE.schema.yaml")
        check(base.isFile) { "请先完成小鹤双拼部署" }
        val schema = File(rimeDir, "${scheme.id}.schema.yaml")
        val text = scheme.schema(base.readText())
        if (!schema.isFile || schema.readText() != text) writeAtomically(schema, text)
        val flypyTable = File(rimeDir, "en_dicts/cn_en_flypy.txt")
        if (flypyTable.isFile) {
            val table = File(rimeDir, "en_dicts/${DoublePinyinScheme.cnEnName(scheme.id)}.txt")
            val content = scheme.cnEnTable(flypyTable.readText())
            if (!table.isFile || table.readText() != content) writeAtomically(table, content)
        }
    }

    /** 初始化时调用：小鹤双拼方案随版本更新后，首道和自定义方案跟着更新。 */
    @Synchronized fun install(context: Context, rimeDir: File) {
        if (!File(rimeDir, "$BASE.schema.yaml").isFile) return
        for (scheme in listOf(DoublePinyinScheme.SHOUDAO) + runCatching { load(context) }.getOrDefault(emptyList())) {
            try { writeSchema(rimeDir, scheme) }
            catch (e: Exception) { Log.w(TAG, "Cannot generate ${scheme.id}", e) }
        }
    }

    /** 首道双拼只在升级后加入方案列表一次，排在小鹤双拼后面；之后用户的启停选择不再改动。 */
    fun offerShoudaoOnce(context: Context, enabled: List<String>): List<String> {
        val prefs = SettingsPreferences.getPrefsPublic(context)
        if (prefs.getBoolean(SHOUDAO_OFFERED, false)) return enabled
        if (!File(SchemaManager.getRimeDir(context), "${DoublePinyinScheme.SHOUDAO_ID}.schema.yaml").isFile) return enabled
        val updated = if (DoublePinyinScheme.SHOUDAO_ID in enabled) enabled else {
            val after = enabled.indexOf(BASE)
            if (after < 0) enabled + DoublePinyinScheme.SHOUDAO_ID
            else enabled.take(after + 1) + DoublePinyinScheme.SHOUDAO_ID + enabled.drop(after + 1)
        }
        if (updated != enabled) SchemaManager.setEnabledSchemas(context, updated)
        prefs.edit().putBoolean(SHOUDAO_OFFERED, true).apply()
        return updated
    }

    fun save(context: Context, scheme: DoublePinyinScheme) {
        require(scheme.valid() && isCustom(scheme.id)) { scheme.problems().firstOrNull() ?: "方案无效" }
        synchronized(this) {
            val old = load(context)
            require((DoublePinyinScheme.BUILTINS + old).none { it.id != scheme.id && it.name == scheme.name.trim() }) { "已有同名方案" }
            val updated = if (old.any { it.id == scheme.id }) old.map { if (it.id == scheme.id) scheme else it } else old + scheme
            val rimeDir = SchemaManager.getRimeDir(context)
            val schemaFile = File(rimeDir, "${scheme.id}.schema.yaml")
            val previous = schemaFile.takeIf { it.isFile }?.readText()
            try {
                writeSchema(rimeDir, scheme)
                persist(context, updated)
                cached = updated
            } catch (e: Exception) {
                if (previous == null) schemaFile.delete() else writeAtomically(schemaFile, previous)
                throw e
            }
        }
        SchemaManager.setEnabledSchemas(context, (SchemaManager.getEnabledSchemas(context) + scheme.id).distinct())
        notifyChanged(context)
    }

    fun delete(context: Context, id: String) {
        require(isCustom(id))
        synchronized(this) {
            val old = load(context)
            require(old.any { it.id == id })
            persist(context, old.filterNot { it.id == id })
            cached = old.filterNot { it.id == id }
            val rimeDir = SchemaManager.getRimeDir(context)
            // 只删本方案的配置和混输词表；词库、用户词和学习记录都保留。
            File(rimeDir, "$id.schema.yaml").delete()
            File(rimeDir, "en_dicts/${DoublePinyinScheme.cnEnName(id)}.txt").delete()
        }
        val fallback = if (File(SchemaManager.getRimeDir(context), "$BASE.schema.yaml").isFile) BASE else "rime_ice"
        if (SettingsPreferences.getCurrentSchema(context) == id) SettingsPreferences.setCurrentSchema(context, fallback)
        SchemaManager.setEnabledSchemas(context, SchemaManager.getEnabledSchemas(context).filterNot { it == id }
            .ifEmpty { listOf(fallback) })
        notifyChanged(context)
    }

    /** 与自定义布局共用一次“应用方案更改”：输入法收到后重新部署。 */
    private fun notifyChanged(context: Context) {
        SettingsPreferences.getPrefsPublic(context).edit().putString(CustomKeyboardLayouts.STATUS, "等待应用双拼方案")
            .putLong(CustomKeyboardLayouts.REVISION, System.currentTimeMillis()).apply()
    }
}
