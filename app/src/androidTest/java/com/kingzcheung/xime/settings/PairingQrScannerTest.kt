package com.kingzcheung.xime.settings

import android.Manifest
import android.graphics.BitmapFactory
import android.os.SystemClock
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.google.zxing.RGBLuminanceSource
import com.kingzcheung.xime.clipboard.sync.PairingQrDecoder
import com.kingzcheung.xime.clipboard.sync.WindowsPairingProtocol
import com.kingzcheung.xime.ui.saveUiEvidence
import com.kingzcheung.xime.ui.settings.WindowsPairingScanner
import com.kingzcheung.xime.ocr.OcrCameraPanel
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PairingQrScannerTest {
    @get:Rule val rule = createComposeRule()
    @get:Rule val camera = GrantPermissionRule.grant(Manifest.permission.CAMERA)

    @Test fun readsWindowsQRCoderOutputWithTheAndroidDecoder() {
        // Dummy protocol fixture rendered using Windows QRCoder 1.8.0, ECC M, 4 pixels/module.
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        val bitmap = assets.open("qr/windows-compact.png").use { BitmapFactory.decodeStream(it) }
        try {
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            val start = SystemClock.elapsedRealtime()
            val result = PairingQrDecoder.scan(RGBLuminanceSource(bitmap.width, bitmap.height, pixels))
            println("Windows fixture QR decode: ${SystemClock.elapsedRealtime() - start}ms, ${bitmap.width}x${bitmap.height}")
            assertTrue(result is PairingQrDecoder.Result.PairingCode)
            val offer = WindowsPairingProtocol.offer((result as PairingQrDecoder.Result.PairingCode).text)
            assertEquals("00112233-4455-6677-8899-aabbccddeeff", offer.getString("server_id"))
            assertEquals("https://192.168.1.2:18740", offer.getJSONArray("endpoints").getString(0))
        } finally { bitmap.recycle() }
    }

    @Test fun cameraStreamsInsideScannerAndReleasesForReopening() {
        val visible = mutableStateOf(true)
        val ocr = mutableStateOf(false)
        var connections = 0
        rule.setContent { MaterialTheme {
            if (visible.value) WindowsPairingScanner(onDismiss = { visible.value = false }) { connections++ }
            if (ocr.value) Box(Modifier.requiredSize(500.dp, 260.dp)) {
                OcrCameraPanel(true, Color.Black, Color.White, Color.Cyan, { ocr.value = false }, {})
            }
        } }
        repeat(2) { round ->
            rule.waitUntil(15000) { rule.onAllNodesWithTag("pairing-camera-ready").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
            rule.onNodeWithText("扫码连接电脑").assertIsDisplayed()
            rule.onNodeWithText("从图片识别").assertIsDisplayed()
            rule.onNodeWithText("关闭").assertIsDisplayed()
            saveUiEvidence("pairing-scanner-$round", rule.onNodeWithTag("pairing-scanner"))
            rule.onNodeWithText("关闭").performClick()
            rule.onNodeWithTag("pairing-scanner").assertDoesNotExist()
            if (round == 0) rule.runOnIdle { visible.value = true }
        }
        rule.runOnIdle { assertEquals(0, connections) }
        rule.runOnIdle { ocr.value = true }
        rule.waitUntil(15000) {
            rule.onAllNodesWithTag("ocr-capture").fetchSemanticsNodes(atLeastOneRootRequired = false).any {
                !it.config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled)
            }
        }
        saveUiEvidence("ocr-camera-after-scanner", rule.onNodeWithTag("ocr-camera-panel"))
        rule.onNodeWithText("取消").performClick()
    }
}
