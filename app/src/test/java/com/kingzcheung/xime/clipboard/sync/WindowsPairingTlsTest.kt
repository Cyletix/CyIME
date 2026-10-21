package com.kingzcheung.xime.clipboard.sync

import com.kingzcheung.xime.plugin.core.api.ClipboardProfile
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyStore
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket

/** Real loopback TLS exchanges with throwaway test certificates; never reads a real clipboard. */
class WindowsPairingTlsTest {
    /**
     * Robolectric tests that ran earlier in the same JVM leave Conscrypt installed as the first
     * security provider. With it in front, the fixture's TLS server dropped every connection (and the
     * JDK's own TLS cannot even start), so this class failed inside the suite but passed alone. Each
     * test runs on the JDK providers, as it does alone, and puts the list back afterwards.
     */
    private val setAside = mutableListOf<Pair<Int, java.security.Provider>>()

    @Before fun useJdkProviders() {
        java.security.Security.getProviders().withIndex().filter { it.value.name.contains("Conscrypt", ignoreCase = true) }
            .forEach { setAside += (it.index + 1) to it.value }
        setAside.forEach { java.security.Security.removeProvider(it.second.name) }
    }

    @After fun restoreProviders() {
        setAside.sortedBy { it.first }.forEach { java.security.Security.insertProviderAt(it.second, it.first) }
        setAside.clear()
    }

    private val serverId = "00112233-4455-6677-8899-aabbccddeeff"
    private val token = "b".repeat(43)

