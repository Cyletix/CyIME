package com.kingzcheung.xime.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Sms
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.kingzcheung.xime.settings.SettingsPreferences

@Composable
internal fun VerificationCodeSettings() {
    val context = LocalContext.current
    fun hasPermission() = ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
    var enabled by remember { mutableStateOf(SettingsPreferences.isSmsCodeEnabled(context) && hasPermission()) }
    var denied by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        enabled = granted
        denied = !granted
        SettingsPreferences.setSmsCodeEnabled(context, granted)
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        enabled = SettingsPreferences.isSmsCodeEnabled(context) && hasPermission()
    }
    VerificationCodeSettingsCard(enabled, denied) { checked ->
        denied = false
        if (checked && !hasPermission()) launcher.launch(Manifest.permission.RECEIVE_SMS)
        else {
            enabled = checked
            SettingsPreferences.setSmsCodeEnabled(context, checked)
        }
    }
}

@Composable
internal fun VerificationCodeSettingsCard(enabled: Boolean, denied: Boolean, onToggle: (Boolean) -> Unit) {
    SettingsSection(title = "验证码", content = {
        Column {
            SettingsToggleItem(Icons.TwoTone.Sms, "新短信验证码自动复制",
                "需授权接收短信。识别中日英文特征词附近的 4～8 位数字，不读取历史短信。自动复制的验证码不加入历史或同步，候选保留 5 分钟。",
                enabled, onToggle)
            Text("复制短信后，候选栏会显示提取的验证码。单框点击验证码；自动跳格的分格框选择逐位填入。不能保证识别所有分格框。", Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            if (denied) Text("未获得短信权限；仍可复制短信提取验证码。部分系统或安装来源会限制此权限，请在应用权限中检查。",
                modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
        }
    })
}
