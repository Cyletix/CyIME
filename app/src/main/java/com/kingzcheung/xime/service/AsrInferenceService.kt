package com.kingzcheung.xime.service

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.kingzcheung.xime.speech.AsrModelManager
import com.kingzcheung.xime.speech.LocalSpeechSession
import com.kingzcheung.xime.speech.SherpaSpeechEngine
import com.kingzcheung.xime.util.FileLogger

/** Model weights and both decode workers live in :asr, never on the keyboard UI thread. */
class AsrInferenceService : Service() {
    private val lock = Any()
    private val idleHandler = Handler(Looper.getMainLooper())
    private var engine: SherpaSpeechEngine? = null
    private var loadedKey: String? = null
    private var session: LocalSpeechSession? = null
    private var keepAlive = false
    private val retiring = java.util.concurrent.atomic.AtomicInteger()
    @Volatile private var generation = 0L
    private val idleRelease = Runnable { synchronized(lock) { if (session == null) releaseEngine() } }

    private fun scheduleRelease() {
        idleHandler.removeCallbacks(idleRelease)
        if (!keepAlive) idleHandler.postDelayed(idleRelease, 60_000)
    }
    private fun retireSession(cancel: Boolean) {
        val old = session ?: return
        session = null
        if (cancel) old.cancel()
        if (old.isIdle) {
            old.awaitIdle()
        } else {
            // Native inference is not interruptible. Its weights are released only after both
            // owning workers exit; a new session can never reuse a recognizer still decoding.
            val retired = engine
            engine = null
            loadedKey = null
            retiring.incrementAndGet()
            Thread { try { old.awaitIdle(); retired?.close() } finally { retiring.decrementAndGet() } }
                .apply { isDaemon = true }.start()
        }
    }
    private fun releaseEngine() { engine?.close(); engine = null; loadedKey = null }

    private val binder = object : IInferenceAsrService.Stub() {
        override fun startAsr(modelId: String, callback: IInferenceAsrCallback): Boolean = synchronized(lock) {
            idleHandler.removeCallbacks(idleRelease)
            generation++
            retireSession(true)
            val token = generation
            try {
                check(retiring.get() == 0) { "上一段语音仍在收尾，请稍后重试" }
                val selection = AsrModelManager(this@AsrInferenceService).selection(modelId)
                check(selection.ready) { "所选语音模型尚未完整下载" }
                if (loadedKey != selection.key) {
                    releaseEngine()
                    engine = SherpaSpeechEngine(this@AsrInferenceService, selection)
                    loadedKey = selection.key
                }
                session = LocalSpeechSession(checkNotNull(engine),
                    onText = { text -> if (generation == token) try { callback.onPartialResult(text) } catch (_: Exception) { } },
                    onError = { message -> if (generation == token) try { callback.onError(message) } catch (_: Exception) { } })
                true
            } catch (e: Exception) {
                FileLogger.e("AsrInferenceService", "startAsr failed", e)
                callback.onError(e.message ?: "离线语音模型加载失败")
                scheduleRelease()
                false
            } catch (e: LinkageError) {
                callback.onError("识别运行库无法加载：${e.message}")
                false
            }
        }
        override fun pushAsrAudio(audioData: ByteArray) {
            synchronized(lock) { session }?.acceptPcm(audioData)
        }
        override fun stopAsr(): String = synchronized(lock) {
            val current = session ?: return@synchronized ""
            try { current.finish() }
            finally { generation++; retireSession(false); scheduleRelease() }
        }
        override fun cancelAsr() = synchronized(lock) {
            generation++; retireSession(true); scheduleRelease()
        }
        override fun releaseAsr() = synchronized(lock) {
            generation++; idleHandler.removeCallbacks(idleRelease); retireSession(true); releaseEngine()
        }
        override fun setKeepModelAlive(keep: Boolean) = synchronized(lock) {
            keepAlive = keep
            idleHandler.removeCallbacks(idleRelease)
            if (!keep && session == null) scheduleRelease()
        }
    }
    override fun onBind(intent: Intent): IBinder = binder
    override fun onDestroy() {
        binder.releaseAsr()
        super.onDestroy()
    }
}
