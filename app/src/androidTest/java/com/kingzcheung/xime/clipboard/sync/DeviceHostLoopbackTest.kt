package com.kingzcheung.xime.clipboard.sync

import androidx.test.platform.app.InstrumentationRegistry
import com.kingzcheung.xime.plugin.core.api.ClipboardProfile
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

/**
 * Pairs this device's client code with its own [DeviceHost] over the real network stack:
 * Keystore TLS key, pinned handshake, one-time QR secret, bearer token, clipboard GET/PUT.
 * Uses synthetic clipboard text only.
 */
class DeviceHostLoopbackTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private var wasEnabled = false

    @After fun restore() { DeviceHost.closeOffer(); DeviceHost.setEnabled(wasEnabled) }

    @Test fun anotherDeviceCanPairAndSyncTextButNotWithoutToken() {
        DeviceHost.initialize(context)
        wasEnabled = DeviceHost.enabled.value
        DeviceHost.setEnabled(true)
        val qr = requireNotNull(DeviceHost.openOffer())
        val offer = WindowsPairingProtocol.offer(qr)
        val origin = offer.getJSONArray("endpoints").getString(0)
        val peer = WindowsPeer(offer.getString("server_id"), offer.getString("name"), origin, offer.getString("certificate_sha256"))

        val pairing = JSONObject().put("v", 2).put("pairing_id", offer.getString("pairing_id"))
            .put("client_id", UUID.randomUUID().toString()).put("client_name", "测试平板").put("client_kind", "android")
        val wrong = WindowsSecureTransport.request(peer, "/api/pair", JSONObject(pairing.toString()).put("secret", "A".repeat(43)))
        assertEquals("wrong secret is refused", 401, wrong.first)
        val (code, body) = WindowsSecureTransport.request(peer, "/api/pair", pairing.put("secret", offer.getString("secret")))
        assertEquals(201, code)
        val paired = WindowsPairingProtocol.received(peer, requireNotNull(body))
        assertEquals("the QR secret is single-use", 410, WindowsSecureTransport.request(peer, "/api/pair", pairing).first)

        assertEquals(401, WindowsSecureTransport.request(peer.copy(token = "B".repeat(43)), "/api/status", null, true).first)
        val status = WindowsSecureTransport.request(paired, "/api/status", null, true)
        assertEquals(200, status.first)
        assertEquals(2, status.second?.optInt("v"))

        val text = "测试同步文字 ${UUID.randomUUID()}"
        val profile = ClipboardProfile.fromText(text)
        val put = JSONObject().put("type", "text").put("text", text).put("hash", profile.hash).put("size", profile.size)
            .put("has_data", false).put("data_name", JSONObject.NULL).put("source", "test")
        assertEquals(200, WindowsSecureTransport.request(paired, "/api/clipboard", put, true).first)
        val (getCode, got) = WindowsSecureTransport.request(paired, "/api/clipboard", null, true)
        assertEquals(200, getCode)
        assertEquals(text, got?.getString("text"))
        assertEquals("unchanged clipboard answers 304", 304,
            WindowsSecureTransport.request(paired, "/api/clipboard", null, true, profile.hash).first)

        DeviceHost.devices.value.firstOrNull { it.name == "测试平板" }?.let(DeviceHost::remove)
        assertEquals("removed device loses access", 401, WindowsSecureTransport.request(paired, "/api/status", null, true).first)
    }
}
