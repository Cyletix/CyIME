package com.kingzcheung.xime.settings

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf

data class CustomKeyboardLayout(
    val id: String,
    val name: String,
    val rows: List<List<String>>,
    val redVowels: Boolean = false,
    val redMoved: Boolean = true,
) {
    /** Precomputed for the per-key preedit path; copies rebuild it with the new rows. */
    val mergedGroups: List<String> = rows.flatten().filter { it.length > 1 }

    fun valid(): Boolean = name.isNotBlank() && name.length <= 32 &&
        (id == QwjrtkLayout.ID || id.matches(Regex("custom_pinyin_[a-f0-9]{32}"))) &&
        rows.map { row -> row.sumOf { it.length } } == (if (hasExtraSlot()) listOf(10, 10, 7) else listOf(10, 9, 7)) &&
        rows.flatten().count { it == EMPTY_SLOT || it == ";" } <= 1 &&
        rows.flatten().all { it == EMPTY_SLOT || it == ";" || it.length in 1..2 && it.all { c -> c in 'a'..'z' } } &&
        rows.flatten().joinToString("").toSet() == (('a'..'z').toSet() + (if (hasSemicolon()) setOf(';') else emptySet()) + (if (EMPTY_SLOT in rows.flatten()) setOf('_') else emptySet()))
    fun representative(c: Char): Char = rows.flatten().firstOrNull { c in it }?.first() ?: c
    fun encode(text: String) = text.map(::representative).joinToString("")
    /** Empty cells are editor-only; typing compacts them without creating a punctuation key. */
    fun typingRows(): List<List<String>> = rows.map { row -> row.filterNot { it == EMPTY_SLOT } }
    fun hasSemicolon() = rows.any { ";" in it }
    private fun hasExtraSlot() = hasSemicolon() || rows.any { EMPTY_SLOT in it }
    fun withSpareSlot(): CustomKeyboardLayout = if (hasExtraSlot()) this else
        copy(rows = rows.mapIndexed { index, row -> if (index == 1) row + EMPTY_SLOT else row })
    fun setSemicolonEnabled(enabled: Boolean): CustomKeyboardLayout {
        val layout = withSpareSlot()
        return layout.copy(rows = layout.rows.map { row -> row.map {
            if (enabled && it == EMPTY_SLOT) ";" else if (!enabled && it == ";") EMPTY_SLOT else it
        } })
    }
    private fun referenceRows() = if (hasExtraSlot()) listOf(BASE[0], BASE[1] + ";", BASE[2]) else BASE
    fun movedLetters(): Set<Char> = rows.zip(referenceRows()).flatMap { (row, original) ->
        row.joinToString("").zip(original).filter { (letter, base) -> letter != base && letter in 'a'..'z' }.map { it.first }
    }.toSet()
    fun isRed(letter: Char): Boolean = letter.lowercaseChar().let {
        it in 'a'..'z' && ((redVowels && it in "aeiou") || (redMoved && it in movedLetters()))
    }
    fun swap(first: String, second: String): CustomKeyboardLayout {
        // Different-size keys cannot cross rows without changing physical row widths.
        val a = rows.indexOfFirst { first in it }; val b = rows.indexOfFirst { second in it }
        if (a < 0 || b < 0 || (a != b && first.length != second.length)) return this
        return copy(rows = rows.map { row -> row.map { when(it) { first -> second; second -> first; else -> it } } })
    }
    fun merge(left: String): CustomKeyboardLayout = copy(rows = rows.map { row ->
        val index = row.indexOf(left)
        if (index < 0 || index == row.lastIndex || left.length != 1 || row[index + 1].length != 1 || left[0] !in 'a'..'z' || row[index + 1][0] !in 'a'..'z') row
        else row.take(index) + (left + row[index + 1]) + row.drop(index + 2)
    })
    fun split(key: String): CustomKeyboardLayout = copy(rows = rows.map { row -> row.flatMap {
        if (it == key) it.map(Char::toString) else listOf(it)
    } })
    fun gestures(base: Map<String, KeyGestureConfig>): Map<String, KeyGestureConfig> {
        val result = base.toMutableMap()
        rows.forEachIndexed { rowIndex, row ->
            var column = 0
            row.forEach { key ->
                if (key == EMPTY_SLOT) { column++; return@forEach }
                val source = base[key.first().toString()] ?: KeyGestureConfig()
                val hint = base[referenceRows()[rowIndex][column].toString()]?.swipeUp
                result[key] = source.copy(
                    tap = GestureDef(label = key, value = key.first().toString()), swipeUp = hint,
                    longPress = if (key.length == 1) source.longPress else LongPressConfig("bubble", key.map { GestureDef(label = it.toString(), value = it.toString()) })
                )
                column += key.length
            }
        }
        return result
    }
    /** Generate an independent prism; the base dictionary, translator and all other options are reused. */
    fun schema(base: String): String {
        require(valid())
        var text = base.replace("schema_id: rime_ice", "schema_id: $id")
            .replace("name: 雾凇拼音", "name: ${JSONObject.quote(name)}")
        if (rows.flatten().any { it.length == 2 }) {
            val speller = Regex("(?ms)^speller:.*?(?=^[a-zA-Z_][a-zA-Z_0-9]*:|\\z)").find(text)
                ?: error("中文26键缺少拼写器配置")
            check(speller.value.contains("  algebra:")) { "中文26键缺少编码规则" }
            val rule = "    - xlit/abcdefghijklmnopqrstuvwxyz/${encode("abcdefghijklmnopqrstuvwxyz")}/\n"
            text = text.replaceRange(speller.range, speller.value.trimEnd() + "\n" + rule + "\n")
            text = text.replace(Regex("(?m)^  prism:.*$"), "  prism: $id")
            if (!text.contains("  prism: $id")) text = text.replace("translator:\n", "translator:\n  prism: $id\n")
        }
        return text
    }
    companion object {
        const val EMPTY_SLOT = "_"
        val BASE = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
        fun fresh() = CustomKeyboardLayout("custom_pinyin_" + UUID.randomUUID().toString().replace("-", ""), "我的布局", BASE.map { it.map(Char::toString) })
    }
}

