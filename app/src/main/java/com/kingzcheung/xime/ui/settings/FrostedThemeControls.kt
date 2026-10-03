package com.kingzcheung.xime.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kingzcheung.xime.settings.FrostedGlassConfig
import kotlin.math.roundToInt

/** Edits a preview draft, never writes preferences before Apply. */
@Composable
internal fun FrostedThemeControls(config: FrostedGlassConfig, isDark: Boolean, onChange: (FrostedGlassConfig) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(if (isDark) "深色玻璃效果" else "浅色玻璃效果", style = MaterialTheme.typography.titleMedium)
        GlassSlider("模糊强度", "${config.blurRadiusDp.roundToInt()} dp", config.blurRadiusDp, 0f..40f) {
            onChange(config.copy(blurRadiusDp = it.roundToInt().toFloat()))
        }
        GlassSlider("背景遮罩不透明度", "${(config.backgroundOpacity * 100).roundToInt()}%", config.backgroundOpacity) {
            onChange(config.copy(backgroundOpacity = (it * 100).roundToInt() / 100f))
        }
        GlassSlider("按键不透明度", "${(config.keyOpacity * 100).roundToInt()}%", config.keyOpacity) {
            onChange(config.copy(keyOpacity = (it * 100).roundToInt() / 100f))
        }
        Text("浅色与深色分别调整，随明暗模式自动切换；点应用同时保存两组。模糊作用于键盘背景，文字保持清晰。",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun GlassSlider(label: String, valueLabel: String, value: Float,
    range: ClosedFloatingPointRange<Float> = 0f..1f, onChange: (Float) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Text(valueLabel, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
        Slider(value, onChange, valueRange = range,
            modifier = Modifier.semantics { contentDescription = label })
    }
}
