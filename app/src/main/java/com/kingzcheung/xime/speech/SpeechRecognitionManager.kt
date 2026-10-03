package com.kingzcheung.xime.speech

import android.Manifest
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.util.Log

import androidx.annotation.RequiresPermission
import com.kingzcheung.xime.plugin.ExtensionManager
import com.kingzcheung.xime.settings.SettingsPreferences
import com.kingzcheung.xime.settings.InputLanguage
import com.kingzcheung.xime.util.FileLogger
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class SpeechRecognitionManager(
    private val context: Context,
    private val backendFactory: ((Context) -> AsrBackend?)? = null,
    private val mainHandler: Handler = Handler(Looper.getMainLooper()),
    private val audioRecordFactory: (() -> AudioRecord?)? = null,
) {

    companion object {
        private const val TAG = "SpeechRecognitionManager"
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val BUFFER_SIZE_SECONDS = 0.1f
        private const val SPEECH_THRESHOLD = 25

        /** 每次后端调用的停止兜底；等待上一段收尾不占用本段定稿时间。 */
        private const val JOIN_TIMEOUT_MS = 8000L

        /**
         * 停止后延迟释放后端的窗口：在线插件 stop() 只发送结束信号，最终结果由
         * 服务端处理完尾点后经 WebSocket 异步回调（1~2s），需比
         * VoiceRecognitionHandler.FINISH_TIMEOUT_MS 更长，保证回调通道存活。
         */
        private const val BACKEND_RELEASE_DELAY_MS = 3500L
    }

    private var backend: AsrBackend? = null
    @Volatile private var recordingThread: RecordingThread? = null
    @Volatile private var released = false
    @Volatile private var recognitionLanguage = InputLanguage.CHINESE

    // 会话序号：用于区分连续语音会话，防止旧会话的回收线程误释放新会话的后端
    @Volatile private var sessionId = 0
    // 待执行的延迟释放所对应的会话序号（-1 表示无待释放）
    @Volatile
    private var pendingReleaseSession = -1
    private val pendingBackendRelease = Runnable {
        val session = pendingReleaseSession
        pendingReleaseSession = -1
        // 主线程只取引用；release() 含跨进程 IPC/断连，放后台线程执行
        val b = synchronized(preloadLock) {
            if (session != -1 && sessionId == session && recordingThread == null &&
                !SettingsPreferences.isSttKeepEngineAlive(context)) {
                val tmp = backend
                backend = null
                tmp
            } else null
        }
        if (b != null) {
            Thread { b.release() }.start()
        }
    }
    // 后台加载 ASR 模型的进行中标记与取消标记
    @Volatile
    private var loadingInProgress = false
    @Volatile
    private var loadingCancelled = false

    private var resultCallback: ((String) -> Unit)? = null
    private var partialResultCallback: ((String) -> Unit)? = null
    private var stateCallback: ((RecognitionState) -> Unit)? = null
    @Volatile
    private var currentState: RecognitionState = RecognitionState.IDLE
    private var errorCallback: ((String, Boolean) -> Unit)? = null
    private var amplitudeCallback: ((Float) -> Unit)? = null
    private var spectrumCallback: ((FloatArray) -> Unit)? = null

    // 预启动的 AudioRecord：手指按下 150ms 后启动，语音激活时直接交给录音线程
    private var preStartedRecord: AudioRecord? = null
    private val preStartTimeoutRunnable = Runnable { cancelPreStart() }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun startRecognition(language: InputLanguage = InputLanguage.CHINESE) {
        if (released) return
        // Never wait for model initialization on the UI thread: hiding the IME must be able to cancel.
        if (recordingThread?.stopRequested == false) {
            FileLogger.w(TAG, "Recognition already running, ignoring start request")
            return
        }
        if (language == InputLanguage.UNSPECIFIED ||
            language != InputLanguage.CHINESE && !SettingsPreferences.isSttUseLocal(context) && backendFactory == null) {
            cancelPreStart()
            errorCallback?.invoke(if (language == InputLanguage.UNSPECIFIED) "当前方案未标注语言，请先选择输入语言"
                else "当前在线语音插件尚未声明${language.displayName}支持，请使用本地多语言模型", true)
            setState(RecognitionState.ERROR)
            return
        }
        recognitionLanguage = language

        loadingCancelled = false
        FileLogger.i(TAG, "Starting speech recognition")
        setState(RecognitionState.PROCESSING)

        if (backend == null) {
            // 按需加载：后台线程加载 ASR 模型，完成后在主线程启动录音，避免阻塞键盘 UI
            FileLogger.i(TAG, "ASR backend not loaded, loading on demand")
            synchronized(preloadLock) {
                if (loadingInProgress) {
                    // 已有加载进行中（如设置开启触发的预加载），等待其完成
                    loadingCancelled = false
                    return
                }
                loadingInProgress = true
                loadingCancelled = false
            }
            Thread {
                try {
                    val ok = preload()
                    if (!ok || synchronized(preloadLock) { backend } == null) {
                        mainHandler.post {
                            errorCallback?.invoke("无法初始化语音引擎，请检查本地模型或在线语音插件配置", true)
                            setState(RecognitionState.ERROR)
                        }
                        return@Thread
                    }
                    if (loadingCancelled || released) {
                        mainHandler.post {
                            if (!released) { scheduleBackendRelease(sessionId); setState(RecognitionState.IDLE) }
                        }
                        return@Thread
                    }
                    mainHandler.post {
                        if (recordingThread == null && !loadingCancelled && !released) {
                            startRecording()
                        }
                    }
                } finally {
                    synchronized(preloadLock) {
                        loadingInProgress = false
                        preloadLock.notifyAll()
                    }
                }
            }.start()
            return
        }

        startRecording()
    }

    private fun startRecording() {
        if (released || loadingCancelled) return
        val currentBackend = synchronized(preloadLock) { backend } ?: return
        synchronized(preloadLock) { sessionId++ }

        // 新会话复用后端：取消上一次会话遗留的延迟释放
        mainHandler.removeCallbacks(pendingBackendRelease)
        pendingReleaseSession = -1

        // 预启动的 AudioRecord 已运行 ~250ms，直接交给录音线程
        var preStarted: AudioRecord? = null
        synchronized(this) {
            preStarted = preStartedRecord
            preStartedRecord = null
        }
        mainHandler.removeCallbacks(preStartTimeoutRunnable)

        // Capture can resume while the previous result is finishing. Backend ownership remains
        // serialized, so an old stop/cancel can never terminate the new recognition session.
        recordingThread = RecordingThread(currentBackend, preStarted, sessionId, recognitionLanguage, recordingThread)
        recordingThread!!.start()
    }

    /**
     * 延迟释放后端：给在线插件的异步最终结果留出送达窗口。窗口内新会话开始
     * （startRecording）会取消释放并复用后端；释放前校验会话序号，避免误释放
     * 新会话正在使用的后端。
     * "引擎常驻"开启时不安排释放——后端跨会话保留，闲置后再用免重建
     * （重新加载模型/重建 Lua 后端与连接正是"闲置后首次使用慢"的根源）；
     * 关闭设置后自然回退为延迟释放，无需主动清理。
     */
    private fun scheduleBackendRelease(session: Int) {
        if (released || SettingsPreferences.isSttKeepEngineAlive(context)) return
        pendingReleaseSession = session
        mainHandler.removeCallbacks(pendingBackendRelease)
        mainHandler.postDelayed(pendingBackendRelease, BACKEND_RELEASE_DELAY_MS)
    }

    fun stopRecognition() {
        loadingCancelled = true
        cancelPreStart()
        Log.d(TAG, "Stopping recognition")
        val thread = recordingThread
        if (thread == null) {
            // 模型仍在后台加载中：标记取消，加载完成后不再启动录音
            if (loadingInProgress) {
                loadingCancelled = true
                mainHandler.post {
                    setState(RecognitionState.IDLE)
                }
            }
            return
        }
        if (thread.stopRequested) return
        thread.stopRequested = true
        // End capture immediately, but let delivery drain captured PCM and finish normally.
        thread.forceStopAudio()
    }

    fun cancelRecognition() {
        // Invalidate callbacks immediately, including results already posted to the UI thread.
        synchronized(preloadLock) { sessionId++ }
        loadingCancelled = true
        cancelPreStart()
        Log.d(TAG, "Canceling recognition")
        val thread = recordingThread
        if (thread == null) {
            // 模型仍在后台加载中：标记取消，加载完成后不再启动录音
            if (loadingInProgress) {
                loadingCancelled = true
                mainHandler.post {
                    setState(RecognitionState.IDLE)
                }
            }
            return
        }
        thread.cancelRequested = true
        if (thread.stopRequested) return
        thread.stopRequested = true
        // Release capture immediately; cancellation remains on the backend's owning worker.
        thread.forceStopAudio()
    }

    fun getState(): RecognitionState = currentState

    private fun setState(state: RecognitionState) {
        currentState = state
        stateCallback?.invoke(state)
    }

    fun setCallbacks(
        onResult: (String) -> Unit,
        onPartialResult: ((String) -> Unit)? = null,
        onStateChange: (RecognitionState) -> Unit,
        onError: (message: String, userVisible: Boolean) -> Unit,
        onAmplitude: ((Float) -> Unit)? = null,
        onSpectrum: ((FloatArray) -> Unit)? = null
    ) {
        resultCallback = onResult
        partialResultCallback = onPartialResult
        stateCallback = onStateChange
        errorCallback = onError
        amplitudeCallback = onAmplitude
        spectrumCallback = onSpectrum
    }

    fun startPreStart() {
        cancelPreStart()
        if (released || recordingThread != null) return
        val record = createAudioRecord() ?: return
        try {
            record.startRecording()
            synchronized(this) { preStartedRecord = record }
        } catch (error: Exception) {
            try { record.release() } catch (_: Exception) { }
            Log.w(TAG, "Microphone pre-start failed", error)
            return
        }
        mainHandler.removeCallbacks(preStartTimeoutRunnable)
        mainHandler.postDelayed(preStartTimeoutRunnable, 2000)
    }

    fun cancelPreStart() {
        mainHandler.removeCallbacks(preStartTimeoutRunnable)
        synchronized(this) {
            val record = preStartedRecord
            preStartedRecord = null
            if (record != null) {
                try { record.stop() } catch (_: Exception) { }
                try { record.release() } catch (_: Exception) { }
            }
        }
    }

    fun release() {
        released = true
        Log.d(TAG, "Releasing speech recognition")
        cancelPreStart()
        mainHandler.removeCallbacks(pendingBackendRelease)
        pendingReleaseSession = -1
        cancelRecognition()
        val b = synchronized(preloadLock) {
            val tmp = backend
            backend = null
            tmp
        }
        if (b != null) {
            // release() 含跨进程 IPC，放到后台线程执行，避免阻塞主线程（onDestroy 等场景）
            val lastRecording = recordingThread
            Thread {
                lastRecording?.awaitBackendFinished()
                b.release()
            }.start()
        }
    }

    private var isPreloading = false
    private val preloadLock = Object()

    fun preload(): Boolean {
        synchronized(preloadLock) {
            if (released) return false
            if (backend != null) return true
            isPreloading = true
        }
        var candidate: AsrBackend? = null
        var retained = false
        try {
            val created = createBackend() ?: return false
            candidate = created
            created.setCallbacks(
                // Model warmup is not an input session and must never write to the editor.
                onResult = {}, onPartialResult = {}, onStateChange = {},
                onError = { error -> Log.w(TAG, "Speech warmup: $error") }
            )
            if (!created.initialize(recognitionLanguage)) return false
            synchronized(preloadLock) {
                if (released) return false
                backend = created
                retained = true
            }
            return true
        } catch (error: Exception) {
            Log.e(TAG, "Unable to initialize speech backend", error)
            return false
        } finally {
            if (!retained) try { candidate?.release() } catch (_: Exception) { }
            synchronized(preloadLock) { isPreloading = false; preloadLock.notifyAll() }
        }
    }

    private fun createBackend(): AsrBackend? {
        backendFactory?.let { return it(context) }
        // 用户开启"本地识别"时才使用离线后端，否则走在线插件
        val useLocal = SettingsPreferences.isSttUseLocal(context)
        return if (useLocal) {
            AsrBackendFactory.create(context) ?: createOnlineAsrBackend()
        } else {
            createOnlineAsrBackend()
        }
    }

    private fun createOnlineAsrBackend(): AsrBackend? {
        val enabledPlugins = ExtensionManager.getEnabledAsrPlugins(context)
        if (enabledPlugins.isEmpty()) return null

        val selectedId = SettingsPreferences.getSttOnlinePluginId(context)
        val selected = enabledPlugins.firstOrNull { it.first == selectedId }
            ?: enabledPlugins.firstOrNull()
        val (_, plugin) = selected ?: return null

        val backend = plugin.createBackend(context.applicationContext)
        val pluginName = ExtensionManager.getAllInstalledPlugins()
            .firstOrNull { it.id == selected?.first }?.name ?: selected?.first ?: "语音识别"
        return PluginAsrBackendAdapter(pluginName, backend)
    }

    private fun createAudioRecord(bufferSecs: Float = 2.0f): AudioRecord? {
        audioRecordFactory?.let { return it() }
        val bufferSize = (SAMPLE_RATE * bufferSecs).toInt()
        return try {
            val record = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize * 2
            )
            if (record.state != AudioRecord.STATE_INITIALIZED) {
                record.release()
                null
            } else {
                record
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create AudioRecord", e)
            null
        }
    }

    private inner class RecordingThread(
        private val currentBackend: AsrBackend,
        preStarted: AudioRecord? = null,
        private val session: Int,
        private val language: InputLanguage,
        private val previousRecording: RecordingThread?,
    ) : Thread("AsrRecording") {

        private val spectrumAnalyzer = SpectrumAnalyzer()
        private val requestedAt = System.nanoTime()

        // Capture stops independently of delivery: normal stop drains PCM, cancel discards it.
        // Explicit flags also survive backend/plugin calls that consume thread interrupts.
        @Volatile
        var stopRequested = false
        @Volatile var cancelRequested = false

        // Own the pre-started microphone before Thread.start(): stop may arrive before run().
        private val microphone = RecordingAudioOwner(preStarted)
        private val audioQueue = ArrayBlockingQueue<ByteArray>(100) // At most 10 s / 320 KB of PCM.
        private val backendFinished = CountDownLatch(1)
        @Volatile private var captureEnded = false
        @Volatile private var deliveryThread: Thread? = null
        @Volatile private var backendOperationStartedAt = 0L
        private val shutdownWatched = AtomicBoolean()

        private fun watchShutdown() {
            if (!shutdownWatched.compareAndSet(false, true)) return
            Thread({
                while (!backendFinished.await(250, TimeUnit.MILLISECONDS)) {
                    val started = backendOperationStartedAt
                    if (started != 0L && System.nanoTime() - started >= TimeUnit.MILLISECONDS.toNanos(JOIN_TIMEOUT_MS)) {
                        // Interrupt the actual model/IPC owner, never advertise it as free early.
                        deliveryThread?.interrupt()
                        break
                    }
                }
            }, "AsrShutdownWatch").start()
        }

        fun awaitBackendFinished() {
            var interrupted = false
            while (true) try {
                backendFinished.await()
                break
            } catch (_: InterruptedException) {
                interrupted = true
            }
            if (interrupted) Thread.currentThread().interrupt()
        }

        private fun enqueue(chunk: ByteArray) {
            if (!audioQueue.offer(chunk)) error("语音引擎准备时间过长，已停止录音，请重试")
        }

        private fun reportFailure(error: Exception, duringDelivery: Boolean = false) {
            if (!cancelRequested && !released && (duringDelivery || !stopRequested)) mainHandler.post {
                if (!released && session == sessionId) {
                    errorCallback?.invoke("录音已停止：${error.message.orEmpty()}", true)
                    setState(RecognitionState.ERROR)
                }
            }
        }

        /** Only this worker touches the backend; the microphone never waits for model/IPC work. */
        private fun deliverAudio() {
            var attempted = false
            try {
                while (previousRecording?.backendFinished?.await(50, TimeUnit.MILLISECONDS) == false) {
                    if (cancelRequested || released) return
                }
                if (cancelRequested || released) return
                bindSessionCallbacks(currentBackend, session) { !cancelRequested }
                attempted = true
                backendOperationStartedAt = System.nanoTime()
                check(currentBackend.start(language)) { "启动语音引擎失败" }
                backendOperationStartedAt = 0L
                FileLogger.i(TAG, "ASR backend ready session=$session elapsedMs=${TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - requestedAt)}")
                while (!cancelRequested && !released) {
                    val chunk = audioQueue.poll(50, TimeUnit.MILLISECONDS)
                    if (chunk != null) {
                        backendOperationStartedAt = System.nanoTime()
                        currentBackend.processAudioChunk(chunk)
                        backendOperationStartedAt = 0L
                    }
                    else if (captureEnded && audioQueue.isEmpty()) break
                }
            } catch (error: Exception) {
                reportFailure(error, duringDelivery = true)
                cancelRequested = true
                forceStopAudio()
            } finally {
                if (attempted) try {
                    backendOperationStartedAt = System.nanoTime()
                    if (cancelRequested || released) currentBackend.cancel() else currentBackend.stop()
                } catch (error: Exception) {
                    Log.w(TAG, "Speech backend stop failed after microphone release", error)
                    reportFailure(error, duringDelivery = true)
                }
                // A cancelled queued recording still represents its predecessor's ownership.
                // A third immediate start must not jump past the original unfinished stop.
                if (!attempted) previousRecording?.awaitBackendFinished()
                backendOperationStartedAt = 0L
                audioQueue.clear()
                backendFinished.countDown()
                completeRecording()
            }
        }

        private fun completeRecording() {
            mainHandler.post {
                if (recordingThread === this) {
                    recordingThread = null
                    if (!released) {
                        if (currentState != RecognitionState.ERROR) setState(RecognitionState.IDLE)
                        scheduleBackendRelease(sessionId)
                    }
                }
            }
        }

        override fun run() {
            var deliveryStarted = false
            try {
                val audioRecord = microphone.acquire { createAudioRecord() }
                    ?: if (stopRequested) return else error("无法启动录音")
                if (stopRequested || !microphone.start()) return
                FileLogger.i(TAG, "Microphone ready session=$session elapsedMs=${TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - requestedAt)}")
                deliveryThread = Thread(::deliverAudio, "AsrAudioDelivery").also { it.start() }
                deliveryStarted = true
                mainHandler.post {
                    if (!released && !stopRequested && recordingThread === this) setState(RecognitionState.LISTENING)
                }

                val buffer = ShortArray((SAMPLE_RATE * BUFFER_SIZE_SECONDS).toInt())
                val byteBuffer = ByteArray(buffer.size * 2)
                var speechDetected = false
                // 语音前缓冲：保存检测到语音前的若干块，检测到后一起送入 ASR，
                // 避免"你/觉"等弱开头的语音块因音量低于阈值被当作静音丢弃
                val preSpeechBuffer = ArrayDeque<ByteArray>()
                val maxPreSpeechChunks = 4  // 0.4s 语音前缓冲

                while (!interrupted() && !stopRequested) {
                    val nread = audioRecord.read(buffer, 0, buffer.size)
                    if (nread > 0) {
                        var peak = 0
                        for (i in 0 until nread) {
                            val s = buffer[i].toInt()
                            byteBuffer[i * 2] = (s and 0xFF).toByte()
                            byteBuffer[i * 2 + 1] = ((s shr 8) and 0xFF).toByte()
                            val abs = if (s < 0) -s else s
                            if (abs > peak) peak = abs
                        }
                        // 归一化振幅（0~1）与频段频谱，驱动频谱可视化
                        val normalized = (peak / 32768f).coerceIn(0f, 1f)
                        val spectrum = spectrumAnalyzer.analyze(buffer, nread)
                        mainHandler.post {
                            if (!released && session == sessionId) {
                                amplitudeCallback?.invoke(normalized)
                                spectrumCallback?.invoke(spectrum)
                            }
                        }
                        val chunk = byteBuffer.copyOf(nread * 2)
                        if (!speechDetected) {
                            preSpeechBuffer.addLast(chunk)
                            // 缓冲满仍未检测到语音：放弃 VAD，直接开始识别，
                            // 保证整段弱音内容也能送入 ASR（开头静音已被缓冲丢弃）
                            if (preSpeechBuffer.size >= maxPreSpeechChunks) {
                                speechDetected = true
                                while (preSpeechBuffer.isNotEmpty()) {
                                    enqueue(preSpeechBuffer.removeFirst())
                                }
                            } else if (isSpeech(chunk)) {
                                speechDetected = true
                                // 把语音前缓冲的块按顺序送入 ASR，保证开头不丢失
                                while (preSpeechBuffer.isNotEmpty()) {
                                    enqueue(preSpeechBuffer.removeFirst())
                                }
                            }
                        } else {
                            enqueue(chunk)
                        }
                    } else if (nread < 0) {
                        break
                    }
                }
            } catch (error: Exception) {
                reportFailure(error)
                cancelRequested = true
            } finally {
                stopRequested = true
                microphone.close()
                captureEnded = true
                watchShutdown()
                if (deliveryStarted) {
                    // A timed-out join must not advertise the backend as free while native work
                    // still owns it. Cancellation already releases capture and discards queued PCM.
                    awaitBackendFinished()
                } else {
                    previousRecording?.awaitBackendFinished()
                    backendFinished.countDown()
                    completeRecording()
                }
                Log.d(TAG, "Recognition thread ended; microphone released")
            }
        }

        fun forceStopAudio() {
            stopRequested = true
            microphone.close()
            watchShutdown()
        }

        private fun isSpeech(chunk: ByteArray): Boolean {
            var peak = 0
            for (i in 0 until chunk.size / 2) {
                val low = chunk[i * 2].toInt() and 0xFF
                val high = chunk[i * 2 + 1].toInt()
                val sample = ((high shl 8) or low).toShort().toInt()
                val abs = kotlin.math.abs(sample)
                if (abs > peak) peak = abs
            }
            return peak > SPEECH_THRESHOLD
        }
    }

    private fun postForSession(token: Int, action: () -> Unit) {
        mainHandler.post {
            if (!released && token == sessionId) action()
        }
    }

    private fun bindSessionCallbacks(currentBackend: AsrBackend, token: Int, acceptsCallback: () -> Boolean) {
        currentBackend.setCallbacks(
            onResult = { text -> postForSession(token) { if (acceptsCallback()) resultCallback?.invoke(text) } },
            onPartialResult = { text -> postForSession(token) { if (acceptsCallback()) partialResultCallback?.invoke(text) } },
            onStateChange = { state -> postForSession(token) { if (acceptsCallback()) setState(state) } },
            onError = { error -> postForSession(token) { if (acceptsCallback()) errorCallback?.invoke(error, true) } },
        )
    }
}
