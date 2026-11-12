package com.kingzcheung.xime.settings

import android.content.Context
import com.kingzcheung.xime.rime.RimeEngine
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

object CellDictionaryManager {
    private val client by lazy {
        OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(60, TimeUnit.SECONDS).followSslRedirects(false).build()
    }

    fun store(context: Context) = CellDictionaryStore(File(context.filesDir, "cell-dictionaries"))

    suspend fun download(offer: CellDictionaryOffer): ByteArray = downloadBytes(Request.Builder()
        .url(offer.downloadUrl).header("Referer", offer.sourceUrl).build(), ScelDictionary.MAX_BYTES,
        "搜狗暂时无法下载，可从来源页下载后导入")

    private suspend fun downloadBytes(request: Request, limit: Int, failure: String): ByteArray = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }
            override fun onResponse(call: Call, response: Response) {
                try {
                    val bytes = response.use {
                        if (!it.isSuccessful || !it.request.url.isHttps) throw IOException("$failure（${it.code}）")
                        val body = requireNotNull(it.body) { "下载内容为空" }
                        require(body.contentLength() <= limit) { "词库文件过大" }
                        body.byteStream().use { input -> ScelDictionary.readBounded(input, limit) }
                    }
                    if (continuation.isActive) continuation.resumeWith(Result.success(bytes))
                } catch (error: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(error)
                }
            }
        })
    }

    private fun bundledUpdates(context: Context): CellWordUpdates = context.assets.open(CellWordUpdates.ASSET).use {
        CellWordUpdates.parse(ScelDictionary.readBounded(it, CellWordUpdates.MAX_BYTES))
    }

    /** Runs only when requested in settings. A failed check leaves the active dictionary alone. */
    suspend fun checkWordUpdates(context: Context): Boolean {
        val bundled = bundledUpdates(context)
        var lastError: IOException? = null
        for (base in CYIME_STORE_BASE_URLS) {
            val url = base.removeSuffix("store/") + "app/src/main/assets/" + CellWordUpdates.ASSET
            try {
                val bytes = downloadBytes(Request.Builder().url(url).build(), CellWordUpdates.MAX_BYTES, "词库更新源暂不可用")
                return store(context).stageWordUpdates(bytes, bundled)
            } catch (e: CancellationException) { throw e }
            catch (e: IOException) { lastError = e }
        }
        throw lastError ?: IOException("词库更新源暂不可用")
    }

    /** Copy bundled dictionaries into the store before the applied set is prepared for Rime. */
    fun syncBuiltins(context: Context) {
        val store = store(context)
        CellDictionaryCatalog.builtins.forEach { builtin ->
            // Some packages omit optional built-in assets.
            try { installBuiltin(context, store, builtin, restore = false) }
            catch (_: java.io.FileNotFoundException) { }
        }
    }

    fun restoreBuiltin(context: Context, id: String) {
        val builtin = requireNotNull(CellDictionaryCatalog.builtins.find { it.id == id }) { "找不到内置词库" }
        installBuiltin(context, store(context), builtin, restore = true)
    }

    private fun installBuiltin(context: Context, store: CellDictionaryStore, builtin: BuiltinCellDictionary, restore: Boolean) {
        val text = context.assets.open(builtin.asset).bufferedReader().use { it.readText() }
        val english = try {
            context.assets.open(builtin.englishAsset).bufferedReader().use { it.readLines() }.filter { it.isNotBlank() }
        } catch (_: java.io.FileNotFoundException) { emptyList() }
        store.installBuiltin(builtin, text, english, restore)
    }

    fun prepare(context: Context, ids: Set<String> = store(context).read().applied,
        english: Boolean = store(context).read().appliedEnglish, wordRevision: Int? = null): List<CellDictionaryRime.Binding> {
        val store = store(context)
        val bundled = runCatching { bundledUpdates(context) }.getOrDefault(CellWordUpdates.empty)
        // Main dictionary words apply with or without cell dictionaries (CellWordUpdates.main).
        val updates = if (wordRevision != null) store.wordUpdates(wordRevision, bundled)
        else runCatching { store.wordUpdates(store.read().appliedWordRevision, bundled) }.getOrDefault(bundled)
        return CellDictionaryRime.prepare(SchemaManager.getRimeDir(context), store,
            SchemaManager.discoverSchemas(context), ids, english, updates)
    }

    /** Native deployment can report success even when an optional pack failed to compile. */
    fun verify(context: Context, bindings: List<CellDictionaryRime.Binding>): Boolean {
        val enabled = SchemaManager.getEnabledSchemas(context).toSet()
        val engine = RimeEngine.getInstance()
        return bindings.filter { it.schemaId in enabled }.all {
            val table = File(SchemaManager.getRimeDir(context), "build/${it.pack}.table.bin")
            val source = File(SchemaManager.getRimeDir(context), "${it.pack}.dict.yaml")
            it.pack in engine.getSchemaPacks(it.schemaId) && table.isFile && table.length() > 64 &&
                table.lastModified() >= source.lastModified() &&
                table.inputStream().use { input ->
                    val head = ByteArray(12)
                    input.read(head) == head.size && head.toString(Charsets.US_ASCII).startsWith("Rime::Table/")
                }
        }
    }
}
