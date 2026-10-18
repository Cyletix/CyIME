package com.kingzcheung.xime.ui.caption

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.kingzcheung.xime.model.BuiltinModelCatalog
import com.kingzcheung.xime.model.ModelDownloadState
import com.kingzcheung.xime.model.ModelManager
import com.kingzcheung.xime.model.ModelStorage
import com.kingzcheung.xime.service.CaptionInferenceClient
import com.kingzcheung.xime.settings.InputLanguage
import com.kingzcheung.xime.speech.AsrModelManager
import com.kingzcheung.xime.speech.SpeechModelCatalog
import com.kingzcheung.xime.speech.caption.CaptionRecorder
import com.kingzcheung.xime.speech.caption.CaptionSpectrum
import com.kingzcheung.xime.speech.caption.KnownVoices
import com.kingzcheung.xime.speech.caption.CaptionTranscript
import com.kingzcheung.xime.util.FileLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.Executors

/** One speaker row on the caption page; ids come from the :asr labeler, names from the user. */
class SpeakerUi(val id: Int, val ordinal: Int) {
    var name by mutableStateOf(when (id) {
        CaptionController.REPLY_SPEAKER -> "我（写）"
        CaptionController.SELF_SPEAKER -> "我（拿手机的人）"
        CaptionController.SINGLE_SPEAKER -> "说话的人"
        else -> "第 $ordinal 个人"
    })
    var named by mutableStateOf(false)
    var ignored by mutableStateOf(false)
    val colorIndex: Int get() = when (id) {
        CaptionController.REPLY_SPEAKER, CaptionController.SELF_SPEAKER -> 7
        CaptionController.SINGLE_SPEAKER -> 1
        else -> (ordinal - 1).mod(7)
    }
}

/** [wallMs] is the clock time the line appeared; session-relative times restart with every session. */
class SegmentUi(val id: Long, val wallMs: Long) {
    /** How much of [text] the caption page has written out so far. */
    val reveal = Reveal()
    private var current by mutableStateOf("")
    var text: String
        get() = current
        set(value) { reveal.update(value, System.currentTimeMillis()); current = value }
    var confirmed by mutableStateOf(false)
    var speakerId by mutableStateOf<Int?>(null)
    var confident by mutableStateOf(true)
    var event by mutableStateOf("")
    /** Spoken length so far; the timeline draws the block this long. */
    var durationMs by mutableStateOf(0L)
    /** Shown in a lane once it has a speaker and something to read. */
    val placed: Boolean get() = speakerId != null && text.isNotEmpty()
}

/**
 * State and actions of the caption page. AIDL callbacks arrive on binder threads and are moved
 * to the main thread before touching Compose state; blocking service calls run on [io].
 */
class CaptionController(context: Context) : CaptionInferenceClient.Listener {
    enum class Status { Idle, Starting, Listening, Paused, Error }

    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor { Thread(it, "CyIME-CaptionCtl").apply { isDaemon = true } }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefs = app.getSharedPreferences("caption_mode", Context.MODE_PRIVATE)
    private val client = CaptionInferenceClient(app)
    private val transcript = CaptionTranscript()
    private val recorder = CaptionRecorder(
        onChunk = { pcm -> client.push(pcm) },
        onLevel = { level -> if (level > .002f) lastLoudAt = SystemClock.elapsedRealtime(); main.post { amplitude = level } },
        onFailure = { message -> main.post { fail(message) } },
        onSpectrum = { frames -> frames.forEachIndexed { k, bands -> main.postDelayed({ if (status == Status.Listening) spectrum = bands }, k * 33L) } })

