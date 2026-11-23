package com.kingzcheung.xime.clipboard

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.atomic.AtomicLong

/**
 * 剪贴板收藏夹的独立存储。
 * 与剪贴板历史（Room）和最近图片完全隔离：不会跟随剪贴板自动清理或容量限制被删除，
 * 新增的收藏也不会进入正常剪贴板历史列表；并独立保存收藏夹自己的固定状态。
 */
internal class ClipboardFavoriteStore private constructor(private val context: Context) {
    sealed interface PendingForm {
        val returnToFavorites: Boolean

        data class Add(override val returnToFavorites: Boolean) : PendingForm
        data class EditText(val id: Long, override val returnToFavorites: Boolean) : PendingForm
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val lock = Any()
    private val nextId = AtomicLong(1L)
    private val _items = MutableStateFlow<List<ClipboardCard>>(emptyList())
    val items: StateFlow<List<ClipboardCard>> = _items.asStateFlow()

    private val _pinnedKeys = MutableStateFlow<Set<String>>(emptySet())
    val pinnedKeys: StateFlow<Set<String>> = _pinnedKeys.asStateFlow()

    @Volatile
    private var pendingForm: PendingForm? = null

    @Volatile
    private var pendingReturnToFavorites: Boolean = false

    @Volatile
    private var searchInFavorites: Boolean = false

    init {
        load()
    }

    fun isFavorited(card: ClipboardCard): Boolean = synchronized(lock) {
        when (card) {
            is ClipboardCard.Text -> _items.value.any { it is ClipboardCard.Text && it.item.text == card.item.text }
            is ClipboardCard.Image -> _items.value.any { it is ClipboardCard.Image && sameFavoriteImage(it.item, card.item) }
        }
    }

    fun addCard(card: ClipboardCard): Boolean = when (card) {
        is ClipboardCard.Text -> addText(card.item.text, card.item.code)
        is ClipboardCard.Image -> addImage(card.item)
    }

    fun addText(text: String, code: String = ""): Boolean {
        if (text.isBlank()) return false
        val trimmedCode = code.trim()
        val now = System.currentTimeMillis()
        synchronized(lock) {
            val current = _items.value.toMutableList()
            val existingIndex = current.indexOfFirst { it is ClipboardCard.Text && it.item.text == text }
            if (existingIndex >= 0) {
                val old = current[existingIndex] as ClipboardCard.Text
                val updated = ClipboardCard.Text(
                    old.item.copy(
                        code = trimmedCode.ifEmpty { old.item.code },
                        isPinned = false,
                        timestamp = now,
                        sensitive = false,
                    )
                )
                current.removeAt(existingIndex)
                current.add(0, updated)
            } else {
                val id = allocateIdLocked()
                current.add(
                    0,
                    ClipboardCard.Text(
                        ClipboardItem(
                            id = id,
                            text = text,
                            code = trimmedCode,
                            isPinned = false,
                            timestamp = now,
                            sensitive = false,
                        )
                    )
                )
            }
            saveLocked(current)
        }
        return true
    }

    fun updateText(id: Long, newText: String, newCode: String? = null): Boolean {
        if (newText.isBlank()) return false
        val now = System.currentTimeMillis()
        synchronized(lock) {
            val current = _items.value.toMutableList()
            val index = current.indexOfFirst { it is ClipboardCard.Text && it.item.id == id }
            if (index < 0) return false
            val old = current[index] as ClipboardCard.Text
            val updated = ClipboardCard.Text(
                old.item.copy(
                    text = newText,
                    code = newCode?.trim() ?: old.item.code,
                    isPinned = false,
                    timestamp = now,
                    sensitive = false,
                )
            )
            current[index] = updated
            // 若编辑成另一条已收藏文本，合并保留一条（被合并记录如已固定，保留固定）。
            val duplicatePinned = current.withIndex().any { (i, entry) ->
                i != index && entry is ClipboardCard.Text && entry.item.text == newText && entry.key in _pinnedKeys.value
            }
            val deduplicated = current.filterIndexed { i, entry ->
                i == index || entry !is ClipboardCard.Text || entry.item.text != newText
            }
            if (duplicatePinned) {
                savePinnedLocked(_pinnedKeys.value + updated.key)
            }
            saveLocked(deduplicated)
        }
        return true
    }

