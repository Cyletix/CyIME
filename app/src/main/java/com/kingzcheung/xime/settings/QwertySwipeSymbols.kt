package com.kingzcheung.xime.settings

import android.content.Context

/**
 * 角标都是固定字符，印什么上屏什么，不随全角／半角转换（只有空格两侧的逗号句号跟随）。
 * 中文：上角标是数字和常用全角符号，下角标是半角符号；英文有自己的一套半角预设。
 */
internal object QwertySwipeSymbols {
    val rows = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
    val keys = rows.joinToString("")
    private fun preset(vararg values: String) = keys.map(Char::toString).zip(values.toList()).toMap()
    val up = preset(
        "1", "2", "3", "4", "5", "6", "7", "8", "9", "0",
        "～", "—", "…", "·", "¥", "、", "【", "】", "！",
        "（", "）", "：", "；", "《", "》", "？")
    val down = preset(
        "!", "@", "#", "$", "%", "^", "&", "*", "(", ")",
        "~", "-", "_", "'", "=", "\\", "[", "]", "/",
        "\"", "`", ":", ";", "<", ">", "?")
    val englishUp = preset(
        "1", "2", "3", "4", "5", "6", "7", "8", "9", "0",
        "~", "-", "_", "'", "=", "\\", "[", "]", "/",
        "\"", "`", ":", ";", "<", ">", "?")
    val englishDown = preset(
        "!", "@", "#", "$", "%", "^", "&", "*", "(", ")",
        "{", "}", "|", "+", "…", "—", "·", "¥", "°",
        "×", "÷", "±", "≈", "≠", "≤", "≥")
    fun up(english: Boolean) = if (english) englishUp else up
    fun down(english: Boolean) = if (english) englishDown else down
    // 2026-10-12 至 10-16 的预设：首次换成固定字符预设时，把当时看到的中文角标另存一份。
    private val v1012Up = preset(
        "1", "2", "3", "4", "5", "6", "7", "8", "9", "0",
        "`", "=", "…", "—", "√", "、", "[", "]", "\\",
        "'", "\"", ":", ";", "<", ">", "?")
    private val v1012Down = preset(
        "!", "@", "#", "$", "%", "^", "&", "*", "(", ")",
        "~", "¥", "·", "_", "°", "！", "{", "}", "|",
        "+", "-", "×", "÷", ",", ".", "/")
    // 2026-10-12 之前的预设，只用于把升级前看到的布局另存为「旧布局」。
    private val legacyUp = keys.map(Char::toString).zip(listOf(
        "1", "2", "3", "4", "5", "6", "7", "8", "9", "0",
        "@", "-", "*", "_", "[", "]", "(", ")", "\\",
        "'", "\"", ":", ";", "!", "?", "/")).toMap()
    private val legacyDown = keys.map(Char::toString).zip(listOf(
        "!", "@", "#", "$", "%", "^", "&", "*", "(", ")",
        "`", "~", "·", "—", "…", "°", "{", "}", "|",
        "=", "+", "-", "×", "÷", "<", ">")).toMap()
    private fun prefix(english: Boolean) = "qwerty_symbols_${if (english) "en" else "zh"}_"

    const val LEGACY_VERSION = "旧布局"
    private const val VERSIONS_KEY = "qwerty_symbol_versions"
    private const val LEGACY_SAVED_KEY = "qwerty_symbol_versions_legacy_saved"
    const val V1012_VERSION = "10-12布局"
    private const val FIXED_PRESET_KEY = "qwerty_symbol_fixed_preset_adopted"
    private const val RECORD = '\u001E'
    private const val FIELD = '\u001F'
    /** 一个版本的 52 个位置：先全部上方，再全部下方。 */
    val slotIds: List<String> get() = listOf("up", "down").flatMap { direction -> keys.map { "$direction.$it" } }

    /** 用户另存的整套中文角标，按保存顺序。 */
    fun versions(context: Context): Map<String, Map<String, String>> {
        val raw = SettingsPreferences.getPrefsPublic(context).getString(VERSIONS_KEY, null).orEmpty()
        val ids = slotIds
        return raw.split(RECORD).mapNotNull { record ->
            val fields = record.split(FIELD)
            if (fields.size != ids.size + 1 || fields[0].isEmpty()) null
            else fields[0] to ids.zip(fields.drop(1)).toMap()
        }.toMap(LinkedHashMap<String, Map<String, String>>())
    }

    fun saveVersion(context: Context, name: String, values: Map<String, String>) =
        writeVersions(context, versions(context) + (versionName(name) to values))

    fun deleteVersion(context: Context, name: String) = writeVersions(context, versions(context) - name)

    fun versionName(name: String) = name.filter { it != RECORD && it != FIELD }.trim()

