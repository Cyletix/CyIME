package com.kingzcheung.xime.speech

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.Base64
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * 火山引擎(豆包)语音识别：接口地址是 openspeech.bytedance.com 或以 wss:// 开头时走这里，不走 OpenAI 格式。
 * - 流式识别 `/api/v3/sauc/bigmodel`、`bigmodel_async`、`bigmodel_nostream`：WebSocket 二进制协议。
 *   双向流式(`bigmodel`、`bigmodel_async`)边说边发、边出字（[LiveSession]）；流式输入(`bigmodel_nostream`)
 *   录完整段再发：发请求参数，再按 200 ms 一包把音频发完，最后一包带结束标记，等服务器带结束标记的那条结果。
 * - 录音文件极速版 `/api/v3/auc/bigmodel/recognize/flash`：HTTPS 上传 JSON，音频是 Base64 WAV。
 * 鉴权：只填密钥时按新版控制台的 API Key 发 `X-Api-Key`；填了 App ID 按旧版发 `X-Api-App-Key` + `X-Api-Access-Key`。
 * 「模型」一栏填资源 ID(`X-Api-Resource-Id`)，没填 volc. 开头的值时按地址用默认资源。
 * 协议按火山引擎「大模型流式语音识别 API」(docs/6561/1354869) 与「录音文件识别极速版」(docs/6561/1631584)。
 */
internal object VolcengineSpeechApi {
    const val HOST = "openspeech.bytedance.com"
    /** 只填域名时用「流式输入」接口：整段发完才出结果，识别率比边说边出字的高，正合整段上传。 */
    const val DEFAULT_STREAM_PATH = "/api/v3/sauc/bigmodel_nostream"
    const val DEFAULT_STREAM_RESOURCE = "volc.seedasr.sauc.duration"
    const val DEFAULT_FLASH_RESOURCE = "volc.bigasr.auc_turbo"
    private const val SAMPLE_RATE = 16000
    /** 官方建议一包 100～200 ms；16 kHz 单声道 16 位，200 ms = 6400 字节。 */
    const val PACKET_BYTES = SAMPLE_RATE * 2 / 5

    // 4 字节帧头：协议版本(4 位) 头长(4 位，单位 4 字节) | 消息类型(4 位) 标志(4 位) | 序列化(4 位) 压缩(4 位) | 保留
    private const val PROTOCOL_VERSION = 0b0001
    private const val HEADER_SIZE = 0b0001
    const val FULL_CLIENT_REQUEST = 0b0001
    const val AUDIO_ONLY_REQUEST = 0b0010
    const val FULL_SERVER_RESPONSE = 0b1001
    const val SERVER_ERROR = 0b1111
    const val FLAG_SEQUENCE = 0b0001
    const val FLAG_LAST = 0b0010
    const val SERIAL_NONE = 0b0000
    const val SERIAL_JSON = 0b0001
    const val COMPRESS_NONE = 0b0000
    const val COMPRESS_GZIP = 0b0001

    const val CODE_OK = 20000000L
    /** 极速版：音频有效但没人说话，按识别出空文字处理。 */
    const val CODE_SILENCE = 20000003L

    fun isVolcengine(url: String): Boolean {
        val u = url.trim().lowercase()
        return u.startsWith("wss://") || u.startsWith("ws://") || HOST in u
    }

    fun isFlash(url: String): Boolean = "/recognize/flash" in url.lowercase()

    /**
     * 双向流式 `/sauc/bigmodel`、`/sauc/bigmodel_async`：说话时就发音频，服务器边认边回字。
     * 只填域名(补成流式输入 nostream)、流式输入、极速版和其他 ws 地址还是录完整段再发。
     */
    fun isLive(url: String): Boolean {
        if (!isVolcengine(url) || isFlash(url)) return false
        val path = endpoint(url).substringAfter("://").substringAfter('/', "").substringBefore('?').trimEnd('/').lowercase()
        return path.endsWith("sauc/bigmodel") || path.endsWith("sauc/bigmodel_async")
    }