    fun addImage(image: ClipboardImage): Boolean {
        val now = System.currentTimeMillis()
        val storedUri = copyLocalImageIfOwned(image.uri) ?: image.uri
        val favoriteImage = image.copy(uri = storedUri, timestamp = now)
        synchronized(lock) {
            val current = _items.value.toMutableList()
            val existingIndex = current.indexOfFirst {
                it is ClipboardCard.Image && sameFavoriteImage(it.item, favoriteImage)
            }
            val existingKey = if (existingIndex >= 0) current[existingIndex].key else null
            val wasPinned = existingKey != null && existingKey in _pinnedKeys.value
            val card = ClipboardCard.Image(favoriteImage)
            if (existingIndex >= 0) {
                current.removeAt(existingIndex)
            }
            current.add(0, card)
            if (wasPinned && existingKey != card.key) {
                savePinnedLocked(_pinnedKeys.value - existingKey + card.key)
            }
            saveLocked(current)
        }
        return true
    }

    fun setPinnedKeys(updated: Set<String>) {
        synchronized(lock) {
            val validKeys = _items.value.mapTo(mutableSetOf()) { it.key }
            savePinnedLocked(updated.intersect(validKeys))
        }
    }

    fun removeMatching(card: ClipboardCard) {
        synchronized(lock) {
            val current = _items.value
            val remaining = current.filterNot {
                when {
                    card is ClipboardCard.Text && it is ClipboardCard.Text -> it.item.text == card.item.text
                    card is ClipboardCard.Image && it is ClipboardCard.Image -> sameFavoriteImage(it.item, card.item)
                    else -> false
                }
            }
            if (remaining.size != current.size) {
                current.filter { it !in remaining }.filterIsInstance<ClipboardCard.Image>().forEach {
                    deleteOwnedFavoriteFile(it.item.uri)
                }
                saveLocked(remaining)
            }
        }
    }

    fun removeCards(cards: List<ClipboardCard>) {
        if (cards.isEmpty()) return
        val keys = cards.mapTo(mutableSetOf()) { it.key }
        synchronized(lock) {
            val current = _items.value
            val remaining = current.filterNot { it.key in keys }
            if (remaining.size != current.size) {
                current.filter { it.key in keys }.filterIsInstance<ClipboardCard.Image>().forEach {
                    deleteOwnedFavoriteFile(it.item.uri)
                }
                saveLocked(remaining)
            }
        }
    }

    fun beginAdd(returnToFavorites: Boolean) {
        pendingForm = PendingForm.Add(returnToFavorites)
        pendingReturnToFavorites = returnToFavorites
    }

    fun beginEditText(id: Long, returnToFavorites: Boolean) {
        pendingForm = PendingForm.EditText(id, returnToFavorites)
        pendingReturnToFavorites = returnToFavorites
    }

    fun setReturnToFavorites(returnToFavorites: Boolean) {
        pendingReturnToFavorites = returnToFavorites
    }

    fun setSearchInFavorites(active: Boolean) {
        searchInFavorites = active
    }

    fun isSearchInFavorites(): Boolean = searchInFavorites

    /**
     * 按正常剪贴板一致的口径搜索收藏内容（原文、拼音首字母、T9 数字、编码或图片标签）。
     */
    fun search(query: ClipboardLiveQuery): List<ClipboardCard> {
        val current = _items.value
        if (query.isBlank) return current
        val textPart = query.text.trim()
        val composingPart = ClipboardSearch.composingInitials(query.composing, AndroidPinyinInitials::initialOf)
        val combinedKey = (ClipboardSearch.key(query.text, AndroidPinyinInitials::initialOf)
            .takeIf { it != ClipboardSearch.EMPTY_KEY }.orEmpty() + composingPart).trim()
        val display = query.display
        val t9Key = if (query.t9) ClipboardSearch.t9Digits(combinedKey) else ""
        return current.filter { card ->
            when (card) {
                is ClipboardCard.Text -> {
                    val text = card.item.text
                    if (display.isNotEmpty() && (text.contains(display, ignoreCase = true) ||
                            card.item.code.contains(display, ignoreCase = true))) return@filter true
                    if (textPart.isNotEmpty() && text.contains(textPart, ignoreCase = true)) return@filter true
                    val recordKey = ClipboardSearch.key(text, AndroidPinyinInitials::initialOf)
                    (combinedKey.isNotEmpty() && recordKey.contains(combinedKey, ignoreCase = true)) ||
                        (t9Key.isNotEmpty() && ClipboardSearch.t9Digits(recordKey).contains(t9Key))
                }
                is ClipboardCard.Image -> display.isNotEmpty() && card.item.label.contains(display, ignoreCase = true)
            }
        }
    }

