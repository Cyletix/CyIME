package com.kingzcheung.xime.clipboard.sync

import com.kingzcheung.xime.plugin.core.api.ClipboardProfile
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class WindowsPairingProtocolTest {
    // Shared network-order fixture with Windows PairingTests; these are dummy values, not credentials.
    private val compact = "cyime://pair?v=2&c=AgHAqAECSTQAESIzRFVmd4iZqrvM3e7_AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8RIjNEVWZ3iJmqu8zd7v8AAAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8AAAAAdzWUAA"

    @Test fun compactWindowsCodeAndLegacyJsonPreserveFullIdentityAndSecret() {
        val data = WindowsPairingProtocol.offer(compact)
        assertEquals("00112233-4455-6677-8899-aabbccddeeff", data.getString("server_id"))
        assertEquals("11223344-5566-7788-99aa-bbccddeeff00", data.getString("pairing_id"))
        assertEquals("https://192.168.1.2:18740", data.getJSONArray("endpoints").getString(0))
        assertEquals("000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f", data.getString("certificate_sha256"))
        assertEquals("AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8", data.getString("secret"))
        assertEquals(2_000_000_000L, data.getLong("expires_at"))
        val legacy = "cyime://pair?v=2&data=" + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(data.toString().toByteArray())
        assertEquals(data.getString("secret"), WindowsPairingProtocol.offer(legacy).getString("secret"))
        for (invalid in listOf(compact.dropLast(4), compact + "AA", compact.replace("AgH", "AgA"), "https://example.org", compact + "&redirect=x")) {
            assertTrue(runCatching { WindowsPairingProtocol.offer(invalid) }.isFailure)
        }
    }

    @Test fun scannerReadsCompactAndLegacyCodesAtPreviewSizesAndRotation() {
        val json = WindowsPairingProtocol.offer(compact)
        val legacy = "cyime://pair?v=2&data=" + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(json.toString().toByteArray())
        for (uri in listOf(compact, legacy)) for (size in listOf(240, 360, 720)) {
            val matrix = com.google.zxing.qrcode.QRCodeWriter().encode(uri, com.google.zxing.BarcodeFormat.QR_CODE, size, size)
            for (rotated in listOf(false, true)) {
                val pixels = IntArray(size * size) { index ->
                    val x = index % size; val y = index / size
                    if (if (rotated) matrix[y, size - 1 - x] else matrix[x, y]) 0xff000000.toInt() else 0xffffffff.toInt()
                }
                assertEquals("$size, rotated=$rotated", uri,
                    PairingQrDecoder.decode(com.google.zxing.RGBLuminanceSource(size, size, pixels)))
            }
        }
    }
    @Test fun discoveryAcceptsLocalLiteralAddressesOnly() {
        for (origin in listOf("https://192.168.1.10:4443", "https://10.0.0.2:8443/", "https://[fd12::2]:4443")) {
            assertEquals(origin.trimEnd('/'), WindowsPairingProtocol.localOrigin(origin))
        }
        for (origin in listOf("http://192.168.1.2:4443", "https://8.8.8.8:4443", "https://example.com:4443",
            "https://192.168.1.2:4443/path", "https://user@192.168.1.2:4443", "https://192.168.1.2:4443?redirect=1",
            "https://192.168.1.2", "https://192.168.1.2:0")) {
            assertTrue(origin, runCatching { WindowsPairingProtocol.localOrigin(origin) }.isFailure)
        }
    }

    private fun profile(text: String): JSONObject {
        val value = ClipboardProfile.fromText(text)
        return JSONObject().put("type", "text").put("text", text).put("hash", value.hash)
            .put("size", value.size).put("has_data", false).put("data_name", JSONObject.NULL)
    }

    @Test fun unicodePayloadUsesUtf8SizeAndVerifiedHash() {
        val text = "中文\n日本語 🧪 𠀀"
        val value = WindowsPairingProtocol.profile(profile(text))
        assertEquals(text, value.text)
        assertEquals(text.toByteArray(Charsets.UTF_8).size.toLong(), value.size)
        for (invalid in listOf(profile(text).put("hash", "0".repeat(64)), profile(text).put("size", text.length),
            profile(text).put("has_data", true), profile(text).put("type", "image"),
            profile("x\u0000y"), profile("中".repeat(22000)))) {
            assertTrue(runCatching { WindowsPairingProtocol.profile(invalid) }.isFailure)
        }
    }

    @Test fun aDifferentPinnedPeerOrPairingProofChangesTheConfirmationCode() {
        val pin = "a".repeat(64)
        val proof = "b".repeat(43)
        val code = WindowsPairingProtocol.code(pin, proof)
        assertTrue(code.matches(Regex("[0-9]{8}")))
        assertNotEquals(code, WindowsPairingProtocol.code("c".repeat(64), proof))
        assertNotEquals(code, WindowsPairingProtocol.code(pin, "d".repeat(43)))
        assertTrue(runCatching { WindowsPairingProtocol.fingerprint("invalid") }.isFailure)
    }
}