    /** 用户填的地址 → 实际地址：极速版没写 http(s):// 时用 https；流式把 https:// 换成 wss://，只填域名时补上流式输入接口。 */
    fun endpoint(baseUrl: String): String {
        val url = baseUrl.trim().trimEnd('/')
        val rest = url.substringAfter("://", url)
        if (isFlash(url)) return if (url.startsWith("http://", true) || url.startsWith("https://", true)) url else "https://$rest"
        val scheme = if (url.startsWith("ws://", true) || url.startsWith("http://", true)) "ws" else "wss"
        return if ('/' in rest) "$scheme://$rest" else "$scheme://$rest$DEFAULT_STREAM_PATH"
    }

    /** 「模型」一栏：volc. 开头的才当资源 ID；还是 OpenAI 的默认模型名或空着时按地址用默认资源。 */
    fun resourceId(model: String, url: String): String =
        model.trim().takeIf { it.startsWith("volc.", ignoreCase = true) }
            ?: if (isFlash(url)) DEFAULT_FLASH_RESOURCE else DEFAULT_STREAM_RESOURCE

    /** 鉴权头：填了 App ID 走旧版(App ID + Access Token)，没填走新版控制台 API Key。 */
    fun headers(appId: String, apiKey: String, resourceId: String, requestId: String): Map<String, String> =
        buildMap {
            if (appId.isNotBlank()) {
                put("X-Api-App-Key", appId.trim())
                put("X-Api-Access-Key", apiKey.trim())
            } else {
                put("X-Api-Key", apiKey.trim())
            }
            put("X-Api-Resource-Id", resourceId)
            put("X-Api-Request-Id", requestId)
            put("X-Api-Connect-Id", requestId)
        }

    /** 第一帧的请求参数：16 kHz 单声道 16 位 PCM；加标点、数字转阿拉伯数字。不传语种，中英混说交给接口自己认。 */
    fun requestJson(): JSONObject = JSONObject()
        .put("user", JSONObject().put("uid", "cyime"))
        .put("audio", JSONObject().put("format", "pcm").put("codec", "raw").put("rate", SAMPLE_RATE)
            .put("bits", 16).put("channel", 1))
        .put("request", JSONObject().put("model_name", "bigmodel").put("enable_itn", true)
            .put("enable_punc", true).put("result_type", "full"))

    private fun header(type: Int, flags: Int, serialization: Int, compression: Int) = byteArrayOf(
        ((PROTOCOL_VERSION shl 4) or HEADER_SIZE).toByte(),
        ((type shl 4) or flags).toByte(),
        ((serialization shl 4) or compression).toByte(),
        0,
    )

    /** 帧 = 帧头 + 4 字节大端负载长度 + gzip 后的负载(这里不带序号)。 */
    private fun frame(type: Int, flags: Int, serialization: Int, payload: ByteArray): ByteArray {
        val body = gzip(payload)
        return ByteBuffer.allocate(8 + body.size)
            .put(header(type, flags, serialization, COMPRESS_GZIP)).putInt(body.size).put(body).array()
    }

    fun encodeFullClientRequest(json: JSONObject): ByteArray =
        frame(FULL_CLIENT_REQUEST, 0, SERIAL_JSON, json.toString().toByteArray(Charsets.UTF_8))

    fun encodeAudio(pcm: ByteArray, last: Boolean): ByteArray =
        frame(AUDIO_ONLY_REQUEST, if (last) FLAG_LAST else 0, SERIAL_NONE, pcm)

    /** 整段录音切成 200 ms 一包，最后一包带结束标记；空录音也发一个空的结束包。 */
    fun encodeAudioPackets(pcm: ByteArray): List<ByteArray> {
        if (pcm.isEmpty()) return listOf(encodeAudio(pcm, last = true))
        val count = (pcm.size + PACKET_BYTES - 1) / PACKET_BYTES
        return (0 until count).map { i ->
            val from = i * PACKET_BYTES
            encodeAudio(pcm.copyOfRange(from, minOf(pcm.size, from + PACKET_BYTES)), last = i == count - 1)
        }
    }

    /** 服务器帧：[json] 为解出的 JSON；不是 JSON 的错误说明放在 [message]。 */
    data class ServerFrame(
        val type: Int,
        val last: Boolean,
        val sequence: Int?,
        val code: Long?,
        val json: JSONObject?,
        val message: String?,
    )

