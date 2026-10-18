package com.kingzcheung.xime.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.kingzcheung.xime.util.FileLogger
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Main-process client for the live-caption contract of [AsrInferenceService] (:asr). It is a
 * second binding of the same service (different intent action), independent of the keyboard's
 * [AsrInferenceClient]; the service itself refuses dictation while a caption session is alive.
 */
class CaptionInferenceClient(context: Context) {
    private val context = context.applicationContext

    interface Listener {
        fun onSegment(id: Long, text: String, startMs: Long, endMs: Long, boundary: Int, confirmed: Boolean, event: String)
        fun onSpeaker(id: Long, speakerId: Int, confident: Boolean)
        fun onError(message: String)
        /** The :asr process died or unbound: the session is gone, speakers are forgotten. */
        fun onLost()
    }

    @Volatile private var service: ICaptionService? = null
    @Volatile private var latch = CountDownLatch(1)
    @Volatile private var callback: ICaptionCallback.Stub? = null
    @Volatile private var listener: Listener? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            service = ICaptionService.Stub.asInterface(binder)
            latch.countDown()
            FileLogger.i(TAG, "connected")
        }
        override fun onServiceDisconnected(name: ComponentName) {
            service = null
            FileLogger.w(TAG, "disconnected")
            listener?.onLost()
        }
        override fun onBindingDied(name: ComponentName) {
            service = null
            latch.countDown()
            listener?.onLost()
        }
    }

    /** Blocking; call off the main thread. */
    fun bind(timeoutMs: Long = 5000): Boolean {
        if (service != null) return true
        latch = CountDownLatch(1)
        val intent = Intent(context, AsrInferenceService::class.java).setAction(AsrInferenceService.ACTION_CAPTION)
        if (!context.bindService(intent, connection, Context.BIND_AUTO_CREATE)) return false
        latch.await(timeoutMs, TimeUnit.MILLISECONDS)
        return service != null
    }

    fun unbind() {
        callback = null
        listener = null
        try { context.unbindService(connection) } catch (_: Exception) { }
        service = null
    }

    private fun requireService(): ICaptionService = service ?: throw IllegalStateException("字幕服务未连接")

    /** Blocking (model load); call off the main thread. */
    fun start(modelId: String, speakerModelPath: String, listener: Listener): Boolean {
        this.listener = listener
        val stub = object : ICaptionCallback.Stub() {
            override fun onSegment(id: Long, text: String, startMs: Long, endMs: Long, boundary: Int, confirmed: Boolean, event: String) {
                if (callback === this) listener.onSegment(id, text, startMs, endMs, boundary, confirmed, event)
            }
            override fun onSpeaker(id: Long, speakerId: Int, confident: Boolean) {
                if (callback === this) listener.onSpeaker(id, speakerId, confident)
            }
            override fun onError(message: String) { if (callback === this) listener.onError(message) }
        }
        callback = stub
        return try { requireService().start(modelId, speakerModelPath, stub) }
        catch (e: Exception) { FileLogger.e(TAG, "start failed", e); callback = null; false }
    }

    fun push(pcm: ByteArray) {
        val stub = callback ?: throw IllegalStateException("字幕会话未启动")
        try { requireService().push(stub.asBinder(), pcm) }
        catch (e: Exception) { throw IllegalStateException("字幕服务已断开", e) }
    }

    fun stop() {
        val stub = callback ?: return
        callback = null
        try { requireService().stop(stub.asBinder()) } catch (e: Exception) { FileLogger.w(TAG, "stop: ${e.message}") }
    }

    fun cancel() {
        val stub = callback ?: return
        callback = null
        try { requireService().cancel(stub.asBinder()) } catch (_: Exception) { }
    }

    fun mergeSpeakers(from: Int, into: Int): Boolean = try { requireService().mergeSpeakers(from, into) } catch (_: Exception) { false }
    fun reassignSegment(segmentId: Long, speakerId: Int) { try { requireService().reassignSegment(segmentId, speakerId) } catch (_: Exception) { } }
    fun setSpeakerIgnored(speakerId: Int, ignored: Boolean) { try { requireService().setSpeakerIgnored(speakerId, ignored) } catch (_: Exception) { } }
    fun setSpeakerNamed(speakerId: Int, named: Boolean) { try { requireService().setSpeakerNamed(speakerId, named) } catch (_: Exception) { } }
    fun setStrictness(level: Int) { try { requireService().setStrictness(level) } catch (_: Exception) { } }
    fun resetSpeakers() { try { requireService().resetSpeakers() } catch (_: Exception) { } }
    fun markSelf(speakerId: Int) { try { requireService().markSelf(speakerId) } catch (_: Exception) { } }
    fun forgetSelf() { try { requireService().forgetSelf() } catch (_: Exception) { } }
    fun rememberSpeaker(speakerId: Int, name: String): Int = try { requireService().rememberSpeaker(speakerId, name) } catch (_: Exception) { -1 }
    fun forgetSpeaker(knownId: Int) { try { requireService().forgetSpeaker(knownId) } catch (_: Exception) { } }

    companion object { private const val TAG = "CaptionInferenceClient" }
}
