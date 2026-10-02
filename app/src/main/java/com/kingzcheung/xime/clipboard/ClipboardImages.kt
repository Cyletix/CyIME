package com.kingzcheung.xime.clipboard

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class ClipboardImage(val uri: String, val mimeType: String, val timestamp: Long, val label: String = "图片")
data class ImagePasteFailure(val image: ClipboardImage, val packageName: String)

/** Copied images stay local; gallery access starts only after an explicit permission action. */
class ClipboardImages private constructor(private val context: Context) {
    companion object {
        private const val MAX_BYTES = 32L * 1024 * 1024
        private const val MAX_HISTORY = 16
        @Volatile private var instance: ClipboardImages? = null
        fun getInstance(context: Context): ClipboardImages = instance ?: synchronized(this) {
            instance ?: ClipboardImages(context.applicationContext).also { instance = it }
        }
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val prefs = context.getSharedPreferences("clipboard_images", Context.MODE_PRIVATE)
    private val directory = File(context.cacheDir, "clipboard_images")
    private val _images = MutableStateFlow<List<ClipboardImage>>(emptyList())
    val images = _images.asStateFlow()
    private val previewState = ClipboardImagePreviewState(prefs.getString("last_preview_copy", null))
    val preview = previewState.preview
    private val _recentPhotosEnabled = MutableStateFlow(false)
    val photoAccess = _recentPhotosEnabled.asStateFlow()
    private val _pasteFailure = MutableStateFlow<ImagePasteFailure?>(null)
    val pasteFailure = _pasteFailure.asStateFlow()
    fun reportPasteFailure(image: ClipboardImage, packageName: String) { _pasteFailure.value = ImagePasteFailure(image, packageName) }
    fun clearPasteFailure() { _pasteFailure.value = null }
    private var history = emptyList<ClipboardImage>()
    private var media = emptyList<ClipboardImage>()
    private var hidden = prefs.getStringSet("hidden_images", emptySet()).orEmpty().toSet()
    private var observing = false
    private var expiry: Job? = null
    private val cachedSources = mutableMapOf<String, ClipboardImage>()
    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) { refresh() }
    }

    init {
        scope.launch {
            mutex.withLock {
                history = runCatching {
                    val array = JSONArray(prefs.getString("history", "[]"))
                    (0 until array.length()).mapNotNull { index ->
                        val item = array.getJSONObject(index)
                        val uri = item.getString("uri")
                        val name = Uri.parse(uri).lastPathSegment ?: return@mapNotNull null
                        if (!File(directory, name).isFile) null
                        else ClipboardImage(uri, item.getString("mime"), item.getLong("time"))
                    }
                }.getOrDefault(emptyList())
                publish()
            }
            refresh()
        }
    }

    fun hasPhotoAccess(): Boolean = if (Build.VERSION.SDK_INT >= 33) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED ||
            (Build.VERSION.SDK_INT >= 34 && ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED)
    } else ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    fun enableRecentPhotos(enabled: Boolean) {
        prefs.edit().putBoolean("recent_photos", enabled).apply()
        refresh()
    }

    fun recentPhotosEnabled(): Boolean = prefs.getBoolean("recent_photos", false) && hasPhotoAccess()

    fun capture(uri: Uri, timestamp: Long = System.currentTimeMillis(), mimeType: String = "image/png") {
        scope.launch {
            val key = mutex.withLock {
                previewState.beginCopy(uri.toString(), timestamp)?.also {
                    prefs.edit().putString("last_preview_copy", it).apply()
                }
            } ?: return@launch
            runCatching {
                val image = prepare(ClipboardImage(uri.toString(), context.contentResolver.getType(uri) ?: mimeType, timestamp))
                mutex.withLock {
                    hidden = hidden - image.uri
                    prefs.edit().putStringSet("hidden_images", hidden).apply()
                    history = retainHistory(listOf(image.copy(timestamp = timestamp)) + history.filterNot { it.uri == image.uri })
                    previewState.prepared(key, image.copy(timestamp = timestamp), System.currentTimeMillis())
                    save()
                    publish()
                }
            }
        }
    }

    private fun retainHistory(items: List<ClipboardImage>): List<ClipboardImage> {
        val pins = context.getSharedPreferences("clipboard_board", Context.MODE_PRIVATE).getStringSet("pins", emptySet()).orEmpty()
        var recent = 0
        return items.filter { "image:${it.uri}" in pins || recent++ < MAX_HISTORY }
    }

    suspend fun prepare(image: ClipboardImage): ClipboardImage = withContext(Dispatchers.IO) {
        mutex.withLock {
            val source = Uri.parse(image.uri)
            val ownName = source.lastPathSegment
            if (source.authority == "${context.packageName}.fileprovider" && source.pathSegments.firstOrNull() == "clipboard_images" && ownName != null && File(directory, ownName).isFile) {
                if (history.none { it.uri == image.uri && it.timestamp == image.timestamp }) {
                    history = retainHistory(listOf(image) + history.filter { it.uri != image.uri })
                    save()
                    publish()
                }
                return@withLock image
            }
            cachedSources[image.uri]?.takeIf { File(directory, Uri.parse(it.uri).lastPathSegment.orEmpty()).isFile }?.let { return@withLock it }
            require(source.scheme == "content") { "Unsupported image URI" }
            val mime = context.contentResolver.getType(source) ?: image.mimeType
            require(mime.startsWith("image/")) { "Not an image" }
            directory.mkdirs()
            val extension = when (mime) { "image/jpeg" -> "jpg"; "image/gif" -> "gif"; "image/webp" -> "webp"; "image/heic" -> "heic"; "image/avif" -> "avif"; else -> "png" }
            val file = File(directory, "${UUID.randomUUID()}.$extension")
            try {
                (context.contentResolver.openInputStream(source) ?: error("Image is unavailable")).use { input ->
                    file.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var total = 0L
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            require(total <= MAX_BYTES) { "Image exceeds 32 MiB" }
                            output.write(buffer, 0, count)
                        }
                        require(total > 0) { "Empty image" }
                    }
                }
                val cached = image.copy(uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file).toString(), mimeType = mime)
                history = retainHistory(listOf(cached) + history)
                cachedSources[image.uri] = cached
                // Readers may still hold a granted URI. Reclaim only old, unreferenced files.
                val retained = history.mapNotNull { Uri.parse(it.uri).lastPathSegment }.toSet()
                directory.listFiles()?.filter { it.name !in retained && it.lastModified() < System.currentTimeMillis() - 24 * 60 * 60_000L }
                    ?.forEach { it.delete() }
                save()
                publish()
                cached
            } catch (error: Exception) { file.delete(); throw error }
        }
    }

    // 同步清除，避免快速收起再打开时在后台任务执行前读到旧提示。
    fun dismissPreview(uri: String? = null) = previewState.dismiss(uri)

    fun clearHistory() {
        scope.launch { mutex.withLock {
            // Remove references immediately; keep granted files available to receiving apps.
            dismissPreview()
            history = emptyList()
            save()
            publish()
        } }
    }

    /** Hide gallery entries and remove copied references; never delete the user's photos or granted files. */
    fun removeImages(uris: Set<String>) {
        scope.launch { mutex.withLock {
            hidden = (hidden + uris).toList().takeLast(1024).toSet()
            prefs.edit().putStringSet("hidden_images", hidden).apply()
            history = history.filterNot { it.uri in uris }
            uris.forEach { dismissPreview(it) }
            save()
            publish()
        } }
    }

    fun refresh() {
        scope.launch { mutex.withLock {
            media = if (recentPhotosEnabled()) runCatching { queryRecentPhotos() }.getOrDefault(emptyList()) else emptyList()
            if (recentPhotosEnabled() && !observing) {
                runCatching { context.contentResolver.registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, observer) }.onSuccess { observing = true }
            } else if (!recentPhotosEnabled() && observing) {
                context.contentResolver.unregisterContentObserver(observer)
                observing = false
            }
            publish()
        } }
    }

    private fun queryRecentPhotos(): List<ClipboardImage> {
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val columns = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.MIME_TYPE, MediaStore.Images.Media.DATE_ADDED)
        val args = Bundle().apply {
            putInt(android.content.ContentResolver.QUERY_ARG_LIMIT, 60)
            putStringArray(android.content.ContentResolver.QUERY_ARG_SORT_COLUMNS, arrayOf(MediaStore.Images.Media.DATE_ADDED))
            putInt(android.content.ContentResolver.QUERY_ARG_SORT_DIRECTION, android.content.ContentResolver.QUERY_SORT_DIRECTION_DESCENDING)
        }
        return context.contentResolver.query(collection, columns, args, null)?.use { cursor ->
            buildList {
                while (cursor.moveToNext() && size < 60) {
                    val mime = cursor.getString(1) ?: continue
                    if (mime.startsWith("image/")) add(ClipboardImage(ContentUris.withAppendedId(collection, cursor.getLong(0)).toString(), mime, cursor.getLong(2) * 1000, "最近照片"))
                }
            }
        }.orEmpty()
    }

    private fun save() {
        val array = JSONArray()
        history.forEach { array.put(JSONObject().put("uri", it.uri).put("mime", it.mimeType).put("time", it.timestamp)) }
        prefs.edit().putString("history", array.toString()).apply()
    }

    private fun publish() {
        _recentPhotosEnabled.value = recentPhotosEnabled()
        _images.value = (history + media).distinctBy { it.uri }.filterNot { it.uri in hidden }.sortedByDescending { it.timestamp }
        // 历史和最近照片只供手动浏览，不从列表挑一张图片反复弹出。
        previewState.expire(System.currentTimeMillis())
        val image = preview.value
        expiry?.cancel()
        expiry = if (image == null) null else scope.launch {
            delay((image.timestamp + ClipboardImagePreviewState.MAX_AGE_MS - System.currentTimeMillis())
                .coerceIn(1, ClipboardImagePreviewState.MAX_AGE_MS))
            refresh()
        }
    }
}