    fun parse(bytes: ByteArray): ServerFrame {
        require(bytes.size >= 4) { "帧太短" }
        val headerBytes = (bytes[0].toInt() and 0x0F) * 4
        val type = (bytes[1].toInt() shr 4) and 0x0F
        val flags = bytes[1].toInt() and 0x0F
        val serialization = (bytes[2].toInt() shr 4) and 0x0F
        val compression = bytes[2].toInt() and 0x0F
        val buffer = ByteBuffer.wrap(bytes, headerBytes, bytes.size - headerBytes)
        val sequence = if (flags and FLAG_SEQUENCE != 0 && buffer.remaining() >= 4) buffer.int else null
        val code = if (type == SERVER_ERROR && buffer.remaining() >= 4) buffer.int.toLong() and 0xFFFFFFFFL else null
        var payload = ByteArray(0)
        if (buffer.remaining() >= 4) {
            val size = buffer.int.coerceIn(0, buffer.remaining())
            payload = ByteArray(size).also { buffer.get(it) }
        }
        if (compression == COMPRESS_GZIP && payload.isNotEmpty()) payload = runCatching { gunzip(payload) }.getOrDefault(payload)
        val text = payload.toString(Charsets.UTF_8)
        val json = if (serialization == SERIAL_JSON || text.trimStart().startsWith("{")) runCatching { JSONObject(text) }.getOrNull() else null
        return ServerFrame(type, flags and FLAG_LAST != 0, sequence, code, json,
            if (json == null) text.trim().takeIf { it.isNotEmpty() } else null)
    }

    /** 识别结果：result.text；它是空的时把 utterances 里的句子按顺序拼起来。没有 result 时为 null。 */
    fun resultText(json: JSONObject?): String? {
        val result = json?.optJSONObject("result") ?: (json?.opt("result") as? JSONArray)?.optJSONObject(0) ?: return null
        result.optString("text").trim().takeIf { it.isNotEmpty() }?.let { return it }
        val utterances = result.optJSONArray("utterances") ?: return ""
        return (0 until utterances.length()).joinToString("") { utterances.optJSONObject(it)?.optString("text").orEmpty().trim() }
    }

    /** 火山引擎的错误码 → 一句人话，后面跟接口自己给的原因。 */
    fun errorText(code: Long?, detail: String?): String {
        val base = when {
            code == 45000001L -> "请求参数不对"
            code == 45000002L -> "录音是空的"
            code == 45000081L -> "等音频超时"
            code == 45000151L -> "音频格式不对"
            code == 55000031L -> "服务器忙"
            code != null && code / 10000000 == 5L -> "服务器出错"
            else -> "接口报错"
        } + (code?.let { "($it)" } ?: "")
        return detail?.let { "$base：${shorten(it)}" } ?: base
    }

    private fun frameError(frame: ServerFrame): String = errorText(frame.code,
        frame.json?.let { j -> listOf("error", "message", "msg").map { j.optString(it) }.firstOrNull { it.isNotBlank() } } ?: frame.message)

    private fun shorten(text: String): String =
        text.replace(Regex("\\s+"), " ").trim().let { if (it.length > 60) it.take(60) + "…" else it }

    /**
     * 识别一段 16 kHz 单声道 16 位小端 PCM。[httpError] 把握手或 HTTP 失败的状态码和接口原因变成提示。
     * 等结果最多 25 秒，比键盘收尾的 30 秒短，超时能显示这里的原因。在后台线程调用。
     */
    fun transcribe(
        client: OkHttpClient,
        baseUrl: String,
        appId: String,
        apiKey: String,
        model: String,
        pcm: ByteArray,
        httpError: (Int, String?) -> String,
        timeoutMs: Long = 25_000L,
    ): String {
        val url = endpoint(baseUrl)
        val headers = headers(appId, apiKey, resourceId(model, url), UUID.randomUUID().toString())
        return if (isFlash(url)) flash(client, url, headers, pcm, httpError) else stream(client, url, headers, pcm, httpError, timeoutMs)
    }

