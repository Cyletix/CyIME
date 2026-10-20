package im.cyime.cylm

/**
 * Thin Kotlin wrapper over libcylm.so (prototype). Nothing in the IME calls it yet: [enabled] defaults to false and
 * no settings switch exists; turning it on is a separate, user-approved change.
 *
 * One [Model] per process (memory-mapped files), one [Session] per text field: a session keeps the incremental
 * decoding state of its previous input, so call [Session.decode] once per keystroke with the whole key string.
 * Not thread-safe: use a session (and its model) from one thread.
 */
object CylmNative {
    @JvmStatic external fun open(lexicon: String, model: String, model2: String?, params: String): Long
    @JvmStatic external fun session(model: Long): Long
    @JvmStatic external fun decode(session: Long, keys: String, mode: Int, context: String?): Array<String>
    @JvmStatic external fun closeSession(session: Long)
    @JvmStatic external fun close(model: Long)

    /** Off until the user turns CyLM on; the library is not even loaded while this is false. */
    @Volatile var enabled: Boolean = false

    private var loaded = false

    @Synchronized private fun load(): Boolean {
        if (!loaded) loaded = runCatching { System.loadLibrary("cylm") }.isSuccess
        return loaded
    }

    class Model internal constructor(internal val handle: Long) : AutoCloseable {
        fun session(): Session = Session(session(handle))
        override fun close() = close(handle)
    }

    class Session internal constructor(private val handle: Long) : AutoCloseable {
        /** mode 0 = 26-key pinyin (full, initials or mixed), 1 = nine-key digits. */
        fun decode(keys: String, mode: Int, context: String? = null): List<String> =
            if (handle == 0L) emptyList() else decode(handle, keys, mode, context).toList()
        override fun close() = closeSession(handle)
    }

    /** Opens a tier's files (compiled lexicon, word model, optional second model); null when disabled or failed. */
    fun openModel(lexicon: String, model: String, model2: String?, params: String): Model? {
        if (!enabled || !load()) return null
        val h = open(lexicon, model, model2, params)
        return if (h == 0L) null else Model(h)
    }
}