    var status by mutableStateOf(Status.Idle); private set
    var message by mutableStateOf(""); private set
    var amplitude by mutableStateOf(0f); private set
    /** Voice-range loudness in [CaptionSpectrum.BANDS] bands, newest ~33 ms frame. */
    var spectrum by mutableStateOf(FloatArray(CaptionSpectrum.BANDS)); private set
    var micMuted by mutableStateOf(false); private set
    val speakers = mutableStateListOf<SpeakerUi>()
    val segments = mutableStateListOf<SegmentUi>()
    var fontLevel by mutableStateOf(prefs.getInt("font", 1)); private set
    var darkTheme by mutableStateOf(prefs.getBoolean("dark", false)); private set
    /** true: one horizontal track per speaker on a time axis (default); false: vertical transcript. */
    var timeline by mutableStateOf(prefs.getBoolean("timeline", true)); private set
    var speakerModelReady by mutableStateOf(speakerModelFile() != null); private set
    var downloadState by mutableStateOf<ModelDownloadState>(ModelDownloadState.Idle); private set
    /** Wall-clock millis of session audio time zero; segment times are relative to it. */
    var sessionStartedAt by mutableStateOf(System.currentTimeMillis()); private set
    var resumeOnForeground = false

    @Volatile private var lastLoudAt = SystemClock.elapsedRealtime()
    private var sessionEpoch = 0L
    private var nextOrdinal = 1
    private val firstPartialAt = HashMap<Long, Long>()
    private val muteCheck = object : Runnable {
        override fun run() {
            micMuted = status == Status.Listening && SystemClock.elapsedRealtime() - lastLoudAt > 15_000
            main.postDelayed(this, 5_000)
        }
    }


    private fun speakerModelFile(): File? =
        File(ModelStorage.getModelDir(app, BuiltinModelCatalog.SPEAKER_MODEL), BuiltinModelCatalog.SPEAKER_MODEL_FILE)
            .takeIf { it.isFile && it.length() > 0 }

    fun start() {
        if (status == Status.Starting || status == Status.Listening) return
        status = Status.Starting
        message = "正在加载语音模型…"
        micMuted = false
        lastLoudAt = SystemClock.elapsedRealtime()
        val epoch = ++sessionEpoch
        io.execute {
            val result = runCatching {
                val mode = captionMode()
                main.post { refinementReady = usesSenseVoice(mode); streamingReady = mode != SpeechModelCatalog.SENSEVOICE }
                val speakerPath = speakerModelFile()?.absolutePath.orEmpty()
                check(client.bind()) { "无法连接语音服务" }
                check(client.start(mode, speakerPath, sessionListener(epoch))) { "语音模型启动失败" }
                check(recorder.start()) { "麦克风启动失败" }
            }
            main.post {
                if (epoch != sessionEpoch) return@post
                result.onSuccess {
                    status = Status.Listening
                    message = ""
                    sessionStartedAt = System.currentTimeMillis()
                    main.removeCallbacks(muteCheck); main.postDelayed(muteCheck, 5_000)
                    FileLogger.i(TAG, "session started speakerModel=${speakerModelReady}")
                }.onFailure { fail(it.message ?: "字幕启动失败") }
            }
        }
    }

    private fun fail(message: String) {
        if (status == Status.Idle) return
        recorder.stop()
        io.execute { client.cancel() }
        status = Status.Error
        this.message = message
        main.removeCallbacks(muteCheck)
        FileLogger.w(TAG, "session failed: $message")
    }

    fun pause() {
        if (status != Status.Listening && status != Status.Starting) return
        ++sessionEpoch
        recorder.stop()
        io.execute { client.stop() }
        status = Status.Paused
        message = ""
        main.removeCallbacks(muteCheck)
    }

    fun resume() = start()

    fun exit() {
        ++sessionEpoch
        recorder.stop()
        main.removeCallbacks(muteCheck)
        status = Status.Idle
        io.execute { client.cancel(); client.resetSpeakers(); client.unbind() }
        scope.cancel()
    }

    /**
     * Every restart (pause, background, speaker-model download) numbers its segments from 1 again.
     * Lines are keyed by session too, so a new "segment 1" can never overwrite or re-label an old one.
     */
    private fun sessionListener(epoch: Long) = object : CaptionInferenceClient.Listener {
        private fun key(id: Long) = epoch * SESSION_STRIDE + id
        override fun onSegment(id: Long, text: String, startMs: Long, endMs: Long, boundary: Int, confirmed: Boolean, event: String) =
            this@CaptionController.onSegment(key(id), text, startMs, endMs, boundary, confirmed, event)
        override fun onSpeaker(id: Long, speakerId: Int, confident: Boolean) =
            this@CaptionController.onSpeaker(key(id), speakerId, confident)
        override fun onError(message: String) = this@CaptionController.onError(message)
        override fun onLost() = this@CaptionController.onLost()
    }

