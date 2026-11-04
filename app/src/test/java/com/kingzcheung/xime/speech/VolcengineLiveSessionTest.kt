package com.kingzcheung.xime.speech

import okhttp3.OkHttpClient
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.DataInputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.Base64
import java.util.Collections
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * 豆包双向流式(bigmodel / bigmodel_async)：说话时音频就发出去、字边说边出；松手才发最后一包。
 * 单元测试里没有 MockWebServer：用 ServerSocket 手写最小 WebSocket 服务器，按官方二进制帧回结果。
 */
class VolcengineLiveSessionTest {
    private val api = VolcengineSpeechApi
    private val client = OkHttpClient.Builder().readTimeout(10, TimeUnit.SECONDS).build()
    private val server = FakeVolcServer()

    @After fun tearDown() {
        server.close()
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }

    @Test fun `只有双向流式地址边说边发，流式输入、只填域名、极速版照旧整段上传`() {
        assertTrue(api.isLive("wss://openspeech.bytedance.com/api/v3/sauc/bigmodel_async"))
        assertTrue(api.isLive("wss://openspeech.bytedance.com/api/v3/sauc/bigmodel/"))
        assertTrue(api.isLive("https://openspeech.bytedance.com/api/v3/sauc/bigmodel_async"))
        assertTrue(api.isLive(" WSS://openspeech.bytedance.com/api/v3/sauc/BigModel_Async "))
        assertFalse(api.isLive("wss://openspeech.bytedance.com/api/v3/sauc/bigmodel_nostream"))
        assertFalse(api.isLive("openspeech.bytedance.com"))
        assertFalse(api.isLive("https://openspeech.bytedance.com/api/v3/auc/bigmodel/recognize/flash"))
        assertFalse(api.isLive("ws://192.168.1.2:8080/asr"))
        assertFalse(api.isLive("https://api.openai.com/v1"))
    }

    @Test fun `说话时就发音频并回中间结果，松手才发最后一包，拼回来和原录音一样`() {
        val partials = CopyOnWriteArrayList<String>()
        val firstPartial = CountDownLatch(1)
        server.replies = { index, last ->
            when {
                last -> serverFrame("你好世界。", sequence = -(index + 1), last = true)
                index == 0 -> serverFrame("你好", sequence = index + 1)
                index == 1 -> serverFrame("你好世", sequence = index + 1)
                // 字没变的那条不再回调一次。
                else -> serverFrame("你好世", sequence = index + 1)
            }
        }
        val session = VolcengineSpeechApi.LiveSession(client, "ws://127.0.0.1:${server.port}/api/v3/sauc/bigmodel_async", "", "key",
            "volc.seedasr.sauc.duration", { code, detail -> "HTTP $code $detail" }) { partials += it; firstPartial.countDown() }

        val chunk = 3200 // 键盘 100 ms 一块
        val pcm = ByteArray(chunk * 7) { (it % 251).toByte() }
        // 三块 = 300 ms：第一包 200 ms 已经发出去，服务器回了中间结果，这时还没松手。
        for (i in 0 until 3) session.feed(pcm.copyOfRange(i * chunk, (i + 1) * chunk))
        assertTrue("没松手就该有中间结果", firstPartial.await(5, TimeUnit.SECONDS))
        assertEquals("你好", partials.first())
        assertFalse("最后一包要等松手", server.lastReceived)
        for (i in 3 until 7) session.feed(pcm.copyOfRange(i * chunk, (i + 1) * chunk))

        assertEquals("你好世界。", session.finish(5_000))
        assertEquals(listOf("你好", "你好世"), partials.toList())
        assertEquals("key", server.headers["x-api-key"])
        assertEquals("volc.seedasr.sauc.duration", server.headers["x-api-resource-id"])
        // 第一帧是请求参数；音频 200 ms 一包，只有最后一包带结束标记，而且最后一包不是空的。
        assertEquals(api.FULL_CLIENT_REQUEST, server.requestType)
        val packets = server.audio.toList()
        assertTrue(packets.dropLast(1).all { it.size == api.PACKET_BYTES })
        assertTrue(packets.last().isNotEmpty())
        assertEquals(listOf(true), server.lastFlags.filter { it })
        assertTrue(server.lastFlags.last())
        assertTrue(pcm.contentEquals(packets.flatMap { it.toList() }.toByteArray()))
    }

    @Test fun `服务器回错误帧时说出原因，之后不再回调中间结果`() {
        server.replies = { index, _ -> if (index == 0) errorFrame(45000001L, """{"error":"invalid resource id"}""") else null }
        val partials = CopyOnWriteArrayList<String>()
        val session = VolcengineSpeechApi.LiveSession(client, "ws://127.0.0.1:${server.port}/api/v3/sauc/bigmodel", "", "key", "x",
            { code, detail -> "HTTP $code $detail" }) { partials += it }
        repeat(4) { session.feed(ByteArray(3200)) }
        try {
            session.finish(5_000)
            fail("应当报错")
        } catch (e: IllegalStateException) {
            assertEquals("请求参数不对(45000001)：invalid resource id", e.message)
        }
        assertTrue(partials.isEmpty())
    }

