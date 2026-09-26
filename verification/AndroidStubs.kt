package android.content

class Configuration(var screenWidthDp: Int = 411, var screenHeightDp: Int = 840)
class Resources(val configuration: Configuration = Configuration())
open class Context(val resources: Resources = Resources(), val prefs: MemoryPreferences = MemoryPreferences())

class MemoryPreferences {
    val values = mutableMapOf<String, Any>()
    val writes = mutableListOf<String>()
    var onChange: ((String) -> Unit)? = null
    fun getInt(key: String, default: Int) = values[key] as? Int ?: default
    fun getBoolean(key: String, default: Boolean) = values[key] as? Boolean ?: default
    fun contains(key: String) = values.containsKey(key)
    fun edit() = Editor()
    inner class Editor {
        private val changes = linkedMapOf<String, Any>()
        fun putInt(key: String, value: Int): Editor { changes[key] = value; return this }
        fun putBoolean(key: String, value: Boolean): Editor { changes[key] = value; return this }
        fun putFloat(key: String, value: Float): Editor { changes[key] = value; return this }
        fun apply() {
            val changed = changes.filter { (k,v) -> values[k] != v }.keys.toList()
            values.putAll(changes)
            writes.addAll(changes.keys)
            changed.forEach { onChange?.invoke(it) }
        }
    }
}
