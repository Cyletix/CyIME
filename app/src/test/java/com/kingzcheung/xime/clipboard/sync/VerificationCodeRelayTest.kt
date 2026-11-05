package com.kingzcheung.xime.clipboard.sync

import com.kingzcheung.xime.clipboard.ClipboardManager
import com.kingzcheung.xime.ui.settings.verificationCodeSendLock
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** 验证码发到其他设备：只走 /api/code，带剩余时间；过期、旧版或被拒就停；收到的严格校验、最多留 3 分钟。 */
class VerificationCodeRelayTest {
    private val computer = WindowsPeer("pc", "DESKTOP-PC", "https://192.168.0.20:18740", "a".repeat(64), "b".repeat(43))
    private val lifetime = VerificationCodeRelay.LIFETIME_MS

    @Test fun bodyRoundTripsAndCapsTheTime() {
        assertEquals("123456" to 60_000L, VerificationCodeRelay.parse(VerificationCodeRelay.body("123456", 60_000)))
        // A sender asking for longer still gets at most the 3 minutes.
        assertEquals("1234" to lifetime, VerificationCodeRelay.parse(VerificationCodeRelay.body("1234", 3_600_000)))
        val body = VerificationCodeRelay.body("123456", 60_000)
        assertEquals(2, body.getInt("v"))
        assertEquals("code", body.getString("type"))
        assertFalse("never looks like a clipboard item", body.has("stamp"))
    }

    @Test fun rejectsAnythingThatIsNotAFreshCode() {
        fun bad(change: JSONObject.() -> Unit) = assertNull(VerificationCodeRelay.parse(VerificationCodeRelay.body("123456", 60_000).apply(change)))
        bad { put("text", "654321") } // hash no longer matches
        bad { put("v", 1) }
        bad { put("type", "text") }
        bad { put("ttl_ms", 1_000) } // too late to use
        bad { remove("ttl_ms") }
        bad { put("size", 7) }
        for (text in listOf("123", "123456789", "12a456", "你好你好", " 12345")) {
            val bytes = text.toByteArray()
            val json = JSONObject().put("v", 2).put("type", "code").put("text", text)
                .put("hash", com.kingzcheung.xime.plugin.core.api.ClipboardProfile.sha256Hex(bytes)).put("size", bytes.size).put("ttl_ms", 60_000)
            assertNull(text, VerificationCodeRelay.parse(json))
        }
    }

    @Test fun deliversOnceOverThePairedChannelWithTheTimeLeft() = runBlocking {
        val calls = mutableListOf<Triple<String, JSONObject?, Boolean>>()
        val sender = VerificationCodeSender({ p, path, body, token, _ -> assertEquals(computer, p); calls += Triple(path, body, token); 200 to JSONObject() },
            now = { 1_000_000L + 20_000L }, retryWaits = longArrayOf(0, 0, 0))
        assertTrue(sender.deliver(computer, "123456", 1_000_000L))
        assertEquals(1, calls.size)
        assertEquals("/api/code", calls[0].first)
        assertTrue("uses the pairing token", calls[0].third)
        assertEquals(lifetime - 20_000L, calls[0].second!!.getLong("ttl_ms"))
    }

    @Test fun olderOrRefusingDevicesAreNotRetried() = runBlocking {
        for (status in listOf(404, 401, 403, 400)) {
            var calls = 0
            val sender = VerificationCodeSender({ _, _, _, _, _ -> calls++; status to null }, now = { 0L }, retryWaits = longArrayOf(0, 0, 0))
            assertFalse(sender.deliver(computer, "123456", 0L))
            assertEquals("status $status", 1, calls)
        }
    }

    @Test fun unreachableDeviceIsRetriedOnlyWhileTheCodeIsFresh() = runBlocking {
        var clock = 0L
        var calls = 0
        val sender = VerificationCodeSender({ _, _, _, _, _ -> calls++; clock += 90_000L; throw IOException("offline") },
            now = { clock }, retryWaits = longArrayOf(0, 0, 0, 0))
        assertFalse(sender.deliver(computer, "123456", 0L))
        // 0 s and 90 s go out; at 180 s the code has expired, so nothing more is sent.
        assertEquals(2, calls)
    }

    @Test fun staleCodeIsNeverSent() = runBlocking {
        var calls = 0
        val sender = VerificationCodeSender({ _, _, _, _, _ -> calls++; 200 to null }, now = { lifetime }, retryWaits = longArrayOf(0))
        assertFalse(sender.deliver(computer, "123456", 0L))
        assertEquals(0, calls)
    }

    @Test fun receivedCodeExpiresByItsOwnTimeButNeverLaterThanALocalOne() {
        val t = 1_000_000L
        assertEquals(t + ClipboardManager.SMS_CODE_LIFETIME_MS, ClipboardManager.smsCodeExpiresAt(t, 0L))
        assertEquals(t + 60_000L, ClipboardManager.smsCodeExpiresAt(t, t + 60_000L))
        assertEquals(t + ClipboardManager.SMS_CODE_LIFETIME_MS, ClipboardManager.smsCodeExpiresAt(t, t + 3_600_000L))
    }

    @Test fun switchIsLockedUntilCodesAndDeviceSyncAreOn() {
        assertNotNull(verificationCodeSendLock(smsCodeOn = false, syncOn = true))
        assertNotNull(verificationCodeSendLock(smsCodeOn = true, syncOn = false))
        assertNull(verificationCodeSendLock(smsCodeOn = true, syncOn = true))
    }
}
