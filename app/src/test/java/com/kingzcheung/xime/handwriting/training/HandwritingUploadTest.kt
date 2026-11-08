package com.kingzcheung.xime.handwriting.training

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.concurrent.thread
import java.util.zip.GZIPInputStream

/**
 * 贡献手写样本分批上传：接收端一次最多收 1000 条，写得多的人以前一次全传会被拒、永远传不上去（11-08）。
 * 用本机 HTTP 服务器代替魔搭接收端，按接收端的回执格式回 {"ok":true,"count":N}。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class)
class HandwritingUploadTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    // Android 单测的类路径里没有 com.sun.net.httpserver：用一个只认 Content-Length 的最小 HTTP 服务器。
    private lateinit var server: ServerSocket
    private val batches = mutableListOf<Int>()
    private var failFrom = Int.MAX_VALUE

    @Before fun start() {
        server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        thread(isDaemon = true) {
            while (!server.isClosed) {
                val socket = runCatching { server.accept() }.getOrNull() ?: break
                socket.use { s ->
                    val input = java.io.BufferedInputStream(s.getInputStream())
                    fun line(): String = buildString {
                        while (true) { val c = input.read(); if (c < 0 || c == 10) break; if (c != 13) append(c.toChar()) }
                    }
                    line() // POST /upload HTTP/1.1
                    var length = 0
                    while (true) {
                        val header = line(); if (header.isEmpty()) break
                        if (header.startsWith("content-length:", ignoreCase = true)) length = header.substringAfter(':').trim().toInt()
                    }
                    val bodyIn = ByteArray(length).also { var read = 0; while (read < length) { val n = input.read(it, read, length - read); if (n < 0) break; read += n } }
                    val lines = GZIPInputStream(bodyIn.inputStream()).bufferedReader().readLines().filter { it.isNotBlank() }
                    val ok = batches.size < failFrom
                    if (ok) batches += lines.size
                    check(lines.all { JSONObject(it).getString("license") == HandwritingContribution.LICENSE })
                    val body = (if (ok) """{"ok":true,"count":${lines.size}}""" else """{"error":"服务器暂时存不了"}""").toByteArray()
                    val out = s.getOutputStream()
                    val head = "HTTP/1.1 ${if (ok) "200 OK" else "503 Service Unavailable"}\r\nContent-Type: application/json\r\n" +
                        "Content-Length: ${body.size}\r\nConnection: close\r\n\r\n"
                    out.write(head.toByteArray())
                    out.write(body); out.flush()
                }
            }
        }
        HandwritingContribution.endpoint = "http://127.0.0.1:${server.localPort}/upload"
        HandwritingContribution.setConsented(context)
        HandwritingContribution.resetUploaded(context)
    }

    @After fun stop() {
        server.close()
        HandwritingContribution.endpoint = HandwritingContribution.UPLOAD_URL
    }

    private fun samples(n: Int): File = File(context.cacheDir, "upload-test.jsonl").apply {
        writeText((1..n).joinToString("\n", postfix = "\n") { """{"id":"$it","source":"contribute"}""" })
    }

    @Test fun uploadsInBatchesAndOnlyNewLinesNextTime() {
        val file = samples(HandwritingContribution.UPLOAD_CHUNK_LINES * 2 + 5)
        val result = HandwritingContribution.upload(context, file, "test")
        assertEquals(HandwritingContribution.UPLOAD_CHUNK_LINES * 2 + 5, result.count)
        assertEquals(listOf(HandwritingContribution.UPLOAD_CHUNK_LINES, HandwritingContribution.UPLOAD_CHUNK_LINES, 5), batches)
        assertEquals(HandwritingContribution.UPLOAD_CHUNK_LINES * 2 + 5, HandwritingContribution.uploadedLines(context))

        file.appendText("""{"id":"new","source":"contribute"}""" + "\n")
        assertEquals(1, HandwritingContribution.upload(context, file, "test").count)
        assertEquals(0, HandwritingContribution.upload(context, file, "test").count)
    }

    @Test fun failedBatchKeepsConfirmedOnesUploaded() {
        val file = samples(HandwritingContribution.UPLOAD_CHUNK_LINES + 10)
        failFrom = 1
        try {
            HandwritingContribution.upload(context, file, "test")
            fail("second batch should fail")
        } catch (e: IllegalStateException) {
            assertEquals("服务器暂时存不了", e.message)
        }
        // 第一批已确认，下次只传剩下的 10 条
        assertEquals(HandwritingContribution.UPLOAD_CHUNK_LINES, HandwritingContribution.uploadedLines(context))
        failFrom = Int.MAX_VALUE
        assertEquals(10, HandwritingContribution.upload(context, file, "test").count)
    }
}
