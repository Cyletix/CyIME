package com.kingzcheung.xime.service

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import com.kingzcheung.xime.speech.AsrModelManager
import com.kingzcheung.xime.speech.LocalSpeechSession
import com.kingzcheung.xime.speech.ResidentSpeechSessionOwner
import com.kingzcheung.xime.speech.SherpaSpeechEngine
import com.kingzcheung.xime.util.FileLogger
import com.kingzcheung.xime.settings.InputLanguage
import java.util.concurrent.atomic.AtomicLong

/** Model weights and both decode workers live in :asr, never on the keyboard UI thread. */
class AsrInferenceService : Service() {
    private val lock = Any()
    private val idleHandler = Handler(Looper.getMainLooper())
    private val sessions = ResidentSpeechSessionOwner()
    private var keepAlive = false
    private val generation = AtomicLong()
    private val idleRelease = Runnable {
        synchronized(lock) { if (!keepAlive) sessions.release(onlyIfIdle = true) }
    }

    private fun scheduleRelease() {
        synchronized(lock) {
            idleHandler.removeCallbacks(idleRelease)
            if (!keepAlive && sessions.isIdle) idleHandler.postDelayed(idleRelease, 60_000)
        }
    }

    private val binder = object : IInferenceAsrService.Stub() {
        override fun startAsr(modelId: String, callback: IInferenceAsrCallback): Boolean =
            startSession(modelId, null, callback)
        override fun startAsrForLanguage(modelId: String, languageId: String, callback: IInferenceAsrCallback): Boolean {
            val language = InputLanguage.fromId(languageId)
            if (language == null || language == InputLanguage.UNSPECIFIED) {
                callback.onError("当前方案未标注可用的语音语言")
                return false
            }
            return startSession(modelId, language, callback)
        }
        private fun startSession(modelId: String, language: InputLanguage?, callback: IInferenceAsrCallback): Boolean {
            idleHandler.removeCallbacks(idleRelease)
            val token = generation.incrementAndGet()
            val startedAt = SystemClock.elapsedRealtime()
            var constructed = false
            var modelLoadMs = 0L
            return try {
                val selection = AsrModelManager(this@AsrInferenceService).selection(modelId, language)
                check(selection.ready) { "所选语音模型尚未完整下载" }
                val selectedAt = SystemClock.elapsedRealtime()
                val ready = sessions.start(selection.key,
                    createEngine = {
                        constructed = true
                        val loadingAt = SystemClock.elapsedRealtime()
                        try { SherpaSpeechEngine(this@AsrInferenceService, selection) }
                        finally { modelLoadMs = SystemClock.elapsedRealtime() - loadingAt }
                    },
                    createSession = { engine -> LocalSpeechSession(engine,
                        onText = { text -> if (generation.get() == token) try { callback.onPartialResult(text) } catch (_: Exception) { } },
                        onError = { message -> if (generation.get() == token) try { callback.onError(message) } catch (_: Exception) { } }) },
                    isActive = { generation.get() == token })
                val readyAt = SystemClock.elapsedRealtime()
                val modelState = if (constructed) "loaded" else if (ready) "resident" else "superseded"
                FileLogger.i("AsrInferenceService", "start ready=$ready model=$modelState " +
                    "selectionMs=${selectedAt - startedAt} modelMs=$modelLoadMs " +
                    "sessionWaitMs=${readyAt - selectedAt - modelLoadMs} totalMs=${readyAt - startedAt}")
                if (!ready) scheduleRelease()
                ready
            } catch (e: Exception) {
                FileLogger.e("AsrInferenceService", "startAsr failed", e)
                if (generation.get() == token) callback.onError(e.message ?: "离线语音模型加载失败")
                scheduleRelease()
                false
            } catch (e: LinkageError) {
                if (generation.get() == token) callback.onError("识别运行库无法加载：${e.message}")
                scheduleRelease()
                false
            }
        }
        override fun pushAsrAudio(audioData: ByteArray) {
            sessions.acceptPcm(audioData)
        }
        override fun stopAsr(): String {
            val token = generation.get()
            return sessions.finish {
                generation.compareAndSet(token, token + 1)
                scheduleRelease()
            } ?: ""
        }
        override fun cancelAsr() {
            generation.incrementAndGet()
            sessions.cancel()
            scheduleRelease()
        }
        override fun releaseAsr() {
            generation.incrementAndGet()
            idleHandler.removeCallbacks(idleRelease)
            sessions.release()
        }
        override fun setKeepModelAlive(keep: Boolean) = synchronized(lock) {
            keepAlive = keep
            idleHandler.removeCallbacks(idleRelease)
            if (!keep && sessions.isIdle) scheduleRelease()
        }
    }
    override fun onBind(intent: Intent): IBinder = binder
    override fun onDestroy() {
        binder.releaseAsr()
        super.onDestroy()
    }
}
