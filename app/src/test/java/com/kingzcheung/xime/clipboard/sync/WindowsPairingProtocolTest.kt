package com.kingzcheung.xime.clipboard.sync

import com.kingzcheung.xime.plugin.core.api.ClipboardProfile
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class WindowsPairingProtocolTest {
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