/** User-owned layouts. A deleted sample is never seeded again; no personal dictionaries are deleted. */
object CustomKeyboardLayouts {
    const val REVISION = "custom_keyboard_layout_revision"
    const val STATUS = "custom_keyboard_layout_status"
    private var cached: List<CustomKeyboardLayout> by mutableStateOf(emptyList())
    fun find(id: String) = cached.firstOrNull { it.id == id }
    fun isCustom(id: String) = id == QwjrtkLayout.ID || id.startsWith("custom_pinyin_")
    @Synchronized fun load(context: Context): List<CustomKeyboardLayout> {
        val file = File(context.filesDir, "custom-keyboard-layouts.json")
        if (!file.exists()) {
            cached = listOf(CustomKeyboardLayout(QwjrtkLayout.ID, "QWJRTK（双拇指）",
                listOf("qwjrtkuiop", "asdfghenl", "zxcybvm").map { it.map(Char::toString) }))
            persist(context, cached)
        } else {
            val array = JSONArray(file.readText())
            cached = (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                val savedRows = o.getString("rows").split('/').map { it.split(',') }
                val migratedRows = if (o.optInt("formatVersion", 1) < 2 && savedRows.getOrNull(1)?.lastOrNull() == ";")
                    savedRows.mapIndexed { index, row -> if (index == 1) row.dropLast(1) + CustomKeyboardLayout.EMPTY_SLOT else row }
                else savedRows
                CustomKeyboardLayout(o.getString("id"), o.getString("name"),
                    migratedRows, o.optBoolean("redVowels"), o.optBoolean("redMoved", true))
            }.also { list -> require(list.all { it.valid() } && list.map { it.id }.distinct().size == list.size) }
        }
        return cached
    }
    private fun persist(context: Context, layouts: List<CustomKeyboardLayout>) {
        val array = JSONArray()
        layouts.forEach { layout -> array.put(JSONObject().put("formatVersion", 2).put("id", layout.id).put("name", layout.name)
            .put("rows", layout.rows.joinToString("/") { it.joinToString(",") }).put("redVowels", layout.redVowels).put("redMoved", layout.redMoved)) }
        writeAtomically(File(context.filesDir, "custom-keyboard-layouts.json"), array.toString())
    }
    private fun writeAtomically(file: File, content: String) {
        val pending = File(file.parentFile, file.name + ".pending")
        try {
            pending.outputStream().use { stream -> stream.write(content.toByteArray()); stream.fd.sync() }
            java.nio.file.Files.move(pending.toPath(), file.toPath(), java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        } finally { pending.delete() }
    }

    fun save(context: Context, layout: CustomKeyboardLayout) {
        require(layout.valid()) { "布局必须完整包含26个字母，名称为1至32字" }
        // Never call schema migration while holding the layout lock: migration loads layouts too.
        synchronized(this) {
            val old = load(context)
            require(old.none { it.id != layout.id && it.name == layout.name }) { "已有同名方案" }
            val updated = if (old.any { it.id == layout.id }) old.map { if (it.id == layout.id) layout else it } else old + layout
            val dir = SchemaManager.getRimeDir(context)
            val base = File(dir, "rime_ice.schema.yaml")
            check(base.isFile) { "请先完成中文26键部署" }
            val schema = File(dir, "${layout.id}.schema.yaml")
            val previous = schema.takeIf { it.isFile }?.readText()
            try {
                writeAtomically(schema, layout.schema(base.readText()))
                persist(context, updated)
            } catch (e: Exception) {
                if (previous == null) schema.delete() else writeAtomically(schema, previous)
                throw e
            }
            cached = updated
        }
        SchemaManager.setEnabledSchemas(context, (SchemaManager.getEnabledSchemas(context) + layout.id).distinct())
        notifyChanged(context)
    }

    /** Upgrade only the translator flags of layouts owned by this editor; keep user key order and dictionaries. */
    fun enableMeasuredCorrection(context: Context) = synchronized(this) {
        val dir = SchemaManager.getRimeDir(context)
        for (layout in load(context)) {
            val file = File(dir, "${layout.id}.schema.yaml")
            if (!file.isFile) continue
            val old = file.readText()
            if (old.contains("cyime_neighbor_correction: true")) continue
            val section = Regex("(?ms)^translator:\\r?\\n.*?(?=^[a-zA-Z_][a-zA-Z_0-9]*:|\\z)").find(old) ?: continue
            val updated = section.value.replace(Regex("(?m)^  enable_correction:.*\\r?\\n"), "")
                .replaceFirst(Regex("^translator:\\r?\\n"), "translator:\n  enable_correction: true\n  cyime_neighbor_correction: true\n")
            val backup = File(context.filesDir, "rime-upgrade-backups/neighbor-correction/${file.name}")
            if (!backup.exists()) { backup.parentFile!!.mkdirs(); writeAtomically(backup, old) }
            writeAtomically(file, old.replaceRange(section.range, updated))
        }
    }
    fun delete(context: Context, id: String) {
        val remaining = SchemaManager.getEnabledSchemas(context).filterNot { it == id }
        synchronized(this) {
            val old = load(context)
            require(old.any { it.id == id })
            val schema = File(SchemaManager.getRimeDir(context), "$id.schema.yaml")
            val previous = schema.takeIf { it.isFile }?.readText()
            // Remove only the schema we own; user dictionaries and learned data are retained.
            check(!schema.exists() || schema.delete()) { "无法删除布局配置" }
            try { persist(context, old.filterNot { it.id == id }) }
            catch (e: Exception) { if (previous != null) writeAtomically(schema, previous); throw e }
            cached = old.filterNot { it.id == id }
        }
        if (SettingsPreferences.getCurrentSchema(context) == id) SettingsPreferences.setCurrentSchema(context, "rime_ice")
        SchemaManager.setEnabledSchemas(context, (remaining + "rime_ice").distinct())
        notifyChanged(context)
    }
    private fun notifyChanged(context: Context) {
        SettingsPreferences.getPrefsPublic(context).edit().putString(STATUS, "等待应用布局")
            .putLong(REVISION, System.currentTimeMillis()).apply()
    }
}
