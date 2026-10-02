package com.kingzcheung.xime.plugin.core.lua

import com.kingzcheung.xime.plugin.core.api.ClipboardProfile
import com.kingzcheung.xime.plugin.core.lua.http.HttpHostApi
import com.kingzcheung.xime.plugin.core.lua.http.HttpResponse
import com.kingzcheung.xime.plugin.core.lua.sdk.SimpleJson
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.net.URL
import java.net.HttpURLConnection
import java.util.concurrent.TimeUnit

/** Actual Lua + Kotlin adapter -> loopback HTTP -> actual Windows ClipboardServer.cs. */
class WindowsClipboardInteropTest {
    private class Network : HttpHostApi {
        var lastStatus = 0
        override fun request(method: String, url: String, headers: Map<String, String>, body: ByteArray?, timeoutMillis: Int?): HttpResponse {
            val c = URL(url).openConnection() as HttpURLConnection
            c.instanceFollowRedirects = false
            c.connectTimeout = timeoutMillis ?: 3000; c.readTimeout = timeoutMillis ?: 3000
            c.requestMethod = method
            headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
            return try {
                if (body != null) { c.doOutput = true; c.outputStream.use { it.write(body) } }
                lastStatus = c.responseCode
                val bytes = (if (lastStatus >= 400) c.errorStream else c.inputStream)?.use { it.readBytes() } ?: ByteArray(0)
                HttpResponse(lastStatus, c.headerFields.filterKeys { it != null }.mapValues { it.value.first() }, bytes)
            } finally { c.disconnect() }
        }
        override fun lastError(): String? = null
    }
    private class Server(file: File) : AutoCloseable {
        val process = ProcessBuilder("dotnet", file.canonicalPath).redirectError(ProcessBuilder.Redirect.INHERIT).start()
        val input = process.inputStream.bufferedReader()
        val output = process.outputStream.bufferedWriter()
        val address = input.readLine().also { check(it?.startsWith("http://127.0.0.1:") == true) }
        fun command(vararg values: Pair<String, Any>): Map<*, *> {
            output.write(SimpleJson.encode(mapOf(*values))); output.newLine(); output.flush()
            return SimpleJson.decode(input.readLine()) as Map<*, *>
        }
        override fun close() {
            output.write("{\"op\":\"quit\"}\n"); output.flush()
            if (!process.waitFor(8, TimeUnit.SECONDS)) process.destroyForcibly()
            input.close(); output.close()
        }
    }
    @Test(timeout = 30_000) fun `real Windows server and Android Lua exchange text and enforce failures`() = runBlocking {
        val dll = File("../tools/clipboard-sync-fixture/bin/Debug/net9.0/ClipboardFixture.dll")
        assumeTrue("Build tools/clipboard-sync-fixture first for local Windows interoperability", dll.exists())
        Server(dll).use { server ->
            val http = Network()
            WindowsSyncHarness(http).use { h ->
                h.store.set("serverUrl", server.address)
                assertNull(h.adapter.onAction("testConnection"))
                val initial = h.adapter.pull()!!; assertEquals("电脑初始文本", initial.text)
                h.adapter.acknowledgePull(initial.hash); assertNull(h.adapter.pull()); assertEquals(304, http.lastStatus)
                val text = "手机：中文\n换行 😀\r\n第二行"
                assertTrue(h.adapter.push(ClipboardProfile.fromText(text, "Android")))
                assertEquals(text, server.command("op" to "read")["text"])
                assertTrue(h.adapter.push(ClipboardProfile.fromText(text, "Android")))
                assertEquals(1L, (server.command("op" to "read")["writes"] as Number).toLong())
                server.command("op" to "copy", "text" to "电脑再次复制 🧪𠮷")
                assertEquals("电脑再次复制 🧪𠮷", h.adapter.pull()!!.text)
                server.command("op" to "copy", "text" to "")
                assertNull(h.adapter.pull()); assertEquals(204, http.lastStatus)
                server.command("op" to "conflict", "value" to true)
                try { h.adapter.push(ClipboardProfile.fromText("冲突")); fail("conflict swallowed") } catch (_: Exception) {}
                assertEquals(409, http.lastStatus)
                server.command("op" to "directions", "send" to false, "receive" to true)
                assertNotNull(h.adapter.testConnection()); assertEquals(403, http.lastStatus)
                h.store.set("pairingCode", "incorrect-pairing-123456")
                assertTrue(h.adapter.onAction("testConnection")!!.contains("配对码")); assertEquals(401, http.lastStatus)
            }
        }
    }
}