    fun peekPendingForm(): PendingForm? = pendingForm

    fun consumePendingForm(): PendingForm? = pendingForm.also { pendingForm = null }

    fun clearPendingForm() {
        pendingForm = null
    }

    fun consumeReturnToFavorites(): Boolean = pendingReturnToFavorites.also { pendingReturnToFavorites = false }

    private fun allocateIdLocked(): Long {
        var candidate = -nextId.getAndIncrement()
        val used = _items.value.mapNotNullTo(mutableSetOf()) { (it as? ClipboardCard.Text)?.item?.id }
        while (candidate in used || candidate >= 0L) {
            candidate = -nextId.getAndIncrement()
        }
        return candidate
    }

    private fun sameFavoriteImage(a: ClipboardImage, b: ClipboardImage): Boolean {
        if (a.uri == b.uri) return true
        if (!a.sha256.isNullOrBlank() && a.sha256 == b.sha256) return true
        if (!a.source.isNullOrBlank() && (a.source == b.source || a.source == b.uri)) return true
        if (!b.source.isNullOrBlank() && b.source == a.uri) return true
        return false
    }

    private fun copyLocalImageIfOwned(uriString: String): String? {
        val dir = File(context.filesDir, FAVORITE_IMAGES_DIR).apply { mkdirs() }
        val sourceFile = when {
            uriString.startsWith("file://") ->
                runCatching { File(android.net.Uri.parse(uriString).path ?: return null) }.getOrNull()
            uriString.startsWith("content://${context.packageName}.fileprovider/clipboard_images/") -> {
                val name = android.net.Uri.parse(uriString).lastPathSegment ?: return null
                File(File(context.cacheDir, "clipboard_images"), name)
            }
            else -> null
        } ?: return null
        if (!sourceFile.isFile) return null
        if (sourceFile.parentFile?.canonicalPath == dir.canonicalPath) return uriString
        val ext = sourceFile.extension.takeIf { it.isNotBlank() }?.let { ".$it" } ?: ".jpg"
        val dest = File(dir, "fav_${System.currentTimeMillis()}_${sourceFile.name.hashCode().toUInt()}$ext")
        return runCatching {
            sourceFile.copyTo(dest, overwrite = true)
            android.net.Uri.fromFile(dest).toString()
        }.getOrNull()
    }

    fun reload() {
        synchronized(lock) {
            load()
        }
    }

    private fun deleteOwnedFavoriteFile(uriString: String) {
        if (!uriString.startsWith("file://")) return
        val file = runCatching { File(android.net.Uri.parse(uriString).path ?: return) }.getOrNull() ?: return
        val dir = File(context.filesDir, FAVORITE_IMAGES_DIR)
        if (runCatching { file.parentFile?.canonicalPath == dir.canonicalPath }.getOrDefault(false)) {
            runCatching { file.delete() }
        }
    }

