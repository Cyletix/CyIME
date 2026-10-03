package com.kingzcheung.xime.settings

import android.content.Context

/** Same preset for Chinese and English; independent user overrides for each language. */
internal object QwertySwipeSymbols {
    val rows = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
    val keys = rows.joinToString("")
    val up = keys.map(Char::toString).zip(listOf(
        "1", "2", "3", "4", "5", "6", "7", "8", "9", "0",
        "@", "-", "*", "_", "[", "]", "(", ")", "\\",
        "'", "\"", ":", ";", "!", "?", "/")).toMap()
    val down = keys.map(Char::toString).zip(listOf(
        "!", "@", "#", "$", "%", "^", "&", "*", "(", ")",
        "`", "~", "·", "—", "…", "°", "{", "}", "|",
        "=", "+", "-", "×", "÷", "<", ">")).toMap()
    private fun prefix(english: Boolean) = "qwerty_symbols_${if (english) "en" else "zh"}_"

    fun load(context: Context, english: Boolean): Map<String, String> {
        val prefs = SettingsPreferences.getPrefsPublic(context)
        val prefix = prefix(english)
        return buildMap {
            for (key in keys) for (direction in listOf("up", "down")) {
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
        custom: KeyGestureConfig?): KeyGestureConfig? {
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
            swipeUp = gesture("up", up.getValue(key), custom?.swipeUp),
            swipeDown = gesture("down", down.getValue(key), custom?.swipeDown))
    }
}