    private fun writeVersions(context: Context, all: Map<String, Map<String, String>>) {
        val ids = slotIds
        val raw = all.entries.joinToString(RECORD.toString()) { (name, values) ->
            (listOf(name) + ids.map { values[it].orEmpty().filter { c -> c != RECORD && c != FIELD } })
                .joinToString(FIELD.toString())
        }
        check(SettingsPreferences.getPrefsPublic(context).edit().putString(VERSIONS_KEY, raw).commit()) { "无法保存布局版本" }
    }

    /**
     * 升级到新预设时只执行一次：把升级前实际看到的中文角标（用户修改 > 自定义 YAML > 旧预设）
     * 另存为「旧布局」，再清掉中文修改让新预设生效。旧布局随时可在符号设置页载入。
     */
    fun preserveLegacyLayout(context: Context, custom: Map<String, KeyGestureConfig>) {
        val prefs = SettingsPreferences.getPrefsPublic(context)
        if (prefs.getBoolean(LEGACY_SAVED_KEY, false)) return
        val overrides = load(context, false)
        fun configured(def: GestureDef?) = def?.let { it.value.ifEmpty { it.label } }
        val legacy = buildMap {
            for (key in QwertySwipeSymbols.keys.map(Char::toString)) {
                put("up.$key", overrides["up.$key"] ?: configured(custom[key]?.swipeUp) ?: legacyUp.getValue(key))
                put("down.$key", overrides["down.$key"] ?: configured(custom[key]?.swipeDown) ?: legacyDown.getValue(key))
            }
        }
        if (LEGACY_VERSION !in versions(context)) saveVersion(context, LEGACY_VERSION, legacy)
        val edit = prefs.edit()
        for (id in slotIds) edit.remove(prefix(false) + id)
        check(edit.putBoolean(LEGACY_SAVED_KEY, true).commit()) { "无法保存旧布局" }
    }

    /**
     * 换成固定字符预设时只执行一次：把当时看到的中文角标另存为「10-12布局」，
     * 再清掉中文和英文的修改，让新的中英预设生效。
     */
    fun adoptFixedPreset(context: Context, custom: Map<String, KeyGestureConfig>) {
        val prefs = SettingsPreferences.getPrefsPublic(context)
        if (prefs.getBoolean(FIXED_PRESET_KEY, false)) return
        val overrides = load(context, false)
        fun configured(def: GestureDef?) = def?.let { it.value.ifEmpty { it.label } }
        val previous = buildMap {
            for (key in QwertySwipeSymbols.keys.map(Char::toString)) {
                put("up.$key", overrides["up.$key"] ?: configured(custom[key]?.swipeUp) ?: v1012Up.getValue(key))
                put("down.$key", overrides["down.$key"] ?: configured(custom[key]?.swipeDown) ?: v1012Down.getValue(key))
            }
        }
        if (V1012_VERSION !in versions(context)) saveVersion(context, V1012_VERSION, previous)
        val edit = prefs.edit()
        for (id in slotIds) { edit.remove(prefix(false) + id); edit.remove(prefix(true) + id) }
        check(edit.putBoolean(FIXED_PRESET_KEY, true).commit()) { "无法保存10-12布局" }
    }

    fun load(context: Context, english: Boolean): Map<String, String> {
        val prefs = SettingsPreferences.getPrefsPublic(context)
        val prefix = prefix(english)
        return buildMap {
            // buildMap has its own empty `keys`; qualify the fixed alphabet explicitly.
            for (key in QwertySwipeSymbols.keys) for (direction in listOf("up", "down")) {
                val id = "$direction.$key"
                if (prefs.contains(prefix + id)) put(id, prefs.getString(prefix + id, "").orEmpty())
            }
        }
    }

    fun save(context: Context, english: Boolean, values: Map<String, String>) {
        val edit = SettingsPreferences.getPrefsPublic(context).edit()
        val prefix = prefix(english)
        for (key in keys) for (direction in listOf("up", "down")) {
            val id = "$direction.$key"
            val value = values[id]
            if (value == null) edit.remove(prefix + id) else edit.putString(prefix + id, value)
        }
        check(edit.commit()) { "无法保存符号设置" }
        KeysConfigHelper.loadConfig(context)
    }

    fun apply(key: String, original: KeyGestureConfig?, overrides: Map<String, String>,
        custom: KeyGestureConfig?, english: Boolean = false): KeyGestureConfig? {
        if (key !in up) return original
        fun gesture(direction: String, preset: String, configured: GestureDef?): GestureDef {
            val override = overrides["$direction.$key"]
            if (override == null && configured != null) return configured
            val value = override ?: preset
            return GestureDef(label = value, value = value,
                display = if (direction == "up") original?.swipeUp?.display ?: DisplayMode.BOTH else DisplayMode.BOTH,
                action = if (value.isEmpty()) com.kingzcheung.xime.keyboard.GestureAction.NONE
                    else com.kingzcheung.xime.keyboard.GestureAction.COMMIT)
        }
        return (original ?: KeyGestureConfig()).copy(
            swipeUp = gesture("up", up(english).getValue(key), custom?.swipeUp),
            swipeDown = gesture("down", down(english).getValue(key), custom?.swipeDown))
    }
}
