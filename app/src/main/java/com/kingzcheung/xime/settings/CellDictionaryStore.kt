package com.kingzcheung.xime.settings

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class InstalledCellDictionary(
    val id: String, val name: String, val category: String,
    val count: Int, val skipped: Int = 0, val sourceUrl: String = "",
    val sha256: String,
)

@Serializable
data class CellDictionaryState(
    val installed: List<InstalledCellDictionary> = emptyList(),
    val selected: Set<String> = emptySet(),
    val applied: Set<String> = emptySet(),
) {
    val pending: Boolean get() = selected != applied
}

/** Downloaded data stays outside Rime until explicitly applied. Never edits learned user dictionaries. */
class CellDictionaryStore(val directory: File) {
    companion object {
        private val lock = Any()
        private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
        internal fun digest(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
            .digest(bytes).joinToString("") { "%02x".format(it) }

        internal fun writeAtomic(file: File, text: String) {
            if (file.isFile && file.readText() == text) return
            file.parentFile?.mkdirs()
            val temp = File.createTempFile(".cell-", ".tmp", file.parentFile)
            try {
                temp.writeText(text, Charsets.UTF_8)
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE)
            } finally { temp.delete() }
        }
    }

    fun read(): CellDictionaryState = synchronized(lock) {
        val file = File(directory, "state.json")
        if (file.exists()) json.decodeFromString<CellDictionaryState>(file.readText()) else CellDictionaryState()
    }

    private fun save(state: CellDictionaryState) {
        writeAtomic(File(directory, "state.json"), json.encodeToString(state))
    }

    fun dictionaryFile(id: String): File {
        require(id.matches(Regex("(sogou_[0-9]+|local_[a-f0-9]{24})"))) { "无效词库标识" }
        return File(directory, "cell_$id.dict.yaml")
    }

    fun install(bytes: ByteArray, offer: CellDictionaryOffer? = null): InstalledCellDictionary = synchronized(lock) {
        val hash = digest(bytes)
        val id = offer?.id ?: "local_${hash.take(24)}"
        val old = read()
        // An installed source is immutable while selected/applied; a failed download never damages it.
        old.installed.firstOrNull { it.id == id }?.let {
            if (dictionaryFile(id).isFile) return@synchronized it
            require(it.sha256 == hash) { "来源词库已更新，请作为本地新词库导入" }
        }
        val parsed = if (offer == null && bytes.firstOrNull { it.toInt().toChar() !in " \r\n\t" } == '{'.code.toByte())
            JsonCellDictionary.parse(bytes) else ScelDictionary.parse(bytes)
        val entry = InstalledCellDictionary(id, offer?.name ?: parsed.name.ifBlank { "本地词库" },
            offer?.category ?: "本地导入", parsed.entries.size, parsed.skipped, offer?.sourceUrl.orEmpty(), hash)
        writeAtomic(dictionaryFile(id), ScelDictionary.toRime("cell_$id", parsed.entries))
        save(old.copy(installed = old.installed.filterNot { it.id == id } + entry))
        entry
    }

    fun select(id: String, enabled: Boolean) = synchronized(lock) {
        val old = read()
        require(old.installed.any { it.id == id } && (!enabled || dictionaryFile(id).isFile)) { "请先下载词库" }
        save(old.copy(selected = if (enabled) old.selected + id else old.selected - id))
    }

    fun markApplied(ids: Set<String>) = synchronized(lock) {
        val old = read()
        require(ids.all { id -> old.installed.any { it.id == id } })
        save(old.copy(applied = ids)) // preserve any newer user selection
    }

    /** Commit only after the engine accepted the selection; repair its previous state on failure. */
    fun activate(ids: Set<String>, prepareAndDeploy: (Set<String>) -> Unit) = synchronized(lock) {
        val previous = read().applied
        try {
            prepareAndDeploy(ids)
            markApplied(ids)
        } catch (error: Exception) {
            try { prepareAndDeploy(previous) } catch (restoreError: Exception) { error.addSuppressed(restoreError) }
            throw error
        }
    }
}