    // ---- callbacks (binder threads) ----
    override fun onSegment(id: Long, text: String, startMs: Long, endMs: Long, boundary: Int, confirmed: Boolean, event: String) {
        val now = SystemClock.elapsedRealtime()
        main.post {
            val line = transcript.update(id, text, startMs, endMs, boundary, confirmed, event) ?: return@post
            val ui = segments.firstOrNull { it.id == id } ?: SegmentUi(id, System.currentTimeMillis() - (endMs - startMs).coerceIn(0, 30_000)).also {
                // The speaker label may arrive before the first text of a short sentence.
                line.speakerId?.let { speaker -> ensureSpeaker(speaker); it.speakerId = speaker; it.confident = line.confident }
                segments.add(it)
            }
            if (text.isNotEmpty() && firstPartialAt.put(id, now) == null) FileLogger.i(TAG, "seg=$id firstTextMs=${now - startAtElapsed()}")
            ui.text = displayText(line)
            ui.event = event
            ui.confirmed = confirmed
            if (endMs > startMs) ui.durationMs = endMs - startMs
            if (confirmed) {
                FileLogger.i(TAG, "seg=$id final len=${text.length} event=$event durMs=${endMs - startMs}")
                // Nothing to read: a short empty segment disappears, a long one shows the marker.
                if (ui.text.isEmpty()) { segments.remove(ui); transcript.remove(id) }
            }
            trimHistory()
        }
    }

    override fun onSpeaker(id: Long, speakerId: Int, confident: Boolean) {
        main.post {
            // A provisional label (sentence still being spoken) never overrides a final one.
            val known = transcript[id]
            if (!confident && known?.speakerId != null && known.confident) return@post
            transcript.assign(id, speakerId, confident)
            val ui = segments.firstOrNull { it.id == id } ?: return@post
            ensureSpeaker(speakerId)
            ui.speakerId = speakerId
            ui.confident = confident
            FileLogger.i(TAG, "seg=$id speaker=$speakerId confident=$confident")
        }
    }

    override fun onError(message: String) { main.post { this.message = message } }

    override fun onLost() { main.post { fail("语音服务中断了，点“重新开始”") } }

    private fun startAtElapsed(): Long = SystemClock.elapsedRealtime() - (System.currentTimeMillis() - sessionStartedAt)

    private fun displayText(line: CaptionTranscript.Entry): String {
        if (line.text.isNotEmpty() && !isNoise(line.text)) return line.text
        if (!line.confirmed) return ""
        return when {
            line.event.equals("BGM", ignoreCase = true) -> "（电视声）"
            line.event.equals("Laughter", ignoreCase = true) -> "（笑）"
            line.endMs - line.startMs >= 1000 -> "（机器没听清）"
            else -> ""
        }
    }

    /** "I." / "嗯." style hallucinations on room noise: at most one letter and no CJK text. */
    private fun isNoise(text: String): Boolean {
        val letters = text.filter { it.isLetterOrDigit() }
        return letters.length <= 1 && letters.none { it.code > 0x2e80 }
    }

    /** Names of the people whose voices are remembered on this phone (written by the :asr process). */
    private val knownNames = HashMap<Int, String>().apply { KnownVoices.list(app).forEach { put(it.id, it.name) } }

    private fun ensureSpeaker(id: Int): SpeakerUi =
        speakers.firstOrNull { it.id == id } ?: SpeakerUi(id, if (id <= SINGLE_SPEAKER) 0 else nextOrdinal++).also { speaker ->
            knownNames[id]?.let { speaker.name = it; speaker.named = true }
            speakers.add(speaker)
        }

    fun isRemembered(speakerId: Int) = KnownVoices.isKnown(speakerId)

