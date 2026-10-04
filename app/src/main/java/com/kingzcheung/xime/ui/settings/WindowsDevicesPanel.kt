package com.kingzcheung.xime.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kingzcheung.xime.clipboard.sync.WindowsDevices
import com.kingzcheung.xime.settings.SettingsPreferences
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun WindowsDevicesPanel() {
    val context = LocalContext.current
    val devices by WindowsDevices.nearby.collectAsState()
    val status by WindowsDevices.status.collectAsState()
    val paired by WindowsDevices.paired.collectAsState()
    val confirm by WindowsDevices.confirm.collectAsState()
    val scope = rememberCoroutineScope()
    var cameraError by remember { mutableStateOf<String?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) scope.launch {
            val text = withContext(Dispatchers.Default) {
                runCatching {
                    val pixels = IntArray(bitmap.width * bitmap.height)
                    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                    MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(bitmap.width, bitmap.height, pixels)))).text
                }.getOrNull()
            }
            if (text != null) { cameraError = null; WindowsDevices.pairQr(text) }
            else cameraError = "未识别到二维码，请靠近电脑二维码重试"
        }
    }
    DisposableEffect(Unit) {
        WindowsDevices.initialize(context); WindowsDevices.start()
        onDispose { if (!SettingsPreferences.isClipboardSyncEnabled(context)) WindowsDevices.stop() }
    }
    SettingsSection(title = "附近电脑", content = {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(status)
            if (paired == null) {
                Text("电脑开启加密连接、局域网和附近发现。自动寻找，无需填写；首次确认两边连接码，以后同网自动重连。", style = MaterialTheme.typography.bodyMedium)
                devices.forEach { peer ->
                    OutlinedButton(onClick = { WindowsDevices.requestNearby(peer) }) { Text("连接 ${peer.name}") }
                }
                if (confirm) Button(onClick = {
                    runCatching { WindowsDevices.confirmConnection() }.onFailure { cameraError = it.message ?: "连接保存失败，请重试" }
                }) { Text("代码一致，连接并同步") }
                OutlinedButton(onClick = { runCatching { camera.launch(null) }.onFailure { cameraError = "无法打开相机" } }) { Text("扫码连接") }
                cameraError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            } else {
                Text("${paired!!.name} · 加密文本同步", style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = { WindowsDevices.forget() }) { Text("断开并取消本机配对") }
            }
        }
    })
}