    private inner class Fixture : AutoCloseable {
        val directory: Path = Files.createTempDirectory("cyime-pairing-tls-")
        private val executor = Executors.newCachedThreadPool()
        val calls = AtomicInteger()
        val authorization = AtomicReference<String?>()
        var redirect: String? = null
        private val server: SSLServerSocket
        val peer: WindowsPeer
        init {
            val keytool = Path.of(System.getProperty("java.home"), "bin", if (System.getProperty("os.name").startsWith("Windows")) "keytool.exe" else "keytool").toString()
            val storePath = directory.resolve("test.p12")
            val process = ProcessBuilder(keytool, "-genkeypair", "-alias", "fixture", "-keyalg", "EC",
                "-dname", "CN=CyIME local test fixture", "-validity", "2", "-storetype", "PKCS12",
                "-keystore", storePath.toString(), "-storepass", "test-only-password", "-noprompt")
                .redirectErrorStream(true).redirectOutput(directory.resolve("keytool.log").toFile()).start()
            check(process.waitFor(30, TimeUnit.SECONDS) && process.exitValue() == 0) { "Could not create throwaway TLS fixture" }
            val keys = KeyStore.getInstance("PKCS12").apply { Files.newInputStream(storePath).use { load(it, "test-only-password".toCharArray()) } }
            val managers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(keys, "test-only-password".toCharArray()) }
            val tls = SSLContext.getInstance("TLS").apply { init(managers.keyManagers, null, null) }
            server = tls.serverSocketFactory.createServerSocket(0, 8, InetAddress.getByName("127.0.0.1")) as SSLServerSocket
            executor.submit {
                while (!server.isClosed) {
                    val socket = try { server.accept() as SSLSocket } catch (_: java.io.IOException) { break }
                    executor.submit {
                        // A rejected TLS handshake is expected in the certificate-change test.
                        runCatching { socket.use { serve(it) } }
                    }
                }
            }
            peer = WindowsPeer(serverId, "测试电脑", "https://127.0.0.1:${server.localPort}",
                ClipboardProfile.sha256Hex(keys.getCertificate("fixture").encoded))
        }
        private fun serve(socket: SSLSocket) {
            socket.soTimeout = 5_000
            socket.startHandshake()
            val input = BufferedInputStream(socket.inputStream)
            fun line(): String {
                val bytes = ByteArrayOutputStream()
                while (true) {
                    val byte = input.read()
                    check(byte >= 0 && bytes.size() < 8192)
                    if (byte == 10) return bytes.toString("UTF-8").trimEnd('\r')
                    bytes.write(byte)
                }
            }
            val requestLine = line()
            val headers = mutableMapOf<String, String>()
            while (true) {
                val header = line()
                if (header.isEmpty()) break
                headers[header.substringBefore(':').lowercase()] = header.substringAfter(':').trim()
            }
            repeat(headers["content-length"]?.toInt() ?: 0) { check(input.read() >= 0) }
            calls.incrementAndGet()
            authorization.set(headers["authorization"])
            val location = redirect
            val pair = requestLine.substringAfter(' ').startsWith("/api/pair")
            val json = JSONObject().put("v", 2)
            if (pair) json.put("server_id", serverId).put("device_id", "11223344-5566-7788-99aa-bbccddeeff00").put("access_token", token)
            val bytes = if (location != null) byteArrayOf() else json.toString().toByteArray()
            val status = if (location != null) "302 Found" else if (pair) "201 Created" else "200 OK"
            val response = "HTTP/1.1 $status\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n" +
                (if (location != null) "Location: $location\r\n" else "") + "\r\n"
            socket.outputStream.write(response.toByteArray())
            socket.outputStream.write(bytes)
            socket.outputStream.flush()
        }
        override fun close() {
            server.close()
            executor.shutdownNow()
            val root = directory.toRealPath()
            check(root.startsWith(Path.of(System.getProperty("java.io.tmpdir")).toRealPath()))
            Files.walk(root).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach {
                check(it.startsWith(root)); Files.deleteIfExists(it)
            } }
        }
    }

    @Test fun strangerWithAValidSelfAdvertisedCertificateAnd201StillNeedsLocalConsent() = runBlocking {
        Fixture().use { service ->
            val saved = mutableListOf<WindowsPeer>()
            val pairing = WindowsNearbyPairing(this, { JSONObject().put("secret", it) },
                { peer, body -> withContext(Dispatchers.IO) { WindowsSecureTransport.request(peer, "/api/pair/nearby", body) } },
                saved::add, {}, { System.nanoTime() / 1_000_000 })
            try {
                pairing.connect(service.peer)
                val pending = withTimeout(10_000) { pairing.confirmation.first { it?.ready == true }!! }
                assertEquals(1, service.calls.get())
                assertNull(service.authorization.get())
                assertTrue("remote success must not persist credentials or enable a session", saved.isEmpty())
                assertTrue(pairing.confirm(pending.requestId))
                assertEquals(service.peer.copy(token = token), saved.single())
            } finally { pairing.cancel() }
        }
    }

    @Test fun trustedPeerReconnectsButSameIdWithAChangedCertificateGetsNoToken() = runBlocking {
        Fixture().use { trusted -> Fixture().use { replacement ->
            var saved = trusted.peer.copy(token = token)
            val transport = WindowsSecureTransport { saved }
            assertNull(transport.testConnection())
            assertEquals("Bearer $token", trusted.authorization.get())
            // Discovery may update the address, never the previously trusted pin or token.
            saved = saved.copy(origin = replacement.peer.origin)
            assertNotEquals(saved.fingerprint, replacement.peer.fingerprint)
            assertNotNull(transport.testConnection())
            assertEquals(0, replacement.calls.get())
            assertNull(replacement.authorization.get())
            saved = saved.copy(origin = trusted.peer.origin)
            assertNull(transport.testConnection())
        } }
    }

    @Test fun redirectCannotMoveAnAuthenticatedRequestToAnotherEndpoint() {
        Fixture().use { trusted -> Fixture().use { other ->
            trusted.redirect = other.peer.origin + "/api/status"
            val result = WindowsSecureTransport.request(trusted.peer.copy(token = token), "/api/status", token = true)
            assertEquals(302, result.first)
            assertEquals(0, other.calls.get())
        } }
    }
}