    private fun trimHistory() {
        while (segments.size > MAX_LINES) { val old = segments.removeAt(0); transcript.remove(old.id) }
    }

    // ---- user actions ----
    /**
     * Names a voice. With [remember] the name and voiceprint are kept on this phone, so later sessions
     * recognise the person by name from their first words; the row moves to the remembered id.
     */
    fun rename(speakerId: Int, name: String, remember: Boolean = false) {
        val speaker = ensureSpeaker(speakerId)
        speaker.name = name
        speaker.named = true
        speaker.ignored = false
        io.execute {
            client.setSpeakerNamed(speakerId, true); client.setSpeakerIgnored(speakerId, false)
            if (!remember) return@execute
            val id = client.rememberSpeaker(speakerId, name)
            main.post {
                if (id <= 0) { message = "没能记住这个声音：他说的话还太少，多说几句再试"; return@post }
                knownNames[id] = name
                if (id != speakerId) {
                    transcript.mergeSpeakers(speakerId, id)
                    for (segment in segments) if (segment.speakerId == speakerId) { segment.speakerId = id; segment.confident = true }
                    speakers.removeAll { it.id == speakerId }
                }
                ensureSpeaker(id).let { it.name = name; it.named = true }
            }
        }
    }

    /** Stops recognising a remembered person; their voiceprint is deleted from the phone. */
    fun forgetVoice(speakerId: Int) {
        if (!KnownVoices.isKnown(speakerId)) return
        knownNames.remove(speakerId)
        io.execute { client.forgetSpeaker(speakerId) }
    }