    @Test fun `没开口就取消不连服务器`() {
        val session = VolcengineSpeechApi.LiveSession(client, "ws://127.0.0.1:${server.port}/api/v3/sauc/bigmodel_async", "", "key", "x",
            { code, _ -> "HTTP $code" }) { }
        session.feed(ByteArray(0))
        session.cancel()
        assertFalse(server.connected.await(300, TimeUnit.MILLISECONDS))
    }

    private fun serverFrame(text: String, sequence: Int, last: Boolean = false): ByteArray {
        val payload = api.gzip(JSONObject().put("result", JSONObject().put("text", text)).toString().toByteArray())
        val flags = api.FLAG_SEQUENCE or (if (last) api.FLAG_LAST else 0)
        return ByteBuffer.allocate(12 + payload.size)
            .put(byteArrayOf(0x11, ((api.FULL_SERVER_RESPONSE shl 4) or flags).toByte(), 0x11, 0))
            .putInt(sequence).putInt(payload.size).put(payload).array()
    }

    private fun errorFrame(code: Long, json: String): ByteArray {
        val payload = json.toByteArray()
        return ByteBuffer.allocate(12 + payload.size).put(byteArrayOf(0x11, (api.SERVER_ERROR shl 4).toByte(), 0x10, 0))
            .putInt(code.toInt()).putInt(payload.size).put(payload).array()
    }

    /** 最小 WebSocket 服务器：握手、读客户端(带掩码)二进制帧、回不带掩码的二进制帧。一次只接一个连接。 */
    private inner class FakeVolcServer : AutoCloseable {
        private val socket = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        val port = socket.localPort
        /** 第 index 个音频包(从 0 数)收到后回什么；last：这包带结束标记。 */
        @Volatile var replies: (Int, Boolean) -> ByteArray? = { _, _ -> null }
        val headers: MutableMap<String, String> = Collections.synchronizedMap(HashMap())
        @Volatile var requestType = -1
        val audio = CopyOnWriteArrayList<ByteArray>()
        val lastFlags = CopyOnWriteArrayList<Boolean>()
        @Volatile var lastReceived = false
        val connected = CountDownLatch(1)
        private var peer: Socket? = null
        private val thread = Thread({ runCatching { serve() } }, "FakeVolcServer").apply { isDaemon = true; start() }

        private fun serve() {
            val conn = socket.accept().also { peer = it }
            connected.countDown()
            val input = DataInputStream(conn.getInputStream())
            val output = conn.getOutputStream()
            handshake(input, output)
            var index = 0
            while (true) {
                val data = readFrame(input) ?: return
                val type = (data[1].toInt() shr 4) and 0x0F
                if (type == api.FULL_CLIENT_REQUEST) { requestType = type; continue }
                val last = data[1].toInt() and api.FLAG_LAST != 0
                val size = ByteBuffer.wrap(data, 4, 4).int
                audio += api.gunzip(data.copyOfRange(8, 8 + size))
                lastFlags += last
                if (last) lastReceived = true
                replies(index++, last)?.let { writeFrame(output, it) }
                if (last) return
            }
        }

        private fun handshake(input: InputStream, output: OutputStream) {
            val lines = StringBuilder()
            while (!lines.endsWith("\r\n\r\n")) lines.append(input.read().also { check(it >= 0) }.toChar())
            lines.lines().drop(1).filter { ':' in it }.forEach {
                headers[it.substringBefore(':').trim().lowercase()] = it.substringAfter(':').trim()
            }
            val accept = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1")
                .digest((headers.getValue("sec-websocket-key") + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").toByteArray()))
            output.write("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: $accept\r\n\r\n".toByteArray())
            output.flush()
        }

        /** 读一个客户端帧；关闭帧或连接断开返回 null。 */
        private fun readFrame(input: DataInputStream): ByteArray? {
            val b0 = input.read().takeIf { it >= 0 } ?: return null
            val b1 = input.readUnsignedByte()
            var length = (b1 and 0x7F).toLong()
            if (length == 126L) length = input.readUnsignedShort().toLong() else if (length == 127L) length = input.readLong()
            val mask = ByteArray(4).also { if (b1 and 0x80 != 0) input.readFully(it) }
            val payload = ByteArray(length.toInt()).also { input.readFully(it) }
            for (i in payload.indices) payload[i] = (payload[i].toInt() xor mask[i % 4].toInt()).toByte()
            return if (b0 and 0x0F == 0x8) null else payload
        }

        private fun writeFrame(output: OutputStream, payload: ByteArray) {
            val head = when {
                payload.size < 126 -> byteArrayOf(0x82.toByte(), payload.size.toByte())
                else -> byteArrayOf(0x82.toByte(), 126, (payload.size shr 8).toByte(), payload.size.toByte())
            }
            output.write(head); output.write(payload); output.flush()
        }

        override fun close() {
            runCatching { peer?.close() }
            runCatching { socket.close() }
            thread.join(1000)
        }
    }
}