    private fun flash(client: OkHttpClient, url: String, headers: Map<String, String>, pcm: ByteArray,
                      httpError: (Int, String?) -> String): String {
        val body = requestJson().apply {
            getJSONObject("audio").put("data", Base64.getEncoder().encodeToString(OnlineSpeechApi.wav(pcm)))
                .put("format", "wav").remove("codec")
        }.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url(url).apply { headers.forEach { (k, v) -> header(k, v) } }
            .header("X-Api-Sequence", "-1").post(body).build()
        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            val json = runCatching { JSONObject(raw) }.getOrNull()
            val message = response.header("X-Api-Message")
            check(response.isSuccessful) { httpError(response.code, message ?: json?.optString("message")?.takeIf { it.isNotBlank() }) }
            val status = response.header("X-Api-Status-Code")?.toLongOrNull()
            if (status == CODE_SILENCE) return ""
            if (status != null && status != CODE_OK) error(errorText(status, message))
            return resultText(json) ?: error("接口返回的格式看不懂")
        }
    }

    private fun stream(client: OkHttpClient, url: String, headers: Map<String, String>, pcm: ByteArray,
                       httpError: (Int, String?) -> String, timeoutMs: Long): String {
        val done = CountDownLatch(1)
        var text: String? = null
        var failure: String? = null
        var latest = ""
        fun finish(result: String?, error: String?) {
            if (done.count == 0L) return
            text = result; failure = error; done.countDown()
        }
        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                // 请求参数和整段录音一次排进发送队列；服务器按顺序读，参数不对时回错误帧。
                webSocket.send(encodeFullClientRequest(requestJson()).toByteString())
                for (packet in encodeAudioPackets(pcm)) {
                    if (!webSocket.send(packet.toByteString())) { finish(null, "录音太长，发不出去"); webSocket.cancel(); return }
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                val frame = runCatching { parse(bytes.toByteArray()) }.getOrElse {
                    finish(null, "接口返回的格式看不懂"); webSocket.cancel(); return
                }
                if (frame.type == SERVER_ERROR) {
                    if (frame.code == CODE_SILENCE) finish("", null) else finish(null, frameError(frame))
                    webSocket.close(1000, null); return
                }
                if (frame.type != FULL_SERVER_RESPONSE) return
                // 中间结果只在有字时更新，最后一条万一是空的也不丢前面认出的字。
                resultText(frame.json)?.takeIf { it.isNotEmpty() }?.let { latest = it }
                if (frame.last) { finish(latest, null); webSocket.close(1000, null) }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                finish(null, "服务器断开了连接" + reason.takeIf { it.isNotBlank() }?.let { "：${shorten(it)}" }.orEmpty())
                webSocket.close(1000, null)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                finish(null, connectError(response, httpError))
            }
        }
        val request = Request.Builder().url(url).apply { headers.forEach { (k, v) -> header(k, v) } }.build()
        val socket = client.newWebSocket(request, listener)
        if (!done.await(timeoutMs, TimeUnit.MILLISECONDS)) {
            socket.cancel()
            error("等识别结果超时")
        }
        failure?.let { error(it) }
        return text.orEmpty().trim()
    }

    /** 握手失败(401、403、404…)带上接口给的原因；没有回包就是连不上。 */
    private fun connectError(response: Response?, httpError: (Int, String?) -> String): String {
        if (response == null || response.code == 101) return "连不上服务器"
        val body = runCatching { response.body?.string() }.getOrNull().orEmpty()
        val detail = response.header("X-Api-Message")
            ?: runCatching { JSONObject(body) }.getOrNull()?.let { j ->
                listOf("error", "message", "msg").map { j.optString(it) }.firstOrNull { it.isNotBlank() }
            }
            ?: body.trim().takeIf { it.isNotEmpty() && '<' !in it }
        return httpError(response.code, detail?.let(::shorten))
    }

    /**
     * 双向流式的一次录音：说话时 [feed] 把音频按 200 ms 一包发出去，服务器每回一条就把目前认出的整段文字交给
     * [onPartial]，键盘上边说边出字；松手时 [finish] 发带结束标记的最后一包，等带结束标记的那条结果。
     * 第一次 [feed] 才连服务器，没开口不占连接。手里总留一点音频(不到一包或正好一包)，最后一包不会是空的。
     * [feed]、[finish]、[cancel] 在同一个后台线程调用；[onPartial] 在 OkHttp 的读线程回调，结束或取消后不再回调。
     */
    class LiveSession(
        private val client: OkHttpClient,
        baseUrl: String,
        appId: String,
        apiKey: String,
        model: String,
        private val httpError: (Int, String?) -> String,
        private val onPartial: (String) -> Unit,
    ) {
        private val url = endpoint(baseUrl)
        private val headers = headers(appId, apiKey, resourceId(model, url), UUID.randomUUID().toString())
        private val lock = Any()
        private val pending = ByteArrayOutputStream()
        /** 连上之前排着的音频帧；连上时先发请求参数，再按顺序发这些。 */
        private val queued = ArrayList<ByteArray>()
        private var socket: WebSocket? = null
        private var opened = false
        private var lastSent = false
        private val ended = java.util.concurrent.atomic.AtomicBoolean()
        private val done = CountDownLatch(1)
        @Volatile private var text: String? = null
        @Volatile private var failure: String? = null
        @Volatile private var latest = ""

        private fun end(result: String?, error: String?) {
            if (!ended.compareAndSet(false, true)) return
            text = result; failure = error; done.countDown()
        }

        /** 在 [lock] 里调用。 */
        private fun send(frame: ByteArray) {
            val ws = socket ?: return
            if (!opened) { queued += frame; return }
            if (!ws.send(frame.toByteString())) { end(null, "录音太长，发不出去"); ws.cancel() }
        }

        /** 在 [lock] 里调用。 */
        private fun connect() {
            if (socket != null) return
            val request = Request.Builder().url(url).apply { headers.forEach { (k, v) -> header(k, v) } }.build()
            socket = client.newWebSocket(request, listener)
        }

        fun feed(pcm: ByteArray) {
            if (pcm.isEmpty() || ended.get()) return
            synchronized(lock) {
                if (lastSent) return
                connect()
                pending.write(pcm)
                if (pending.size() <= PACKET_BYTES) return
                val bytes = pending.toByteArray()
                pending.reset()
                var from = 0
                while (bytes.size - from > PACKET_BYTES) {
                    send(encodeAudio(bytes.copyOfRange(from, from + PACKET_BYTES), last = false))
                    from += PACKET_BYTES
                }
                pending.write(bytes, from, bytes.size - from)
            }
        }

        /** 发最后一包并等结果，最多 [timeoutMs]（比键盘收尾的 30 秒短，超时能显示这里的原因）。 */
        fun finish(timeoutMs: Long = 25_000L): String {
            val ws = synchronized(lock) {
                if (!lastSent) {
                    connect()
                    lastSent = true
                    send(encodeAudio(pending.toByteArray(), last = true))
                    pending.reset()
                }
                socket
            }
            if (!done.await(timeoutMs, TimeUnit.MILLISECONDS)) {
                ws?.cancel()
                error("等识别结果超时")
            }
            failure?.let { error(it) }
            return text.orEmpty().trim()
        }

        fun cancel() {
            end(null, "已取消")
            synchronized(lock) { lastSent = true; socket }?.cancel()
        }

        private val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                synchronized(lock) {
                    if (!webSocket.send(encodeFullClientRequest(requestJson()).toByteString())) return
                    opened = true
                    queued.forEach(::send)
                    queued.clear()
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                if (ended.get()) return
                val frame = runCatching { parse(bytes.toByteArray()) }.getOrElse {
                    end(null, "接口返回的格式看不懂"); webSocket.cancel(); return
                }
                if (frame.type == SERVER_ERROR) {
                    if (frame.code == CODE_SILENCE) end("", null) else end(null, frameError(frame))
                    webSocket.close(1000, null); return
                }
                if (frame.type != FULL_SERVER_RESPONSE) return
                // 中间结果只在有字时更新，最后一条万一是空的也不丢前面认出的字。
                val changed = resultText(frame.json)?.takeIf { it.isNotEmpty() && it != latest }?.also { latest = it } != null
                if (frame.last) { end(latest, null); webSocket.close(1000, null) }
                else if (changed) onPartial(latest)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                end(null, "服务器断开了连接" + reason.takeIf { it.isNotBlank() }?.let { "：${shorten(it)}" }.orEmpty())
                webSocket.close(1000, null)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                end(null, connectError(response, httpError))
            }
        }
    }

    fun gzip(data: ByteArray): ByteArray =
        ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(data) } }.toByteArray()

    fun gunzip(data: ByteArray): ByteArray = GZIPInputStream(ByteArrayInputStream(data)).use { it.readBytes() }
}
