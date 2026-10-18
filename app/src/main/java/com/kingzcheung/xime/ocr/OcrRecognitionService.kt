package com.kingzcheung.xime.ocr

import android.app.Service
import android.content.*
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.*
import kotlinx.coroutines.*
import java.io.File
import java.util.UUID

/** A bounded, serial native worker in :ocr. No IME engine locks or typing work run here. */
class OcrRecognitionService : Service() {
    private lateinit var worker: HandlerThread
    private lateinit var handler: Handler
    private var session: DocumentOcrEngine.Session? = null
    override fun onCreate() {
        super.onCreate()
        worker = HandlerThread("ocr-line", Process.THREAD_PRIORITY_BACKGROUND).apply { start() }
        handler = Handler(worker.looper) { message ->
            if (message.what != 1) return@Handler false
            val name = message.data.getString("file").orEmpty()
            val reply = Bundle()
            if (name.matches(Regex("[a-f0-9-]+\\.png"))) {
                val file = File(cacheDir, "ocr-frames/$name")
                try {
                    val bitmap = BitmapFactory.decodeFile(file.path) ?: error("Unreadable frame")
                    try {
                        val engine = session ?: DocumentOcrEngine.Session(this).also { session = it }
                        reply.putString("text", engine.recognize(bitmap, singleLine = true))
                    } finally { bitmap.recycle() }
                } catch (_: Exception) { reply.putString("error", "识别失败，请重拍") }
                finally { file.delete() }
            } else reply.putString("error", "无效画面")
            runCatching { message.replyTo?.send(Message.obtain(null, 1, message.arg1, 0).apply { data = reply }) }
            true
        }
    }
    override fun onBind(intent: Intent): IBinder = Messenger(handler).binder
    override fun onDestroy() {
        handler.post { session?.close(); session = null; worker.quitSafely() }
        super.onDestroy()
    }
}

/** One client per visible scanner. Closing it rejects late replies and releases the worker. */
internal class OcrRecognitionClient(private val context: Context) : AutoCloseable {
    private val ready = CompletableDeferred<Messenger>()
    private var request: CompletableDeferred<String>? = null
    private var nextId = 0
    private var closed = false
    private val reply = Messenger(Handler(Looper.getMainLooper()) { message ->
        if (!closed && message.arg1 == nextId) {
            val error = message.data.getString("error")
            if (error == null) request?.complete(message.data.getString("text").orEmpty())
            else request?.completeExceptionally(IllegalStateException(error))
        }
        true
    })
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) { ready.complete(Messenger(service)) }
        override fun onServiceDisconnected(name: ComponentName) {
            val error = IllegalStateException("识别服务已断开，请重新打开")
            ready.completeExceptionally(error); request?.completeExceptionally(error)
        }
    }
    private val bound = context.bindService(Intent(context, OcrRecognitionService::class.java), connection, Context.BIND_AUTO_CREATE)
    init { if (!bound) ready.completeExceptionally(IllegalStateException("无法启动识别")) }

    suspend fun recognize(bitmap: Bitmap): String = withTimeout(12_000) {
        check(!closed && request == null)
        val result = CompletableDeferred<String>()
        request = result
        val id = ++nextId
        val file = File(File(context.cacheDir, "ocr-frames").apply { mkdirs() }, "${UUID.randomUUID()}.png")
        try {
            withContext(Dispatchers.IO) { file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) } }
            ready.await().send(Message.obtain(null, 1, id, 0).apply {
                data = Bundle().apply { putString("file", file.name) }; replyTo = reply
            })
            result.await()
        } finally { request = null; withContext(NonCancellable + Dispatchers.IO) { file.delete() } }
    }
    override fun close() {
        closed = true
        request?.cancel(); ready.cancel()
        if (bound) context.unbindService(connection)
    }
}
