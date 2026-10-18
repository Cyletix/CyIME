package com.kingzcheung.xime.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kingzcheung.xime.settings.FrostedGlassConfig
import com.kingzcheung.xime.ui.theme.SamsungWindowBlur
import kotlin.math.roundToInt

/** Edits a preview draft, never writes preferences before Apply. */
@Composable
internal fun FrostedThemeControls(config: FrostedGlassConfig, isDark: Boolean,
    blurAvailable: Boolean = rememberCrossWindowBlurAvailable(), compact: Boolean = false,
    onChange: (FrostedGlassConfig) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 8.dp)) {
        if (!compact) Text(if (isDark) "深色玻璃效果" else "浅色玻璃效果", style = MaterialTheme.typography.titleMedium)
        GlassSlider("模糊强度", "${config.blurRadiusDp.roundToInt()} dp" +
            if (blurAvailable) "" else " · 当前系统不可用", config.blurRadiusDp, 0f..40f, enabled = blurAvailable) {
            onChange(config.copy(blurRadiusDp = it.roundToInt().toFloat()))
        }
        GlassSlider("背景不透明度", "${(config.backgroundOpacity * 100).roundToInt()}%", config.backgroundOpacity) {
            onChange(config.copy(backgroundOpacity = (it * 100).roundToInt() / 100f))
        }
        GlassSlider("按键不透明度", "${(config.keyOpacity * 100).roundToInt()}%", config.keyOpacity) {
            onChange(config.copy(keyOpacity = (it * 100).roundToInt() / 100f))
        }
        if (!compact) Text("背景与按键独立调整，切换深浅时互换；取消不保存。文字和图标不随这两个透明度淡化。" +
            "调得过透时，键盘会自动加深到在白色和黑色页面上都能看清按键文字。",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun GlassSlider(label: String, valueLabel: String, value: Float,
    range: ClosedFloatingPointRange<Float> = 0f..1f, enabled: Boolean = true, onChange: (Float) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Text(valueLabel, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
        Slider(value, onChange, valueRange = range, enabled = enabled,
            modifier = Modifier.semantics { contentDescription = label })
    }
}

@Composable
private fun rememberCrossWindowBlurAvailable(): Boolean {
    if (android.os.Build.VERSION.SDK_INT < 31) return false
    val context = LocalContext.current
    val manager = remember(context) { context.getSystemService(android.view.WindowManager::class.java) }
    var available by remember(manager) { mutableStateOf(runCatching { manager?.isCrossWindowBlurEnabled == true }.getOrDefault(false)) }
    DisposableEffect(manager) {
        val listener = java.util.function.Consumer<Boolean> { available = it }
        val registered = manager != null && runCatching {
            manager.addCrossWindowBlurEnabledListener(context.mainExecutor, listener)
        }.isSuccess
        if (!registered) available = false
        onDispose {
            if (registered) runCatching { manager?.removeCrossWindowBlurEnabledListener(listener) }
        }
    }
    return available || SamsungWindowBlur.isAvailable
}
