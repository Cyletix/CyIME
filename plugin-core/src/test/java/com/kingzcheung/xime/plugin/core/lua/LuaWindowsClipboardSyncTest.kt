package com.kingzcheung.xime.plugin.core.lua

import com.kingzcheung.xime.plugin.core.api.ClipboardProfile
import com.kingzcheung.xime.plugin.core.lua.http.HttpResponse
import com.kingzcheung.xime.plugin.core.lua.sdk.SimpleJson
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.Base64

class LuaWindowsClipboardSyncTest {
    private fun response(text: String, headers: Map<String, String> = emptyMap()): HttpResponse {
        val p = ClipboardProfile.fromText(text)
        return HttpResponse(200, headers, SimpleJson.encode(mapOf("type" to "text", "hash" to p.hash,
            "text" to text, "has_data" to false, "data_name" to null, "size" to p.size,
            "source" to "CyIME-Windows")).toByteArray(Charsets.UTF_8))
    }
    @Test fun `PUT uses UTF8 profile and fixed username without a separate username setting`() = runBlocking {
        val http = WindowsSyncHarness.Http()
        WindowsSyncHarness(http).use { h ->
            val text = "手机：中文\n换行 😀\r\n第二行"
            http.responses += response(text)
            assertTrue(h.adapter.push(ClipboardProfile.fromText(text)))
            val request = http.requests.single()
            assertEquals("PUT", request.method)
            assertEquals("http://127.0.0.1:18740/api/clipboard", request.url)
            assertEquals("Basic " + Base64.getEncoder().encodeToString("cyime:test-only-pairing-123456".toByteArray()), request.headers["Authorization"])
            val body = SimpleJson.decode(request.body!!.toString(Charsets.UTF_8)) as Map<*, *>
            assertEquals(text, body["text"])
            assertEquals(text.toByteArray().size.toLong(), (body["size"] as Number).toLong())
            assertEquals(listOf("serverUrl", "pairingCode", "testConnection"), h.adapter.getSettingsSchema().map { it.key })
        }
    }
    @Test fun `GET caches ETag only after host acknowledges and handles case insensitive header`() = runBlocking {
        val http = WindowsSyncHarness.Http()
        WindowsSyncHarness(http).use { h ->
            repeat(2) { http.responses += response("电脑", mapOf("eTaG" to "\"etag\"")) }
            val p = h.adapter.pull()!!
            h.adapter.pull()
            assertNull(http.requests.last().headers["If-None-Match"])
            h.adapter.acknowledgePull(p.hash)
            http.responses += HttpResponse(304)
            assertNull(h.adapter.pull())
            assertEquals("\"etag\"", http.requests.last().headers["If-None-Match"])
            assertNull(h.store.get("lastEtag")) // No disk cache that could outlive an unapplied item.
        }
    }
    @Test fun `changing pairing or endpoint invalidates ETag`() = runBlocking {
        val http = WindowsSyncHarness.Http()
        WindowsSyncHarness(http).use { h ->
            http.responses += response("old", mapOf("ETag" to "old"))
            h.adapter.acknowledgePull(h.adapter.pull()!!.hash)
            h.store.set("serverUrl", "http://192.168.1.88:18740/")
            http.responses += response("new")
            assertEquals("new", h.adapter.pull()!!.text)
            assertNull(http.requests.last().headers["If-None-Match"])
        }
    }
    @Test fun `changing endpoint during request rejects old computers response`() = runBlocking {
        val http = WindowsSyncHarness.Http()
        WindowsSyncHarness(http).use { h ->
            http.responses += response("旧电脑")
            http.onRequest = { h.store.set("serverUrl", "http://192.168.1.99:18740") }
            try { h.adapter.pull(); fail("old peer response accepted") } catch (_: Exception) {}
            http.onRequest = {}
            http.responses += response("新电脑")
            assertEquals("新电脑", h.adapter.pull()!!.text)
            assertNull(http.requests.last().headers["If-None-Match"])
        }
    }
    @Test fun `empty clipboard does not clear phone and clears stale ETag`() = runBlocking {
        val http = WindowsSyncHarness.Http()
        WindowsSyncHarness(http).use { h ->
            http.responses += response("old", mapOf("ETag" to "old")); h.adapter.acknowledgePull(h.adapter.pull()!!.hash)
            http.responses += HttpResponse(204); assertNull(h.adapter.pull())
            http.responses += response("old"); assertNotNull(h.adapter.pull())
            assertNull(http.requests.last().headers["If-None-Match"])
        }
    }
    @Test fun `bad credentials forbidden direction conflict and offline are failures not no change`() = runBlocking {
        val http = WindowsSyncHarness.Http()
        WindowsSyncHarness(http).use { h ->
            listOf(401, 403, 409, 503, 413).forEach { code ->
                http.responses += HttpResponse(code)
                try { h.adapter.pull(); fail("HTTP $code was swallowed") } catch (_: Exception) {}
            }
            try { h.adapter.pull(); fail("offline was swallowed") } catch (_: Exception) {}
            http.responses += HttpResponse(401)
            assertTrue(h.adapter.onAction("testConnection")!!.contains("配对码"))
        }
    }
    @Test fun `non JSON success and corrupt profile cannot pass connection test or be applied`() = runBlocking {
        val http = WindowsSyncHarness.Http()
        WindowsSyncHarness(http).use { h ->
            listOf("<html>portal</html>", "{}", "{\"type\":\"text\",\"text\":\"bad\",\"hash\":\"wrong\",\"size\":3,\"has_data\":false}").forEach {
                http.responses += HttpResponse(200, body = it.toByteArray())
                try { h.adapter.pull(); fail("invalid profile") } catch (_: Exception) {}
                http.responses += HttpResponse(200, body = it.toByteArray())
                assertNotNull(h.adapter.onAction("testConnection"))
            }
        }
    }
    @Test fun `bad address or missing pairing is rejected before network`() = runBlocking {
        val http = WindowsSyncHarness.Http()
        WindowsSyncHarness(http).use { h ->
            listOf("", "file:///tmp/a", "http://a/api/clipboard", "http://user:pass@a", "http://a?key=secret").forEach {
                h.store.set("serverUrl", it); assertNotNull(h.adapter.testConnection())
            }
            h.store.set("serverUrl", "http://a:18740"); h.store.set("pairingCode", "")
            assertNotNull(h.adapter.testConnection()); assertTrue(http.requests.isEmpty())
        }
    }
    @Test fun `oversized and NUL text never reach network`() = runBlocking {
        val http = WindowsSyncHarness.Http()
        WindowsSyncHarness(http).use { h ->
            listOf("中".repeat(30_000), "x\u0000y").forEach {
                try { h.adapter.push(ClipboardProfile.fromText(it)); fail("bad text accepted") } catch (_: Exception) {}
            }
            assertTrue(http.requests.isEmpty())
        }
    }
}
