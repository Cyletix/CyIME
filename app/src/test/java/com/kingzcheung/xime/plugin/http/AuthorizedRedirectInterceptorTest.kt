package com.kingzcheung.xime.plugin.http

import com.kingzcheung.xime.plugin.core.lua.ws.NetworkPolicy
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.*
import org.junit.Test

class AuthorizedRedirectInterceptorTest {
    @Test
    fun `redirect checks happen before connecting to denied host for ordinary and SSE requests`() {
        for (accept in listOf("text/plain", "text/event-stream")) {
            ServerSocket(0).use { server ->
                val worker = Executors.newSingleThreadExecutor()
                try {
                    server.soTimeout = 700
                    val contacts = worker.submit<Int> {
                        server.accept().use { socket ->
                            val reader = socket.getInputStream().bufferedReader()
                            while (reader.readLine()?.isNotEmpty() == true) Unit
                            socket.getOutputStream().write(("HTTP/1.1 302 Found\r\n" +
                                "Location: http://127.0.0.1:${server.localPort}/denied\r\n" +
                                "Content-Length: 0\r\nConnection: close\r\n\r\n").toByteArray())
                        }
                        try { server.accept().use { 2 } } catch (_: SocketTimeoutException) { 1 }
                    }
                    val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
                        .addInterceptor(AuthorizedRedirectInterceptor { url ->
                            NetworkPolicy.check(url, emptySet(), listOf("localhost"), setOf("localhost"))
                        }).build()
                    try {
                        client.newCall(Request.Builder().url("http://localhost:${server.localPort}/start")
                            .header("Accept", accept).build()).execute().close()
                        fail("未授权跳转必须失败")
                    } catch (expected: IOException) {
                        assertTrue(expected.message.orEmpty().contains("127.0.0.1"))
                    } finally {
                        client.connectionPool.evictAll()
                        client.dispatcher.executorService.shutdown()
                    }
                    assertEquals(1, contacts.get(3, TimeUnit.SECONDS))
                } finally { worker.shutdownNow() }
            }
        }
    }
}