    /**
     * Saves this session's captions as text in Downloads: one line per sentence, "说话人  时间  内容".
     * Returns where it went, or null when there is nothing to save or the write failed.
     */
    fun saveTranscript(): String? {
        val clock = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
        val lines = segments.filter { line -> line.text.isNotEmpty() && speakers.none { it.id == line.speakerId && it.ignored } }
            .map { line ->
                val who = speakers.firstOrNull { it.id == line.speakerId }?.name ?: "说话的人"
                "$who  ${clock.format(java.util.Date(line.wallMs))}  ${line.text}"
            }
        if (lines.isEmpty()) return null
        val name = "字幕_" + java.text.SimpleDateFormat("yyyy-MM-dd_HHmm", java.util.Locale.getDefault()).format(java.util.Date()) + ".txt"
        val bytes = lines.joinToString("\n", postfix = "\n").toByteArray()
        return runCatching {
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                val values = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.Downloads.DISPLAY_NAME, name)
                    put(android.provider.MediaStore.Downloads.MIME_TYPE, "text/plain")
                }
                val uri = checkNotNull(app.contentResolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values))
                checkNotNull(app.contentResolver.openOutputStream(uri)).use { it.write(bytes) }
                "下载/$name"
            } else {
                // Android 9 has no Downloads collection without storage permission: keep it in the app's own folder.
                val dir = checkNotNull(app.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS))
                java.io.File(dir, name).writeBytes(bytes)
                java.io.File(dir, name).absolutePath
            }
        }.onFailure { FileLogger.e(TAG, "save transcript failed", it) }.getOrNull()
    }

    /** "That voice is this person": the picked speaker keeps its name, the other row disappears. */
    fun mergeInto(from: Int, into: Int) {
        if (from == into) return
        transcript.mergeSpeakers(from, into)
        for (segment in segments) if (segment.speakerId == from) { segment.speakerId = into; segment.confident = true }
        speakers.removeAll { it.id == from }
        io.execute { client.mergeSpeakers(from, into) }
    }

    /** "这是我": the holder's voice. Remembered on this phone; call captions leave it out from now on. */
    fun markSelf(speakerId: Int) {
        if (speakerId != SELF_SPEAKER) {
            transcript.mergeSpeakers(speakerId, SELF_SPEAKER)
            for (segment in segments) if (segment.speakerId == speakerId) { segment.speakerId = SELF_SPEAKER; segment.confident = true }
            speakers.removeAll { it.id == speakerId }
        }
        ensureSpeaker(SELF_SPEAKER)
        io.execute { client.markSelf(speakerId) }
    }

    fun forgetSelf() {
        speakers.firstOrNull { it.id == SELF_SPEAKER }?.let { it.name = "第 ${nextOrdinal++} 个人" }
        io.execute { client.forgetSelf() }
    }

    fun markTelevision(speakerId: Int) {
        val speaker = ensureSpeaker(speakerId)
        speaker.ignored = true
        speaker.name = "电视"
        io.execute { client.setSpeakerIgnored(speakerId, true) }
    }

    fun reassign(segmentId: Long, speakerId: Int) {
        if (!transcript.reassign(segmentId, speakerId)) return
        segments.firstOrNull { it.id == segmentId }?.let { it.speakerId = speakerId; it.confident = true }
        ensureSpeaker(speakerId)
        // Only the running session's labeler knows this sentence's voiceprint.
        val raw = segmentId - sessionEpoch * SESSION_STRIDE
        if (raw in 1 until SESSION_STRIDE) io.execute { client.reassignSegment(raw, speakerId) }
    }

    fun addReply(text: String) {
        if (text.isBlank()) return
        ensureSpeaker(REPLY_SPEAKER)
        val id = -(System.currentTimeMillis())
        val line = SegmentUi(id, System.currentTimeMillis())
        line.text = text.trim(); line.confirmed = true; line.speakerId = REPLY_SPEAKER
        line.reveal.showAll()
        segments.add(line)
        trimHistory()
    }

    fun cycleFont() { fontLevel = (fontLevel + 1) % FONT_SP.size; prefs.edit().putInt("font", fontLevel).apply() }
    fun toggleLayout() { timeline = !timeline; prefs.edit().putBoolean("timeline", timeline).apply() }
    fun toggleTheme() { darkTheme = !darkTheme; prefs.edit().putBoolean("dark", darkTheme).apply() }

    /**
     * Captions always correct with SenseVoice when it is on disk (punctuation, CER 7.6 % vs 15 %),
     * whatever the keyboard's voice setting says; without it they fall back to the keyboard's own
     * model and the page offers the download.
     */
    private fun captionMode(): String {
        val manager = AsrModelManager(app)
        fun ready(mode: String) = runCatching { manager.selection(mode, null).ready }.getOrDefault(false)
        if (ready(SpeechModelCatalog.TWO_PASS)) return SpeechModelCatalog.TWO_PASS
        if (ready(SpeechModelCatalog.SENSEVOICE)) return SpeechModelCatalog.SENSEVOICE
        val keyboard = runCatching { manager.getSelectedModelId(InputLanguage.CHINESE) }.getOrNull()
        return listOfNotNull(keyboard, SpeechModelCatalog.PARAFORMER).firstOrNull { ready(it) }
            ?: error("还没有中文语音模型，请在 设置 → 语音转文本 里下载")
    }

    private fun usesSenseVoice(mode: String) = mode == SpeechModelCatalog.TWO_PASS || mode == SpeechModelCatalog.SENSEVOICE ||
        mode == SpeechModelCatalog.ZIPFORMER_TWO_PASS

    /** Whether the call-caption accessibility service is on; refreshed whenever the page comes back. */
    var callCaptionEnabled by mutableStateOf(true)
    fun refreshCallCaption() { callCaptionEnabled = CallCaptionService.isEnabled(app) }

    /** SenseVoice is on disk (or the page has not checked yet). */
    var refinementReady by mutableStateOf(true); private set
    var refinementDownload by mutableStateOf<ModelDownloadState>(ModelDownloadState.Idle); private set

    /**
     * Paraformer is on disk: words appear while the person is still speaking. SenseVoice alone
     * waits for each pause and then drops a whole sentence at once, too fast for an elder to read.
     */
    var streamingReady by mutableStateOf(true); private set
    var streamingDownload by mutableStateOf<ModelDownloadState>(ModelDownloadState.Idle); private set

    fun downloadStreaming() {
        if (streamingDownload is ModelDownloadState.Downloading) return
        streamingDownload = ModelDownloadState.Downloading(0f, 0, -1)
        scope.launch {
            ModelManager.downloadModel(app, SpeechModelCatalog.PARAFORMER) { state ->
                main.post {
                    streamingDownload = state
                    if (state is ModelDownloadState.Complete && (status == Status.Listening || status == Status.Starting)) { pause(); start() }
                }
            }
        }
    }

    fun downloadRefinement() {
        if (refinementDownload is ModelDownloadState.Downloading) return
        refinementDownload = ModelDownloadState.Downloading(0f, 0, -1)
        scope.launch {
            ModelManager.downloadModel(app, SpeechModelCatalog.SENSEVOICE) { state ->
                main.post {
                    refinementDownload = state
                    if (state is ModelDownloadState.Complete && (status == Status.Listening || status == Status.Starting)) { pause(); start() }
                }
            }
        }
    }

    fun downloadSpeakerModel() {
        if (downloadState is ModelDownloadState.Downloading) return
        downloadState = ModelDownloadState.Downloading(0f, 0, -1)
        scope.launch {
            ModelManager.downloadModel(app, BuiltinModelCatalog.SPEAKER_MODEL) { state ->
                main.post {
                    downloadState = state
                    if (state is ModelDownloadState.Complete) {
                        speakerModelReady = speakerModelFile() != null
                        // The running session has no labeler yet; a restart attaches it.
                        if (speakerModelReady && (status == Status.Listening || status == Status.Starting)) { pause(); start() }
                    }
                }
            }
        }
    }

    fun release() {
        exit()
        io.shutdown()
    }

    companion object {
        private const val TAG = "Caption"
        const val REPLY_SPEAKER = -1
        /** The phone holder's remembered voice (same id as the :asr tracker). */
        const val SELF_SPEAKER = com.kingzcheung.xime.speech.caption.SpeakerTracker.SELF
        /** Without a speaker model every line goes to this single unnamed speaker. */
        const val SINGLE_SPEAKER = 0
        /** Above every id a session uses, including pieces split at a change of voice (SPLIT_BASE = 2^40). */
        const val SESSION_STRIDE = 1_000_000_000_000_000L
        const val MAX_LINES = 400
        val FONT_SP = intArrayOf(24, 32, 44)
        val FONT_LABEL = arrayOf("大", "特大", "巨大")
        /** Okabe-Ito colour-blind safe palette: orange, sky blue, bluish green, blue, vermillion, reddish purple, yellow, grey. */
        val COLORS = longArrayOf(0xFFE69F00, 0xFF56B4E9, 0xFF009E73, 0xFF0072B2, 0xFFD55E00, 0xFFCC79A7, 0xFFF0E442, 0xFF999999)
        val PRESET_NAMES = listOf("这是我", "儿子", "女儿", "老伴", "孙子", "孙女", "医生", "邻居")
        /** Ready-made replies for a hard-of-hearing reader, by situation; one tap shows them to the other person. */
        val PHRASE_GROUPS: List<Pair<String, List<String>>> = listOf(
            "回答" to listOf("好的", "是", "不是", "对", "不对", "可以", "不要", "知道了", "谢谢", "没关系", "等一下", "我再想想"),
            "听不清" to listOf("请再说一遍", "说慢一点", "大声一点", "写下来给我看", "我听不见，请看手机", "请对着手机说", "我没听懂"),
            "生活" to listOf("我饿了，想吃饭", "我渴了，想喝水", "我想睡觉", "我要上厕所", "我冷", "我热", "我累了，想歇一会儿",
                "我想出去走走", "帮我开灯", "帮我关电视", "我想洗澡", "我想换衣服"),
            "家人" to listOf("我想见儿子", "我想见女儿", "我想见孙子", "我想见孙女", "我想孩子们了", "帮我给家里打个电话", "孩子什么时候来？", "你们吃饭了吗？", "路上小心"),
            "身体" to listOf("我不舒服", "我头疼", "我头晕", "胸口难受", "喘不上气", "我摔倒了", "我肚子疼", "该吃药了吗？", "帮我量一下血压"),
        )
        const val EMERGENCY_TEXT = "请马上帮我打120！"

    }
}