    private fun load() {
        val raw = prefs.getString(KEY_ITEMS, null)
        val parsed = if (raw.isNullOrEmpty()) emptyList() else runCatching {
            val array = JSONArray(raw)
            buildList {
                var maxAbsId = 0L
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val type = obj.optString("type")
                    val timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                    when (type) {
                        TYPE_TEXT -> {
                            val rawId = obj.optLong("id", 0L)
                            val id = if (rawId < 0L) rawId else -(i + 1L)
                            maxAbsId = maxOf(maxAbsId, -id)
                            val text = obj.optString("text", "")
                            if (text.isNotEmpty()) {
                                add(
                                    ClipboardCard.Text(
                                        ClipboardItem(
                                            id = id,
                                            text = text,
                                            code = obj.optString("code", ""),
                                            isPinned = false,
                                            timestamp = timestamp,
                                            sensitive = false,
                                        )
                                    )
                                )
                            }
                        }
                        TYPE_IMAGE -> {
                            val rawUri = obj.optString("uri", "")
                            val uri = normalizeRestoredImageUri(rawUri)
                            if (uri.isNotEmpty()) {
                                add(
                                    ClipboardCard.Image(
                                        ClipboardImage(
                                            uri = uri,
                                            mimeType = obj.optString("mimeType", "image/*"),
                                            timestamp = timestamp,
                                            label = obj.optString("label", "图片"),
                                            source = obj.optString("source", "").takeIf { it.isNotEmpty() },
                                            sha256 = obj.optString("sha256", "").takeIf { it.isNotEmpty() },
                                        )
                                    )
                                )
                            }
                        }
                    }
                }
                nextId.set(maxAbsId + 1L)
            }
        }.getOrDefault(emptyList())
        _items.value = parsed
        val validKeys = parsed.mapTo(mutableSetOf()) { it.key }
        _pinnedKeys.value = prefs.getStringSet(KEY_PINNED, emptySet()).orEmpty().intersect(validKeys)
    }

    private fun normalizeRestoredImageUri(rawUri: String): String {
        if (!rawUri.startsWith("file://")) return rawUri
        val path = runCatching { android.net.Uri.parse(rawUri).path }.getOrNull() ?: return rawUri
        val marker = "/$FAVORITE_IMAGES_DIR/"
        val idx = path.lastIndexOf(marker)
        if (idx < 0) return rawUri
        val name = path.substring(idx + marker.length)
        if (name.isEmpty() || name.contains('/') || name.contains('\\')) return rawUri
        val local = File(File(context.filesDir, FAVORITE_IMAGES_DIR), name)
        return if (local.isFile) android.net.Uri.fromFile(local).toString() else rawUri
    }

    private fun savePinnedLocked(pinned: Set<String>) {
        _pinnedKeys.value = pinned
        prefs.edit().putStringSet(KEY_PINNED, pinned).apply()
    }

    private fun saveLocked(list: List<ClipboardCard>) {
        _items.value = list
        val validKeys = list.mapTo(mutableSetOf()) { it.key }
        val filteredPins = _pinnedKeys.value.intersect(validKeys)
        val array = JSONArray()
        list.forEach { card ->
            val obj = JSONObject()
            when (card) {
                is ClipboardCard.Text -> {
                    obj.put("type", TYPE_TEXT)
                    obj.put("id", card.item.id)
                    obj.put("text", card.item.text)
                    obj.put("code", card.item.code)
                    obj.put("timestamp", card.item.timestamp)
                }
                is ClipboardCard.Image -> {
                    obj.put("type", TYPE_IMAGE)
                    obj.put("uri", card.item.uri)
                    obj.put("mimeType", card.item.mimeType)
                    obj.put("timestamp", card.item.timestamp)
                    obj.put("label", card.item.label)
                    card.item.source?.let { obj.put("source", it) }
                    card.item.sha256?.let { obj.put("sha256", it) }
                }
            }
            array.put(obj)
        }
        val editor = prefs.edit().putString(KEY_ITEMS, array.toString())
        if (filteredPins != _pinnedKeys.value) {
            _pinnedKeys.value = filteredPins
            editor.putStringSet(KEY_PINNED, filteredPins)
        }
        editor.apply()
    }

    companion object {
        private const val PREFS_NAME = "clipboard_favorites"
        private const val KEY_ITEMS = "items_json"
        private const val KEY_PINNED = "pinned_keys"
        private const val TYPE_TEXT = "text"
        private const val TYPE_IMAGE = "image"
        private const val FAVORITE_IMAGES_DIR = "clipboard_favorites"

        @Volatile
        private var instance: ClipboardFavoriteStore? = null

        fun getInstance(context: Context): ClipboardFavoriteStore =
            instance ?: synchronized(this) {
                instance ?: ClipboardFavoriteStore(context.applicationContext).also { instance = it }
            }
    }
}
